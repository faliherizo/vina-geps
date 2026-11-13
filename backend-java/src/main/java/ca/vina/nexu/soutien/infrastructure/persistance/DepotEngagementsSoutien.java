package ca.vina.nexu.soutien.infrastructure.persistance;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Affectation;
import ca.vina.nexu.soutien.domaine.EngagementsDeService.Politique;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** La politique de soutien de chaque organisme (migration Node 0173) ; sans ligne, la politique standard. */
@Repository
public class DepotEngagementsSoutien implements EngagementsDeService.Registre, EngagementsDeService.Repertoire, EngagementsDeService.Ecriture {

    private static final String SELECTION = "SELECT t.id AS tenantId, t.name AS nom, e.politique, e.modifieLe, e.modifiePar "
            + "FROM tenants t LEFT JOIN soutien_engagements e ON e.tenantId = t.id ";

    private final JdbcClient jdbc;

    public DepotEngagementsSoutien(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Politique de(String tenantId) {
        if (tenantId == null) {
            return Politique.STANDARD;
        }
        return jdbc.sql("SELECT politique FROM soutien_engagements WHERE tenantId = ?").param(tenantId).query(String.class).optional()
                .map(Politique::de).orElse(Politique.STANDARD);
    }

    @Override
    public List<Affectation> affectations() {
        return jdbc.sql(SELECTION + "ORDER BY t.name").query(DepotEngagementsSoutien::versAffectation).list();
    }

    @Override
    public Affectation fixer(String tenantId, Politique politique, String acteurId, Instant le) {
        if (jdbc.sql("SELECT COUNT(*) FROM tenants WHERE id = ?").param(tenantId).query(Integer.class).single() == 0) {
            throw new IntrouvableException("organisme introuvable");
        }
        jdbc.sql("INSERT INTO soutien_engagements (tenantId, politique, modifieLe, modifiePar) VALUES (?,?,?,?) "
                        + "ON CONFLICT(tenantId) DO UPDATE SET politique = excluded.politique, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(tenantId, politique.code(), Iso.de(le), acteurId).update();
        return jdbc.sql(SELECTION + "WHERE t.id = ?").param(tenantId).query(DepotEngagementsSoutien::versAffectation).single();
    }

    private static Affectation versAffectation(ResultSet rs, int i) throws SQLException {
        String code = rs.getString("politique");
        return new Affectation(rs.getString("tenantId"), rs.getString("nom"), code == null ? Politique.STANDARD : Politique.de(code),
                Optional.ofNullable(rs.getString("modifieLe")), Optional.ofNullable(rs.getString("modifiePar")));
    }
}
