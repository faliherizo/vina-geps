package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>absences d'effectifs</strong> (lot R5), identique à {@code backend/src/effectifs/absences/db.ts} :
 * la demande, sa décision automatique conservée (action et motifs des règles), la décision humaine et la dérogation ;
 * le comblement de ses places et les avis envoyés (lot U5).
 */
@Component("effectifsSchemaAbsences")
public class SchemaAbsencesEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_absences (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, "
                    + "codeAbsence TEXT NOT NULL, du TEXT NOT NULL, au TEXT NOT NULL, motif TEXT, statut TEXT NOT NULL, actionRegles TEXT NOT NULL, "
                    + "motifsRegles TEXT NOT NULL DEFAULT '[]', declareeLe TEXT NOT NULL, declareePar TEXT NOT NULL, decideeLe TEXT, decideePar TEXT, "
                    + "motifDecision TEXT, derogationCode TEXT, derogationMotif TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_absences_ressource ON eff_absences (tenantId, ressourceId, du)",
            "CREATE INDEX IF NOT EXISTS idx_eff_absences_unite ON eff_absences (tenantId, uniteCode, du)",
            "CREATE TABLE IF NOT EXISTS eff_comblements (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, absenceId TEXT NOT NULL, placeId TEXT NOT NULL, "
                    + "mode TEXT NOT NULL, resultat TEXT NOT NULL, attribueeA TEXT, mecanisme TEXT NOT NULL, creeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_comblements_absence ON eff_comblements (tenantId, absenceId)",
            "CREATE TABLE IF NOT EXISTS eff_comblement_avis (comblementId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, canal TEXT NOT NULL, "
                    + "statut TEXT NOT NULL, erreur TEXT, envoyeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_comblement_avis ON eff_comblement_avis (tenantId, comblementId)");

    public SchemaAbsencesEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
