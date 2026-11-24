-- Retour de 0029-effectifs-reglages-journal (addenda 8, M-10) : plus aucun organisme ne journalise ses consultations.
DROP TABLE IF EXISTS eff_reglages_journal;
DELETE FROM DATABASECHANGELOG WHERE ID = '0029-effectifs-reglages-journal';
