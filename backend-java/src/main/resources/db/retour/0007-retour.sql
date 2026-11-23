-- Retour de 0007-feuille-champs-et-taches (deux changesets).
ALTER TABLE eff_segments_temps DROP COLUMN attributs;
ALTER TABLE eff_segments_temps DROP COLUMN description;
DROP TABLE IF EXISTS eff_temps_champs;
DELETE FROM DATABASECHANGELOG WHERE ID IN ('0007-segment-description', '0007-feuille-champs');
