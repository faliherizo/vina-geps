-- Retour de 0013-alertes-cascades (ST-G04).
DROP TABLE IF EXISTS alr_cascade_traces;
DROP TABLE IF EXISTS alr_cascades;
DROP TABLE IF EXISTS alr_politique_versions;
DROP TABLE IF EXISTS alr_politiques;
DELETE FROM DATABASECHANGELOG WHERE ID = '0013-alertes-cascades';
