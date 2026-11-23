# Un organisme par défaut, prêt à l'emploi

> Pour qui installe, montre ou reprend VINA GePS (Urgences-santé) et veut un organisme où **chaque écran s'ouvre**
> dès la première connexion — en particulier `/admin/mesabsences`, qui répond sinon « votre compte n'est relié à
> aucune ressource planifiable ». C'est aussi l'ordre que suivent les fiches d'aide pour paramétrer un organisme à la
> main.

`scripts/creer-organisme-par-defaut.cjs` crée l'organisme, active ses modules, pose une configuration de base et un
compte par rôle, puis **vérifie** que chaque rôle ouvre ses écrans. Il ne passe que par l'API publique — en
superadmin, puis en administration de l'organisme — et n'écrit jamais en base.

## Lancer

```bash
SUPERADMIN_PASSWORD='…' node scripts/creer-organisme-par-defaut.cjs \
  --api http://localhost:8095 \
  --nom "Organisme par défaut"          # facultatif ; c'est la clé de l'organisme
  [--superadmin-user superadmin]        # défaut : $SUPERADMIN_USERNAME, sinon superadmin
  [--sans-donnees-de-demo]              # configuration seule, sans quarts attribués ni absence
```

| Option | Rôle |
|---|---|
| `--api` | L'adresse du frontal (ou du serveur Java). Défaut : `$API_URL`, sinon `http://localhost:8095` (pile Docker locale). |
| `--nom` | Le nom de l'organisme. Un organisme de ce nom est **repris**, jamais doublé. |
| `--superadmin-user` | Le nom de connexion du superadmin. |
| `SUPERADMIN_PASSWORD` | Le mot de passe du superadmin, **dans l'environnement seulement** : passé en argument, il s'afficherait dans `ps`, l'historique et tout rapport d'échec. |
| `--sans-donnees-de-demo` | N'attribue aucun quart, ne déclare aucune absence, ne prépare aucune feuille de temps. |

Sortie : `0` si tout est créé et que l'autocontrôle passe, `2` si un contrôle échoue (le rôle, l'écran et le motif
sont listés), `1` sur une erreur d'API (méthode, chemin, statut et motif sont affichés).

### Les identifiants

Chaque mot de passe est tiré au hasard (28 caractères, quatre classes ; la politique exige 12). Ils ne sont écrits
**que** dans `scripts/.organisme-par-defaut.<nom>.local.json` — ignoré par git (`.gitignore`), mode `600`. La
console n'affiche que les noms de connexion. Ce fichier ne se versionne pas, ne se colle pas dans un billet, et se
remet en main propre.

**Démonstration publique** : avec `DEMO_PASSWORD` dans l'environnement, chaque compte reçoit ce mot de passe, et un
compte qui ne l'a plus y est remis. C'est ainsi que se sème geps.vinaitsolution.com, dont les comptes sont publiés
([COMPTES-DEMO-GEPS.md](COMPTES-DEMO-GEPS.md)). À ne jamais employer pour un organisme réel.

Perdu ? Relancer le script : un compte dont le mot de passe n'est plus connu en reçoit un nouveau, posé par
l'administration (ou par le superadmin pour le compte `admin`), et le fichier est réécrit. La console le signale.

## Ce qui est créé

L'ordre ci-dessous est celui du script, et celui du paramétrage à la main : **une valeur ne vise qu'une valeur déjà
en vigueur** (un titre d'emploi vise un corps d'emploi, une ressource vise une unité, un équipage vise un milieu).

| # | Quoi | Détail | Écran |
|---|---|---|---|
| 1 | **Organisme** | Plan `enterprise`, Québec, avec son compte `admin` | Superadmin › Organismes |
| 2 | **Modules** | Tous ceux du catalogue (`GET /api/saas/modules`) sauf les options d'hébergement (`byok`, `dedicated`) : 17 aujourd'hui, dont effectifs (socle, règles, planification, disponibilités, absences, échanges, AGPH, présence, temps, communication, intégrations), alertes, paie, approbations, notifications, intégrations, confidentialité | Superadmin › Modules et accès |
| 3 | **Unités** | `ORG` (racine), `OPS` Opérations préhospitalières et `SOUT` Soutien et administration, sous `ORG` | Référentiels |
| 4 | **Corps et titres d'emploi** | `PARA` (titres `PSP`, `PSA`), `REPA` (`RMU`), `ADM` (`AGA`) | Référentiels |
| 5 | **Statuts d'emploi** | `TC` temps complet, `TPR` temps partiel régulier, `TP` temps partiel occasionnel | Référentiels |
| 6 | **Conventions** | `CONV-PARA`, `CONV-SOUT` | Référentiels |
| 7 | **Natures de quart** | `REG` (compte dans les quotas), `ADH`, `REMP`, `HS` | Référentiels |
| 8 | **Plages horaires** | `J` 7 h–15 h, `S` 15 h–23 h, `N` 23 h–7 h | Référentiels |
| 9 | **Types de milieu** | `CASERNE`, `CCS` (centre opérationnel), `PS` (point de service) | Référentiels |
| 10 | **Codes d'absence** | `MAL` maladie (banque `MAL`, 8 h/j, long terme dès 14 j), `VAC` vacances (banque `VAC`, 8 h/j, planifiable, 14 j), `FERM` férié mobile (banque, 8 h/j), `FORM` formation, `CSS` sans solde (14 j), `INV` invalidité (long terme) | Codes d'absence |
| 11 | **Types d'heures** | `REG` (code de paie 001), `PREA`, `REPAS`, `ABS`, `HS` (+50 %) | Référentiels |
| 12 | **Activités de présence, fériés** | `INTER`, `DISPO`, `FORMA` ; 24 juin, 1er juillet, 25 décembre de l'année | Référentiels |
| 13 | **Milieux** | `CAS-01` Caserne 1, `CAS-02` Caserne 2 (unité `OPS`), `CCS` Centre de communication santé (`SOUT`) | Milieux |
| 14 | **Règles** (portée organisme, effet aujourd'hui) | Attribution : repos < 8 h bloque, > 16 h sur 24 h bloque, > 48 h dans la semaine avertit. Absence : solde de banque insuffisant refuse, préavis < 14 j sur un code planifiable exige l'approbation, > 5 jours exige l'approbation | Règles |
| 15 | **Paramètres du temps** | Seuil de jour 12 h, de semaine 40 h, 1 palier d'approbation, tolérance d'écart 15 min ; `CONV-PARA` : 80 h par période de paie ; `CONV-SOUT` : 35 h par semaine | Feuilles de temps › Paramètres |
| 16 | **Comptes, ressources, périmètres** | Voir le tableau suivant | Comptes ; Ressources ; Périmètres |
| 17 | **Périodes** | « Période de démarrage » (deux semaines depuis le lundi, unité `OPS`) : équipages créés, puis **publiée**. « Période suivante — collecte des disponibilités » : en **collecte** jusqu'à la veille de son début | Périodes |
| 18 | **Équipages** | Chaque jour restant de la période courante, un quart de jour et un de nuit à la Caserne 1, deux paramédics (`PARA` × 2), minimum opérationnel 2 — 16 à 28 selon le jour de la semaine | Quarts et équipages |
| 19 | **Démonstration** (sauf `--sans-donnees-de-demo`) | Chaque employé reçoit un quart de jour (à deux jours d'écart) ; `employe1` déclare des vacances (`VAC`) dans trois semaines, par « Mes absences » ; les feuilles de temps de la semaine sont préparées | Mon horaire ; Mes absences ; Ma feuille de temps |

Compte à la création, sur la pile locale : 1 organisme, 17 modules, 42 valeurs de référentiel, 3 milieux, 6 règles,
7 comptes (+ `admin`), 7 ressources, 3 périmètres, 2 périodes, 16 équipages (un dimanche), 3 attributions, 1 absence.

### Rôles et comptes

| Compte | Rôle | Personne | Ressource (matricule, unité, corps, titre, statut) | Périmètre |
|---|---|---|---|---|
| `admin` | `admin` | administration de l'organisme | — (compte technique) | tout l'organisme, par son rôle |
| `direction` | `director` | Dominique Direction | `D0001`, `SOUT`, `ADM`, `AGA`, `TC` | `ORG`, sous-arbre, **gestion** |
| `chef-ops` | `manager` | Camille Chef des opérations | `G0001`, `OPS`, `PARA`, `PSA`, `TC` | `OPS`, sous-arbre, **gestion** |
| `rh` | `rh` | Rosalie Ressources humaines | `R0001`, `SOUT`, `ADM`, `AGA`, `TC` | `ORG`, sous-arbre, lecture |
| `comptable` | `accountant` | Charles Comptable | `C0001`, `SOUT`, `ADM`, `AGA`, `TC` | — |
| `employe1` | `employee` | Émilie Paramédic | `E0001`, `OPS`, `PARA`, `PSA`, `TC` | — |
| `employe2` | `employee` | Étienne Paramédic | `E0002`, `OPS`, `PARA`, `PSP`, `TPR` | — |
| `employe3` | `employee` | Éloïse Paramédic | `E0003`, `OPS`, `PARA`, `PSP`, `TP` | — |

**Pourquoi chaque personne a une ressource** : les écrans de « Mon espace » (Mes absences, Mon horaire, Mes
disponibilités, Ma feuille de temps) cherchent la ressource reliée au compte (`utilisateurId`) ; sans elle, la
déclaration d'absence répond « votre compte n'est relié à aucune ressource planifiable ». Le personnel de soutien
(direction, RH, comptable) est placé dans l'unité `SOUT`, corps `ADM` : il a ses écrans personnels sans jamais
paraître dans les bassins de candidats des quarts de paramédics.

**Pourquoi un périmètre** : le serveur refuse par défaut. Sans périmètre de gestion, la console d'un gestionnaire
s'ouvre sur des listes vides ; la direction gère la racine, le chef des opérations son unité.

Les comptes sont prêts à l'emploi : le mur de première connexion (changement de mot de passe, second facteur) est
levé, comme pour les démonstrations. En production, l'administration le rétablit
(Administration › Comptes › « Réinitialiser la sécurité ») avant de remettre les comptes.

## L'autocontrôle

À la fin, le script se connecte sous **chaque** compte et vérifie :

| Compte | Doit répondre 2xx | Doit répondre 403 |
|---|---|---|
| tous sauf `admin` | `GET /api/effectifs/ma-ressource` avec une ressource ; `/api/effectifs/absences` (Mes absences) ; `/api/effectifs/mon-horaire` (Mon horaire) ; `/api/effectifs/disponibilites/moi` (Mes disponibilités) ; `/api/effectifs/temps/moi` (Ma feuille de temps) — aucune réponse ne contient « aucune ressource planifiable » | |
| `admin` | `/api/auth/users`, `/api/effectifs/regles`, `/api/effectifs/configuration/paquet` | |
| `direction` | `/api/effectifs/planification/ressources` (≥ 7 visibles), `/api/effectifs/temps/feuilles` | `/api/auth/users` |
| `chef-ops` | `/api/effectifs/planification/ressources?uniteCode=OPS` (≥ 4 visibles), `/api/effectifs/planification/periodes` | `/api/auth/users` |
| `rh` | `/api/paie/dossiers`, `/api/paie/reglages` | `/api/auth/users` |
| `comptable` | `/api/paie/cycles`, `/api/paie/mes-bulletins` | `/api/auth/users` |
| `employe1..3` | | `/api/auth/users`, `/api/effectifs/configuration/paquet` |

`ma-ressource` lit exactement la liaison qu'exige la déclaration d'absence (`LectureRessources.deLActeur`) : s'il
rend une ressource, `/admin/mesabsences` déclare.

## Rejouer, compléter, remettre à zéro

- **Rejouer** est sans danger : chaque objet est retrouvé par sa clé (nom de l'organisme, code de référentiel ou de
  milieu, clé et portée de règle, nom de connexion, matricule, compte et unité du périmètre, nom de période, date,
  début et milieu d'équipage). Ce qui manque est créé ; une ressource mal reliée est reliée de nouveau ; une période
  n'est jamais ramenée en arrière (une période publiée le reste). La console sépare « créés » et « déjà présents ».
- **Changer les valeurs** : elles vivent dans `scripts/lib/organisme-par-defaut/donnees.cjs`. Une valeur modifiée
  d'un objet existant n'est **pas** réécrite (le script retrouve par la clé, il ne compare pas le contenu) : publier
  une nouvelle version à l'écran (référentiels et règles sont datés et versionnés).
- **Un second organisme** : `--nom "Autre organisme"` ; ses identifiants vont dans un autre fichier local.
- **Remettre à zéro** :
  - un organisme : Superadmin › Organismes › résilier, puis « Détruire les données » (attestation de destruction) ;
    supprimer ensuite `scripts/.organisme-par-defaut.<nom>.local.json` ; relancer le script recrée tout ;
  - toute la pile locale : `docker compose down -v` efface le volume `donnees` (base SQLite créée par Liquibase au
    démarrage suivant).

## Vérifier

```bash
# Les parties pures (données, idempotence, autocontrôle) et le client HTTP :
node --test --experimental-test-coverage --test-coverage-include='scripts/lib/organisme-par-defaut/*.cjs' \
  --test-coverage-exclude='**/*.test.cjs' scripts/lib/organisme-par-defaut/organisme-par-defaut.test.cjs

# Le script entier, contre un serveur Java en marche (deux fois : la seconde ne doit rien créer) :
SUPERADMIN_PASSWORD='…' node scripts/creer-organisme-par-defaut.cjs --api http://localhost:8095 --nom "Essai"
```

L'orchestration (`scripts/creer-organisme-par-defaut.cjs`) n'a pas d'essai unitaire : elle ne fait qu'enchaîner des
appels d'API, et c'est son exécution contre le serveur — autocontrôle compris — qui la vérifie.

## Ce que le script ne fait pas

- Pas de postes ni de campagne AGPH, pas de paie traitée : `scripts/semer-urgences-sante.cjs` sème la démonstration
  complète des maquettes.
- Pas de convention paramétrée article par article : `scripts/semer-sp-csn.cjs` pose la convention SP-CSN.
- Un périmètre ne se ferme pas le jour même de son ouverture (« la fermeture doit suivre le début ») : un doublon
  créé par erreur se ferme le lendemain, dans Périmètres.
