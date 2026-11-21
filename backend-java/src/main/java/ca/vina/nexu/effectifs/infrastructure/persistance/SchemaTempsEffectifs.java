package ca.vina.nexu.effectifs.infrastructure.persistance;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Le schéma des <strong>feuilles de temps d'effectifs</strong> (lot R9), identique à {@code backend/src/effectifs/temps/db.ts} :
 * la feuille, ses segments versionnés (une correction remplace, n'écrase pas), les décisions de chaque palier, et les
 * paramètres de l'organisme. Aucun total ni aucune anomalie n'est stocké, sauf l'instantané de la soumission.
 */
@Component("effectifsSchemaTemps")
public class SchemaTempsEffectifs {

    static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS eff_temps_parametres (tenantId TEXT PRIMARY KEY, seuilJourHeures INTEGER NOT NULL, seuilSemaineHeures INTEGER NOT NULL, "
                    + "paliersApprobation INTEGER NOT NULL, toleranceEcartMinutes INTEGER NOT NULL, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS eff_feuilles (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, ressourceId TEXT NOT NULL, uniteCode TEXT NOT NULL, "
                    + "debut TEXT NOT NULL, fin TEXT NOT NULL, statut TEXT NOT NULL, palier INTEGER NOT NULL DEFAULT 0, paliersRequis INTEGER NOT NULL, "
                    + "instantane TEXT, soumiseLe TEXT, soumisePar TEXT, approuveeLe TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, "
                    + "UNIQUE (tenantId, ressourceId, debut))",
            "CREATE INDEX IF NOT EXISTS idx_eff_feuilles_periode ON eff_feuilles (tenantId, debut, statut)",
            "CREATE TABLE IF NOT EXISTS eff_segments_temps (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, feuilleId TEXT NOT NULL, placeId TEXT, "
                    + "date TEXT NOT NULL, debut TEXT NOT NULL, fin TEXT NOT NULL, typeHeureCode TEXT NOT NULL, source TEXT NOT NULL, version INTEGER NOT NULL, "
                    + "remplaceId TEXT, actif INTEGER NOT NULL, motif TEXT, creeLe TEXT NOT NULL, creePar TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_segments_feuille ON eff_segments_temps (tenantId, feuilleId, actif)",
            "CREATE TABLE IF NOT EXISTS eff_feuille_decisions (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, feuilleId TEXT NOT NULL, palier INTEGER NOT NULL, "
                    + "decision TEXT NOT NULL, motif TEXT, decideLe TEXT NOT NULL, decidePar TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS idx_eff_feuille_decisions ON eff_feuille_decisions (tenantId, feuilleId)",
            // Les champs propres à l'organisme (changeset 0007) : la feuille s'adapte à l'emploi sans code par emploi.
            "CREATE TABLE IF NOT EXISTS eff_temps_champs (tenantId TEXT NOT NULL, code TEXT NOT NULL, libelle TEXT NOT NULL, type TEXT NOT NULL, "
                    + "valeurs TEXT, requis INTEGER NOT NULL DEFAULT 0, actif INTEGER NOT NULL DEFAULT 1, ordre INTEGER NOT NULL DEFAULT 0, PRIMARY KEY (tenantId, code))",
            // Les seuils d'heures supplémentaires propres à une convention (changeset 0020, AD-03) ; un seuil nul reprend celui de l'organisme.
            "CREATE TABLE IF NOT EXISTS eff_temps_seuils_conventions (tenantId TEXT NOT NULL, conventionCode TEXT NOT NULL, seuilJourHeures INTEGER, "
                    + "seuilSemaineHeures INTEGER, seuilPeriodePaieHeures INTEGER, modifieLe TEXT NOT NULL, modifiePar TEXT NOT NULL, "
                    + "PRIMARY KEY (tenantId, conventionCode))");

    /**
     * Les colonnes ajoutées après coup (E-RF-13) — {@code CREATE TABLE IF NOT EXISTS} ne les ajoute pas à une table existante :
     * la corroboration se paramètre (tolérance, sort d'un pointage non confirmé, de deux sources qui divergent) ; un segment tiré
     * d'une source tierce porte son nom et sa confirmation.
     */
    static final List<Colonne> AJOUTS = List.of(
            // La description des tâches et les valeurs des champs de l'organisme, par segment (changeset 0007).
            new Colonne("eff_segments_temps", "description", "TEXT"),
            new Colonne("eff_segments_temps", "attributs", "TEXT"),
            new Colonne("eff_temps_parametres", "toleranceCorroborationMinutes", "INTEGER NOT NULL DEFAULT 5"),
            new Colonne("eff_temps_parametres", "actionNonCorrobore", "TEXT NOT NULL DEFAULT 'SIGNALER'"),
            new Colonne("eff_temps_parametres", "actionSourcesDivergentes", "TEXT NOT NULL DEFAULT 'BLOQUER'"),
            new Colonne("eff_segments_temps", "sourceExterne", "TEXT"),
            new Colonne("eff_segments_temps", "confirmeLe", "TEXT"),
            new Colonne("eff_segments_temps", "confirmePar", "TEXT"),
            // La feuille prise dans une paie traitée (changeset 0011) : elle ne se modifie plus.
            new Colonne("eff_feuilles", "payeeLe", "TEXT"),
            new Colonne("eff_feuilles", "paieId", "TEXT"),
            // Le seuil d'heures supplémentaires de la période de paie (changeset 0020, AD-03) : 0 = aucun.
            new Colonne("eff_temps_parametres", "seuilPeriodePaieHeures", "INTEGER NOT NULL DEFAULT 0"));

    /** Une colonne à ajouter, si elle manque. */
    record Colonne(String table, String nom, String definition) { }

    public SchemaTempsEffectifs(JdbcClient jdbc) {
        DDL.forEach(d -> jdbc.sql(d).update());
        AJOUTS.forEach(c -> ajouter(jdbc, c));
    }

    private static void ajouter(JdbcClient jdbc, Colonne c) {
        if (!jdbc.sql("PRAGMA table_info(" + c.table() + ")").query((rs, i) -> rs.getString("name")).list().contains(c.nom())) {
            jdbc.sql("ALTER TABLE " + c.table() + " ADD COLUMN " + c.nom() + " " + c.definition()).update();
        }
    }
}
