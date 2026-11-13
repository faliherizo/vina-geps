package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma de l'<strong>AGPH</strong> et des <strong>remplacements long terme</strong> (lot R7), identique à
 * {@code backend/src/effectifs/agph/db.ts}. L'ancienneté des rangs est un instantané : c'est la valeur figée à
 * l'ouverture qui fait foi, pas celle de la fiche du jour.
 */
@Component("effectifsSchemaAgph")
public class SchemaAgph {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_agph_campagnes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, annee INTEGER NOT NULL, "
                    + "nom TEXT NOT NULL, corpsEmploiCode TEXT NOT NULL, etat TEXT NOT NULL, delaiChoixMinutes INTEGER NOT NULL, creeLe TEXT NOT NULL, "
                    + "ouverteLe TEXT, closeLe TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_eff_agph_campagne ON eff_agph_campagnes (tenantId, uniteCode, corpsEmploiCode, annee)",
            "CREATE TABLE IF NOT EXISTS eff_agph_rangs (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, rang INTEGER NOT NULL, "
                    + "anciennete REAL NOT NULL, etat TEXT NOT NULL, tourDebut TEXT, tourFin TEXT, PRIMARY KEY (tenantId, campagneId, ressourceId))",
            "CREATE TABLE IF NOT EXISTS eff_agph_postes (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, posteId TEXT NOT NULL, "
                    + "PRIMARY KEY (tenantId, campagneId, posteId))",
            "CREATE TABLE IF NOT EXISTS eff_agph_choix (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, posteId TEXT NOT NULL, "
                    + "mode TEXT NOT NULL, choisiLe TEXT NOT NULL, choisiPar TEXT NOT NULL, PRIMARY KEY (tenantId, campagneId, ressourceId), "
                    + "UNIQUE (tenantId, campagneId, posteId))",
            "CREATE TABLE IF NOT EXISTS eff_remplacements (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, uniteCode TEXT NOT NULL, posteId TEXT NOT NULL, "
                    + "origine TEXT NOT NULL, referenceId TEXT, debut TEXT NOT NULL, fin TEXT NOT NULL, finManifestation TEXT NOT NULL, etat TEXT NOT NULL, "
                    + "attribueA TEXT, placesAttribuees INTEGER NOT NULL DEFAULT 0, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, decideLe TEXT)",
            "CREATE TABLE IF NOT EXISTS eff_remplacement_interets (remplacementId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, "
                    + "deposeLe TEXT NOT NULL, retireLe TEXT, PRIMARY KEY (tenantId, remplacementId, ressourceId))",
            "CREATE TABLE IF NOT EXISTS eff_agph_depots (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, conformite TEXT NOT NULL, "
                    + "motifs TEXT NOT NULL DEFAULT '[]', heures REAL NOT NULL, deposeLe TEXT NOT NULL, deposePar TEXT NOT NULL, "
                    + "PRIMARY KEY (tenantId, campagneId, ressourceId))",
            "CREATE TABLE IF NOT EXISTS eff_agph_plages (campagneId TEXT NOT NULL, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, jour INTEGER NOT NULL, "
                    + "debut INTEGER NOT NULL, fin INTEGER NOT NULL, type TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_agph_plages ON eff_agph_plages (tenantId, campagneId, ressourceId)");

    /** Les colonnes venues après la première livraison du lot R7 (E-CH-1, E-CH-9, E-CH-10) : table, colonne, définition. */
    record ColonneAjoutee(String table, String colonne, String definition) { }

    static final List<ColonneAjoutee> COLONNES_AJOUTEES = List.of(
            new ColonneAjoutee("eff_agph_campagnes", "budgetHeuresSemaine", "REAL"),
            new ColonneAjoutee("eff_agph_campagnes", "reviseeLe", "TEXT"),
            new ColonneAjoutee("eff_remplacements", "transfereLe", "TEXT"),
            new ColonneAjoutee("eff_remplacements", "quartsTransferes", "INTEGER NOT NULL DEFAULT 0"),
            new ColonneAjoutee("eff_remplacements", "motifNonComble", "TEXT"));

    public SchemaAgph(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
        COLONNES_AJOUTEES.forEach(c -> ajouterColonne(jdbc, c.table(), c.colonne(), c.definition()));
        // L'ancien état « transféré » se lit désormais « non comblé, transféré le… » : un seul état final.
        jdbc.sql("UPDATE eff_remplacements SET etat = 'NON_COMBLE', transfereLe = COALESCE(transfereLe, decideLe) WHERE etat = 'TRANSFERE'").update();
    }

    private static void ajouterColonne(JdbcClient jdbc, String table, String colonne, String definition) {
        List<String> colonnes = jdbc.sql("SELECT name FROM pragma_table_info(?)").param(table).query(String.class).list();
        if (!colonnes.contains(colonne)) {
            // Un identifiant ne se lie pas en paramètre : table, colonne et définition viennent de COLONNES_AJOUTEES, jamais d'une saisie.
            jdbc.sql("ALTER TABLE " + table + " ADD COLUMN " + colonne + " " + definition).update();
        }
    }
}
