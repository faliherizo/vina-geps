-- Retour de 0019-soutien-nature-maintenance (ADR 0085) : les fenêtres redeviennent toutes des entretiens.
ALTER TABLE soutien_maintenances DROP COLUMN nature;
DELETE FROM DATABASECHANGELOG WHERE ID = '0019-soutien-nature-maintenance';
