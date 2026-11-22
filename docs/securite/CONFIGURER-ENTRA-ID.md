# Configurer Microsoft Entra ID pour VINA GePS

Ce guide relie un locataire **Microsoft Entra ID** à un organisme de VINA GePS, de l'inscription d'application jusqu'à
la première connexion. Il décrit aussi la **liaison des comptes existants**, qui est exigée depuis l'ADR 0093.

- **Décisions** :
  - [ADR 0075](../adr/0075-annuaires-oidc-generiques.md) : les annuaires OIDC ;
  - [ADR 0093](../adr/0093-resolution-identite-oidc-et-liaison-des-comptes.md) : la résolution par (émetteur, sujet) et la
    liaison.
- **Pour Keycloak et les autres fournisseurs OIDC** : [OIDC-KEYCLOAK.md](../urgences-sante/OIDC-KEYCLOAK.md). La liaison est
  la même.

## 1. Deux façons de brancher Entra ID

| | Annuaire de l'organisme (**recommandé**) | Entra ID d'instance |
|---|---|---|
| Où | Écran **Administration › Organisme › Annuaires** | Variables `AZURE_*` du serveur |
| Organisme | Celui de l'annuaire : un compte d'un autre organisme est refusé | Aucun |
| Rôles | Correspondances groupe → rôle, par annuaire | `AZURE_ROLE_MAPPING` (JSON) |
| Comptes nouveaux | Option « juste-à-temps » par annuaire, désactivée par défaut | `SSO_PROVISIONNEMENT_JAT=true`, faux par défaut |
| Adresse de retour | `https://<app>/api/auth/sso/annuaires/callback` | `https://<app>/api/auth/sso/entra/callback` |

La suite décrit l'annuaire de l'organisme. Pour l'instance, voir la section 6.

## 2. L'inscription d'application dans Entra

Dans le [centre d'administration Entra](https://entra.microsoft.com), ouvrir **Identité › Applications › Inscriptions
d'applications › Nouvelle inscription**, puis suivre ces étapes.

1. **Nom** : `VINA GePS — <organisme>`. **Types de comptes** : *Comptes dans cet annuaire organisationnel uniquement*
   (locataire unique). Ne jamais choisir le multilocataire : l'application refuse d'ailleurs `common`, `organizations` et
   `consumers`.
2. **URI de redirection** : plateforme **Web**, adresse `https://<adresse publique>/api/auth/sso/annuaires/callback`.
   L'écran Annuaires affiche l'adresse exacte à copier (« Adresse de retour à inscrire »). Elle doit correspondre à la
   lettre, schéma et port compris. Elle dépend de `PUBLIC_APP_URL`, ou de l'adresse de l'organisme en sous-domaine.
3. **Authentification** :
   - laisser décochés *Jetons d'accès* et *Jetons d'ID* du flux implicite : l'application utilise le flux par code avec
     PKCE ;
   - les jetons d'ID sont émis par l'échange du code.
4. **Certificats et secrets** :
   - créer un **secret client** et noter sa **valeur** (affichée une seule fois) ;
   - prévoir son échéance et son renouvellement ;
   - un certificat client n'est pas encore pris en charge par l'échange du code : utiliser un secret.
5. **Autorisations d'API** : *Microsoft Graph › Autorisations déléguées* : `openid`, `profile` et `email`. Accorder le
   consentement administrateur.
6. **Revendication des groupes** (facultatif, pour les rôles par groupe) :
   - dans **Configuration du jeton › Ajouter une revendication de groupes**, choisir *Groupes de sécurité* ;
   - pour le jeton d'ID, choisir *ID de groupe* ;
   - les **identifiants (GUID)** des groupes sont alors à saisir dans les correspondances.
   - Variante : déclarer des **rôles d'application** (*Rôles d'application*), les attribuer aux utilisateurs dans
     *Applications d'entreprise*, et choisir « Rôles d'application (roles) » dans l'annuaire.
7. Noter l'**ID d'application (client)** et l'**ID de l'annuaire (locataire)**, deux GUID, depuis la page *Vue d'ensemble*.

## 3. Le secret sur le serveur

Le secret ne transite jamais par l'écran ni par la base. Il est déployé comme **variable d'environnement** du serveur
Java, sous un nom qui commence par `ANNUAIRE_` :

```bash
ANNUAIRE_ENTRA_SIEGE=<valeur du secret client>   # Secrets Manager en AWS ; .env sur un poste ; jamais dans le dépôt
PUBLIC_APP_URL=https://geps.urgences-sante.qc.ca  # l'adresse publique, dont dépend l'adresse de retour
COOKIE_SECURE=1                                   # (nexu.cookie.secure) : témoins Secure, obligatoire en HTTPS
```

L'écran Annuaires indique si la variable est déployée (« ✓ déployé ») sans jamais en montrer la valeur.

## 4. L'annuaire dans l'écran d'administration

Dans **Administration › Organisme › Annuaires › Déclarer un annuaire** :

| Champ | Valeur |
|---|---|
| Nom | Le libellé du bouton de connexion (« Entra Urgences-santé ») |
| Fournisseur d'identité | Microsoft Entra ID |
| Locataire Entra | L'ID de l'annuaire (GUID), ou `organisme.onmicrosoft.com` |
| Identifiant du client | L'ID d'application (GUID) |
| Variable du secret | `ANNUAIRE_ENTRA_SIEGE` |
| Domaines de courriel admis | `urgences-sante.qc.ca` (un invité B2B d'un autre domaine est refusé) |
| Appartenances lues dans | *Groupes de sécurité (groups)* ou *Rôles d'application (roles)* |
| Correspondances | Une par ligne, par priorité : `<GUID du groupe> = manager` |
| Rôle par défaut | Vide : un membre sans groupe reconnu est refusé |
| Créer le compte à la première connexion | Décoché par défaut (section 5) |

**Correspondance des rôles.** La première ligne dont le groupe figure dans le jeton l'emporte. Le rôle d'un compte relié
**suit l'annuaire** à chaque connexion : retiré d'un groupe dans Entra, l'agent perd le rôle au prochain retour. Une
**désactivation locale** du compte l'emporte toujours sur Entra : c'est la mesure d'urgence.

## 5. Comptes existants : la liaison

Le serveur ne retrouve **jamais** un compte par le courriel. Il le retrouve par le couple **(émetteur `iss`, sujet
`sub`)** du jeton, une fois relié. Voici la première connexion de Jean, qui a déjà un compte :

1. Jean clique « Entra Urgences-santé » ; Entra l'authentifie (MFA compris).
2. Le serveur vérifie le jeton (section 7), puis cherche `(iss, sub)` : rien n'est relié.
3. **Aucun compte n'est créé.** Le navigateur va à la page **Relier mon compte** (`/connexion/relier`). Elle montre
   l'adresse vérifiée, masquée (`j***@urgences-sante.qc.ca`), et trois preuves :
   - **le mot de passe** de son compte existant (identifiant facultatif ; collage accepté) ;
   - **le lien d'invitation** reçu par courriel, si l'administration l'a invité : le compte invité naît relié ;
   - **demander l'approbation** : la demande apparaît dans **Administration › Organisme › Demandes de liaison
     d'identité**. L'administrateur vérifie l'identité de la personne, puis **approuve** (l'identité est reliée au compte qui
     porte le courriel) ou **refuse**.
4. Une fois la preuve acceptée, l'identité est reliée (`auth_identites_externes`) et la session s'ouvre. Les connexions
   suivantes entrent **directement**, même si le courriel change dans Entra.

**Limites de la liaison :**

- **Durée.** Elle dure dix minutes et admet cinq essais. Le verrou du pare-feu s'applique au mot de passe comme à la
  connexion.
- **Courriel inconnu.** Si aucun compte de l'organisme ne porte le courriel, la page le dit, et rien n'est créé. Il faut
  une invitation.
- **Comptes nés de l'ancien provisionnement.** Leur mot de passe est aléatoire : ils se relient par invitation ou par
  approbation.

### Provisionnement juste-à-temps (JAT)

Cocher **« Créer le compte à la première connexion »** pour que tout membre admis par l'annuaire obtienne un compte dès
sa première connexion. Ce compte reçoit l'organisme de l'annuaire et le rôle de ses correspondances, et il est relié
aussitôt. Même activé, le JAT **ne crée jamais un second compte**. Si un compte de l'organisme porte déjà ce courriel, la
liaison avec preuve reste exigée. À n'activer que si l'annuaire Entra est la source de vérité des personnes de
l'organisme.

## 6. Entra ID d'instance (variables d'environnement)

Pour une instance sans organismes multiples :

```bash
AZURE_CLIENT_ID=<ID d'application>
AZURE_CLIENT_SECRET=<secret>
AZURE_TENANT_ID=<ID de l'annuaire>                       # jamais « common »
AZURE_REDIRECT_URI=https://<adresse>/api/auth/sso/entra/callback
AZURE_ROLE_MAPPING={"claim":"roles","map":{"Nexu.Admin":"admin","Nexu.Manager":"manager"},"fallback":"employee"}
SSO_PROVISIONNEMENT_JAT=false                            # vrai : crée les comptes inconnus (jamais un courriel déjà porté)
```

- **Sans `AZURE_CLIENT_ID`**, Entra d'instance est **désactivé**. Le bouton Microsoft disparaît, et ses routes répondent
  404.
- **Le mode « simulé »**, où le code porte les revendications, n'existe qu'en essai (`NEXU_ESSAI=1`) **et** sur demande
  (`SSO_SIMULE=1`). Il n'est jamais actif en production : un code simulé présenté à un serveur réel est refusé (401).

## 7. Ce que le serveur vérifie, et le dépannage

À chaque retour, avant toute résolution, le serveur vérifie :

- la signature RS256 par le JWKS publié ;
- `aud` = l'ID d'application ;
- `iss` ;
- `nonce`, envoyé dans la demande et contrôlé au retour (rejeu) ;
- `exp`, avec 60 s de tolérance d'horloge ;
- la présence de `sub` ;
- `azp` si plusieurs audiences.

Puis l'organisme vérifie :

- le courriel vérifié ;
- le domaine admis ;
- un groupe ou un rôle par défaut ;
- le compte actif et de l'organisme.

| Symptôme | Cause probable | Remède |
|---|---|---|
| Entra : `AADSTS50011` (redirect URI mismatch) | L'adresse de retour inscrite diffère de celle envoyée | Copier l'adresse affichée par l'écran Annuaires ; vérifier `PUBLIC_APP_URL` (schéma, port) |
| `Entra : émetteur inattendu` | Locataire mal saisi, ou jeton d'un autre locataire | Saisir le GUID du locataire ; un compte invité B2B doit appartenir aux domaines admis |
| `OIDC : l'émetteur publié (…) n'est pas celui de l'annuaire` | Fournisseur générique : l'émetteur saisi diffère du `issuer` publié (barre finale, `http`/`https`, nom d'hôte) | Saisir exactement le `issuer` du document `/.well-known/openid-configuration` |
| `nonce inattendu (rejeu ?)` | Retour rejoué, onglet ancien, ou deux connexions croisées | Recommencer depuis la page de connexion |
| `invalid or expired SSO state` / « demande de connexion inconnue ou expirée » | Plus de dix minutes entre le départ et le retour, ou serveur redémarré | Recommencer ; en multi-instance, prévoir une affinité de session |
| `JWT expired` / `premature` | Horloge du serveur décalée de plus de 60 s | Synchroniser l'horloge (NTP) |
| `le secret de l'annuaire n'est pas déployé` (503) | Variable `ANNUAIRE_…` absente du serveur | Déployer le secret et redémarrer |
| `le domaine de « … » n'est pas admis` | Courriel hors des domaines déclarés | Ajouter le domaine, ou refuser l'invité |
| `aucun groupe de « … » ne donne accès` | Revendication `groups` absente du jeton d'ID, ou GUID non déclaré | Ajouter la revendication de groupes (section 2, étape 6) ; vérifier les GUID |
| `le compte de l'application est désactivé` | Désactivation locale | Réactiver le compte si c'est voulu |
| `le compte n'appartient pas à l'organisme de cet annuaire` | Identité reliée à un compte d'un autre organisme | Vérifier l'annuaire et le compte |
| Page « Relier mon compte » à chaque connexion | La demande d'approbation n'est pas encore approuvée | L'administration approuve dans **Demandes de liaison** |

Le journal d'audit consigne chaque issue :

- `auth.sso.liaison.requise` ;
- `auth.sso.lie`, avec la preuve et l'émetteur ;
- `auth.sso.refus`, avec le motif ;
- `auth.sso.liaison.demandee`, `auth.sso.liaison.approuvee` et `auth.sso.liaison.refusee` ;
- `auth.sso.login.ok` ;
- `auth.sso.annuaire.refus`.

Il ne consigne ni mot de passe ni jeton.

## 8. Les témoins de session

| Témoin | Attributs |
|---|---|
| `session` | `HttpOnly`, `SameSite=Lax`, `Path=/`, 8 h, `Secure` si `COOKIE_SECURE` |
| `csrf` | `SameSite=Lax`, lisible par l'interface (double soumission) |
| `liaison_sso` | `HttpOnly`, `SameSite=Strict`, `Path=/api/auth/sso/liaison`, 10 min, `Secure` si `COOKIE_SECURE` ; effacé à la liaison |

## 9. Essayer avec le Keycloak du bac à sable

Un vrai fournisseur OIDC tourne en local : Keycloak 26, avec le royaume `vina-geps-liaison`. Ce royaume contient :

- Jean, dont le courriel correspond à un compte existant ;
- un inconnu.

```bash
docker compose -f bac-a-sable/docker-compose.yml up -d keycloak     # http://localhost:18180 (console : admin / admin)
cd backend-java && mvn -o -DskipTests -Djacoco.skip=true package && cd ..
E2E_API_PORT=4071 E2E_WEB_PORT=5371 \
  ANNUAIRE_KEYCLOAK_LIAISON=bac-a-sable-liaison-secret-essai-uniquement PUBLIC_APP_URL=http://localhost:5371 \
  E2E_KEYCLOAK_LIAISON_URL=http://localhost:18180 \
  backend-java/scripts/campagne-e2e.sh tests/e2e/liaison-identite-keycloak.spec.ts
```

L'essai déclare l'annuaire, puis crée le compte de Jean. Il passe ensuite par la vraie page de Keycloak, la page
« Relier mon compte », un mauvais puis le bon mot de passe, et une seconde connexion directe. Il vérifie enfin que
l'inconnu n'obtient ni compte ni session. Les mots de passe et le secret du royaume sont des valeurs de bac à sable.
