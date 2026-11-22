-- Retour de 0025-auth-sessions-revoquees (PT-02) : la déconnexion n'efface plus que le témoin du navigateur.
DROP INDEX IF EXISTS idx_auth_sessions_revoquees_expire;
DROP TABLE IF EXISTS auth_sessions_revoquees;
DELETE FROM DATABASECHANGELOG WHERE ID = '0025-auth-sessions-revoquees';
