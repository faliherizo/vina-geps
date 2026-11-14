package ca.vina.nexu.auth.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.net.URI;
import java.util.regex.Pattern;

/**
 * <strong>Les annuaires d'un organisme</strong> — AO 26-015-DO P-TN-2 (« l'intégration à plusieurs
 * annuaires actifs, tel que : Active Directories »), conception § 6.9.
 *
 * <p>Un annuaire est un fournisseur d'identité que l'organisme déclare — un locataire Microsoft Entra ID,
 * ou tout fournisseur <strong>OpenID Connect</strong> (Keycloak, Okta, Auth0…) désigné par son émetteur
 * (ADR 0075). Un organisme peut en
 * déclarer plusieurs (fusion, sous-traitants, établissements partenaires), et chacun dit quels de
 * ses groupes donnent quel rôle. Quatre invariants, vérifiés ici plutôt qu'à l'écran :
 * <ul>
 *   <li>une autorité <em>multi-locataire</em> ({@code common}, {@code organizations},
 *       {@code consumers}) est refusée : elle laisserait entrer n'importe quel compte Microsoft ;</li>
 *   <li>un courriel que le fournisseur déclare non vérifié ({@code email_verified: false}) n'entre pas ;</li>
 *   <li>un courriel hors des domaines déclarés n'entre pas, même authentifié par l'annuaire (les
 *       invités B2B d'un locataire Entra portent le domaine de leur propre organisation) ;</li>
 *   <li>appartenir à l'annuaire ne suffit pas : sans groupe correspondant ni rôle par défaut, la
 *       connexion est refusée.</li>
 * </ul>
 */
public final class Annuaires {

    private static final Pattern GUID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern DOMAINE = Pattern.compile("^[a-z0-9-]+(\\.[a-z0-9-]+)+$");
    /** Le préfixe borne les variables lisibles : un administrateur ne désigne pas le secret des jetons de session. */
    private static final Pattern VARIABLE_SECRET = Pattern.compile("^ANNUAIRE_[A-Z0-9_]{1,60}$");
    private static final Set<String> AUTORITES_OUVERTES = Set.of("common", "organizations", "consumers");
    private static final int NOM_MAX = 80;
    private static final int GROUPE_MAX = 256;
    private static final String SUPERADMIN = "superadmin";
    private static final String AUTORITE_ENTRA = "https://login.microsoftonline.com/";
    /** L'identifiant d'un client OIDC générique : Keycloak et ses pairs acceptent un nom, pas seulement un GUID. */
    private static final Pattern CLIENT_OIDC = Pattern.compile("^[A-Za-z0-9._:@-]{1,255}$");
    /** HTTP en clair n'est admis que sur la machine même : le poste de développement et les essais. */
    private static final Set<String> HOTES_LOCAUX = Set.of("localhost", "127.0.0.1");
    private static final Set<String> SCHEMAS_PUBLICS = Set.of("https");
    private static final Set<String> SCHEMAS_LOCAUX = Set.of("https", "http");
    private static final String VERIFIE = "email_verified";

    /** Le protocole de l'annuaire : il décide d'où viennent les adresses d'autorisation et d'échange. */
    public enum Fournisseur {
        /** Microsoft Entra ID : l'autorité se déduit du locataire. */
        ENTRA("entra"),
        /** Tout fournisseur OpenID Connect : les adresses se lisent dans son document de découverte. */
        OIDC("oidc");

        private final String code;

        Fournisseur(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Fournisseur de(String code) {
            return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                    .orElseThrow(() -> new AnnuaireInvalideException(List.of("fournisseur inconnu : « " + code + " » (attendu : entra, oidc)")));
        }
    }

    private Annuaires() {
    }

    /** La revendication du jeton qui porte les appartenances : groupes de sécurité ou rôles d'application. */
    public enum Revendication {
        GROUPES("groups"), ROLES("roles");

        private final String code;

        Revendication(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Revendication de(String code) {
            return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst()
                    .orElseThrow(() -> new AnnuaireInvalideException(List.of("revendication inconnue : « " + code + " » (attendu : groups, roles)")));
        }
    }

    /** Une règle : les membres de ce groupe reçoivent ce rôle. L'ordre des règles est celui de leur priorité. */
    public record Correspondance(String groupe, String role) {
    }

    /** Ce que l'administrateur saisit, avant validation. */
    public record Saisie(String nom, String locataireEntra, String clientId, String variableSecret, List<String> domaines,
                         String revendication, List<Correspondance> correspondances, String roleParDefaut, boolean actif,
                         String fournisseur, String emetteur) {
    }

    /**
     * Un annuaire validé. {@code roleParDefaut} vide : l'annuaire seul ne donne aucun accès. {@code locataireEntra}
     * ne vaut que pour Entra, {@code emetteur} que pour un fournisseur OIDC générique.
     */
    public record Annuaire(String id, String tenantId, String nom, String locataireEntra, String clientId, String variableSecret,
                           List<String> domaines, Revendication revendication, List<Correspondance> correspondances,
                           Optional<String> roleParDefaut, boolean actif, Fournisseur fournisseur, String emetteur) {

        public String autorite() {
            return switch (fournisseur) {
                case ENTRA -> AUTORITE_ENTRA + locataireEntra;
                case OIDC -> emetteur;
            };
        }

        /** Le domaine du courriel figure parmi ceux de l'organisme. */
        public boolean admet(String courriel) {
            int arobase = courriel.lastIndexOf('@');
            return arobase > 0 && domaines.contains(courriel.substring(arobase + 1).toLowerCase(Locale.ROOT));
        }

        /** Le rôle que donnent les revendications : la première règle déclarée dont le groupe est reçu, sinon le défaut. */
        public Optional<String> roleDe(Map<String, Object> revendications) {
            List<String> recus = valeurs(revendications.get(revendication.code()));
            return correspondances.stream().filter(c -> recus.contains(c.groupe())).map(Correspondance::role).findFirst().or(() -> roleParDefaut);
        }
    }

    private static List<String> valeurs(Object brut) {
        if (brut instanceof Collection<?> c) {
            return c.stream().map(String::valueOf).toList();
        }
        return brut == null ? List.of() : List.of(String.valueOf(brut));
    }

    /** Port : l'échange du code d'autorisation contre des revendications vérifiées (signature, émetteur, nonce). */
    @FunctionalInterface
    public interface Echange {
        Map<String, Object> echanger(String code, Oidc.Configuration configuration, Oidc.Demande demande);
    }

    /** Port : les adresses qu'un fournisseur OIDC publie dans son document de découverte. */
    @FunctionalInterface
    public interface Decouverte {
        Points points(String emetteur);

        /** Où envoyer l'usager s'authentifier, et où échanger le code. */
        record Points(String autorisation, String jeton) {
        }
    }

    /** Port : les comptes d'un organisme, tels qu'une connexion par annuaire les trouve, les crée et les reclasse. */
    public interface Comptes {
        Optional<Compte> duCourriel(String tenantId, String courriel);

        /** Un compte sans mot de passe utilisable : il n'entre que par son annuaire. */
        Compte creer(String tenantId, String courriel, String role);

        Compte reclasser(Compte compte, String role);
    }

    /** Port : le jeton de session d'un compte. */
    @FunctionalInterface
    public interface Sessions {
        String emettre(Compte compte);
    }

    /** Une saisie refusée, avec tous ses motifs à la fois : l'administrateur corrige en une fois. */
    public static final class AnnuaireInvalideException extends RuntimeException {
        private final List<String> motifs;

        public AnnuaireInvalideException(List<String> motifs) {
            super(String.join(" ; ", motifs));
            this.motifs = List.copyOf(motifs);
        }

        public List<String> motifs() {
            return motifs;
        }
    }

    /** Une connexion que l'annuaire authentifie mais que l'organisme n'admet pas. */
    public static final class ConnexionRefuseeException extends RuntimeException {
        public ConnexionRefuseeException(String motif) {
            super(motif);
        }
    }

    /**
     * Valide une saisie contre les rôles attribuables de l'organisme.
     *
     * @throws AnnuaireInvalideException avec chaque motif de refus
     */
    public static Annuaire valider(String id, String tenantId, Saisie s, Set<String> rolesAttribuables) {
        List<String> motifs = new ArrayList<>();
        String nom = texte(s.nom());
        if (nom.isEmpty() || nom.length() > NOM_MAX) {
            motifs.add("le nom est requis (" + NOM_MAX + " caractères au plus)");
        }
        Fournisseur fournisseur = fournisseurDe(s.fournisseur(), motifs);
        Identite identite = fournisseur == Fournisseur.OIDC ? identiteOidc(s, motifs) : identiteEntra(s, motifs);
        String variable = texte(s.variableSecret());
        if (!VARIABLE_SECRET.matcher(variable).matches()) {
            motifs.add("la variable du secret doit commencer par ANNUAIRE_ (majuscules, chiffres, _)");
        }
        List<String> domaines = domaines(s.domaines(), motifs);
        Revendication revendication = revendicationDe(s.revendication(), motifs);
        List<Correspondance> regles = correspondances(s.correspondances(), rolesAttribuables, motifs);
        Optional<String> defaut = Optional.ofNullable(s.roleParDefaut()).map(String::trim).filter(r -> !r.isEmpty());
        defaut.filter(r -> !attribuable(r, rolesAttribuables)).ifPresent(r -> motifs.add("rôle par défaut non attribuable : « " + r + " »"));
        if (regles.isEmpty() && defaut.isEmpty()) {
            motifs.add("déclarer au moins une correspondance groupe → rôle, ou un rôle par défaut");
        }
        if (!motifs.isEmpty()) {
            throw new AnnuaireInvalideException(motifs);
        }
        return new Annuaire(id, tenantId, nom, identite.locataire(), identite.clientId(), variable, domaines, revendication, regles, defaut, s.actif(),
                fournisseur, identite.emetteur());
    }

    /** Ce qui désigne le fournisseur et le client, selon le protocole. */
    private record Identite(String locataire, String emetteur, String clientId) {
    }

    private static Fournisseur fournisseurDe(String code, List<String> motifs) {
        try {
            return Fournisseur.de(code == null || code.isBlank() ? Fournisseur.ENTRA.code() : code.trim());
        } catch (AnnuaireInvalideException e) {
            motifs.addAll(e.motifs());
            return Fournisseur.ENTRA;
        }
    }

    private static Identite identiteEntra(Saisie s, List<String> motifs) {
        String locataire = texte(s.locataireEntra()).toLowerCase(Locale.ROOT);
        verifierLocataire(locataire, motifs);
        String clientId = texte(s.clientId()).toLowerCase(Locale.ROOT);
        if (!GUID.matcher(clientId).matches()) {
            motifs.add("l'identifiant d'application (client) doit être un GUID");
        }
        return new Identite(locataire, null, clientId);
    }

    private static Identite identiteOidc(Saisie s, List<String> motifs) {
        String emetteur = texte(s.emetteur()).replaceAll("/+$", "");
        verifierEmetteur(emetteur, motifs);
        String clientId = texte(s.clientId());
        if (!CLIENT_OIDC.matcher(clientId).matches()) {
            motifs.add("l'identifiant du client OIDC est requis (lettres, chiffres, . _ : @ -)");
        }
        return new Identite("", emetteur, clientId);
    }

    /**
     * L'émetteur d'un fournisseur OIDC : une adresse absolue, en HTTPS — sauf sur la machine même, où un
     * Keycloak de développement écoute en clair. Ni requête ni fragment : l'émetteur se compare à la lettre
     * à la revendication {@code iss} du jeton.
     */
    private static void verifierEmetteur(String emetteur, List<String> motifs) {
        URI uri;
        try {
            uri = URI.create(emetteur);
        } catch (IllegalArgumentException e) {
            motifs.add("l'émetteur OIDC doit être une adresse (p. ex. https://sso.organisme.ca/realms/vina-geps)");
            return;
        }
        boolean local = uri.getHost() != null && HOTES_LOCAUX.contains(uri.getHost());
        boolean schemaAdmis = uri.getScheme() != null && (local ? SCHEMAS_LOCAUX : SCHEMAS_PUBLICS).contains(uri.getScheme());
        if (emetteur.isEmpty() || uri.getHost() == null || !schemaAdmis || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            motifs.add("l'émetteur OIDC doit être une adresse https sans requête (http n'est admis que sur localhost)");
        }
    }

    private static String texte(String brut) {
        return brut == null ? "" : brut.trim();
    }

    private static void verifierLocataire(String locataire, List<String> motifs) {
        if (AUTORITES_OUVERTES.contains(locataire)) {
            motifs.add("le locataire « " + locataire + " » admet n'importe quel compte Microsoft : déclarer celui de l'organisme");
        } else if (!GUID.matcher(locataire).matches() && !DOMAINE.matcher(locataire).matches()) {
            motifs.add("le locataire Entra doit être un GUID ou un domaine (p. ex. organisme.onmicrosoft.com)");
        }
    }

    private static List<String> domaines(List<String> bruts, List<String> motifs) {
        List<String> domaines = (bruts == null ? List.<String>of() : bruts).stream()
                .map(d -> texte(d).toLowerCase(Locale.ROOT)).filter(d -> !d.isEmpty()).distinct().toList();
        if (domaines.isEmpty()) {
            motifs.add("déclarer au moins un domaine de courriel admis");
        }
        domaines.stream().filter(d -> !DOMAINE.matcher(d).matches()).forEach(d -> motifs.add("domaine invalide : « " + d + " »"));
        return domaines;
    }

    private static Revendication revendicationDe(String code, List<String> motifs) {
        try {
            return Revendication.de(code == null || code.isBlank() ? Revendication.GROUPES.code() : code.trim());
        } catch (AnnuaireInvalideException e) {
            motifs.addAll(e.motifs());
            return Revendication.GROUPES;
        }
    }

    private static List<Correspondance> correspondances(List<Correspondance> brutes, Set<String> rolesAttribuables, List<String> motifs) {
        List<Correspondance> regles = new ArrayList<>();
        for (Correspondance c : brutes == null ? List.<Correspondance>of() : brutes) {
            String groupe = texte(c.groupe());
            String role = texte(c.role());
            if (groupe.isEmpty() || groupe.length() > GROUPE_MAX) {
                motifs.add("un groupe est requis pour chaque correspondance");
            } else if (!attribuable(role, rolesAttribuables)) {
                motifs.add("rôle non attribuable pour le groupe « " + groupe + " » : « " + role + " »");
            } else if (regles.stream().anyMatch(r -> r.groupe().equals(groupe))) {
                motifs.add("le groupe « " + groupe + " » est déclaré deux fois");
            } else {
                regles.add(new Correspondance(groupe, role));
            }
        }
        return List.copyOf(regles);
    }

    /** Le superadmin n'appartient à aucun organisme : aucun annuaire d'organisme ne le confère. */
    private static boolean attribuable(String role, Set<String> rolesAttribuables) {
        return !SUPERADMIN.equals(role) && rolesAttribuables.contains(role);
    }

    /**
     * Ce que l'organisme exige d'une identité que son annuaire a authentifiée : un domaine admis, et
     * un rôle donné par ses groupes ou par défaut.
     *
     * @return le rôle à porter
     * @throws ConnexionRefuseeException sinon
     */
    public static String admettre(Annuaire a, String courriel, Map<String, Object> revendications) {
        // Un courriel que le fournisseur déclare non vérifié ne prouve rien : n'importe qui peut l'avoir saisi.
        if (Boolean.FALSE.equals(revendications.get(VERIFIE))) {
            throw new ConnexionRefuseeException("le courriel « " + courriel + " » n'est pas vérifié par l'annuaire « " + a.nom() + " »");
        }
        if (!a.admet(courriel)) {
            throw new ConnexionRefuseeException("le domaine de « " + courriel + " » n'est pas admis par l'annuaire « " + a.nom() + " »");
        }
        return a.roleDe(revendications).orElseThrow(() ->
                new ConnexionRefuseeException("aucun groupe de « " + courriel + " » ne donne accès dans l'annuaire « " + a.nom() + " »"));
    }
}
