# Plan de continuité et de reprise après sinistre

> AO 26-015-DO, **P-TN-3** (exercices de reprise au moins une fois par année, avec révision du plan) et **P-TN-4**
> (même niveau de service en reprise). Pour l'exploitation et pour l'évaluateur.

## 1. Objectifs

| Objectif | Valeur | Source | Où il est tenu |
|---|---|---|---|
| Temps de reprise (RTO) | **48 heures** au plus | Addenda 2, réponse 12 | `infra/terraform/aws/objectifs-de-reprise.tf` refuse un plan au-delà |
| Point de reprise (RPO) | **15 minutes** | Exigence 48 | Sauvegardes applicatives incrémentales aux 15 minutes (`Sauvegardes.java`), plafond vérifié au plan Terraform |
| Disponibilité | 99,7 % par mois hors maintenance annoncée | Addenda 6, réponse 9 | Jugée chaque mois par `EngagementsDeService` |

## 2. Copies

Le détail des copies, de leur fréquence et de leur conservation est dans
[COPIES-DE-SECURITE.md](../urgences-sante/COPIES-DE-SECURITE.md). En résumé : copies applicatives cohérentes aux 15
minutes et quotidiennes, copie du volume quotidienne (35 jours) et mensuelle (6 ans), toutes au Québec. Sur la
démonstration OVH, une archive complète du volume est prise avant chaque déploiement (`deploy/geps/deployer.sh`, les 10
dernières).

## 3. Scénarios et reprise

| Scénario | Reprise | Délai visé |
|---|---|---|
| Tâche applicative en panne | Remplacée d'office (ECS ; `restart: unless-stopped` sur OVH) | minutes |
| Version défectueuse | Retour arrière automatique du déploiement (disjoncteur ECS ; `deployer.sh` revient à la version précédente) | minutes |
| Base corrompue ou donnée détruite | Restauration de la dernière copie saine dans un volume neuf, vérifiée, puis bascule décidée | < 4 h |
| Perte d'une zone de disponibilité | Volume régional multizone ; tâche relancée dans une autre zone | minutes |
| Perte de la région | Restauration dans ca-west-1 depuis la copie hors région — **seulement si l'organisme l'autorise par écrit** (art. 17.07.04) | ≤ 48 h |

## 4. L'exercice

`deploy/geps/exercice-reprise.sh`, lancé chaque trimestre par `.github/workflows/exercice-reprise.yml` ou à la main :

1. restaure la dernière copie dans un volume neuf, à côté du service, jamais à sa place ;
2. démarre la version en service sur ce volume, dans une pile **isolée** : réseau interne sans sortie Internet, aucun
   port publié — aucun texto, courriel ou webhook ne peut partir de la copie ;
3. vérifie l'intégrité de la base (`PRAGMA integrity_check`), la santé, la connexion du superadmin et la lecture des
   organismes, et les compare au service vivant ;
4. mesure le RPO (âge de la copie) et le RTO (restauration → service vérifié) contre les objectifs ;
5. écrit le rapport, puis détruit la pile et le volume de l'exercice, même en cas d'échec.

En production AWS s'y ajoute l'essai mensuel automatisé d'AWS Backup (restauration du volume dans un volume jetable,
validation, suppression ; échec signalé au sujet d'alertes).

## 5. Révision du plan

Après chaque exercice : un écart (vérification en échec, objectif manqué) ouvre une correction et une révision de ce
plan, consignée au registre ci-dessous. Sans écart, le plan est revu au moins une fois par année.

## 6. Registre des exercices

| Date | Lieu | Copie (âge) | RTO mesuré | Verdict | Révision |
|---|---|---|---|---|---|
| 2026-11-26 | Démonstration OVH, pile isolée | archive du volume du 24 nov. (56 h) | 29 s, restauration → service vérifié | ✅ intégrité ok, superadmin connecté, 6 organismes restaurés sur 6 | Vérification de connexion corrigée : présenter l'hôte commun (résolution par sous-domaine) |
| 2026-11-26 | Démonstration OVH, essai de panne | — | 15 s d'indisponibilité, retour automatique | ✅ 518 pannes semblables tiennent dans le budget mensuel de 99,7 % | Un premier essai par `docker kill` a laissé le service à terre 11 minutes : Docker ne relance pas un conteneur arrêté de l'hôte. L'essai simule désormais la panne de l'intérieur et relance lui-même le service s'il ne revient pas en 2 minutes |
