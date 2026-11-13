package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>échanges de quarts</strong> (lot R6), identique à {@code backend/src/effectifs/echanges/db.ts} :
 * deux places, deux personnes, le verdict combiné des deux sens au moment de la proposition, et les décisions qui
 * ont suivi.
 */
@Component("effectifsSchemaEchanges")
public class SchemaEchangesEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_echanges (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, placeCedeeId TEXT NOT NULL, placeVoulueId TEXT NOT NULL, "
                    + "ressourceAId TEXT NOT NULL, ressourceBId TEXT NOT NULL, uniteA TEXT NOT NULL, uniteB TEXT NOT NULL, statut TEXT NOT NULL, "
                    + "actionRegles TEXT NOT NULL, motifsRegles TEXT NOT NULL DEFAULT '[]', proposeLe TEXT NOT NULL, proposePar TEXT NOT NULL, "
                    + "decideLe TEXT, decidePar TEXT, motifDecision TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_echanges_a ON eff_echanges (tenantId, ressourceAId)",
            "CREATE INDEX IF NOT EXISTS idx_eff_echanges_b ON eff_echanges (tenantId, ressourceBId)");

    public SchemaEchangesEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
