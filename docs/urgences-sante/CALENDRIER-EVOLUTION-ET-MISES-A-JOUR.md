# Calendrier d'évolution, correctifs et mises à jour

> AO 26-015-DO : **E-TN-3** (mises à jour périodiques rendues disponibles à tous les clients en
> même temps selon un calendrier d'évolution ; correctifs déployés rapidement après validation),
> **E-TN-14** (mises à jour fréquentes et régulières pour la conformité législative et la
> cybersécurité), **E-TN-16** (retour arrière en moins de 24 heures), **P-TN-6** (plages
> d'entretien). Chaque engagement ci-dessous renvoie au mécanisme du dépôt qui l'applique.

## 1. Une seule version pour tous

La solution est un seul code et une seule image par version (`.github/workflows/ci-cd.yml`,
travail `docker` : l'image porte l'empreinte du commit). Tous les organismes d'une instance
reçoivent la même version au même moment. Une **fonction nouvelle** arrive **désactivée** : chaque
organisme l'active quand il est prêt, par module (Administration › Modules, E-TN-4).

## 2. Les cadences

| Cadence | Contenu | Mécanisme |
|---|---|---|
| Chaque semaine (lundi) | Mises à jour de dépendances proposées, une demande par écosystème | `.github/dependabot.yml` |
| Chaque semaine (lundi 6 h UTC) | Balayage des vulnérabilités de l'image et des dépendances | `ci-cd.yml`, cron `0 6 * * 1` |
| Chaque semaine (dimanche, 2 h–6 h HE) | Plage d'entretien : déploiement de ce qui a été validé | `ci-cd.yml`, cron `0 8 * * 0` + `scripts/plage-entretien.sh` |
| Chaque mois | Version de fonctionnalités (notes de version publiées 5 jours ouvrables avant) | § 3 |
| Chaque trimestre | Revue de conformité législative (normes du travail, conventions, fiscalité, Loi 25) et revue du registre des risques | § 4 ; `docs/securite/REGISTRE-RISQUES.md` |
| Chaque année | Exercice de reprise et révision du plan (P-TN-3) ; test d'intrusion externe | `docs/securite/PLAN-CONTINUITE-REPRISE.md` |
| Sans délai | Correctif de sécurité critique (CVSS ≥ 9) ou d'un incident de niveau 1 | § 3, « urgence » |

## 3. Le circuit d'un changement, du commit à la production

1. **Demande de fusion** : essais unitaires et d'intégration, couverture ≥ 96 % par classe,
   normes Java et architecture, analyse statique, balayage des dépendances, DAST OWASP ZAP,
   campagne e2e et audit d'accessibilité. Une seule défaillance bloque la fusion.
2. **Fusion sur la branche principale** : l'image est construite et publiée sous son empreinte.
   Rien ne part en production à ce moment.
3. **Validation** : la version est déployée dans l'environnement de tests et de formation de
   l'organisme (`urgences-sante-essai.tfvars`), où l'organisme peut la vérifier.
4. **Annonce** : la fenêtre d'entretien est annoncée 72 heures à l'avance par la bannière et les
   avis (module Soutien, `SoutienTest`) ; les notes de version disent ce qui change, ce qui arrive
   désactivé, et ce que l'organisme doit faire.
5. **Production** : le déploiement part seul dans la plage d'entretien du dimanche
   (`deploy` ou `deploy-aws` selon la cible), après la porte `scripts/plage-entretien.sh`.
6. **Urgence** : un correctif qui ne peut pas attendre se déploie à la main avec l'option
   « urgence » et un **motif écrit** (10 caractères au moins), consigné dans le journal du
   travail. L'organisme est avisé dans l'heure.

## 4. Mises à jour législatives

La revue trimestrielle relève les changements applicables (Loi sur les normes du travail,
conventions collectives de l'organisme, Revenu Québec, Loi 25 sur la protection des
renseignements personnels, directives du ministère de la Cybersécurité et du Numérique). Un
changement qui touche une règle d'affaires se livre de deux façons :

- **paramétrable** (taux, seuils, délais, codes) : nouvelle valeur datée dans les règles des
  effectifs, sans nouvelle version et sans effet rétroactif ;
- **comportement nouveau** : version de fonctionnalités, selon le circuit du § 3, avec la date
  d'entrée en vigueur de la loi comme échéance.

## 5. Retour arrière

Toute version publiée reste disponible sous son empreinte. Revenir à la précédente, c'est
relancer le déploiement avec l'option « version » (E-TN-16) ; sur ECS, le disjoncteur de
déploiement reprend aussi seul la révision précédente si la nouvelle ne devient pas saine. Le
schéma de base de données ne se rétrograde pas : une migration qui le change est rétrocompatible
avec la version précédente pendant au moins une version, et la restauration d'une sauvegarde
reste le recours pour les données.

## 6. Notes de version

Une note par version, dans `docs/notes-de-version/AAAA-MM.md`, en français : nouveautés (et le
module qui les active), corrections, changements de comportement, actions attendues de
l'organisme, et l'empreinte de l'image.
