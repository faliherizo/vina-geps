package ca.vina.nexu.auth.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>L'invitation à créer son compte</strong> — jumeau de {@code backend/src/auth/invitations.ts}.
 *
 * <p>L'administration pose une invitation ; la personne reçoit un lien à usage unique et choisit
 * elle-même son mot de passe. Personne d'autre ne l'a jamais connu. Le jeton n'est conservé que par
 * son <strong>empreinte</strong> : une fuite de la base ne donne aucun lien utilisable.
 */
public final class Invitations {

    public static final int VALIDITE_JOURS = 7;
    private static final int VALIDITE_MAX = 30;
    private static final Pattern COURRIEL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern NOM_UTILISATEUR = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");

    private Invitations() {
    }

    public enum Etat {
        OFFERTE("offerte"), ACCEPTEE("acceptee"), REVOQUEE("revoquee"), EXPIREE("expiree");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Toute invitation refusée l'est avec son motif : 400. */
    public static final class InvitationInvalideException extends IllegalArgumentException {
        public InvitationInvalideException(String message) {
            super(message);
        }
    }

    public record Invitation(String id, String tenantId, String courriel, String username, String role, String employeeId,
                             String expireLe, String accepteeLe, String revoqueeLe, String creeLe, String creePar, String compteId) {
    }

    public record Saisie(String courriel, String username, String role, String employeeId, int jours) {
    }

    public record Publique(String username, String courriel, String expireLe) {
    }

    /**
     * La naissance du compte à l'acceptation : la personne choisit son mot de passe, et la double
     * authentification est exigée à la première connexion. Un refus (mot de passe faible, nom déjà
     * pris) lève une {@link IllegalArgumentException} qui le nomme.
     */
    public interface Comptes {
        /** Rend l'identifiant du compte créé. */
        String creer(Invitation i, String username, String password);
    }

    /** Le port des invitations ; le jeton n'y entre que par son empreinte. */
    public interface Depot {
        List<Invitation> lister(String tenantId);

        Optional<Invitation> lire(String tenantId, String id);

        Optional<Invitation> parEmpreinte(String empreinte);

        boolean compteExiste(String tenantId, String username);

        Optional<Invitation> enCours(String tenantId, String username, String maintenant);

        void poser(Invitation i, String empreinte);

        void revoquer(String id, String le);

        void accepter(String id, String le, String compteId, String username);
    }

    public static Etat etatDe(Invitation i, Instant maintenant) {
        if (i.accepteeLe() != null) {
            return Etat.ACCEPTEE;
        }
        if (i.revoqueeLe() != null) {
            return Etat.REVOQUEE;
        }
        return !Instant.parse(i.expireLe()).isAfter(maintenant) ? Etat.EXPIREE : Etat.OFFERTE;
    }

    public static Instant expiration(Instant maintenant, int jours) {
        return maintenant.plus(Duration.ofDays(jours));
    }

    public static Saisie valider(Map<String, Object> e, List<String> rolesPermis) {
        String courriel = texte(e.get("courriel")).toLowerCase(Locale.ROOT);
        if (!COURRIEL.matcher(courriel).matches()) {
            throw new InvitationInvalideException("un courriel valide est obligatoire : c'est par lui que part le lien");
        }
        String username = texte(e.get("username"));
        if (!NOM_UTILISATEUR.matcher(username).matches()) {
            throw new InvitationInvalideException(
                    "le nom d'utilisateur fait de 3 à 64 caractères, en lettres, chiffres, point, tiret ou soulignement");
        }
        String role = texte(e.get("role"));
        if (!rolesPermis.contains(role)) {
            throw new InvitationInvalideException("rôle inconnu « " + role + " » — attendu : " + String.join(", ", rolesPermis));
        }
        Object employe = e.get("employeeId");
        String employeeId = employe == null || "".equals(employe) ? null : String.valueOf(employe).trim();
        return new Saisie(courriel, username, role, employeeId, jours(e.get("jours")));
    }

    private static int jours(Object brut) {
        if (brut == null || "".equals(brut)) {
            return VALIDITE_JOURS;
        }
        double n = brut instanceof Number nb ? nb.doubleValue() : nombre(String.valueOf(brut));
        if (n != Math.rint(n) || n < 1 || n > VALIDITE_MAX) {
            throw new InvitationInvalideException("la validité d'une invitation va de 1 à 30 jours");
        }
        return (int) n;
    }

    /** Ce que le lien montre avant toute authentification : le minimum pour se décider. */
    public static Publique publique(Invitation i, Instant maintenant) {
        return switch (etatDe(i, maintenant)) {
            case ACCEPTEE -> throw new InvitationInvalideException("ce lien a déjà servi : connectez-vous");
            case REVOQUEE -> throw new InvitationInvalideException("cette invitation a été retirée");
            case EXPIREE -> throw new InvitationInvalideException("ce lien a expiré : demandez une nouvelle invitation à votre administration");
            case OFFERTE -> new Publique(i.username(), i.courriel(), i.expireLe());
        };
    }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static double nombre(String t) {
        try {
            return Double.parseDouble(t.trim());
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }
}
