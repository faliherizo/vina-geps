-- Retour de 0031-effectifs-conges-annuels (addenda 8, M-02) : les campagnes et les choix sont retirés ; les absences
-- déjà octroyées restent, comme toute absence déclarée.
DROP TABLE IF EXISTS eff_conges_choix;
DROP TABLE IF EXISTS eff_conges_campagnes;
DELETE FROM DATABASECHANGELOG WHERE ID = '0031-effectifs-conges-annuels';
