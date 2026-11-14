# VINA GEPS et Keycloak : connexion OIDC, utilisateurs, rôles et groupes

> Écrit pour l'équipe TI qui branche VINA GEPS sur un Keycloak (ou tout autre fournisseur OpenID
> Connect), et pour les développeurs qui maintiennent l'essai de bout en bout. Ce document décrit le
> processus validé le 14 novembre 2026 : comment un compte Keycloak devient un compte de
> l'organisme, avec quel rôle, et ce qui se passe quand il change de groupe, quitte ou est
> désactivé. Le protocole avec Microsoft Entra ID est décrit dans
> [UTILISATEURS-PROFILS-ACCES.md](UTILISATEURS-PROFILS-ACCES.md) ; la décision, dans
> l'[ADR 0075](../adr/0075-annuaires-oidc-generiques.md).

## 1. En bref

| Question | Réponse |
|---|---|
| Protocole | OpenID Connect 1.0, flux *Authorization Code* avec **PKCE** (S256), `state` et `nonce` |
| Où se déclare Keycloak | Par organisme : Administration › Organisme › « Déclarer un annuaire », fournisseur **OpenID Connect** |
| Ce que VINA GEPS lit de Keycloak | Le document de découverte (`{émetteur}/.well-known/openid-configuration`) : adresses d'autorisation et d'échange, clés publiques (JWKS) |
| Jeton vérifié | L'`id_token` : signature RS256 par le JWKS, émetteur (`iss`) égal à l'émetteur déclaré, audience (`aud`) égale au client, expiration, `nonce` |
| Rôle dans VINA GEPS | Déduit des **rôles de royaume** (revendication `roles`) ou des **groupes** (revendication `groups`), par des règles ordonnées : la première qui correspond l'emporte |
| Source de vérité des appartenances | Keycloak. Le rôle est recalculé **à chaque connexion** |
| Mesure d'urgence | Désactiver le compte dans VINA GEPS : il est refusé, quoi qu'en dise Keycloak |

Avant ce changement, le module OIDC ne savait parler qu'à Microsoft Entra ID. Les adresses
`login.microsoftonline.com/…/oauth2/v2.0/…` étaient écrites en dur, et un identifiant de client
devait être un GUID. Un Keycloak était donc refusé dès sa déclaration. L'annuaire « OpenID Connect »
lit désormais ses adresses dans le document de découverte de l'émetteur, et le flux Entra n'a pas
changé.

## 2. Le parcours d'une connexion

```mermaid
sequenceDiagram
    autonumber
    actor U as Personne
    participant App as VINA GEPS (navigateur)
    participant API as Serveur Java
    participant KC as Keycloak (royaume)
    U->>App: choisit l'organisme, puis « Se connecter avec Keycloak »
    App->>API: GET /api/auth/sso/annuaires/{id}/login
    API->>KC: GET {émetteur}/.well-known/openid-configuration
    API-->>App: 302 vers authorization_endpoint (client_id, state, nonce, code_challenge S256)
    App->>KC: page de connexion de Keycloak (mot de passe, MFA du royaume)
    KC-->>App: 302 vers /api/auth/sso/annuaires/callback?code&state
    App->>API: GET callback
    API->>KC: POST token_endpoint (code, code_verifier, secret du client)
    KC-->>API: id_token signé
    API->>KC: GET JWKS : vérifie signature, iss, aud, exp, nonce
    API->>API: admission par l'organisme (courriel vérifié, domaine, règles groupe → rôle)
    API->>API: compte trouvé, créé ou reclassé dans l'organisme ; session émise
    API-->>App: 302 vers l'adresse publique, cookies de session (HttpOnly) et CSRF
```

Le code est dans `backend-java/.../auth/` :

- `domaine/Annuaires.java` : la déclaration, sa validation et l'admission ;
- `application/ServiceAnnuaires.java` : le flux ;
- `infrastructure/persistance/EchangeurOidcHttp.java` : la découverte, l'échange du code et la vérification du jeton ;
- `infrastructure/web/ControleurAnnuaires.java` : les routes.

## 3. Configurer Keycloak

Le royaume d'essai versionné, [`e2e/donnees/keycloak/realm-vina-geps.json`](../../e2e/donnees/keycloak/realm-vina-geps.json),
sert de modèle. Il s'importe tel quel dans un Keycloak 26.

### 3.1 Le client

| Réglage | Valeur | Pourquoi |
|---|---|---|
| Client ID | `vina-geps` | Ce que VINA GEPS envoie, et ce que l'audience du jeton doit porter |
| Client authentication | **On** (client confidentiel) | L'échange du code exige le secret du client |
| Standard flow | On ; *Direct access grants* et *Implicit flow* : **Off** | Seul le flux Authorization Code est employé |
| PKCE | `S256` (attribut `pkce.code.challenge.method`) | Keycloak refuse alors une demande sans PKCE |
| Valid redirect URIs | `https://{adresse publique}/api/auth/sso/annuaires/callback`, exactement | L'adresse de retour s'affiche dans l'écran des annuaires ; pas de joker en production |
| Mappeurs du client | `roles` : *User Realm Role*, revendication `roles`, multivaluée, **dans l'ID token**<br>`groups` : *Group Membership*, revendication `groups`, *Full group path* **Off**, **dans l'ID token** | Par défaut, Keycloak range les rôles dans `realm_access.roles` du jeton d'accès seulement. VINA GEPS lit l'`id_token`, et une revendication plate |

### 3.2 Les rôles : une hiérarchie par composition

| Rôle de royaume | Contient (composite) | Rôle VINA GEPS |
|---|---|---|
| `vina-admin` | `vina-directeur` | `admin` |
| `vina-directeur` | `vina-chef` | `director` |
| `vina-rh` | `vina-employe` | `rh` |
| `vina-chef` | `vina-employe` | `manager` |
| `vina-employe` | — | `employee` |

Le mappeur *User Realm Role* émet les rôles **effectifs**, composites développés. Diane, qui n'a
que `vina-admin`, reçoit donc `[vina-admin, vina-directeur, vina-chef, vina-employe]`. C'est
**l'ordre des règles** déclarées dans VINA GEPS qui choisit : la plus élevée d'abord.

### 3.3 Les groupes : la hiérarchie de l'organisation

```
/Urgences-santé
├── Administration TI        → vina-admin
├── Direction                → vina-directeur
├── Ressources humaines      → vina-rh
└── Opérations               → vina-employe
    ├── Chefs d'équipe       → vina-chef
    └── Paramédics           (aucun rôle propre : hérite de vina-employe)
```

Un membre d'un sous-groupe hérite des rôles de ses groupes parents. Il suffit donc de placer une
paramédic dans « Paramédics » pour qu'elle soit employée ; la promouvoir revient à l'ajouter à
« Chefs d'équipe ». On attribue les rôles **aux groupes, jamais aux personnes** : un départ ou une
mutation ne se fait alors qu'à un endroit.

### 3.4 Les utilisateurs

- **Courriel vérifié** (`emailVerified`) : VINA GEPS refuse un courriel que Keycloak déclare non
  vérifié, car n'importe qui aurait pu le saisir.
- **Domaine** : le courriel doit appartenir à un domaine déclaré pour l'annuaire. Un invité
  `@gmail.com` est refusé, même dans le bon groupe.
- **Prénom et nom** remplis : avec le profil utilisateur déclaratif de Keycloak 26, un profil
  incomplet fait apparaître un écran « Mettre à jour le compte » à la première connexion.
- **MFA** : il s'impose dans Keycloak (flux *browser*, OTP ou WebAuthn). VINA GEPS ne le
  redemande pas après une fédération.

## 4. Déclarer l'annuaire dans VINA GEPS

Administration › Organisme › **Déclarer un annuaire** (réservé au rôle `admin` de l'organisme) :

| Champ | Exemple | Règle |
|---|---|---|
| Fournisseur d'identité | OpenID Connect (Keycloak, Okta…) | `entra` reste le défaut |
| Émetteur OIDC | `https://sso.urgences-sante.qc.ca/realms/vina-geps` | La valeur **exacte** de `iss`. HTTPS obligatoire ; `http` n'est admis que sur `localhost` et `127.0.0.1`. Ni requête ni fragment ; la barre finale est retirée |
| Identifiant du client | `vina-geps` | Lettres, chiffres, `. _ : @ -` |
| Variable du secret | `ANNUAIRE_KEYCLOAK` | Le **nom** d'une variable d'environnement du serveur, qui porte le secret du client. Le secret ne passe jamais par l'écran ; la liste dit « ✓ déployé » ou « ✗ absente » |
| Domaines admis | `urgences-sante.qc.ca` | Un au moins |
| Appartenances lues dans | Rôles d'application (`roles`) | Ou groupes (`groups`), si l'on mappe des groupes plutôt que des rôles |
| Correspondances | `vina-admin = admin`<br>`vina-directeur = director`<br>`vina-rh = rh`<br>`vina-chef = manager`<br>`vina-employe = employee` | Une par ligne, **par priorité** |
| Rôle par défaut | Aucun | Sans règle correspondante, la connexion est refusée. Un rôle par défaut ouvrirait l'accès à tout membre du royaume |

## 5. Gestion des utilisateurs : le cycle de vie

| Événement | Où agir | Effet dans VINA GEPS |
|---|---|---|
| Arrivée | Keycloak : créer la personne, la placer dans son groupe | Compte **créé** dans l'organisme à la première connexion. Identifiant = courriel ; aucun mot de passe utilisable |
| Promotion, mutation | Keycloak : changer de groupe | Rôle **recalculé** à la connexion suivante ; même compte, pas de doublon |
| Retrait de tous les groupes | Keycloak | Connexion **refusée** : « aucun groupe … ne donne accès » |
| Désactivation dans Keycloak | Keycloak : *Enabled* Off | Keycloak refuse lui-même ; VINA GEPS ne voit rien passer |
| Mesure d'urgence | VINA GEPS : désactiver le compte (Administration › Comptes) | Refusé au retour de Keycloak : « le compte … est désactivé dans l'application » |
| Départ | Keycloak : supprimer ou désactiver | Plus aucune connexion. Une session déjà ouverte dure jusqu'à son expiration (voir § 8) |

Chaque connexion, admise ou refusée, est consignée au journal d'audit : `auth.sso.login.ok` avec
le rôle et `provisioned` ; `auth.sso.annuaire.refus` avec le motif.

## 6. Les essais : profils JSON et hiérarchies

### 6.1 Ce qui est vérifié

| Niveau | Essai | Ce qu'il prouve |
|---|---|---|
| Domaine | `AnnuairesTest` (`oidcGenerique`, `courrielVerifie`) | Validation de l'émetteur et du client ; fournisseur inconnu ; courriel non vérifié refusé |
| Infrastructure | `ControleurSsoHttpTest.decouverte` | Lecture des adresses ; document incomplet ou illisible refusé |
| HTTP | `ControleurAnnuairesHttpTest.oidcGenerique` | Redirection vers l'adresse découverte, configuration de l'échange, rôle par priorité, rôle par défaut, refus, fournisseur injoignable (503) |
| Écran | `frontend/tests/annuaires-organisme.test.tsx` | Le choix du fournisseur remplace le locataire par l'émetteur |
| **Bout en bout, vrai Keycloak** | [`e2e/tests/e2e/oidc-keycloak.spec.ts`](../../e2e/tests/e2e/oidc-keycloak.spec.ts) | Déclaration à l'écran, puis chaque profil du jeu JSON par la vraie page de Keycloak, puis la hiérarchie dynamique |

### 6.2 Le jeu de profils

[`e2e/donnees/keycloak/profils-oidc.json`](../../e2e/donnees/keycloak/profils-oidc.json) décrit
l'annuaire à déclarer et, pour chaque profil, ce que la connexion doit produire. Ajouter un profil,
c'est ajouter un utilisateur au royaume et une entrée au jeu : l'essai se génère.

| Profil | Groupes Keycloak | Rôles effectifs | Attendu |
|---|---|---|---|
| `diane.admin` | Administration TI | admin, directeur, chef, employé | **admin** ; voit l'administration ; administre les annuaires |
| `robert.directeur` | Direction | directeur, chef, employé | **director** ; approbations et console des feuilles ; ni administration ni avis |
| `rachel.rh` | Ressources humaines | rh, employé | **rh** ; écrans employé ; pas de console d'encadrement |
| `marc.chef` | Opérations/Chefs d'équipe | chef, employé (hérité) | **manager** ; console et approbations ; pas d'administration |
| `julie.paramedic` | Opérations/Paramédics | employé (hérité du parent) | **employee** ; ses écrans seulement |
| `luc.cumul` | RH + Chefs d'équipe | rh, chef, employé | **rh** : la règle `vina-rh` est déclarée avant `vina-chef` |
| `invite.externe` | Paramédics, courriel `@gmail.com` | employé | **refusé** : domaine non admis |
| `nadia.nonverifiee` | Paramédics, courriel non vérifié | employé | **refusé** : courriel non vérifié |
| `oscar.orphelin` | aucun | aucun | **refusé** : aucun groupe ne donne accès |
| `denis.desactive` | Paramédics, désactivé | — | **refusé par Keycloak** ; l'application ne voit rien |

Pour chaque profil admis, l'essai vérifie quatre choses :
- le rôle et l'organisme lus sur `/api/auth/me` ;
- les menus visibles et absents ;
- le droit d'administrer les annuaires, sur l'API (200 ou 403) : un menu masqué n'est pas une garde ;
- l'absence de violation axe-core.

**Hiérarchie dynamique** : par l'API d'administration de Keycloak, Julie est ajoutée à
« Chefs d'équipe ». Elle devient gestionnaire, sur le **même** compte. Retirée du groupe, elle
redevient employée. Désactivée dans VINA GEPS, elle est refusée.

### 6.3 Lancer l'essai

```bash
scripts/keycloak-essai.sh start          # Keycloak 26.8 sur :8180, royaume importé (console : admin / admin)
(cd backend-java && mvn -o -q -DskipTests -Djacoco.skip=true package)   # si le serveur a changé
E2E_KEYCLOAK_URL=http://localhost:8180 \
ANNUAIRE_KEYCLOAK=vina-geps-secret-essai-uniquement \
PUBLIC_APP_URL=http://localhost:5331 \
E2E_API_PORT=4031 E2E_WEB_PORT=5331 \
  backend-java/scripts/campagne-e2e.sh tests/e2e/oidc-keycloak.spec.ts
scripts/keycloak-essai.sh stop
```

Sans `E2E_KEYCLOAK_URL`, le fichier est **sauté** : le rapport de Playwright le compte comme tel,
il ne passe pas en vert sans avoir rien vérifié. Le secret et les mots de passe du royaume sont des
valeurs d'essai, à ne jamais reprendre ailleurs.

Résultat du 14 novembre 2026 : **12 essais sur 12**, en 46 secondes. Une contre-épreuve a été faite :
avec un attendu faussé (Luc en `manager`), l'essai échoue sur « cumul : rôle et organisme ».

## 7. Déployer avec un vrai Keycloak

- **Le même émetteur pour le navigateur et le serveur.** Keycloak inscrit dans `iss` l'adresse sous
  laquelle on l'a joint. Le navigateur et le serveur Java doivent donc l'atteindre par **la même
  adresse publique**. Fixer `KC_HOSTNAME=https://sso.organisme.ca` ; dans la pile Docker locale,
  `localhost:8180` n'est pas joignable depuis le conteneur du serveur.
- **Le secret** : une variable `ANNUAIRE_…` par annuaire, dans l'environnement du serveur (fichier
  `.env` de la pile, ou gestionnaire de secrets). Sans elle, la connexion répond 503 et le nomme.
- **Keycloak injoignable** : la connexion répond 503 avec « l'annuaire … est injoignable ». La
  connexion locale d'urgence (« bris de glace ») reste disponible.
- **Plusieurs royaumes** : un annuaire par royaume, chacun avec ses domaines et ses règles.

## 8. Écarts constatés

| Écart | Conséquence | Correctif proposé |
|---|---|---|
| Un refus au retour (domaine, courriel non vérifié, aucun groupe, compte désactivé) s'affiche en **JSON brut** sur la route de rappel | Le motif est juste, mais la personne lit `{"errors":[…]}` au lieu d'un message dans la page de connexion | Rediriger vers la page de connexion avec un code de refus, et afficher le motif traduit (à décider) |
| Pas de déconnexion fédérée | Se déconnecter de VINA GEPS ne ferme pas la session Keycloak. La connexion suivante, sur le même poste, passe sans mot de passe | Appeler `end_session_endpoint` à la déconnexion ; ajouter la déconnexion par canal arrière |
| Pas de provisionnement SCIM ni d'événements de Keycloak | Un départ n'est constaté qu'à la tentative de connexion suivante. Une session déjà ouverte dure jusqu'à son expiration | SCIM 2.0, ou écoute des événements d'administration de Keycloak |
| Les groupes ne portent que le **rôle** | L'unité, le périmètre et le supérieur hiérarchique (`employees.managerId`) ne viennent pas de Keycloak | Mapper un attribut `unite` ou `manager` du jeton vers le périmètre et l'organigramme |
| La route d'instance `/api/auth/sso/entra/*` (configurée par `AZURE_*`) crée des comptes **sans organisme** et ne reclasse pas le rôle | Elle ne doit servir qu'à la démonstration | Déjà signalé dans UTILISATEURS-PROFILS-ACCES.md § 6 |
