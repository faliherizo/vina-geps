# ADR 0083 — Un annuaire LDAP se joint en LDAPS seulement, par liaison au nom de la personne

- **Statut** : accepté, le 15 novembre 2026. Remplace la partie « annuaire » de l'ADR 0082.
- **Décideurs** : équipe VINA.
- **Concerne** : `auth/domaine/Annuaires.java` (fournisseur `ldaps`, port `Liaison`), `auth/application/ServiceAnnuaires.java`,
  `auth/infrastructure/persistance/LiaisonLdaps.java`, `auth/infrastructure/web/ControleurAnnuaires.java`, `frontend/src/Login.tsx`,
  `frontend/src/admin/AnnuairesOrganisme.tsx`, `pom.xml` (UnboundID LDAP SDK 7.0.5).
- **Story** : ST-G11 (FX-AO-20260810, critère 82 : « pour les échanges LDAP avec l'annuaire de l'établissement, la
  solution doit réaliser ses communications à l'aide du protocole LDAPS »).

## Contexte

L'ADR 0082 avait différé LDAPS : la fédération OIDC ou SAML couvre un Active Directory local, et aucun serveur LDAP
ne pouvait éprouver le code dans la chaîne de construction. Le critère 82 restait partiel en attendant la question
Q-LDAP. Deux faits ont changé :

- **UnboundID LDAP SDK est désormais disponible** (Maven Central). Il fournit le client, et un serveur LDAPS en
  mémoire pour les essais : le protocole et le TLS s'éprouvent pour de vrai, sans serveur externe.
- Un établissement qui n'a ni AD FS ni Entra ID n'a, pour joindre son AD, que LDAP. Lui demander de monter une
  fédération pour répondre à la grille serait reporter le coût sur lui.

## Options considérées

1. **Un troisième fournisseur d'annuaire, `ldaps`, qui authentifie par liaison (bind)** (retenue). La personne saisit
   son identifiant et son mot de passe AD sur la page de connexion. Le compte de service retrouve son DN, puis une
   seconde liaison, à ce DN, vérifie le mot de passe. Les groupes (`memberOf`) donnent le rôle par les mêmes
   correspondances que l'OIDC.
2. **Une synchronisation périodique des comptes par LDAPS** (création, désactivation). C'est un geste plus lourd :
   il crée et désactive des comptes sans que personne ne se connecte, et il exige des règles d'appariement. Il
   n'ajoute rien au critère 82 que l'option 1 ne couvre pas déjà.
3. **JNDI du JDK** au lieu d'UnboundID. JNDI ne prend sa fabrique TLS que par nom de classe, ce qui rend l'essai
   contre un certificat d'essai artificiel. Il ne vérifie pas non plus le nom d'hôte du certificat sans code
   supplémentaire.

## Décision

- **LDAPS seulement.** L'adresse de l'annuaire est une URL RFC 4516 en `ldaps://hôte:636/base` ; `ldap://` en clair
  est refusé à la saisie, même sur la machine locale. Le certificat de l'annuaire doit être reconnu par le magasin de
  confiance de la JVM : l'établissement y importe son autorité. Son **nom d'hôte est vérifié** contre l'URL.
- **Aucun changement de schéma.** La table `auth_annuaires` portait déjà le fournisseur (`ldaps`), l'adresse
  (colonne `emetteur`) et l'identifiant du client (`clientId`, ici le DN du compte de service). Le secret du compte
  de service reste une variable `ANNUAIRE_*`, jamais en base.
- **Garde-fous de la liaison.**
  - Un mot de passe vide est refusé avant tout appel : en LDAP, une liaison sans mot de passe réussit en anonyme.
  - Le filtre de recherche est bâti par l'API (`Filter.createEqualityFilter`), jamais par concaténation : un
    identifiant comme `*` ou `x)(uid=*` ne peut pas élargir la recherche.
  - La recherche est plafonnée à deux résultats. Un identifiant ambigu, inconnu ou sans courriel ne connecte
    personne.
  - Le refus ne dit pas lequel de l'identifiant ou du mot de passe est faux.
- **Même admission que l'OIDC** (`Annuaires.admettre`) : domaine de courriel admis, rôle donné par un groupe
  (sous son DN ou son CN) ou par le défaut, compte désactivé localement toujours refusé.
- **Même verrou que la connexion par mot de passe** : le pare-feu compte les échecs par adresse et identifiant, et
  verrouille au seuil. Une rafale contre notre page ne verrouille donc pas le compte AD de la personne.
- **Une panne n'est pas un refus** : annuaire injoignable, compte de service refusé ou certificat non reconnu
  donnent 503, pas 403, et ne comptent pas comme un mauvais mot de passe pour l'usager.
- **Écran** : sur la page de connexion, le bouton d'un annuaire LDAPS envoie l'identifiant et le mot de passe déjà
  saisis (`POST /api/auth/sso/annuaires/{id}/connexion`), sans redirection. L'administration propose « Annuaire LDAP
  (Active Directory), en LDAPS ».

## Conséquences

- **Positives** : le critère 82 est servi sans attendre Q-LDAP, et un AD non fédéré entre comme Entra ou Keycloak.
  Aucune table n'est ajoutée, donc aucun script de retour de schéma.
- **Négatives** :
  - Une dépendance de plus (UnboundID, environ 3 Mo), à suivre dans l'audit des dépendances.
  - Le mot de passe AD transite par l'application, chiffré de bout en bout (TLS jusqu'au serveur, LDAPS jusqu'à
    l'annuaire), mais il y transite. La fédération reste préférable quand elle existe, et l'écran ne l'impose pas.
- **Essais** :
  - `LiaisonLdapsTest`, contre un vrai serveur LDAPS UnboundID en mémoire : connexion, mot de passe faux ou vide,
    identifiant inconnu, ambigu (2 et 3 résultats) ou forcé (`*`, injection de filtre), compte de service refusé,
    base absente, annuaire injoignable, panne pendant la liaison, autorité non reconnue, **certificat au nom d'un
    autre hôte**.
  - `AnnuairesTest` : la validation de l'URL et du DN.
  - `ControleurAnnuairesHttpTest` : la route, la session, le refus consigné sans le mot de passe, le verrou au
    troisième échec, la panne en 503.
  - `annuaires-organisme.test.tsx` (vitest) et `login-sso-saml.spec.ts` (Playwright, avec axe) : les écrans.
