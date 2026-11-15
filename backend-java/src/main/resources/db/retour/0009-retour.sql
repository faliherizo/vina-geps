-- Retour de 0009-alertes-r1-bis.
DROP TABLE IF EXISTS alr_reglages;
DROP TABLE IF EXISTS alr_modele_groupes;
DROP TABLE IF EXISTS alr_modele_version_reponses;
DROP TABLE IF EXISTS alr_modele_versions;
ALTER TABLE alr_alertes DROP COLUMN modeleVersion;
ALTER TABLE alr_alertes DROP COLUMN modeleId;
ALTER TABLE alr_modeles DROP COLUMN version;
DELETE FROM DATABASECHANGELOG WHERE ID = '0009-alertes-r1-bis';
