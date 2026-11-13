package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>disponibilités par période</strong> (lot R4), identique à
 * {@code backend/src/effectifs/disponibilites/db.ts} : un dépôt par ressource et par période, avec sa conformité
 * au moment du dépôt, et ses plages.
 */
@Component("effectifsSchemaDisponibilites")
public class SchemaDisponibilites {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_depots (tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, ressourceId TEXT NOT NULL, conformite TEXT NOT NULL, "
                    + "motifs TEXT NOT NULL DEFAULT '[]', deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, approuveLe TEXT, approuvePar TEXT, "
                    + "PRIMARY KEY (tenantId, periodeId, ressourceId))",
            "CREATE TABLE IF NOT EXISTS eff_disponibilites (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, ressourceId TEXT NOT NULL, "
                    + "date TEXT NOT NULL, debut INTEGER NOT NULL, fin INTEGER NOT NULL, type TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_disponibilites ON eff_disponibilites (tenantId, periodeId, ressourceId)");

    public SchemaDisponibilites(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
