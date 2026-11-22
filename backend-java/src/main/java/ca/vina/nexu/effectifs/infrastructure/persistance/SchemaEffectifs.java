package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma du <strong>socle des effectifs</strong>, identique à {@code backend/src/effectifs/socle/db.ts}.
 * Côté Node, ces tables naissent à la première utilisation ; aucune migration ne les porte. Le Java fait de
 * même, à la construction : il peut servir une base où le Node n'est jamais passé.
 */
@Component("effectifsSchema")
public class SchemaEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_referentiels (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, type TEXT NOT NULL, code TEXT NOT NULL, libelleFr TEXT NOT NULL, "
                    + "libelleEn TEXT, comportement TEXT, ordre INTEGER NOT NULL DEFAULT 0, attributs TEXT NOT NULL DEFAULT '{}', effectifDepuis TEXT NOT NULL, "
                    + "effectifJusqua TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_referentiels ON eff_referentiels (tenantId, type, code, effectifDepuis)",
            "CREATE INDEX IF NOT EXISTS idx_eff_referentiels_type ON eff_referentiels (tenantId, type)",
            "CREATE TABLE IF NOT EXISTS eff_perimetres (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, utilisateurId TEXT NOT NULL, uniteCode TEXT NOT NULL, "
                    + "etendue TEXT NOT NULL, niveau TEXT NOT NULL, depuis TEXT NOT NULL, jusqua TEXT, creeLe TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_perimetres_utilisateur ON eff_perimetres (tenantId, utilisateurId)",
            "CREATE TABLE IF NOT EXISTS eff_milieux (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, typeCode TEXT NOT NULL, "
                    + "uniteCode TEXT NOT NULL, adresse TEXT, latitude REAL, longitude REAL, rayonMetres INTEGER, actif INTEGER NOT NULL DEFAULT 1, creeLe TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_milieux ON eff_milieux (tenantId, code)",
            "CREATE TABLE IF NOT EXISTS eff_milieu_preuves (tenantId TEXT NOT NULL, milieuId TEXT NOT NULL, mecanisme TEXT NOT NULL, exige INTEGER NOT NULL DEFAULT 0, "
                    + "parametres TEXT NOT NULL DEFAULT '{}', secret TEXT, PRIMARY KEY (tenantId, milieuId, mecanisme))",
            "CREATE TABLE IF NOT EXISTS eff_journal (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, horodatage TEXT NOT NULL, acteurId TEXT NOT NULL, origine TEXT NOT NULL, "
                    + "action TEXT NOT NULL, objetType TEXT NOT NULL, objetId TEXT NOT NULL, uniteCode TEXT, avant TEXT, apres TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_journal_temps ON eff_journal (tenantId, horodatage)",
            "CREATE INDEX IF NOT EXISTS idx_eff_journal_objet ON eff_journal (tenantId, objetType, objetId)",
            // Soldes des banques transmis par l'organisme (changeset 0022, AD-06b) ; heures en texte décimal exact.
            "CREATE TABLE IF NOT EXISTS eff_soldes_banques (tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, banqueCode TEXT NOT NULL, heures TEXT NOT NULL, auJour TEXT NOT NULL, recuLe TEXT NOT NULL, PRIMARY KEY (tenantId, ressourceId, banqueCode))");

    public SchemaEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
