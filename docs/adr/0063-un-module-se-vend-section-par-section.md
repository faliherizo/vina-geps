# ADR 0063 — Un module sectoriel se vend section par section, et son écran remplacé s'en détache

- **Statut** : accepté — livré le 25 octobre 2026
- **Date** : 25 octobre 2026
- **Décideurs** : équipe Nexu VINA
- **Concerne** : `backend/src/saas/{catalogueModules.ts,catalogueVues.ts,tenants.ts}`,
  migration `0169`, `backend/src/{revenus/infrastructure/web/routes.ts,clients/infrastructure/routes.ts}`,
  `frontend/src/santequebec/menu.ts`, `frontend/src/App.tsx`
- **Demande** : trois défauts signalés le 25 octobre 2026 — le module Santé Québec n'est pas
  gérable depuis *Modules et accès* ; *Revenus et tarifs* et *Clients et comptes recevables* sont
  toujours actifs ; l'écran « Horaires cliniques » de la version précédente reste au menu

## Contexte

Trois symptômes, une seule cause : **un écran au menu sans code de module qui le gouverne**.

Le module Santé Québec compte 36 vues réparties en six sections. Toutes portaient le code
`horaires` : le superadmin ne pouvait que tout ouvrir ou tout fermer, alors qu'un établissement
achète la planification et les gardes sans forcément les rapports Power BI ni le classeur
documentaire. *Revenus* et *Clients*, eux, n'avaient **aucun** code : ils suivaient `finance`,
donc ils s'ouvraient avec le grand livre et aucune case ne les fermait. Et l'écran unique de la
version précédente partageait le code du module qui le remplace — migrer vers les 36 vues ne
retirait pas du menu la page qu'elles remplacent.

Le catalogue des vues, qui permet de dégarnir un menu entrée par entrée, ignorait les 36 vues du
module. Son essai de conformité ne le voyait pas : il lit `navigation.tsx` par expression
régulière, et le groupe Santé Québec y est **construit par code** depuis `santequebec/menu.ts`.
Un audit aveugle à un écran est un audit décoratif — c'est le même mécanisme que l'écran sauté
en silence déjà consigné ailleurs.

## Décision

**Un socle, cinq sections, et chaque écran son code.**

1. `horaires` devient le **socle** : il porte l'accueil du module et il est le `requiert` des cinq
   codes de section (`sq_planification`, `sq_gardes`, `sq_communication`, `sq_rapports`,
   `sq_administration`). Fermer le socle ferme tout le module ; fermer une section ne retire que
   ses vues. L'accueil reste sur le socle parce que c'est la porte d'entrée : la cacher derrière
   une section laisserait un établissement sans point de départ.
2. L'écran de la version précédente passe à `horaires_legacy`, **sans `requiert`** : il ne dépend
   pas du module qui le remplace. Un établissement qui possédait `horaires` ne le reçoit pas —
   c'est le seul changement visible, et il est demandé. **Mais il est accordé à qui possède
   `effectifs`** : trois de ses onglets — heures supplémentaires, pointage, fériés et
   statistiques — servent la planification ouvrière de l'AO 26-015-DO et non Santé Québec, et les
   fermer pour tout le monde les aurait rendus **injoignables** en silence.
3. *Revenus* et *Clients* reçoivent leur code, famille `finance`, **avec garde serveur** —
   `requireModule` sur leur routeur, 403 nommé. Les plans qui vendent le grand livre les nomment
   désormais explicitement, sinon un **nouvel** établissement perdrait deux écrans que la
   migration conserve aux anciens.
4. Les 36 vues entrent au **catalogue des vues**, chacune sous le code de sa section, et l'essai
   de conformité lit maintenant les **deux** sources de menu.

La migration `0169` est un **rattrapage** : qui possédait `horaires` reçoit les cinq sections, qui
possédait `finance` reçoit `revenus` et `clients`. Sans lui, le menu filtrerait sur des codes que
personne ne possède, et aucun essai ne le verrait — les jeux d'essai créent leurs établissements
avec la liste complète des modules. C'est le mécanisme des migrations 0028 et 0104, pour la même
raison. Une liste de modules illisible est **laissée telle quelle** : la réécrire effacerait la
seule trace de ce que l'établissement avait souscrit.

## Options écartées

- **Un code par vue (36 codes).** Le catalogue des vues fait déjà cela, et mieux : il masque sans
  prétendre à un contrôle d'accès. Trente-six cases dans *Modules et accès* ne se lisent plus.
- **Découper aussi les routes par section.** `quarts`, `personnes` et `periodes` servent la
  planification, les gardes et les échanges à la fois. Les répartir aurait fermé des écrans qui
  marchent, sur une correspondance devinée.
- **Supprimer l'écran de la version précédente.** Un établissement encore en transition s'en sert.
  Un code qui s'active d'une case coûte moins qu'une page injoignable.

## Conséquences

- Le superadmin règle le module section par section, et chaque vue séparément.
- Les établissements existants ne perdent rien, sauf l'ancien écran — ce qui était demandé.
- **Dette assumée et mesurée** : `/api/horaires` **n'a pas** de garde de module. Fermer le module
  retire ses vues du menu et refuse l'URL directe, mais la route répond encore à un compte de
  l'établissement. Poser `requireModule("horaires")` est une ligne ; ce qui coûte, c'est que
  **52 suites d'intégration** créent leur établissement au plan « enterprise », qui ne comprend
  pas ce module sectoriel : elles passeraient toutes au rouge en 403, et il faut leur accorder le
  module avant de fermer la porte. Le module sectoriel voisin (`municipal`) est dans le même état.
  L'écart est **fixé par un essai** qui constate l'état actuel (200, non 403), pour que sa
  fermeture soit un changement d'essai délibéré et non une surprise.

## Vérification

```bash
cd backend && npx jest tests/integration/modules-sante-quebec.integration.test.ts   # 7/7
cd backend && npx jest tests/unit/catalogue-vues.test.ts tests/unit/modules-catalogue.test.ts
E2E_WEB_PORT=5190 E2E_API_PORT=4010 npx playwright test --config=e2e/playwright.config.ts \
  --project=interface e2e/tests/ui/sante-quebec-pages.spec.ts -g "section|menu"
```
