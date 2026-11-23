-- Retour de 0006-alertes (ADR 0074).
DROP TABLE IF EXISTS alr_remises;
DROP TABLE IF EXISTS alr_alerte_reponses;
DROP TABLE IF EXISTS alr_alertes;
DROP TABLE IF EXISTS alr_modele_reponses;
DROP TABLE IF EXISTS alr_modeles;
DROP TABLE IF EXISTS alr_reponses;
DELETE FROM DATABASECHANGELOG WHERE ID = '0006-alertes';
