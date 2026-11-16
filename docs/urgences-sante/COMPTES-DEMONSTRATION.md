# Les comptes de démonstration d'Urgences-santé

> Écrit pour l'évaluateur de l'avis 26-015-DO et pour qui montre le produit : **qui** se connecte,
> **ce qu'il voit**, et **pourquoi ce n'est pas la même chose** d'un profil à l'autre.

Les comptes sont créés par `node scripts/semer-urgences-sante.cjs`, à partir de
`e2e/donnees/urgences-sante/demonstration.json`. Ce document est le **miroir** de ce fichier : un
essai (`frontend/tests/urgences-sante-comptes.test.ts`) échoue si l'un nomme un profil que l'autre
ignore — une liste de comptes tenue à la main finit toujours par décrire des comptes qui n'existent
plus.

## Se connecter

À l'écran de connexion, **choisir l'établissement** « Urgences-santé — démonstration (…) », puis
l'un des comptes ci-dessous. Le mot de passe est le même pour tous :

```
Passe!Demo2026
```

Il se change par `--mdp` au moment de semer. Ce sont des comptes de démonstration : ils n'ont ni
second facteur ni question secrète, parce qu'un mur de configuration devant l'écran qu'on vient
montrer n'apprend rien à personne. **Un organisme réel n'est jamais semé ainsi** — la politique
normale impose mot de passe fort, second facteur et trois questions au premier accès.

## Les profils

| Compte | Personne | Profil | Ce qu'il voit | Planche |
|---|---|---|---|---|
| `admin` | — | **Administration de l'organisme** | Tout le module, plus le paramétrage : référentiels, milieux, périmètres, règles, intégrations, rétention, journal. | s11, s12 |
| `claire.nadeau` | Claire Nadeau | **Planificatrice des effectifs** | Comblement, quarts, postes, périodes, quotas, absences, échanges, AGPH, présence, feuilles de temps — **dans son périmètre**. | s01, s05 |
| `denis.lavoie` | Denis Lavoie | **Directeur des opérations** | Les mêmes écrans de pilotage : il lit les indicateurs et tranche les dérogations. | s01, s10 |
| `julie.roy` | Julie Roy | **Paramédic — temps complet** | Son horaire, ses disponibilités, son pointage, sa feuille de temps, ses absences, ses échanges. | s02, s03 |
| `marc.dube` | Marc Dubé | **Paramédic — temps partiel régulier** | La même chose : il faut **deux** personnes pour qu'un échange de quarts existe. | s07 |
| `ana.cote` | Ana Côté | **Paramédic — temps partiel occasionnel** | Le même espace personnel ; son statut la place en fin de tour de rôle à l'AGPH. | s04, s07 |
| `sophie.marchand` | Sophie Marchand | **Ressources humaines** | Les écrans personnels, et toute la paie : règles de paie variable, dossiers, réglages, validation des temps, cycles (ADR 0084). Elle n'est pas planifiable, donc ni horaire ni feuille de temps. | — |
| `luc.gagnon` | Luc Gagnon | **Comptabilité — paie** | Valide les temps, prépare et traite la paie, télécharge le registre et les fiches en PDF, lit remises et relevés ; il ne voit ni les règles ni les dossiers (ADR 0084). | — |

## Ce que la différence prouve

Ouvrir le produit avec le compte d'administration ne prouve presque rien : il voit tout. La
démonstration vaut par le **contraste** :

- **Julie** n'a aucune console. Elle ne voit ni le comblement, ni les feuilles des autres : le menu
  ne les lui propose pas, et l'URL directe ne les ouvre pas non plus.
- **Claire** voit les consoles, mais **seulement son périmètre** : une unité qu'elle n'encadre pas
  n'apparaît ni dans ses listes ni dans ses bassins de candidats.
- **Sophie** n'a pas de ressource : ses écrans personnels disent **pourquoi** — « votre compte
  n'est relié à aucune ressource planifiable » — au lieu d'afficher un tableau vide.

C'est ce contraste que mesure `e2e/tests/e2e/urgences-sante-parcours.spec.ts` : chaque parcours est
joué **au nom de la personne concernée**, et non depuis le compte d'administration. Un écran peut
être complet pour l'administratrice et inatteignable pour la paramédic à qui il est destiné — un
rôle manquant, une ressource non reliée, un module fermé —, et rien ne le signale si la preuve a
été faite sous un autre compte.

## La communication du personnel

Les trois écrans de **Communication** du menu — Messagerie du personnel, Appeler un collègue,
Listes de diffusion du personnel — sont ceux du module Santé Québec, servis ici. Ils ne sont pas
recopiés : deux messageries dans un produit sont deux boîtes où chercher un message, et elles
divergeraient au premier correctif. Ce qui change d'un module à l'autre est le **nom au bandeau** et
la **mise en page** (une page par entrée de menu, sans barre d'onglets).

Le module se vend à part (`effectifs_communication`) et **exige le socle des horaires cliniques**
(`horaires`), qui sert son API. L'organisme de démonstration a les deux ; le groupe
« Opérations — secteur Est » et un fil (« Couverture du quart de nuit — véhicule 412 ») sont semés,
de sorte que l'écran montre une conversation réelle et **des noms**, non des identifiants.

## Pourquoi le menu est si court

Le menu ne porte que quatre sections : le tableau de bord, Urgences-santé, Mon espace et
l'Administration. Les sections d'un autre métier — Comptabilité, Paie & RH, Ventes, Temps &
présences, Santé Québec — sont **masquées pour cet organisme** (Superadmin › Modules et accès), et
le semoir pose ce réglage.

Elles paraissaient auparavant, et la raison vaut d'être dite : ces sections ne portent **aucun code
de module**, et chacune héberge encore des écrans du **socle** — ceux que tout plan comprend,
énumérés avec leur motif dans `frontend/tests/menus-gouvernes-par-module.test.ts`. Une section
s'affiche dès qu'une de ses entrées est visible : il suffisait donc d'`orgchart` pour faire
apparaître « Paie & RH », et de `rapports` pour « Comptabilité ». Aucun écran de comptabilité ni de
paie n'était ouvert — c'était un défaut de rangement, pas d'abonnement
(`frontend/tests/tenant-urgences-sante-menu.test.ts` le mesure).

⚠️ Le masquage **dégarnit un menu** et ferme l'URL directe ; ce n'est **pas** un contrôle d'accès.
La frontière de sécurité reste le module, gardé au serveur en refus par défaut.

## Ce que l'organisme contient

Semé depuis les planches : trois sites (Centre-ville, Nord, Sud), trois quarts du jour avec leur
comblement (3/4, 1/3, 2/2), les postes P-104 à P-106, deux règles (repos minimal, disponibilités
minimales), une période publiée et une période en collecte, et les feuilles de temps de la semaine
écoulée. Détail planche par planche :
[`maquettes/CORRESPONDANCE-MAQUETTES-ECRANS.md`](maquettes/CORRESPONDANCE-MAQUETTES-ECRANS.md).
