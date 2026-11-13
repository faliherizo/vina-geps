# ADR 0025 — Le backend Java adopte une couche application et le CQRS, contexte par contexte

- **Statut** : accepté
- **Date** : 2026-09-14

## Contexte

Le port Java du backend (570 classes, 85 % des routes le 14 septembre) n'a que deux couches par
contexte : `domaine` (pur, vérifié par ArchUnit) et `infrastructure` (contrôleurs et dépôts JDBC
qui se parlent directement, `Map<String, Object>` aux frontières). Ça a permis d'étrangler le
Node vite, avec un domaine testé à 100 %. Mais les cas d'utilisation n'ont pas de nom : ils sont
répartis entre un contrôleur qui orchestre et un dépôt qui décide, lectures et écritures mêlées
dans les mêmes classes. La demande du 14 septembre : « CQRS, clean architecture, DTO, entités,
infra, domaine bien séparés — documenté avant implémentation ».

## Options écartées

- **Continuer à deux couches** : le contrat HTTP est tenu, la couverture est là ; mais chaque
  route de plus grossit un dépôt qui fait tout, et la lecture d'un tableau recharge des entités
  entières. C'est la dette qu'on rembourse plus cher plus tard.
- **CQRS complet avec bus et base de lecture** (Axon, Kafka, projections asynchrones) : deux
  services de plus à exploiter, une cohérence à retardement à expliquer, pour un volume qui ne le
  demande pas.
- **Réécrire les 570 classes d'un bloc avant de reprendre le port** : c'est la réécriture que
  [PLAN-MIGRATION-BACKEND-JAVA.md](../PLAN-MIGRATION-BACKEND-JAVA.md) a refusée — le risque est
  la régression silencieuse d'un contrat que le Node tient encore.

## Décision

1. **Quatre couches par contexte** : `domaine` (entités, objets-valeur, ports), `application`
   (`commandes`, `requetes`, `dto`), `infrastructure` (`web`, `persistance`, `configuration`).
   Les dépendances pointent vers l'intérieur ; `application` ne connaît ni Spring ni JDBC ; le
   câblage des gestionnaires est un `@Configuration` d'infrastructure.
2. **CQRS sans bus** : une commande (record) → un gestionnaire (une classe) → un port d'écriture ;
   une requête → un gestionnaire qui projette par un port de lecture, sans écrire. Ports
   d'écriture et de lecture séparés (ISP). Une seule base, cohérence immédiate.
3. **DTO nommés** : records immuables dans `application/dto` ; `Map<String, Object>` reste
   confiné au bord (Jackson, JDBC).
4. **Cliquet par contexte** : tout nouveau code suit la règle ; un contexte dont tous les
   contrôleurs passent par `application` entre dans `CONTEXTES_CQRS` d'`ArchitectureTest`, où
   une règle interdit pour toujours à ses contrôleurs de toucher un dépôt. La fin du port `payroll`
   (179 routes) est le premier chantier ; `paie` est le premier contexte conforme.
5. **Tests par couche** : domaine 100 %, application avec doubles des ports (96 %), adaptateurs
   sur SQLite réel (92 %), contrat HTTP MockMvc, tests de contrat des ports contre chaque
   implémentation.

Le détail (packages, objets, conventions, tableau de migration) :
[ARCHITECTURE-JAVA-CQRS.md](../ARCHITECTURE-JAVA-CQRS.md).

## Conséquences

- Chaque cas d'utilisation a un nom et un test ; une règle qui change se trouve en une classe.
- Une route de plus coûte un gestionnaire de plus, pas un dépôt plus gros ; la lecture d'un
  écran ne recharge que ce qu'il montre.
- Plus de classes (commande, gestionnaire, DTO par cas) : c'est le prix de la lisibilité, et
  ArchUnit garantit qu'elles restent à leur place.
- Les contextes non encore conformes coexistent avec les conformes ; la liste `CONTEXTES_CQRS`
  ne rétrécit jamais — un test échoue si l'on tente de la réduire.
