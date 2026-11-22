-- Retour de 0023-effectifs-index-api-echanges (AD-06) : les lectures de l'API d'échange redeviennent des balayages.
DROP INDEX IF EXISTS idx_eff_equipages_date;
DROP INDEX IF EXISTS idx_eff_feuilles_approuvee;
DELETE FROM DATABASECHANGELOG WHERE ID = '0023-effectifs-index-api-echanges';
