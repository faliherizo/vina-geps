-- Retour de 0024-offres-quart-tour-de-role (ADR 0089, AD-07) : les quarts libérés s'offrent de nouveau à tous les
-- admissibles en même temps ; l'ordre des offres passées et leur compte d'heures sont perdus.
DROP INDEX IF EXISTS idx_eff_offre_rangs_ressource;
DROP TABLE IF EXISTS eff_offre_rangs;
DROP INDEX IF EXISTS idx_eff_offres_etat;
DROP INDEX IF EXISTS idx_eff_offres_absence;
DROP TABLE IF EXISTS eff_offres;
DROP TABLE IF EXISTS eff_offres_parametres;
DELETE FROM DATABASECHANGELOG WHERE ID = '0024-offres-quart-tour-de-role';
