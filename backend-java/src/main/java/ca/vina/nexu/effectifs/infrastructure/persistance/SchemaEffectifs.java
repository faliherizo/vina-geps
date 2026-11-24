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
            "CREATE TABLE IF NOT EXISTS eff_soldes_banques (tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, banqueCode TEXT NOT NULL, heures TEXT NOT NULL, auJour TEXT NOT NULL, recuLe TEXT NOT NULL, PRIMARY KEY (tenantId, ressourceId, banqueCode))",
            // Choix annuel des congés : campagnes et choix (changeset 0031, addenda 8, M-02).
            "CREATE TABLE IF NOT EXISTS eff_conges_campagnes (tenantId TEXT NOT NULL, id TEXT NOT NULL, nom TEXT NOT NULL, uniteCode TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, annee INTEGER NOT NULL, du TEXT NOT NULL, au TEXT NOT NULL, codeAbsence TEXT NOT NULL, plafondParSemaine INTEGER NOT NULL, maxSemainesParPersonne INTEGER NOT NULL, etat TEXT NOT NULL, PRIMARY KEY (tenantId, id))",
            "CREATE TABLE IF NOT EXISTS eff_conges_choix (tenantId TEXT NOT NULL, campagneId TEXT NOT NULL, ressourceId TEXT NOT NULL, rang INTEGER NOT NULL, semaine TEXT NOT NULL, statut TEXT NOT NULL, motif TEXT, PRIMARY KEY (tenantId, campagneId, ressourceId, semaine))",
            // Rattachements datés d'une personne à d'autres unités (changeset 0030, addenda 8, M-09).
            "CREATE TABLE IF NOT EXISTS eff_rattachements (tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, depuis TEXT NOT NULL, jusqua TEXT, poseePar TEXT NOT NULL, poseeLe TEXT NOT NULL, PRIMARY KEY (tenantId, ressourceId, uniteCode, depuis))",
            // Réglage du journal : les consultations (changeset 0029, addenda 8, M-10).
            "CREATE TABLE IF NOT EXISTS eff_reglages_journal (tenantId TEXT PRIMARY KEY, consultations INTEGER NOT NULL DEFAULT 0)",
            // Demandes opérationnelles (changeset 0028, addenda 8, M-08).
            "CREATE TABLE IF NOT EXISTS eff_demandes (tenantId TEXT NOT NULL, id TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, typeCode TEXT NOT NULL, du TEXT NOT NULL, au TEXT NOT NULL, commentaire TEXT, statut TEXT NOT NULL, creeLe TEXT NOT NULL, decideePar TEXT, decideeLe TEXT, motifDecision TEXT, PRIMARY KEY (tenantId, id))",
            // Compétences détenues et leurs échéances (changeset 0027, addenda 8, M-04).
            "CREATE TABLE IF NOT EXISTS eff_competences (tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, code TEXT NOT NULL, obtenueLe TEXT NOT NULL, echeance TEXT, poseePar TEXT NOT NULL, poseeLe TEXT NOT NULL, PRIMARY KEY (tenantId, ressourceId, code))");

    public SchemaEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
    }
}
