package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.securite.SessionsRevoquees;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;

/**
 * Les sessions fermées par la déconnexion ({@code auth_sessions_revoquees}, changeset 0025 ; PT-02). Une entrée vit
 * jusqu'à l'échéance de son jeton : au-delà, le jeton est refusé de toute façon, et l'entrée est purgée à la révocation
 * suivante. La table reste donc de la taille des déconnexions des huit dernières heures.
 */
@Repository
public class DepotSessionsRevoquees implements SessionsRevoquees {

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotSessionsRevoquees(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    @Override
    public boolean revoquee(String jetonId) {
        return jdbc.sql("SELECT COUNT(*) FROM auth_sessions_revoquees WHERE jti = ?").params(jetonId).query(Integer.class).single() > 0;
    }

    /** Un jeton sans échéance (aucun n'en émet) est retenu le temps d'une session ordinaire. */
    @Override
    public void revoquer(String jetonId, Instant expireLe) {
        Instant maintenant = horloge.instant();
        jdbc.sql("DELETE FROM auth_sessions_revoquees WHERE expireLe < ?").params(Iso.de(maintenant)).update();
        jdbc.sql("INSERT OR IGNORE INTO auth_sessions_revoquees (jti, expireLe, revoqueeLe) VALUES (?, ?, ?)")
                .params(jetonId, Iso.de(expireLe == null ? maintenant.plusSeconds(JetonDeSession.VALIDITE_SECONDES) : expireLe), Iso.de(maintenant)).update();
    }
}
