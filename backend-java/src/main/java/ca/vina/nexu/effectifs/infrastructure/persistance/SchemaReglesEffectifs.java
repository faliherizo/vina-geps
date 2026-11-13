package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma du <strong>moteur de règles des effectifs</strong> (lot R2), identique à
 * {@code backend/src/effectifs/regles/db.ts}. Comme le socle, il naît à la construction : le Java peut
 * servir une base où le Node n'est jamais passé.
 */
@Component("effectifsSchemaRegles")
public class SchemaReglesEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_regles (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, contexte TEXT NOT NULL, cle TEXT NOT NULL, porteeType TEXT NOT NULL, "
                    + "porteeCode TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_regles ON eff_regles (tenantId, contexte, cle, porteeType, IFNULL(porteeCode, ''))",
            "CREATE TABLE IF NOT EXISTS eff_regle_versions (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, regleId TEXT NOT NULL, version INTEGER NOT NULL, "
                    + "condition TEXT NOT NULL, action TEXT NOT NULL, message TEXT NOT NULL, priorite INTEGER NOT NULL DEFAULT 0, effectifDepuis TEXT NOT NULL, "
                    + "effectifJusqua TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_regle_versions ON eff_regle_versions (tenantId, regleId, version)",
            "CREATE TABLE IF NOT EXISTS eff_regle_evaluations (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, contexte TEXT NOT NULL, objetType TEXT NOT NULL, "
                    + "objetId TEXT NOT NULL, action TEXT NOT NULL, motifs TEXT NOT NULL, versions TEXT NOT NULL, faits TEXT NOT NULL, dateDuFait TEXT NOT NULL, "
                    + "horodatage TEXT NOT NULL, declencheur TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_regle_evaluations_objet ON eff_regle_evaluations (tenantId, objetType, objetId)");

    public SchemaReglesEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
