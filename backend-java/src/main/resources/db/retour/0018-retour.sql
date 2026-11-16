-- Retour de 0018-paie-regles-et-validation (ADR 0084) : les bulletins gardent leur brut dans leur calcul.
DROP TABLE IF EXISTS paie_validations_temps;
DROP TABLE IF EXISTS paie_regles;
ALTER TABLE paie_bulletins DROP COLUMN gains;
ALTER TABLE paie_reglages DROP COLUMN validationTempsExigee;
ALTER TABLE paie_dossiers DROP COLUMN echelon;
DELETE FROM DATABASECHANGELOG WHERE ID = '0018-paie-regles-et-validation';
