-- Retour de 0022-effectifs-soldes-banques (AD-06b) : les soldes reçus de l'organisme sont retirés ; une absence n'en
-- connaît plus aucun (le fait soldeBanqueConnu vaut faux).
DROP TABLE IF EXISTS eff_soldes_banques;
DELETE FROM DATABASECHANGELOG WHERE ID = '0022-effectifs-soldes-banques';
