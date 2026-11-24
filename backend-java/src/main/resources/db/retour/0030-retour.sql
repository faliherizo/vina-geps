-- Retour de 0030-effectifs-rattachements (addenda 8, M-09) : chaque personne ne compte plus que dans son unité principale.
DROP TABLE IF EXISTS eff_rattachements;
DELETE FROM DATABASECHANGELOG WHERE ID = '0030-effectifs-rattachements';
