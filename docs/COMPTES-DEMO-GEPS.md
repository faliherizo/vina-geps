# Comptes de la démonstration geps.vinaitsolution.com

> Pour qui montre VINA GePS ou le fait essayer à quelqu'un : où se connecter, avec quel compte, pour voir quoi.

**Adresse :** <https://geps.vinaitsolution.com> › **Accéder à l'application**, puis choisir l'organisme
(<https://geps.vinaitsolution.com/acceder>).

**Mot de passe de tous les comptes ci-dessous : `Passe!Demo2026`**

Ce mot de passe est **publié** : il ne protège rien. La démonstration ne contient donc **aucune donnée réelle**. Les
intégrations sont simulées (`DEMONSTRATION_INTEGRATIONS`) : aucun texto ni courriel ne part, et aucune connexion Entra
ou SAML ne sort du serveur.

## Se connecter

Chaque organisme a **sa propre adresse** (ADR 0033, ADR 0094). L'adresse désigne l'organisme : il n'y a rien à choisir
à la connexion, et les comptes d'un organisme ne s'ouvrent que sur la sienne.

| Organisme | Adresse de connexion |
|---|---|
| Urgences-santé | <https://urgence-sante.geps.vinaitsolution.com> |
| Clincom — Santé Québec Laurentides, avis FX-AO | <https://clincom.geps.vinaitsolution.com> |
| Urgences-santé — démonstration (maquettes de l'avis) | <https://urgence-sante-demo.geps.vinaitsolution.com> |

1. Ouvrir l'adresse de l'organisme, ou passer par **Accéder à l'application** sur la vitrine et choisir l'organisme dans
   la liste (les organismes ouverts que gère le superadmin).
2. Saisir le nom de connexion et `Passe!Demo2026`.

La connexion de <https://geps.vinaitsolution.com/admin/login> n'ouvre que les **comptes globaux** (exploitation de la
plateforme) : un compte d'organisme y est refusé.

Aucun compte ne demande de changer son mot de passe ni de configurer une seconde étape : la démonstration s'ouvre
directement. Si quelqu'un change un mot de passe à l'écran, le prochain déploiement le remet (voir
[Remettre la démonstration en état](#remettre-la-démonstration-en-état)).

## Les organismes et leurs comptes

Deux des trois organismes portent l'heure de leur semis dans leur nom (« … (2026-11-23 02:10) »). Le sous-domaine va
toujours à l'exemplaire le plus récent : un exemplaire plus ancien le cède (`clincom-<début de son identifiant>`).

### Urgences-santé

L'organisme prêt à l'emploi : un compte par rôle, chaque personne reliée à sa ressource ; c'est celui que filme la vidéo « Mise en route ».

| Nom de connexion | Rôle | Personne | Ce qu’elle montre |
|---|---|---|---|
| `admin` | Administration | Administration de l'organisme | Paramétrage complet ; compte technique sans ressource (pas de « Mes absences ») |
| `direction` | Direction | Dominique Direction | Matricule D0001, unité SOUT |
| `chef-ops` | Gestionnaire | Camille Chef des opérations | Matricule G0001, unité OPS |
| `rh` | Ressources humaines | Rosalie Ressources humaines | Matricule R0001, unité SOUT |
| `comptable` | Comptable | Charles Comptable | Matricule C0001, unité SOUT |
| `employe1` | Employé | Émilie Paramédic | Matricule E0001, unité OPS |
| `employe2` | Employé | Étienne Paramédic | Matricule E0002, unité OPS |
| `employe3` | Employé | Éloïse Paramédic | Matricule E0003, unité OPS |

### Urgences-santé — démonstration (AAAA-MM-JJ HH:MM)

Le contenu des maquettes de l'avis 26-015-DO : chaque valeur nomme sa planche.

| Nom de connexion | Rôle | Personne | Ce qu’elle montre |
|---|---|---|---|
| `admin` | Administration | Administration de l'organisme | Paramétrage complet |
| `julie.roy` | Employé | Julie Roy | Paramédic — temps complet |
| `marc.dube` | Employé | Marc Dubé | Paramédic — temps partiel régulier |
| `ana.cote` | Employé | Ana Côté | Paramédic — temps partiel occasionnel |
| `claire.nadeau` | Gestionnaire | Claire Nadeau | Planificatrice des effectifs |
| `denis.lavoie` | Direction | Denis Lavoie | Directeur des opérations |
| `sophie.marchand` | Ressources humaines | Sophie Marchand | Ressources humaines |
| `luc.gagnon` | Comptable | Luc Gagnon | Comptabilité — paie |

### Santé Québec Laurentides — démonstration FX-AO (AAAA-MM-JJ HH:MM)

L'avis FX-AO-20260810 : communications cliniques, six sites, alertes et cascades.

| Nom de connexion | Rôle | Personne | Ce qu’elle montre |
|---|---|---|---|
| `admin` | Administration | Administration de l'organisme | Paramétrage complet |
| `coord.urgence` | Direction | Sophie Lalonde | Coordonnatrice des mesures d'urgence — envoie les alertes critiques (critère 18) |
| `chef.urgence.hsj` | Gestionnaire | Martin Gagnon | Chef de l'urgence, Saint-Jérôme — expéditeur (critères 1-17) |
| `chef.soins.hse` | Gestionnaire | Nadia Bouchard | Cheffe des soins, Saint-Eustache — expéditrice, suivi partagé (critère 12) |
| `dre.tremblay` | Employé | Dre Isabelle Tremblay | Cardiologue de garde — destinataire (critères 9, 24) |
| `dr.lavoie` | Employé | Dr Antoine Lavoie | Cardiologue — destinataire, deuxième de la garde |
| `inf.roy` | Employé | Julie Roy | Infirmière, urgence de Saint-Jérôme — destinataire |
| `inf.cote` | Employé | Marc Côté | Infirmier, Saint-Eustache — destinataire |
| `inf.pelletier` | Employé | Émilie Pelletier | Infirmière, Mont-Laurier — destinataire |
| `inh.bergeron` | Employé | Luc Bergeron | Inhalothérapeute, Sainte-Agathe — destinataire |
| `exp.garde.hsj` | Expéditeur (rôle propre) | Chantal Ouellet | Agente de la centrale des gardes, Saint-Jérôme — expéditrice sans administration (addenda 2, Q20-Q21) |

## Le compte d'exploitation de la plateforme

Le **superadmin** (créer et suspendre des organismes, accorder les modules, consulter l'exploitation) ne fait **pas**
partie de la démonstration publique : son mot de passe a été tiré au hasard sur le serveur et n'est pas publié.

| Où le trouver | Comment |
|---|---|
| Sur le serveur | `grep ^SUPERADMIN_PASSWORD= ~/projets/vina-geps/deploy/geps/.env.geps` (session `deploy`) |
| Dans les pipelines | secret `SUPERADMIN_PASSWORD` de l'environnement GitHub `geps-demo` (illisible, utilisable seulement par les workflows) |
| Sur le poste qui a installé la démo | `deploy/geps/acces-exploitation.local.env` (ignoré par git, mode 600) |

Pour s'y connecter : <https://geps.vinaitsolution.com/admin/login> (l'hôte commun, sans organisme), puis `superadmin`.

Le compte `admin` **hors organisme** (créé au premier démarrage, mot de passe `ADMIN_PASSWORD` du même fichier) sert
au banc de performance (secret `PERF_COMPTE_PASSWORD` de l'environnement `geps-demo`). Il ne se montre pas.

## Remettre la démonstration en état

| Besoin | Geste |
|---|---|
| Un mot de passe a été changé, un compte supprimé | Actions › **Semer la démonstration** › Run. L'organisme `Urgences-santé` est repris et chaque mot de passe remis à `Passe!Demo2026`. |
| Une démonstration a été abîmée pendant une présentation | Actions › **Semer la démonstration**, `forcer` coché : un nouvel organisme horodaté est semé, l'ancien reste. |
| La base a été perdue | Le prochain déploiement sème les trois organismes ; ou **Semer la démonstration** tout de suite. |

En ligne de commande, depuis un poste qui a Node 22 :

```bash
SUPERADMIN_PASSWORD='…' node scripts/semer-demo-geps.cjs --api https://geps.vinaitsolution.com [--forcer] [--seulement fx-ao]
```

## Vérifier que ce document dit vrai

Ce tableau est tiré de [`e2e/donnees/geps/comptes-demo.json`](../e2e/donnees/geps/comptes-demo.json), qui est aussi
la source de l'essai [`deploiement-comptes-demo.spec.ts`](../e2e/tests/e2e/deploiement-comptes-demo.spec.ts). Cet
essai ouvre une session avec **chaque** compte après chaque déploiement : un compte refusé fait échouer le pipeline,
au lieu d'être découvert devant la personne à qui l'on montre le produit.

```bash
cd e2e && E2E_BASE_URL=https://geps.vinaitsolution.com E2E_COMPTES_DEMO=1 \
  npx playwright test --project=e2e tests/e2e/deploiement-comptes-demo.spec.ts
```

Ou Actions › **Recette e2e (environnement déployé)** › Run, environnement `geps-demo`.

Ajouter un compte : l'ajouter au jeu de données du semis concerné, puis à `comptes-demo.json`, puis à ce tableau.
