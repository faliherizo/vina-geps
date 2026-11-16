# VINA GePS : utilisateurs, profils, protocole d'authentification et gestion des accès

> Écrit pour l'équipe TI d'Urgences-santé, chargée de brancher VINA GePS sur son annuaire, et
> pour l'administration fonctionnelle, qui attribue les profils. Ce document décrit qui utilise
> le produit, avec quel protocole on s'y connecte, et comment un profil devient un accès.

## 1. En bref : le protocole à retenir

**OpenID Connect (OIDC) avec Microsoft Entra ID, flux « Authorization Code + PKCE ».** C'est
l'exigence E-TN-2 de l'avis (« La solution authorise les utilisateurs via la plateforme Microsoft
Entra Id »), et c'est le flux implanté et vérifié par les essais.

| Question | Réponse |
|---|---|
| Protocole | OIDC 1.0 sur OAuth 2.0, *Authorization Code* avec **PKCE** (S256), `state` et `nonce` |
| Fournisseur d'identité | Le locataire Microsoft Entra ID d'Urgences-santé ; un locataire précis, **jamais** `common`, `organizations` ou `consumers` |
| Autre fournisseur OIDC | Keycloak, Okta ou tout fournisseur OpenID Connect, déclaré par son émetteur (ADR 0075). Processus et essais : [OIDC-KEYCLOAK.md](OIDC-KEYCLOAK.md) |
| Jeton lu | L'`id_token`, dont la signature est vérifiée par les clés publiques (JWKS) d'Entra, ainsi que l'émetteur, l'audience, l'expiration et le `nonce` |
| Rôle dans VINA GePS | Déduit des **groupes de sécurité** Entra (revendication `groups`) ou des **rôles d'application** (revendication `roles`) |
| Second facteur | Celui de l'**accès conditionnel** d'Entra (MFA, appareil conforme, emplacement). VINA GePS ne le redemande pas après Entra |
| Solution de rechange | **SAML 2.0** (`/api/auth/sso/saml/*`), si Urgences-santé ne peut pas émettre d'application OIDC |
| Hors fédération | Un compte local d'urgence (« bris de glace ») par organisme, avec mot de passe fort, TOTP ou clé d'accès (passkey) |

**Pourquoi OIDC plutôt que SAML.** Entra ID le prend en charge nativement. OIDC transporte des
JSON signés que l'on vérifie par JWKS, sans canonicalisation XML, qui est la source historique des
failles SAML. Il se configure en une inscription d'application, et le même flux sert l'application
Web et l'application mobile. SAML reste disponible pour un fournisseur tiers (ADFS, Okta) ou une
contrainte d'Urgences-santé.

**Pourquoi pas WS-Federation, LDAP direct ou Kerberos.** WS-Fed est un protocole hérité. Un LDAP
direct obligerait VINA GePS à voir passer les mots de passe et à joindre l'Active Directory sur
site depuis l'infonuagique. Kerberos ne traverse pas Internet. Aucun ne satisfait E-TN-2 aussi
directement qu'OIDC.

## 2. Ce qu'Urgences-santé doit fournir

### 2.1 Inscrire l'application dans Entra ID

1. *Entra ID › Inscriptions d'applications › Nouvelle inscription* : nom « VINA GePS », comptes de
   **ce répertoire seulement** (locataire unique).
2. **URI de redirection** (plateforme Web) : `https://<hôte de l'organisme>/api/auth/sso/annuaires/callback`.
3. *Certificats et secrets* : créer un **secret client**, d'une durée de 12 mois au plus, et le
   transmettre par le canal chiffré convenu, jamais par courriel. Il est rangé dans une variable
   d'environnement `ANNUAIRE_<NOM>` : la base ne garde que le **nom** de la variable, jamais le
   secret.
4. *Configuration de jeton* : ajouter la revendication **`groups`** (groupes de sécurité, sous
   forme d'ID d'objet), ou déclarer des **rôles d'application** (voir 2.2).
5. *Autorisations d'API* : `openid`, `profile`, `email` (délégation), avec le consentement
   administrateur. Aucune autorisation Microsoft Graph n'est nécessaire.
6. *Applications d'entreprise › Propriétés* : exiger l'**attribution des utilisateurs**, pour que
   seuls les groupes attribués puissent se connecter.
7. *Accès conditionnel* : appliquer la politique MFA d'Urgences-santé à l'application.

Renseignements à communiquer au fournisseur : l'**ID du locataire** (GUID), l'**ID
d'application (client)**, les **domaines de courriel** admis (par exemple `urgences-sante.qc.ca`),
et la **table groupe → profil** (2.2).

### 2.2 Les groupes et les profils

Une table de correspondance suffit. Les noms de groupe ci-dessous sont **proposés** ; Urgences-santé
garde ses propres conventions et ne fournit que les ID d'objet.

| Groupe Entra (proposé) | Profil VINA GePS | Rôle technique |
|---|---|---|
| `GEPS-Administration` | Administration de l'organisme | `admin` |
| `GEPS-Direction-Operations` | Directeur ou directrice des opérations | `director` |
| `GEPS-Planification` | Planificateur, chef d'équipe, superviseur | `manager` |
| `GEPS-Ressources-humaines` | Ressources humaines | `rh` |
| `GEPS-Paie` | Paie | `accountant` |
| `GEPS-Personnel` | Paramédic, répartiteur, personnel de soutien | `employee` |

On peut utiliser à la place des **rôles d'application** (`Nexu.Admin`, `Nexu.Director`,
`Nexu.Manager`, `Nexu.RH`, `Nexu.Accountant`, `Nexu.Employee`), qui correspondent par défaut aux
rôles ci-dessus. Ils évitent la limite de 200 groupes dans un jeton (*groups overage*), au-delà de
laquelle Entra ne transmet plus les groupes.

**Une personne qui fait partie de plusieurs groupes reçoit le rôle de la première règle qui la vise** : les règles d'un annuaire sont ordonnées par priorité. Placer les rôles les plus larges en tête.

**Une personne qui ne fait partie d'aucun groupe correspondant est refusée**, sauf si l'annuaire
déclare un rôle par défaut. On recommande de ne **pas** en déclarer : appartenir au locataire ne
suffit pas pour entrer.

### 2.3 Plusieurs annuaires (P-TN-2)

Un organisme peut déclarer **plusieurs** locataires Entra : en cas de fusion, de sous-traitance ou
avec un établissement partenaire. Chacun a ses domaines admis et sa table de groupes. L'écran de
connexion propose un bouton par annuaire actif. La déclaration se fait sous *Administration ›
Utilisateurs et rôles › Annuaires* (`/api/auth/annuaires`), et elle est refusée si :

- l'autorité est multi-locataire (`common`, `organizations`, `consumers`) ;
- un domaine est mal formé, ou le secret n'est pas désigné par une variable `ANNUAIRE_*` ;
- un groupe donne le rôle `superadmin` (l'exploitation de la plateforme ne se délègue pas à un
  annuaire client).

Un courriel hors des domaines déclarés est refusé **même s'il a été authentifié par Entra**. Les
invités B2B d'un locataire portent en effet le domaine de leur propre organisation.

## 3. Les utilisateurs

Il y a quatre populations, que l'avis 26-015-DO décrit dans ses besoins.

| Population | Effectif ordre de grandeur | Profil | Ce qu'elle fait dans VINA GePS | Où |
|---|---|---|---|---|
| Paramédics, répartiteurs médicaux d'urgence, personnel de soutien | La grande majorité | `employee` | Consulter son horaire, donner ses disponibilités, choisir à l'AGPH, pointer, valider sa feuille de temps, déclarer une absence, demander un échange, échanger des messages | Web et **mobile**, souvent hors du poste de travail |
| Planificateurs, chefs d'équipe, superviseurs | Quelques dizaines | `manager` | Confectionner et publier les horaires, combler, approuver absences, échanges, pointages et feuilles, dans leur **périmètre** | Web, au poste |
| Direction des opérations | Quelques personnes | `director` | Lire les indicateurs, trancher les dérogations aux règles, approuver | Web |
| Ressources humaines, paie | Quelques personnes | `rh`, `accountant` | Lire, exporter vers la paie, avis aux employés | Web |
| Administration fonctionnelle | 2 à 4 personnes | `admin` | Référentiels, règles, milieux, périmètres, intégrations, rétention, journal, rôles et accès | Web |
| Exploitant (fournisseur) | Équipe du fournisseur | `superadmin` | Organismes, modules souscrits, sauvegardes. **Aucun accès aux données métier** sans emprunt d'identité journalisé | Console d'exploitation |

Les comptes de l'organisme de démonstration (`admin`, `claire.nadeau`, `denis.lavoie`,
`julie.roy`, `marc.dube`, `ana.cote`, `sophie.marchand`) sont décrits dans
[COMPTES-DEMONSTRATION.md](COMPTES-DEMONSTRATION.md). Ce sont des comptes **locaux**, sans second
facteur, qui n'existent que pour la démonstration.

## 4. Comment un profil devient un accès

L'accès se décide en quatre couches, toutes jugées **au serveur**. Le menu ne fait que refléter
leur résultat.

```
Entra ID ──► rôle ──► capacités ──► périmètre ──► (module souscrit)
 (qui)      (quoi)    (geste)       (sur qui)        (quoi existe)
```

1. **Le rôle** vient de la table groupe → rôle, à chaque connexion. Retirer quelqu'un d'un groupe
   Entra lui retire son rôle **à la prochaine connexion**. Désactiver le compte dans Entra lui
   interdit de se reconnecter.
2. **Les capacités** (`politique-acces.json`) disent quel rôle peut faire quel geste : par exemple
   `effectifs.manage` (planifier) pour `admin`, `director` et `manager`, ou `effectifs.configure`
   (paramétrer) pour `admin`. Un organisme peut définir **ses propres rôles** à partir des
   capacités (E-TN-5, `RolesEtablissement`).
3. **Le périmètre** (*Paramètres des effectifs › Périmètres*) borne un gestionnaire aux **unités**
   qu'il encadre. Le **refus est la règle par défaut** : un planificateur sans périmètre ne voit
   personne.
4. **Les modules** souscrits par l'organisme : un module coupé fait disparaître ses écrans, et ses
   routes répondent par un refus.

Le reste se règle dans *Administration › Accès* :

- les **objets protégés** : un écran ou une donnée dont on restreint l'accès au-delà du rôle ;
- les **octrois datés** : un droit accordé à une personne, pour une période, avec un motif. Ils
  servent pour un remplacement ou un intérim ; à échéance, le droit tombe sans intervention ;
- **« Qui accède à quoi »** : la revue d'accès, exportable, que la sécurité de l'information demande
  à chaque trimestre.

### Cycle de vie d'un compte

| Événement | Ce qui se passe |
|---|---|
| Première connexion par Entra | Le compte est **créé dans l'organisme de l'annuaire**, avec le rôle déduit des groupes, et la connexion est journalisée (`auth.sso.login.ok`) |
| Changement de groupe | Le rôle est recalculé à la connexion suivante |
| Départ | Le compte est désactivé dans Entra et la personne ne peut plus entrer. Ses traces restent selon la politique de rétention (P-TN-12) |
| Lien avec le dossier d'effectif | Le compte est relié à sa **ressource planifiable** (matricule), sous *Gestion des quarts › Ressources*. Sans ce lien, « Mon horaire » l'annonce clairement |
| Bris de glace | Le compte local `admin` de l'organisme, avec second facteur, mot de passe hors ligne sous scellé et une connexion journalisée |

**Provisionnement à l'avance (SCIM).** Il n'est pas implanté. La création à la première connexion
(*just-in-time*) suffit pour l'accès. Le lien avec le dossier d'effectif vient de l'intégration de
paie et de RH (matricule), et non de l'annuaire. SCIM sera utile le jour où Urgences-santé voudra
qu'un départ dans Entra désactive aussi le compte dans VINA GePS **sans attendre** une tentative de
connexion. C'est un lot à chiffrer.

## 5. Sessions et sécurité

- La session est un **cookie `httpOnly`, `Secure`, `SameSite`**, accompagné d'un jeton CSRF. Aucun
  jeton n'est accessible au JavaScript.
- Les échecs et les refus de connexion sont journalisés (`auth.sso.annuaire.refus`). Une rafale de
  refus est un signal de sécurité transmis au SIEM.
- La restriction par pays de connexion est réglable par organisme (`RestrictionPaysConnexion`).
- **L'emprunt d'identité** par le soutien est borné dans le temps, journalisé et annoncé à l'écran
  par un bandeau.

## 6. Écarts connus

| Écart | Conséquence | Correctif proposé |
|---|---|---|
| La route globale `/api/auth/sso/entra/*` (un seul locataire, configuré par `AZURE_*`) crée le compte **sans organisme** | En production multi-organisme, elle ne doit pas être utilisée : seule la route par annuaire (`/api/auth/sso/annuaires/*`) rattache le compte à son organisme | Réserver la route globale à la démonstration, ou la retirer une fois les annuaires déclarés |
| Pas de SCIM | Un départ n'est constaté qu'à la tentative de connexion suivante, avec la même session au plus | Lot SCIM 2.0 (`/scim/v2/Users`, `/Groups`) |
| Un refus au retour du fournisseur s'affiche en JSON brut | Le motif est juste mais peu lisible | Rediriger vers la page de connexion avec le motif (voir OIDC-KEYCLOAK.md § 8) |
| Pas de déconnexion fédérée (*front-channel logout*) | Se déconnecter d'Entra ne ferme pas une session VINA GePS déjà ouverte | Brancher `end_session_endpoint` et la déconnexion par canal frontal |
