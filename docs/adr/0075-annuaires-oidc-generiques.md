# ADR 0075 — Un annuaire d'organisme peut être tout fournisseur OpenID Connect, désigné par son émetteur

- **Statut** : accepté, le 14 novembre 2026.
- **Décideurs** : équipe VINA.
- **Concerne** :
  - `auth/domaine/Annuaires.java` (`Fournisseur`, `emetteur`, port `Decouverte`, courriel vérifié) ;
  - `auth/application/ServiceAnnuaires.java` ;
  - `auth/infrastructure/persistance/EchangeurOidcHttp.java` et `DepotAnnuaires.java` ;
  - le changeset `0008-annuaires-oidc.xml` ;
  - `frontend/src/admin/AnnuairesOrganisme.tsx`.
- **Documentation** : [docs/urgences-sante/OIDC-KEYCLOAK.md](../urgences-sante/OIDC-KEYCLOAK.md).

## Contexte

Les annuaires d'organisme (P-TN-2) faisaient déjà le plus difficile, et le faisaient bien :
- PKCE, vérification de l'`id_token` par JWKS, de l'émetteur et du nonce ;
- admission par domaine et par groupes ;
- reclassement à chaque connexion ;
- primauté de la désactivation locale.

Mais un annuaire ne pouvait être qu'un locataire Microsoft Entra ID : l'autorité était
`https://login.microsoftonline.com/{locataire}`, les adresses `/oauth2/v2.0/authorize` et `/token`
étaient construites en dur, et un client devait être un GUID. Un Keycloak, ou un Okta, était refusé
dès la saisie. Or on demande de valider le processus avec Keycloak, et des organismes clients en
exploitent.

## Options considérées

1. **Un annuaire « OIDC générique », adresses lues dans le document de découverte** (retenue).
2. **Keycloak en courtier devant Entra** : VINA GEPS ne parlerait qu'à Entra, et Keycloak serait
   déclaré comme fournisseur d'Entra. Aucun code ne change, mais l'organisme dépend d'une
   fédération Entra qu'il n'a pas forcément. C'est un contournement, pas une prise en charge.
3. **Gabarit Keycloak en dur** (`{base}/realms/{r}/protocol/openid-connect/…`) : simple, mais
   propre à un seul produit. Okta, Auth0 ou ADFS demanderaient chacun un nouveau gabarit, ce
   qu'interdit le principe ouvert/fermé.
4. **Spring Security OAuth2 Client** : une bibliothèque éprouvée, mais elle remplacerait un flux
   déjà vérifié par les essais. Sa configuration statique se prête mal à des annuaires déclarés à
   chaud par organisme. Coût et risque disproportionnés.

## Décision

- `Annuaires.Fournisseur` est une énumération : `ENTRA` (le défaut, inchangé) ou `OIDC`. Un
  annuaire `OIDC` porte un **émetteur**, sous les règles suivantes :
  - HTTPS ; `http` n'est admis que sur `localhost` et `127.0.0.1` ;
  - ni requête ni fragment ;
  - la barre finale est retirée, car l'émetteur se compare à la lettre à `iss`.

  Son client est un nom (lettres, chiffres, `. _ : @ -`).
- Le **port `Annuaires.Decouverte`** rend les adresses d'autorisation et d'échange du document de
  découverte. Il est implanté par `EchangeurOidcHttp`, qui sert déjà à l'échange du code et à la
  lecture du JWKS. La découverte est lue à chaque départ de connexion : une rotation d'adresses
  chez le fournisseur ne demande rien.
- Un fournisseur injoignable rend un **503 nommé**, pas un 500.
- Pour **tous** les annuaires, un courriel déclaré non vérifié (`email_verified: false`) est
  refusé. Entra n'émet pas cette revendication, son comportement ne change donc pas.
- Le changeset `0008` ajoute `fournisseur` (défaut `entra`) et `emetteur` : les annuaires existants
  restent des annuaires Entra.

## Conséquences

- **Positives**
  - Keycloak, Okta, Auth0 ou ADFS se déclarent à l'écran, sans code.
  - La vérification du jeton, l'admission et le cycle de vie restent ceux d'Entra, déjà éprouvés.
  - Un essai de bout en bout contre un vrai Keycloak (`oidc-keycloak.spec.ts`, profils JSON)
    vérifie les rôles, les groupes hérités, les composites, la priorité des règles et les refus.
- **Négatives**
  - Une requête de découverte à chaque départ de connexion. C'est un aller-retour réseau, sans
    cache. Acceptable au volume d'un organisme ; un cache court se justifierait au-delà.
  - Le navigateur et le serveur doivent atteindre le fournisseur par **la même adresse**, puisque
    `iss` en dépend. C'est une contrainte de déploiement, documentée.
- **Écarts laissés ouverts** (OIDC-KEYCLOAK.md § 8) : refus affiché en JSON brut, pas de
  déconnexion fédérée, pas de SCIM, groupes limités au rôle.
