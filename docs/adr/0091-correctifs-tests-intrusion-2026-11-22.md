# ADR 0091 — Correctifs des tests d'intrusion du 22 novembre 2026 : révocation des sessions et durcissement

- **Statut** : accepté, le 22 novembre 2026.
- **Décideurs** : équipe VINA.
- **Source** : rapport `RAPPORT-TESTS-INTRUSION-2026-11-22.md`, dans le projet distinct `urgences-sante-pentest`.
  Résumé : [docs/securite/TESTS-INTRUSION.md](../securite/TESTS-INTRUSION.md).
- **Concerne** :
  - `partage/securite` : `JetonDeSession`, `FiltreSession`, `SessionsRevoquees` ;
  - `auth` : `DepotSessionsRevoquees`, `ControleurAuth`, `ProvisionnementComptes` ;
  - changeset `0025` ; `application.properties` ;
  - `frontend/nginx.conf`, `frontend/Dockerfile`, `frontend/index.html`.

## Contexte

Les tests d'intrusion locaux ont relevé 6 constats, dont aucun critique ni élevé :

| Constat | Gravité | Problème |
|---|---|---|
| PT-01 | Moyen | La carte des routes est publiée sans session. |
| PT-02 | Moyen | Un jeton reste valide 8 h après la déconnexion. |
| PT-03 | Moyen | Les comptes amorcés ont des mots de passe par défaut, sans changement imposé. |
| PT-04 | Faible | Les pages n'ont pas de CSP. |
| PT-05 | Faible | Des en-têtes sont en double et se contredisent sur l'API. |
| PT-06 | Faible | Un compte créé par un superadmin devient global sans qu'il l'ait voulu. |

Seul PT-02 demande une décision de structure.

## Options considérées pour PT-02

1. **Un identifiant par jeton (`jti`) et une liste de révocation en base, consultée par le filtre** (retenue). C'est le
   même mécanisme que la fin d'une session empruntée (`SessionsEmpruntees`, ADR 0038). Une lecture par clé primaire suffit.
   La table ne garde que les déconnexions des huit dernières heures.
2. **Une « version de session » par compte, incrémentée à la déconnexion.** La déconnexion fermerait alors toutes les
   sessions de la personne (téléphone, poste) au lieu de la seule qui se déconnecte. Il faudrait aussi lire le compte à
   chaque requête.
3. **Des jetons courts (15 min) et un jeton de rafraîchissement.** La fenêtre serait réduite sans être fermée. Il
   faudrait refaire la parité avec le format du Node et le flux de l'interface.
4. **Un cache en mémoire.** Il ne tient pas à plusieurs instances, et il se perd au redémarrage.

## Décision

- **PT-01** : `nexu.migration.exposer-routes` vaut `false` par défaut ; un profil de développement le pose.
- **PT-02** :
  - `JetonDeSession` émet un `jti` et fournit `lireAvecIdentite` (identifiant et échéance).
  - `FiltreSession` traite un jeton dont le `jti` est révoqué comme une absence de session : la route répond 401.
  - La déconnexion inscrit le `jti` jusqu'à l'échéance du jeton (`auth_sessions_revoquees`, changeset 0025, avec son
    retour). Chaque nouvelle révocation purge les entrées échues.
  - Un jeton émis avant ce changement n'a pas de `jti`. Il reste lu, et expire au plus tard huit heures après le
    déploiement.
- **PT-03** : un compte amorcé sans mot de passe fourni, ou dont le mot de passe vaut le nom de son rôle, doit en changer
  à la première connexion, sauf en mode d'essai (e2e, BDD, banc de charge).
- **PT-04** : une seule CSP, déclarée par un `map` nginx, est posée sur chaque page HTML :
  - `script-src 'self'` sans exception : le seul script en ligne (l'avis pour Internet Explorer) devient un fichier ;
  - styles en ligne admis ;
  - `blob:` pour la visionneuse de PDF, `data:` pour la photo de profil.
- **PT-05** : sur `/api/` et `/public/`, seuls les en-têtes de l'API s'appliquent ; nginx n'y ajoute que HSTS, et celui
  de l'API est retiré.
- **PT-06** : un compte créé par un superadmin prend l'établissement du corps, sinon celui de l'en-tête `X-Tenant-Id`.
  Sans l'un ni l'autre, il reste global, comme demandé.
- **IN-02** : l'image de l'interface s'installe par `npm ci` sur le verrou.

## Conséquences

- **Positives** :
  - Une déconnexion ferme réellement la session, que le jeton passe par le témoin ou par Bearer.
  - Une pile montée sans ses secrets n'ouvre plus `admin/admin`.
  - Chaque constat a son essai de non-régression, dans le dépôt (Java, vitest) et dans le projet de tests d'intrusion.
- **Négatives** :
  - Chaque requête authentifiée fait une lecture de plus, par clé primaire.
  - Le premier démarrage local sans `.env` demande de changer le mot de passe d'`admin`.
- **Non retenu ici** :
  - nginx sans privilège (IN-01) : il changerait le port d'écoute de l'image et du déploiement ;
  - la mise à jour de vitest et de vite (IN-03 : outillage de développement, non livré) ;
  - le seuil de verrouillage par adresse IP (IN-05).

  Ces trois points sont consignés dans le rapport.
- **Essais** :
  - `JetonDeSessionIdentiteTest`, `DepotSessionsRevoqueesTest`, `ProvisionnementComptesTest` ;
  - `ControleurAuthHttpTest` : `.laDeconnexionRevoqueLeJeton`, `.unJetonSansIdentifiant`, `.leSuperadminCreeDansLEtablissementChoisi` ;
  - `SchemaLiquibaseTest`, `RetourArriereSchemaTest` ;
  - vitest `tests/nginx-conf.test.ts` (PT-04, PT-05) ;
  - `urgences-sante-pentest/tests/08-constats-ouverts.test.mjs`, rejoué contre la pile reconstruite.
