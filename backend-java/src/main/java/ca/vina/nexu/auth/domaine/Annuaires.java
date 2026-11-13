package ca.vina.nexu.auth.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * <strong>Les annuaires d'un organisme</strong> — AO 26-015-DO P-TN-2 (« l'intégration à plusieurs
 * annuaires actifs, tel que : Active Directories »), conception § 6.9.
 *
 * <p>Un annuaire est un locataire Microsoft Entra ID que l'organisme déclare : un organisme peut en
 * déclarer plusieurs (fusion, sous-traitants, établissements partenaires), et chacun dit quels de
 * ses groupes donnent quel rôle. Trois invariants, vérifiés ici plutôt qu'à l'écran :
 * <ul>
 *   <li>une autorité <em>multi-locataire</em> ({@code common}, {@code organizations},
 *       {@code consumers}) est refusée : elle laisserait entrer n'importe quel compte Microsoft ;</li>
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
                         String revendication, List<Correspondance> correspondances, String roleParDefaut, boolean actif) {
    }

    /** Un annuaire validé. {@code roleParDefaut} vide : l'annuaire seul ne donne aucun accès. */
    public record Annuaire(String id, String tenantId, String nom, String locataireEntra, String clientId, String variableSecret,
                           List<String> domaines, Revendication revendication, List<Correspondance> correspondances,
                           Optional<String> roleParDefaut, boolean actif) {

        public String autorite() {
            return AUTORITE_ENTRA + locataireEntra;
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
        String locataire = texte(s.locataireEntra()).toLowerCase(Locale.ROOT);
        verifierLocataire(locataire, motifs);
        String clientId = texte(s.clientId()).toLowerCase(Locale.ROOT);
        if (!GUID.matcher(clientId).matches()) {
            motifs.add("l'identifiant d'application (client) doit être un GUID");
        }
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
        return new Annuaire(id, tenantId, nom, locataire, clientId, variable, domaines, revendication, regles, defaut, s.actif());
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
        if (!a.admet(courriel)) {
            throw new ConnexionRefuseeException("le domaine de « " + courriel + " » n'est pas admis par l'annuaire « " + a.nom() + " »");
        }
        return a.roleDe(revendications).orElseThrow(() ->
                new ConnexionRefuseeException("aucun groupe de « " + courriel + " » ne donne accès dans l'annuaire « " + a.nom() + " »"));
    }
}
