-- Retour de 0011-feuilles-payees : une feuille PAYEE redevient APPROUVEE avant que ses colonnes disparaissent.
UPDATE eff_feuilles SET statut = 'APPROUVEE' WHERE statut = 'PAYEE';
ALTER TABLE eff_feuilles DROP COLUMN paieId;
ALTER TABLE eff_feuilles DROP COLUMN payeeLe;
DELETE FROM DATABASECHANGELOG WHERE ID = '0011-feuilles-payees';
