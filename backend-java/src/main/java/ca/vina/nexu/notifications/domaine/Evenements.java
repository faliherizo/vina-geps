package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * **Le catalogue des événements notifiables** (ADR 0029) — port de {@code notifications/evenements.ts} :
 * ce dont une personne peut être avisée, par où par défaut, et ce qu'elle a le droit d'éteindre.
 * Un événement <em>réglable</em> se ferme entièrement ; un événement <em>essentiel</em> garde au
 * moins un canal écrit. Les codes sont ceux du Node : les deux backends publient la même matrice.
 */
public final class Evenements {

    private Evenements() {
    }

    public static final List<String> CANAUX = Canal.CODES;

    public record Canaux(boolean courriel, boolean texto, boolean poussee) {

        public boolean ouvert(Canal canal) {
            return switch (canal) {
                case COURRIEL -> courriel;
                case TEXTO -> texto;
                case POUSSEE -> poussee;
            };
        }

        public Canaux avec(Canal canal, boolean valeur) {
            return switch (canal) {
                case COURRIEL -> new Canaux(valeur, texto, poussee);
                case TEXTO -> new Canaux(courriel, valeur, poussee);
                case POUSSEE -> new Canaux(courriel, texto, valeur);
            };
        }

        /** Au moins un canal écrit est ouvert : ce qu'un avis essentiel exige. */
        public boolean ecritOuvert() {
            return courriel || texto;
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Canal c : Canal.values()) {
                m.put(c.code(), ouvert(c));
            }
            return m;
        }
    }

    /**
     * @param essentiel   la personne ne ferme pas <em>tous</em> ses canaux écrits à cet avis ;
     * @param obligatoire aucune préférence ne ferme <em>aucun</em> canal : une règle de l'organisation prévaut sur le choix
     *                    de la personne (FX-AO-20260810, critère 22 ; l'alerte critique, critère 18).
     */
    public record Evenement(String code, String libelle, String module, String destinataire, Canaux defauts, boolean essentiel, boolean obligatoire) {

        /** Un événement réglable par la personne : seul l'essentiel garde un canal écrit. */
        public Evenement(String code, String libelle, String module, String destinataire, Canaux defauts, boolean essentiel) {
            this(code, libelle, module, destinataire, defauts, essentiel, false);
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("libelle", libelle);
            m.put("module", module);
            m.put("destinataire", destinataire);
            m.put("defauts", defauts.vue());
            m.put("essentiel", essentiel);
            m.put("obligatoire", obligatoire);
            m.put("moduleLibelle", LIBELLE_MODULE.get(module));
            return m;
        }
    }

    public static final Map<String, String> LIBELLE_MODULE = Map.of("effectifs", "Urgences-santé", "notifications", "Encadrement", "alertes", "Communications cliniques");

    public static final List<Evenement> TOUS = List.of(
            new Evenement("remplacement.attribue", "Remplacement confirmé", "effectifs", "la ressource retenue", new Canaux(true, true, true), false),
            new Evenement("quart.offert", "Quart disponible offert", "effectifs", "les ressources admissibles au quart", new Canaux(true, true, true), false),
            new Evenement("quart.attribue", "Quart vacant attribué d'office", "effectifs", "la ressource prioritaire retenue", new Canaux(true, true, true), false),
            new Evenement("message.direct", "Message de l'encadrement", "notifications", "la personne visée", new Canaux(true, true, true), true),
            // Essentiel : une personne ne ferme pas tous ses canaux écrits à une alerte clinique (ADR 0074).
            new Evenement("alerte.recue", "Alerte clinique reçue", "alertes", "les destinataires de l'alerte", new Canaux(true, true, true), true),
            // ST-G07 : une alerte critique se pousse avec l'étiquette « critique » — canal Android à importance maximale,
            // niveau d'interruption critique sur iOS si Apple l'accorde, interaction exigée dans le navigateur.
            // Obligatoire : la préférence d'une personne ne ferme aucun canal à une alerte critique. Le domaine des alertes
            // imposait déjà tous les canaux (Priorite.canauxImposes), mais l'envoi revérifiait la préférence canal par canal
            // et marquait « désactivé » le texto qu'elle avait fermé (tests e2e FX-AO, FX-14, 22 nov. 2026).
            new Evenement(Evenements.ALERTE_CRITIQUE, "Alerte clinique critique reçue", "alertes", "les destinataires de l'alerte", new Canaux(true, true, true), true, true));

    /** L'événement d'une alerte de priorité critique. */
    public static final String ALERTE_CRITIQUE = "alerte.critique";
    /** L'étiquette d'une poussée ordinaire, et celle d'une poussée critique (ST-G07). */
    public static final String ETIQUETTE_NOTIFICATION = "notification";
    public static final String ETIQUETTE_CRITIQUE = "critique";

    /** L'étiquette de poussée d'un événement : « critique » pour l'alerte critique, l'ordinaire pour tout le reste. */
    public static String etiquetteDe(String evenement) {
        return ALERTE_CRITIQUE.equals(evenement) ? ETIQUETTE_CRITIQUE : ETIQUETTE_NOTIFICATION;
    }

    public static Optional<Evenement> parCode(String code) {
        return TOUS.stream().filter(e -> e.code().equals(code)).findFirst();
    }

    /** Un réglage partiel complété par les défauts de l'événement ; une clé absente garde le défaut. */
    public static Canaux completer(Evenement e, Map<String, Object> partiel) {
        Canaux c = e.defauts();
        for (Canal canal : Canal.values()) {
            if (partiel.get(canal.code()) instanceof Boolean b) {
                c = c.avec(canal, b);
            }
        }
        return c;
    }

    /** Le refus d'un réglage par événement, nommé — code inconnu, valeur qui n'est pas vrai/faux, essentiel sans canal écrit. */
    @SuppressWarnings("unchecked")
    public static List<String> refusReglages(Object regles) {
        List<String> erreurs = new ArrayList<>();
        if (regles == null) {
            return erreurs;
        }
        if (!(regles instanceof Map<?, ?> carte)) {
            erreurs.add("evenements : un objet code → canaux attendu");
            return erreurs;
        }
        for (Map.Entry<?, ?> entree : carte.entrySet()) {
            String code = String.valueOf(entree.getKey());
            Optional<Evenement> e = parCode(code);
            if (e.isEmpty()) {
                erreurs.add("evenements." + code + " : événement inconnu");
                continue;
            }
            if (!(entree.getValue() instanceof Map<?, ?> v)) {
                erreurs.add("evenements." + code + " : canaux attendus");
                continue;
            }
            Optional<Canal> mauvais = Arrays.stream(Canal.values()).filter(c -> v.get(c.code()) != null && !(v.get(c.code()) instanceof Boolean)).findFirst();
            if (mauvais.isPresent()) {
                erreurs.add("evenements." + code + "." + mauvais.get().code() + " : vrai ou faux attendu");
                continue;
            }
            if (e.get().essentiel() && !completer(e.get(), (Map<String, Object>) v).ecritOuvert()) {
                erreurs.add(e.get().libelle() + " : avis essentiel, gardez le courriel ou le texto ouvert");
            }
        }
        return erreurs;
    }
}
