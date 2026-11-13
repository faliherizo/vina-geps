package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Regroupement;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Le regroupement des avis : le défaut de l'établissement ({@code notification_regroupement_defaut})
 * et le choix de chaque compte (colonne {@code regroupement} de {@code notification_preferences},
 * migration 0162). Jumeau de {@code serviceRegroupement.ts}.
 */
@Repository
public class DepotRegroupement {

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotPreferences preferences;

    public DepotRegroupement(JdbcClient jdbc, Clock horloge, DepotPreferences preferences) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.preferences = preferences;
    }

    public Regroupement.Mode defautEtablissement(String tenantId) {
        return jdbc.sql("SELECT mode FROM notification_regroupement_defaut WHERE tenantId = ?").param(tenantId)
                .query(String.class).optional().map(Regroupement.Mode::de).orElse(null);
    }

    public Regroupement.Mode reglerDefaut(String tenantId, String acteurId, Object mode) {
        Regroupement.Mode m = Regroupement.Mode.de(mode);
        jdbc.sql("INSERT INTO notification_regroupement_defaut (tenantId, mode, modifieLe, modifiePar) VALUES (?,?,?,?)"
                        + " ON CONFLICT(tenantId) DO UPDATE SET mode = excluded.mode, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, m.code(), Iso.de(horloge.instant()), acteurId).update();
        return m;
    }

    public Regroupement.Mode choixDuCompte(String userId) {
        Optional<String> choix = jdbc.sql("SELECT regroupement FROM notification_preferences WHERE userId = ?").param(userId)
                .query((rs, i) -> Optional.ofNullable(rs.getString("regroupement"))).optional().flatMap(o -> o);
        return choix.map(Regroupement.Mode::de).orElse(null);
    }

    /** {@code null} remet la personne sur le défaut de l'établissement : « je suis le défaut » est un choix. */
    public Regroupement.Mode reglerChoix(String userId, Object mode) {
        Regroupement.Mode m = mode == null || "".equals(mode) ? null : Regroupement.Mode.de(mode);
        String code = m == null ? null : m.code();
        String maintenant = Iso.de(horloge.instant());
        boolean existe = !jdbc.sql("SELECT userId FROM notification_preferences WHERE userId = ?").param(userId)
                .query(String.class).list().isEmpty();
        if (existe) {
            jdbc.sql("UPDATE notification_preferences SET regroupement = ?, updatedAt = ? WHERE userId = ?").params(code, maintenant, userId).update();
        } else {
            jdbc.sql("INSERT INTO notification_preferences (userId, tenantId, courriel, texto, poussee, regroupement, updatedAt) VALUES (?, NULL, 1, 1, 1, ?, ?)")
                    .params(userId, code, maintenant).update();
        }
        return m;
    }

    public Regroupement.Mode effectifDuCompte(String tenantId, String userId) {
        return Regroupement.effectif(choixDuCompte(userId), defautEtablissement(tenantId));
    }
}
