-- Retour de 0026 (ADR 0093) : les identités reliées et les demandes de liaison disparaissent ; le SSO retrouverait
-- de nouveau le compte par le courriel. L'option de provisionnement juste-à-temps des annuaires est retirée.
ALTER TABLE auth_annuaires DROP COLUMN provisionnementJat;
DROP INDEX IF EXISTS idx_auth_demandes_liaison_attente;
DROP TABLE IF EXISTS auth_demandes_liaison;
DROP INDEX IF EXISTS idx_auth_identites_externes_user;
DROP TABLE IF EXISTS auth_identites_externes;
DELETE FROM DATABASECHANGELOG WHERE ID = '0026-annuaires-provisionnement-jat';
DELETE FROM DATABASECHANGELOG WHERE ID = '0026-auth-identites-externes';
