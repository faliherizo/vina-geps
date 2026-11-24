package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.ReglagesJournalPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptateur SQLite du réglage du journal ({@code eff_reglages_journal}, changeset 0029) ; sans ligne, rien n'est journalisé. */
@Repository
public class DepotReglagesJournal implements ReglagesJournalPort {

    private final JdbcClient jdbc;

    public DepotReglagesJournal(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean consultations(String tenantId) {
        return jdbc.sql("SELECT consultations FROM eff_reglages_journal WHERE tenantId = ?").param(tenantId).query(Integer.class).optional().orElse(0) == 1;
    }

    @Override
    public void poserConsultations(String tenantId, boolean actif) {
        jdbc.sql("INSERT INTO eff_reglages_journal (tenantId, consultations) VALUES (?, ?) ON CONFLICT (tenantId) DO UPDATE SET consultations = excluded.consultations")
                .params(tenantId, actif ? 1 : 0).update();
    }
}
