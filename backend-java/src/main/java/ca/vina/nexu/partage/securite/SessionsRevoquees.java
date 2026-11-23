package ca.vina.nexu.partage.securite;

import java.time.Instant;

/**
 * Port : les sessions <strong>révoquées</strong> avant leur échéance (tests d'intrusion du 22 novembre 2026, PT-02 ;
 * ASVS 3.3.1). Un jeton de session est signé et vaut huit heures : sans cette liste, la déconnexion n'effaçait que le
 * témoin du navigateur, et un jeton copié restait utilisable jusqu'à l'échéance. Le filtre de session consulte ce port ;
 * la déconnexion l'alimente. Une entrée n'a pas à survivre au jeton qu'elle révoque.
 */
public interface SessionsRevoquees {

    /** Vrai si la session de cet identifiant ({@code jti}) a été fermée. */
    boolean revoquee(String jetonId);

    /** Ferme la session de cet identifiant jusqu'à l'échéance de son jeton. */
    void revoquer(String jetonId, Instant expireLe);

    /** Aucune révocation : le port des essais qui ne la visent pas. */
    SessionsRevoquees AUCUNE = new SessionsRevoquees() {
        @Override
        public boolean revoquee(String jetonId) {
            return false;
        }

        @Override
        public void revoquer(String jetonId, Instant expireLe) {
            // Rien à retenir : ce port ne révoque rien, par définition.
        }
    };
}
