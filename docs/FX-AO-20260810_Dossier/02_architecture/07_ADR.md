# Architecture Decision Records (ADR)

## ADR-001 — Architecture modulaire par domaines
- **Statut :** proposée, à valider.
- **Contexte :** les capacités couvrent communication, groupes, identité, distribution, escalades, audit et intégrations.
- **Décision :** séparer en modules métier avec contrats d'API/événements; éviter le découpage prématuré en microservices physiques.
- **Motifs :** cohérence transactionnelle, déploiement plus simple, évolution contrôlée.
- **Conséquences :** modules testables indépendamment; extraction ultérieure possible si charge/organisation le justifie.

## ADR-002 — Configuration métier dynamique
- **Statut :** proposée.
- **Décision :** référentiels, types, rôles, permissions, groupes, canaux, politiques et étapes d'escalade gérés en base et versionnés.
- **Motifs :** multi-sites et structures variables; éviter les références statiques.
- **Conséquences :** validations de schéma, audit des changements, cache invalidé par version, interface d'administration.

## ADR-003 — Snapshot des destinataires au moment de l'envoi
- **Statut :** proposée.
- **Décision :** résoudre les groupes dynamiques à l'envoi et enregistrer un snapshot des destinataires effectifs.
- **Motifs :** auditabilité, preuve de qui était ciblé même si le groupe change ensuite.
- **Conséquences :** stockage additionnel; politique de conservation à définir.

## ADR-004 — Orchestration durable des relances
- **Statut :** proposée.
- **Décision :** persister chaque instance d'escalade, échéance, étape et version de politique; utiliser un scheduler durable.
- **Motifs :** reprise après panne et traçabilité.
- **Conséquences :** idempotence, verrouillage/concurrence et gestion de reprise obligatoires.

## ADR-005 — Événements asynchrones pour les canaux
- **Statut :** proposée.
- **Décision :** découpler demande de diffusion et adaptateurs de canaux par transport asynchrone.
- **Motifs :** résilience face aux latences fournisseurs et montée en charge.
- **Conséquences :** livraison au moins une fois possible; déduplication et corrélation requises.

## ADR-006 — Modèle d'hébergement non figé
- **Statut :** à décider avec le client.
- **Contexte :** documents autorisent SaaS, nuage privé ou infrastructure autorisée et décrivent aussi une responsabilité d'infrastructure par l'établissement.
- **Décision :** conserver une architecture portable; choisir le profil après arbitrage contractuel.
- **Conséquences :** deux modèles d'exploitation et RACI possibles; sizing dépendant du profil.

## ADR-007 — Conservation distincte contenu / métadonnées / audit
- **Statut :** proposée, rétention à confirmer.
- **Décision :** séparer contenu clinique, métadonnées de distribution et audit; appliquer politiques de conservation par catégorie.
- **Motifs :** minimisation, conformité, export et destruction maîtrisée.
- **Conséquences :** politiques de rétention, gel légal et destruction cryptographique à définir.
