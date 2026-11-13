package ca.vina.nexu.effectifs.domaine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>Intégrations des effectifs</strong> — AO 26-015-DO, lot R10 (E-TN-7, P-TN-7 ; RAO, SAAV, PFT, BD Corpo,
 * Microsoft Graph, ESB, entrepôt de données) ; jumeau pur de {@code backend/src/effectifs/integrations/domaine.ts}.
 *
 * <p>Chaque système externe est un <strong>connecteur</strong> : sortant (on lui remet des messages par une boîte d'envoi
 * transactionnelle, livrés et retentés par l'ordonnanceur) ou entrant (on tire ses données par un port). Le mode — simulateur
 * ou HTTP — est une <strong>donnée</strong> de configuration de l'organisme, jamais une condition dans le code métier ; le
 * secret d'un adaptateur HTTP ne vit jamais en base, seulement le <strong>nom</strong> de la variable d'environnement qui le porte.
 */
public final class IntegrationsEffectifs {

    /** Un versement à l'entrepôt regroupe au plus ce nombre d'entrées de journal : un message reste de taille raisonnable. */
    public static final int TAILLE_VERSEMENT = 500;
    /** Le curseur d'une source jamais lue : tout ce qu'elle a produit. */
    public static final String ORIGINE_DES_TEMPS = "1970-01-01T00:00:00.000Z";
    /** Au-delà, une ancienneté reçue est une erreur de saisie de la source, pas une carrière. */
    public static final int ANCIENNETE_MAX_ANNEES = 60;
    /** Le nom sous lequel le journal consigne ce que les intégrations décident seules. */
    public static final String MECANISME = "integrations-effectifs";

    private static final Pattern NOM_VARIABLE = Pattern.compile("^[A-Z][A-Z0-9_]{2,63}$");
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final int SUCCES_MIN = 200;
    private static final int SUCCES_MAX = 300;
    /** Le destinataire a dit non : retenter ne changera rien. */
    private static final Set<Integer> REFUS = Set.of(400, 409, 422);
    private static final double CENTIEMES = 100.0;
    private static final Set<String> HOTES_LOCAUX = Set.of("localhost", "127.0.0.1");
    private static final String SCHEMA_SUR = "https";
    private static final String SCHEMA_LOCAL = "http";
    private static final String SCHEMA_SFTP = "sftp";
    private static final int PORT_SSH = 22;
    private static final String PARAMETRE_EMPREINTE = "empreinte=";
    /** L'empreinte SHA-256 d'une clé d'hôte, telle que {@code ssh-keygen -lf} l'écrit : « SHA256: » et 43 caractères base64. */
    private static final Pattern EMPREINTE = Pattern.compile("^SHA256:[A-Za-z0-9+/]{43}$");

    private IntegrationsEffectifs() {
    }

    public enum Sens { SORTANT, ENTRANT }

    /** Ce qu'un connecteur sortant accepte ; son chemin chez le destinataire : {@code LOT_PAIE} → {@code /lot-paie}. */
    public enum TypeMessage {
        EQUIPAGE, LOT_PAIE, QUART_CALENDRIER, EVENEMENT, VERSEMENT_JOURNAL;

        public String chemin() {
            return "/" + name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /** Ce que chaque connecteur échange, et dans quel sens. Ajouter un connecteur, c'est ajouter une ligne ici et son port. */
    public enum CodeConnecteur {
        RAO(Sens.ENTRANT, "RAO — répartition assistée par ordinateur (horodatages)"),
        SAAV(Sens.SORTANT, "SAAV — attitrage des véhicules (équipages)", TypeMessage.EQUIPAGE),
        PFT(Sens.SORTANT, "PFT — production des feuilles de temps (lots de paie)", TypeMessage.LOT_PAIE),
        BD_CORPO(Sens.ENTRANT, "BD Corpo — dossier employé (ancienneté)"),
        GRAPH(Sens.SORTANT, "Microsoft Graph — calendriers", TypeMessage.QUART_CALENDRIER),
        ESB(Sens.SORTANT, "Bus d'entreprise (ESB) — événements", TypeMessage.EVENEMENT),
        ENTREPOT(Sens.SORTANT, "Entrepôt de données — versements", TypeMessage.VERSEMENT_JOURNAL);

        private final Sens sens;
        private final String libelle;
        private final List<TypeMessage> types;

        CodeConnecteur(Sens sens, String libelle, TypeMessage... types) {
            this.sens = sens;
            this.libelle = libelle;
            this.types = List.of(types);
        }

        public Sens sens() {
            return sens;
        }

        public String libelle() {
            return libelle;
        }

        public List<TypeMessage> types() {
            return types;
        }
    }

    /** Simulateur (essai), HTTP (appel par message), SFTP (fichiers de lot déposés et cueillis — addenda 06, réponse 3). */
    public enum Mode { SIMULATEUR, HTTP, SFTP }

    /**
     * Un serveur SFTP : le compte, l'hôte, le répertoire de dépôt, et l'empreinte de sa clé d'hôte. L'empreinte est
     * exigée : sans elle, la première connexion ferait confiance à n'importe quel serveur qui répond à cette adresse.
     */
    public record AdresseSftp(String compte, String hote, int port, String repertoire, String empreinte) { }

    public enum EtatMessage {
        EN_ATTENTE, ENVOYE, REJETE, ECHEC;

        /** Un message rejeté par le destinataire ou en échec durable se rejoue ; un message envoyé, jamais (idempotence). */
        public boolean rejouable() {
            return this == REJETE || this == ECHEC;
        }
    }

    /** La configuration d'un connecteur pour un organisme : jamais un jeton, seulement le nom de sa variable. */
    public record ConfigConnecteur(Mode mode, String url, String jetonRef, boolean actif) {
        public static final ConfigConnecteur DEFAUT = new ConfigConnecteur(Mode.SIMULATEUR, null, null, false);
    }

    /** Un connecteur tel que gardé : sa configuration, son jeu d'essai (simulateur) et son curseur de lecture. */
    public record ConnecteurEnregistre(CodeConnecteur code, ConfigConnecteur config, List<Object> jeuEssai, String curseur) {
        public ConnecteurEnregistre {
            // Un jeu d'essai peut porter des éléments nuls (il est rendu tel que saisi) : List.copyOf les refuserait.
            jeuEssai = Collections.unmodifiableList(new ArrayList<>(jeuEssai));
        }

        public static ConnecteurEnregistre parDefaut(CodeConnecteur code) {
            return new ConnecteurEnregistre(code, ConfigConnecteur.DEFAUT, List.of(), null);
        }
    }

    /** Un message de la boîte d'envoi, tel que l'API le rend. */
    public record MessageSortant(String id, CodeConnecteur connecteur, TypeMessage type, String cle, Map<String, Object> charge, EtatMessage etat,
                                 int tentatives, String refExterne, String motif, String deposeLe, String deposePar, String traiteLe) { }

    /** Ce qu'une destination reçoit : la clé est la clé d'idempotence. */
    public record MessageALivrer(String id, CodeConnecteur connecteur, TypeMessage type, String cle, Map<String, Object> charge) { }

    /** L'issue d'une livraison : acceptée (avec la référence du destinataire) ou rejetée (motif). */
    public sealed interface Issue permits Acceptee, Rejetee { }

    public record Acceptee(String refExterne) implements Issue { }

    public record Rejetee(String motif) implements Issue { }

    public record HorodatageExterne(String cle, String matricule, String sens, String horodatage) { }

    public record DossierEmploye(String matricule, double anciennete) { }

    /** Un équipage publié tel que le SAAV le reçoit, avec ses membres dans l'ordre des places. */
    public record EquipageSaav(String id, int debut, int fin, String milieuId, List<Membre> membres) { }

    public record Membre(String corpsEmploi, String matricule) { }

    // ── Validation ──────────────────────────────────────────────────────────

    private static <E extends Enum<E>> String liste(E[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static <E extends Enum<E>> Optional<E> parmi(E[] valeurs, Object v) {
        return Arrays.stream(valeurs).filter(e -> e.name().equals(v)).findFirst();
    }

    public static CodeConnecteur connecteurDe(Object v) {
        return parmi(CodeConnecteur.values(), v).orElseThrow(() -> new EffectifsInvalideException("connecteur : " + liste(CodeConnecteur.values())));
    }

    /** Le relais du journal ne vaut que pour le bus et l'entrepôt : les autres connecteurs ont leurs propres gestes. */
    public static CodeConnecteur relaisDe(Object v) {
        CodeConnecteur c = connecteurDe(v);
        if (c != CodeConnecteur.ESB && c != CodeConnecteur.ENTREPOT) {
            throw new EffectifsInvalideException("relais : ESB ou ENTREPOT");
        }
        return c;
    }

    public static EtatMessage etatDe(Object v) {
        return parmi(EtatMessage.values(), v).orElseThrow(() -> new EffectifsInvalideException("« etat » : " + liste(EtatMessage.values())));
    }

    public static void verifierType(CodeConnecteur code, TypeMessage type) {
        if (code.sens() != Sens.SORTANT) {
            throw new EffectifsInvalideException(code + " est un connecteur entrant : on tire ses données, on ne lui envoie rien");
        }
        if (!code.types().contains(type)) {
            throw new EffectifsInvalideException(code + " n'accepte que : " + code.types().stream().map(Enum::name).collect(Collectors.joining(", ")));
        }
    }

    /** Absent : la valeur courante ; un texte non vide : rogné ; tout le reste : effacé (comme le Node). */
    private static String texte(Map<String, Object> brut, String cle, String defaut) {
        if (!brut.containsKey(cle)) {
            return defaut;
        }
        return brut.get(cle) instanceof String s && !s.isBlank() ? s.strip() : null;
    }

    /**
     * Valide une configuration. En mode HTTP, l'adresse est obligatoire et <strong>chiffrée</strong> (https) — P-TN-7
     * demande un protocole sécurisé ; seul un poste local de développement y échappe.
     */
    public static ConfigConnecteur configDe(Map<String, Object> brut, ConfigConnecteur actuelle) {
        Mode mode = brut.containsKey("mode") ? parmi(Mode.values(), brut.get("mode"))
                .orElseThrow(() -> new EffectifsInvalideException("« mode » : " + liste(Mode.values()))) : actuelle.mode();
        String url = texte(brut, "url", actuelle.url());
        String jetonRef = texte(brut, "jetonRef", actuelle.jetonRef());
        boolean actif = brut.containsKey("actif") ? Boolean.TRUE.equals(brut.get("actif")) : actuelle.actif();
        if (jetonRef != null && !NOM_VARIABLE.matcher(jetonRef).matches()) {
            throw new EffectifsInvalideException("« jetonRef » : le NOM d'une variable d'environnement (MAJUSCULES_ET_SOULIGNÉS), jamais le secret lui-même");
        }
        if (mode == Mode.HTTP) {
            verifierAdresse(url);
        }
        if (mode == Mode.SFTP) {
            adresseSftp(url);
            if (jetonRef == null) {
                throw new EffectifsInvalideException("« jetonRef » : obligatoire en mode SFTP — la variable qui porte la clé privée du compte");
            }
        }
        return new ConfigConnecteur(mode, url, jetonRef, actif);
    }

    private static void verifierAdresse(String url) {
        if (url == null) {
            throw new EffectifsInvalideException("« url » : obligatoire en mode HTTP");
        }
        URI u = adresse(url);
        String schema = u.getScheme().toLowerCase(Locale.ROOT);
        boolean local = u.getHost() != null && HOTES_LOCAUX.contains(u.getHost());
        if (!schema.equals(SCHEMA_SUR) && !(local && schema.equals(SCHEMA_LOCAL))) {
            throw new EffectifsInvalideException("« url » : un protocole sécurisé (https) est exigé");
        }
    }

    /** {@code sftp://compte@hote[:port]/repertoire?empreinte=SHA256:…} ; toute autre forme est refusée en la nommant. */
    public static AdresseSftp adresseSftp(String url) {
        if (url == null) {
            throw new EffectifsInvalideException("« url » : obligatoire en mode SFTP");
        }
        URI u = adresse(url);
        if (!SCHEMA_SFTP.equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() == null) {
            throw new EffectifsInvalideException("« url » : sftp://compte@hote/repertoire?empreinte=SHA256:… attendu");
        }
        String repertoire = u.getPath() == null || u.getPath().isBlank() ? "/" : u.getPath();
        String requete = u.getQuery() == null ? "" : u.getQuery();
        String empreinte = requete.startsWith(PARAMETRE_EMPREINTE) ? requete.substring(PARAMETRE_EMPREINTE.length()) : "";
        if (!EMPREINTE.matcher(empreinte).matches()) {
            throw new EffectifsInvalideException("« url » : l'empreinte de la clé d'hôte est exigée (?empreinte=SHA256:…, comme ssh-keygen -lf l'écrit)");
        }
        return new AdresseSftp(u.getUserInfo(), u.getHost(), u.getPort() < 0 ? PORT_SSH : u.getPort(), repertoire, empreinte);
    }

    private static URI adresse(String url) {
        try {
            URI u = new URI(url);
            if (u.getScheme() == null) {
                throw new URISyntaxException(url, "schéma absent");
            }
            return u;
        } catch (URISyntaxException e) {
            throw new EffectifsInvalideException("« url » : adresse invalide");
        }
    }

    /** Le jeton, lu au moment de l'appel ; une variable nommée mais absente est une erreur de configuration nommée. */
    public static Optional<String> jetonDe(String jetonRef, SecretsIntegrationPort secrets) {
        if (jetonRef == null) {
            return Optional.empty();
        }
        return Optional.of(secrets.variable(jetonRef).filter(v -> !v.isEmpty()).orElseThrow(() -> new EffectifsInvalideException(
                "la variable d'environnement " + jetonRef + " n'est pas définie sur le serveur : le jeton du connecteur est introuvable")));
    }

    // ── Réponse d'un destinataire ────────────────────────────────────────────

    /**
     * La lecture d'une réponse HTTP : 2xx acceptée ; 400, 409, 422 rejetée (le destinataire a dit non) ; le reste est
     * passager — vide, et l'ordonnanceur retentera.
     */
    public static Optional<Issue> issueHttp(int statut, Map<String, Object> corps) {
        if (statut >= SUCCES_MIN && statut < SUCCES_MAX) {
            Object ref = premier(corps.get("id"), corps.get("reference"));
            return Optional.of(new Acceptee(ref == null ? "HTTP-" + statut : String.valueOf(ref)));
        }
        if (REFUS.contains(statut)) {
            Object erreurs = corps.get("errors") instanceof List<?> l ? l.stream().map(String::valueOf).collect(Collectors.joining(" ; ")) : null;
            Object motif = premier(premier(corps.get("message"), corps.get("motif")), erreurs);
            return Optional.of(new Rejetee(motif == null ? "rejet " + statut : String.valueOf(motif)));
        }
        return Optional.empty();
    }

    private static Object premier(Object a, Object b) {
        return a != null ? a : b;
    }

    // ── Données entrantes ────────────────────────────────────────────────────

    private static Map<?, ?> carte(Object brut) {
        return brut instanceof Map<?, ?> m ? m : Map.of();
    }

    private static String chaine(Map<?, ?> m, String cle) {
        Object v = m.get(cle);
        return v == null ? "" : String.valueOf(v);
    }

    /** Un horodatage reçu, champ par champ en texte : une ligne bancale sera rejetée par l'import, nommée. */
    public static HorodatageExterne horodatageDe(Object brut) {
        Map<?, ?> m = carte(brut);
        return new HorodatageExterne(chaine(m, "cle"), chaine(m, "matricule"), chaine(m, "sens"), chaine(m, "horodatage"));
    }

    public static DossierEmploye dossierDe(Object brut) {
        Map<?, ?> m = carte(brut);
        return new DossierEmploye(chaine(m, "matricule"), m.containsKey("anciennete") ? nombre(m.get("anciennete")) : Double.NaN);
    }

    /** La conversion numérique de {@code Number(v)} : un nul vaut 0, un texte vide aussi, l'illisible ne vaut rien (NaN). */
    public static double nombre(Object v) {
        return switch (v) {
            case null -> 0;
            case Number n -> n.doubleValue();
            case Boolean b -> b ? 1 : 0;
            case String s -> nombreDuTexte(s.strip());
            default -> Double.NaN;
        };
    }

    private static double nombreDuTexte(String s) {
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Une ancienneté reçue de BD Corpo : un nombre d'années positif, à deux décimales. */
    public static OptionalDouble ancienneteValide(double n) {
        if (!Double.isFinite(n) || n < 0 || n > ANCIENNETE_MAX_ANNEES) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(Math.round(n * CENTIEMES) / CENTIEMES);
    }

    /** Un nombre tel que le Node l'écrit en JSON : entier sans décimale, sinon tel quel ({@code 4}, pas {@code 4.0}). */
    public static Number nombreAffiche(double n) {
        return n == Math.rint(n) && Math.abs(n) < Long.MAX_VALUE ? (Number) Math.round(n) : (Number) n;
    }

    // ── Gestes sortants ──────────────────────────────────────────────────────

    public static String dateDe(Object v) {
        if (v instanceof String s && DATE.matcher(s).matches()) {
            return s;
        }
        throw new EffectifsInvalideException("« date » : AAAA-MM-JJ");
    }

    public static String uniteDe(Object v) {
        String u = v instanceof String s ? s.strip() : "";
        if (u.isEmpty()) {
            throw new EffectifsInvalideException("« uniteCode » : l'unité dont on publie les équipages");
        }
        return u;
    }

    /** Les lignes (une par place) regroupées par équipage, dans l'ordre de lecture. */
    public static List<EquipageSaav> equipagesDe(List<DonneesIntegrationPort.LigneEquipage> lignes) {
        Map<String, EquipageSaav> parId = new LinkedHashMap<>();
        for (DonneesIntegrationPort.LigneEquipage l : lignes) {
            parId.computeIfAbsent(l.equipageId(), id -> new EquipageSaav(id, l.debut(), l.fin(), l.milieuId(), new ArrayList<>()))
                    .membres().add(new Membre(l.corpsEmploiCode(), l.matricule()));
        }
        return parId.values().stream().map(e -> new EquipageSaav(e.id(), e.debut(), e.fin(), e.milieuId(), List.copyOf(e.membres()))).toList();
    }

    /**
     * La clé d'idempotence d'une publication SAAV : l'unité, la date et la composition, écrite comme le Node l'écrit
     * ({@code JSON.stringify}) — la clé change quand la composition change, et seulement alors.
     */
    public static String cleEquipages(String unite, String date, List<EquipageSaav> equipages) {
        String json = equipages.stream().map(e -> "{\"id\":" + json(e.id()) + ",\"debut\":" + e.debut() + ",\"fin\":" + e.fin()
                + ",\"milieuId\":" + json(e.milieuId()) + ",\"membres\":[" + e.membres().stream()
                .map(m -> "{\"corpsEmploi\":" + json(m.corpsEmploi()) + ",\"matricule\":" + json(m.matricule()) + "}")
                .collect(Collectors.joining(",")) + "]}").collect(Collectors.joining(","));
        return unite + ":" + date + ":[" + json + "]";
    }

    /** Un texte JSON tel que {@code JSON.stringify} l'écrit. */
    static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            b.append(echappe(c));
        }
        return b.append('"').toString();
    }

    private static String echappe(char c) {
        return switch (c) {
            case '"' -> "\\\"";
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> c < ' ' ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        };
    }

    /** Un quart vers un calendrier : la place, la personne et l'horaire — un quart déplacé ou échangé repart. */
    public static String cleQuart(DonneesIntegrationPort.QuartPublie q, String ressourceId) {
        return q.placeId() + ":" + ressourceId + ":" + q.date() + ":" + q.debut() + "-" + q.fin();
    }

    // ── Événements pour le bus et versements à l'entrepôt ────────────────────

    /**
     * Les actions du journal qui deviennent des <strong>événements</strong> publiés sur le bus. Le journal est la source :
     * aucun module n'appelle le bus, rien ne se perd si le bus est coupé (le curseur reprend).
     */
    private static final Map<String, String> EVENEMENTS_ESB = Map.of(
            "place.attribuee", "effectifs.place.attribuee",
            "place.attribution.forcee", "effectifs.place.attribuee",
            "place.liberee", "effectifs.place.liberee",
            "echange.applique", "effectifs.echange.applique",
            "absence.approuver", "effectifs.absence.acceptee",
            "absence.deroger", "effectifs.absence.acceptee",
            "absence.annuler", "effectifs.absence.annulee",
            "temps.approuver", "effectifs.feuille.approuvee",
            "presence.pointage", "effectifs.presence.pointage");

    public static Optional<String> evenementDe(String action) {
        return Optional.ofNullable(EVENEMENTS_ESB.get(action));
    }

    /** Le curseur d'une entrée : l'ordre de lecture du journal (horodatage, puis identifiant). */
    public static String curseurDe(DonneesIntegrationPort.EntreeJournal e) {
        return e.horodatage() + e.id();
    }

    public static <T> List<List<T>> lots(List<T> elements, int taille) {
        List<List<T>> l = new ArrayList<>();
        for (int i = 0; i < elements.size(); i += taille) {
            l.add(List.copyOf(elements.subList(i, Math.min(elements.size(), i + taille))));
        }
        return l;
    }
}
