-- Retour de 0028-effectifs-demandes (addenda 8, M-08) : les demandes opérationnelles sont retirées.
DROP TABLE IF EXISTS eff_demandes;
DELETE FROM DATABASECHANGELOG WHERE ID = '0028-effectifs-demandes';
