-- Retour de 0020-temps-seuils-periode-paie-et-conventions (ADR 0087, AD-03) : les heures supplémentaires redeviennent
-- jour et semaine, pour tout l'organisme.
DROP TABLE IF EXISTS eff_temps_seuils_conventions;
ALTER TABLE eff_temps_parametres DROP COLUMN seuilPeriodePaieHeures;
DELETE FROM DATABASECHANGELOG WHERE ID = '0020-temps-seuils-periode-paie-et-conventions';
