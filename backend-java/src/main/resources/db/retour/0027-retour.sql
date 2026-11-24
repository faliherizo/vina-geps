-- Retour de 0027-effectifs-competences (addenda 8, M-04) : les compétences détenues sont retirées ; une attribution ne
-- connaît plus aucune compétence (toute compétence exigée manque).
DROP TABLE IF EXISTS eff_competences;
DELETE FROM DATABASECHANGELOG WHERE ID = '0027-effectifs-competences';
