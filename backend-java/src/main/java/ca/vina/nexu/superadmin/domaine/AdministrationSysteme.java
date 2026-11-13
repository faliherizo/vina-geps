package ca.vina.nexu.superadmin.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Les règles de l'administration du système — port de la partie décisionnelle de
 * {@code superadmin/service.ts} et {@code superadmin/routes.ts} : un administrateur par
 * établissement au moins, un mot de passe provisoire de huit caractères, le courriel d'accès
 * en français.
 */
public final class AdministrationSysteme {

    private AdministrationSysteme() {
    }

    /** Port : les établissements (le contexte {@code saas} les tient). */
    public interface Etablissements {
        java.util.Map<String, Object> creer(ca.vina.nexu.saas.domaine.Locataires.Demande demande);

        java.util.Map<String, Object> modifier(String id, Modification m);

        java.util.Map<String, Object> activer(String id, int mois);

        java.util.Map<String, Object> changerStatut(String id, String statut);

        /** Les modules activés d'un établissement — vide sans quota. */
        List<String> modules(String id);
    }

    public record Modification(String name, String region, String address, String city, String postalCode, String phone, String email, String slug) {
    }

    /** Port : les administrateurs d'établissement et leurs accès. */
    public interface Administrateurs {
        List<java.util.Map<String, Object>> etablissementsAvecAdmins(java.util.function.Function<String, List<String>> modulesDe);

        List<java.util.Map<String, Object>> lister(String tenantId);

        java.util.Map<String, Object> creer(String tenantId, String username, String motDePasse, boolean changementImpose);

        String motDePasseProvisoire();

        java.util.Map<String, Object> reinitialiserMotDePasse(String tenantId, String userId);

        java.util.Map<String, Object> activer(String tenantId, String userId, boolean actif);
    }

    /** Port : une unité de travail — l'établissement et son administrateur naissent ensemble ou pas du tout. */
    public interface Transactions {
        <T> T executer(java.util.function.Supplier<T> geste);
    }

    /** Un mot de passe provisoire fait huit caractères au moins ; sans changement imposé, la politique élevée s'applique ailleurs. */
    public static void verifierMotDePasseAdmin(String motDePasse, boolean changementImpose) {
        if (motDePasse == null || motDePasse.isEmpty() || (changementImpose && motDePasse.length() < 8)) {
            throw new IllegalArgumentException("temporary password must be at least 8 characters");
        }
    }

    /** Désactiver le dernier administrateur actif rendrait l'établissement inadministrable. */
    public static void verifierDesactivation(int administrateursActifs) {
        if (administrateursActifs <= 1) {
            throw new IllegalStateException("dernier administrateur actif : l'établissement deviendrait inadministrable");
        }
    }

    /** Le courriel d'accès ({@code sendCredentialsMail}) : création ou réinitialisation, en français. */
    public record Acces(String prenom, String identifiant, String motDePasse, String etablissement, boolean reinitialisation, String urlConnexion) {
        public String sujet() {
            return reinitialisation ? "Réinitialisation de votre mot de passe — Nexu VINA"
                    : "Votre accès Nexu VINA" + (etablissement != null ? " — " + etablissement : "");
        }

        public String texte() {
            List<String> lignes = new ArrayList<>();
            lignes.add("Bonjour " + prenom + ",");
            lignes.add("");
            lignes.add(reinitialisation ? "Votre mot de passe a été réinitialisé par un administrateur." : "Un compte vous a été créé.");
            lignes.add("");
            if (etablissement != null) {
                lignes.add("  établissement : " + etablissement);
            }
            lignes.add("  identifiant : " + identifiant);
            lignes.add("  mot de passe temporaire : " + motDePasse);
            if (urlConnexion != null) {
                lignes.add("");
                lignes.add("  connexion : " + urlConnexion);
            }
            lignes.add("");
            lignes.add(reinitialisation ? "À votre prochaine connexion, vous devrez le changer."
                    : "À votre première connexion, vous devrez :\n  1. changer votre mot de passe\n  2. configurer une couche de sécurité (question secrète ou MFA)");
            lignes.add("");
            if (etablissement != null) {
                lignes.add("Si vous détenez un accès dans plusieurs établissements, le vôtre est identifié ci-dessus.");
                lignes.add("");
            }
            lignes.add("Merci.");
            return String.join("\n", lignes);
        }
    }

    /** L'URL de connexion du déploiement ({@code PUBLIC_APP_URL/login}), s'il en a une. */
    public static Optional<String> urlConnexion(String urlPublique) {
        if (urlPublique == null || urlPublique.trim().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(urlPublique.trim().replaceAll("/+$", "") + "/login");
    }
}
