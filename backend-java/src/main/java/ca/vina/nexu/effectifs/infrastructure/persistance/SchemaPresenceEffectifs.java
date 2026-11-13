package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma de la <strong>présence</strong> (lot R8), identique à {@code backend/src/effectifs/presence/db.ts} : les
 * pointages, le verdict de chaque preuve, et les horodatages importés d'une source externe. Aucune position n'est
 * conservée — seule la distance au milieu, qui suffit à juger (Loi 25, minimisation).
 */
@Component("effectifsSchemaPresence")
public class SchemaPresenceEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_pointages (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, placeId TEXT, milieuId TEXT, "
                    + "uniteCode TEXT NOT NULL, sens TEXT NOT NULL, horodatage TEXT NOT NULL, origine TEXT NOT NULL, saisieId TEXT, resultat TEXT NOT NULL, "
                    + "ecart TEXT, distanceMetres INTEGER, actionRegles TEXT NOT NULL, motifsRegles TEXT NOT NULL DEFAULT '[]', statut TEXT NOT NULL, "
                    + "justification TEXT, saisiLe TEXT NOT NULL, saisiPar TEXT NOT NULL, decideLe TEXT, decidePar TEXT, motifDecision TEXT)",
            "CREATE INDEX IF NOT EXISTS idx_eff_pointages_ressource ON eff_pointages (tenantId, ressourceId, horodatage)",
            "CREATE INDEX IF NOT EXISTS idx_eff_pointages_statut ON eff_pointages (tenantId, statut)",
            "CREATE TABLE IF NOT EXISTS eff_pointage_preuves (tenantId TEXT NOT NULL, pointageId TEXT NOT NULL, mecanisme TEXT NOT NULL, exigee INTEGER NOT NULL, "
                    + "fournie INTEGER NOT NULL, satisfaite INTEGER NOT NULL, distanceMetres INTEGER, detail TEXT, PRIMARY KEY (tenantId, pointageId, mecanisme))",
            "CREATE TABLE IF NOT EXISTS eff_horodatages_externes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, source TEXT NOT NULL, cle TEXT NOT NULL, "
                    + "ressourceId TEXT NOT NULL, sens TEXT NOT NULL, horodatage TEXT NOT NULL, importeLe TEXT NOT NULL, importePar TEXT NOT NULL, "
                    + "UNIQUE (tenantId, source, cle))",
            "CREATE INDEX IF NOT EXISTS idx_eff_horodatages_ressource ON eff_horodatages_externes (tenantId, ressourceId, horodatage)",
            // Profils de pointage (E-GP-1, E-GP-2, P-GP-1) : la table que le Node créait seul, et sans laquelle le Java
            // ne pouvait ni lire ni poser un profil.
            "CREATE TABLE IF NOT EXISTS eff_profils_pointage (tenantId TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, mode TEXT NOT NULL, "
                    + "porteeType TEXT NOT NULL, porteeCode TEXT, naturesNonPayees TEXT NOT NULL, pausesManuelles INTEGER NOT NULL DEFAULT 0, "
                    + "presencePrealableMinutes INTEGER NOT NULL DEFAULT 15, fenetreAPosterioriJours INTEGER, actif INTEGER NOT NULL DEFAULT 1, "
                    + "majLe TEXT NOT NULL, majPar TEXT, PRIMARY KEY (tenantId, code))");

    /**
     * Les colonnes ajoutées après coup, comme le fait {@code ajouterColonne} du Node : {@code CREATE TABLE IF NOT EXISTS}
     * ne les pose pas sur une table existante. La <b>nature d'une pause</b> (E-GP-2) est portée par le pointage qui l'ouvre ;
     * l'<b>activité</b> d'un temps de travail (E-GP-15), par celui qui l'ouvre.
     */
    static final List<Colonne> AJOUTS = List.of(
            new Colonne("eff_pointages", "naturePause", "TEXT"),
            new Colonne("eff_pointages", "activiteCode", "TEXT"));

    /** Une colonne à ajouter, si elle manque. */
    record Colonne(String table, String nom, String definition) { }

    public SchemaPresenceEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
        AJOUTS.forEach(c -> ajouter(jdbc, c));
    }

    private static void ajouter(JdbcClient jdbc, Colonne c) {
        if (!jdbc.sql("PRAGMA table_info(" + c.table() + ")").query((rs, i) -> rs.getString("name")).list().contains(c.nom())) {
            jdbc.sql("ALTER TABLE " + c.table() + " ADD COLUMN " + c.nom() + " " + c.definition()).update();
        }
    }
}
