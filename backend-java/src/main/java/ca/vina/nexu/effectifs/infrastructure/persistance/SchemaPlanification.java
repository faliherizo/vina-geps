package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma de la <strong>planification des effectifs</strong> (lot R3), identique à
 * {@code backend/src/effectifs/planification/db.ts}. Ni taux de comblement, ni statut opérationnel, ni quota
 * courant : ils se calculent à la lecture — un chiffre tenu à côté de ce qu'il résume finit par le contredire.
 */
@Component("effectifsSchemaPlanification")
public class SchemaPlanification {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_ressources (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, matricule TEXT NOT NULL, nom TEXT NOT NULL, utilisateurId TEXT, "
                    + "uniteCode TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, titreEmploiCode TEXT, statutEmploiCode TEXT, conventionCode TEXT, "
                    + "anciennete REAL NOT NULL DEFAULT 0, actif INTEGER NOT NULL DEFAULT 1, creeLe TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_ressources ON eff_ressources (tenantId, matricule)",
            "CREATE TABLE IF NOT EXISTS eff_periodes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, nom TEXT NOT NULL, debut TEXT NOT NULL, "
                    + "fin TEXT NOT NULL, etat TEXT NOT NULL, finCollecte TEXT, creeLe TEXT NOT NULL, publieeLe TEXT, closeLe TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_periodes_unite ON eff_periodes (tenantId, uniteCode, debut)",
            "CREATE TABLE IF NOT EXISTS eff_postes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, "
                    + "statutEmploiCode TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, milieuId TEXT NOT NULL, natureCode TEXT NOT NULL, cycle TEXT NOT NULL, "
                    + "debutCycle TEXT NOT NULL, parametres TEXT NOT NULL DEFAULT '{}', etat TEXT NOT NULL, titulaireId TEXT, creeLe TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_postes ON eff_postes (tenantId, code)",
            "CREATE TABLE IF NOT EXISTS eff_equipages (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, periodeId TEXT NOT NULL, uniteCode TEXT NOT NULL, date TEXT NOT NULL, "
                    + "debut INTEGER NOT NULL, fin INTEGER NOT NULL, natureCode TEXT NOT NULL, plageCode TEXT, milieuId TEXT NOT NULL, modeAttribution TEXT NOT NULL, "
                    + "minimumOperationnel INTEGER NOT NULL, note TEXT, creeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_equipages_periode ON eff_equipages (tenantId, periodeId, date)",
            "CREATE TABLE IF NOT EXISTS eff_places (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, equipageId TEXT NOT NULL, rang INTEGER NOT NULL, "
                    + "corpsEmploiCode TEXT NOT NULL, posteId TEXT, ressourceId TEXT, attribueePar TEXT, justificationForcage TEXT, attribueeLe TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_places_equipage ON eff_places (tenantId, equipageId)",
            "CREATE INDEX IF NOT EXISTS idx_eff_places_ressource ON eff_places (tenantId, ressourceId)",
            "CREATE TABLE IF NOT EXISTS eff_quotas (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, plageCode TEXT, natureCode TEXT, "
                    + "quota INTEGER NOT NULL, effectifDepuis TEXT NOT NULL, effectifJusqua TEXT, cle TEXT NOT NULL, creeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_quotas_cle ON eff_quotas (tenantId, cle)",
            "CREATE TABLE IF NOT EXISTS eff_surcharges (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, cle TEXT NOT NULL, valeur INTEGER NOT NULL, depuis TEXT NOT NULL, "
                    + "jusqua TEXT NOT NULL, motif TEXT NOT NULL, auteur TEXT NOT NULL, creeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_surcharges_cle ON eff_surcharges (tenantId, cle)");

    public SchemaPlanification(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
