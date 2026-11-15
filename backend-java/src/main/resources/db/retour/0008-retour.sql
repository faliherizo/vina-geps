-- Retour de 0008-annuaires-oidc (ADR 0075) : un annuaire OIDC générique n'existe pas avant ; il est retiré.
DELETE FROM auth_annuaires WHERE fournisseur IS NOT NULL AND fournisseur <> 'entra';
ALTER TABLE auth_annuaires DROP COLUMN emetteur;
ALTER TABLE auth_annuaires DROP COLUMN fournisseur;
DELETE FROM DATABASECHANGELOG WHERE ID = '0008-annuaires-oidc';
