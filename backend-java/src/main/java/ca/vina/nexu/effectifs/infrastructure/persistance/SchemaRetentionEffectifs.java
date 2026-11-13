package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Le schéma de la <strong>rétention des effectifs</strong> (lot R11), identique au {@code tables()} de
 * {@code backend/src/effectifs/retention/service.ts}. La purge touche les tables des autres lots : leurs schémas
 * sont exigés ici, comme le Node les assure avant de compter ou de supprimer — une purge qui trouverait une table
 * absente échouerait au lieu de ne rien trouver.
 */
@Component("effectifsSchemaRetention")
// Les tables purgées appartiennent aux autres lots : elles doivent exister avant la première purge.
@DependsOn({"effectifsSchema", "effectifsSchemaRegles", "effectifsSchemaPlanification", "effectifsSchemaPresence", "effectifsSchemaTemps",
        "effectifsSchemaAbsences", "effectifsSchemaEchanges"})
public class SchemaRetentionEffectifs {

    static final String DDL = "CREATE TABLE IF NOT EXISTS eff_retention (\n"
            + "    tenantId TEXT NOT NULL, classe TEXT NOT NULL, annees INTEGER NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL,\n"
            + "    PRIMARY KEY (tenantId, classe))";

    public SchemaRetentionEffectifs(JdbcClient jdbc) {
        jdbc.sql(DDL).update();
    }
}
