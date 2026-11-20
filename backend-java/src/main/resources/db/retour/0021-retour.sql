-- Retour de 0021-agph-traitement-delai-echu (ADR 0087, AD-05) : le délai échu attribue d'office, comme avant.
ALTER TABLE eff_agph_campagnes DROP COLUMN traitementDelaiEchu;
DELETE FROM DATABASECHANGELOG WHERE ID = '0021-agph-traitement-delai-echu';
