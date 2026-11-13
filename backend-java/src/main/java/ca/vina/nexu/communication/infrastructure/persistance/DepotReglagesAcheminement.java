package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.AcheminementUnites;
import ca.vina.nexu.communication.domaine.CanalReception;
import ca.vina.nexu.communication.domaine.ModeReception;
import ca.vina.nexu.communication.domaine.ReglagesAcheminementPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Les réglages d'acheminement, une ligne par étape : {@code msg_acheminement_unites} (changeset 0002). */
@Repository
public class DepotReglagesAcheminement implements ReglagesAcheminementPort.Lecture, ReglagesAcheminementPort.Ecriture {

    private record Ligne(String uniteId, AcheminementUnites.Etape etape, String modifieLe, String modifiePar) { }

    private final JdbcClient jdbc;

    public DepotReglagesAcheminement(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AcheminementUnites.Reglage> reglage(String tenantId, String uniteId) {
        List<Ligne> lignes = jdbc.sql("SELECT uniteId, canal, mode, modifieLe, modifiePar FROM msg_acheminement_unites WHERE tenantId = ? AND uniteId = ? ORDER BY ordre")
                .params(tenantId, uniteId)
                .query((rs, i) -> new Ligne(rs.getString("uniteId"), new AcheminementUnites.Etape(CanalReception.de(rs.getString("canal")), ModeReception.de(rs.getString("mode"))),
                        rs.getString("modifieLe"), rs.getString("modifiePar"))).list();
        return lignes.isEmpty() ? Optional.empty() : Optional.of(new AcheminementUnites.Reglage(uniteId, lignes.stream().map(Ligne::etape).toList(),
                lignes.getFirst().modifieLe(), lignes.getFirst().modifiePar()));
    }

    @Override
    @Transactional
    public void enregistrer(String tenantId, AcheminementUnites.Reglage r) {
        retirer(tenantId, r.uniteId());
        for (int i = 0; i < r.etapes().size(); i++) {
            AcheminementUnites.Etape e = r.etapes().get(i);
            jdbc.sql("INSERT INTO msg_acheminement_unites (tenantId, uniteId, ordre, canal, mode, modifieLe, modifiePar) VALUES (?,?,?,?,?,?,?)")
                    .params(tenantId, r.uniteId(), i + 1, e.canal().code(), e.mode().code(), r.modifieLe(), r.modifiePar()).update();
        }
    }

    @Override
    public boolean retirer(String tenantId, String uniteId) {
        return jdbc.sql("DELETE FROM msg_acheminement_unites WHERE tenantId = ? AND uniteId = ?").params(tenantId, uniteId).update() > 0;
    }
}
