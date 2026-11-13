# ADR 0030 — Les normes Java (enums plutôt que chaînes, SOLID, clean code) s'imposent par une porte à cliquet

- **Statut** : accepté
- **Date** : 2026-09-16
- **Norme** : [docs/NORMES-JAVA.md](../NORMES-JAVA.md)

## Contexte

Le backend Java est né d'un port module par module du Node (1 215 routes, 940 classes, 91 500
lignes le 16 septembre 2026). Le port a gardé les conventions du Node : statuts, rôles, canaux et
types comparés comme des chaînes (`"approved".equals(…)`, `case "courriel"`, `List.of("draft",
"active", …)`). Neuf `enum` seulement. Une faute de frappe passe la compilation, un cas ajouté ne
casse rien à la construction, et la campagne JMeter du 16 septembre a trouvé un
`NullPointerException` devenu 500 là où une validation typée aurait répondu 400.

Demande : « pas de chaînes en dur dans les codes Java, préférer les enums, SOLID, clean code — un
document à respecter, corriger partout, et la norme dans la configuration de Claude pour toutes les
demandes futures ».

## Décision

1. **Une norme écrite** (`docs/NORMES-JAVA.md`) : valeur d'un ensemble fermé = `enum` avec son code,
   conversion une fois à la frontière, `switch` exhaustif ; constantes nommées ; SOLID appliqué à la
   découpe existante (gestionnaire par cas d'utilisation, ports étroits, injection) ; clean code
   (nommage métier, records, exceptions typées, pas de code mort, pourquoi et non quoi).
2. **Une porte automatisée à cliquet** (`NormesJavaTest`) : les écarts (`"…".equals(`, `case "…"`,
   `List.of("…", "…")`) sont comptés par contexte contre `backend-java/normes-java-plafonds.properties` ;
   un compte qui **monte** fait échouer `mvn verify`, un plafond plus large que le compte réel aussi
   (le cliquet ne remonte jamais).
3. **La correction se fait contexte par contexte**, en commençant par le code du jour :
   `notifications` (32 → 5 : `Canal`, `TypeDestinataire`, `CanalJournal`, `StatutEnvoi`) et
   `partage.securite.Role` (rôles intégrés). Les 928 écarts restants ont un plan (norme § 7) : chaque
   intervention dans une classe héritée la convertit avant d'y ajouter quoi que ce soit.
4. **La norme entre dans la configuration de Claude** (`CLAUDE.md` du projet, section « Normes Java »)
   : elle s'applique sans qu'on la rappelle.

## Options écartées

- **Tout réécrire d'un bloc** (928 sites, 940 classes) : plusieurs semaines sans valeur livrée, un
  risque de régression sur 1 215 routes, et rien qui empêche le retour des chaînes ensuite. Le
  cliquet donne la garantie inverse : ça ne s'aggrave jamais, ça s'améliore à chaque passage.
- **Une règle Checkstyle/PMD « pas de littéral »** : trop large (messages, chemins, clés JSON sont
  légitimes) ou trop étroite ; le compte par contexte cible exactement les trois formes qui
  remplacent un enum.
- **Des constantes `String` au lieu d'enums** : évite la faute de frappe mais pas le `switch`
  incomplet ni la comparaison hors ensemble ; l'enum apporte l'exhaustivité.

## Conséquences

- `mvn verify` refuse une classe qui ajoute une chaîne comparée là où un enum existe ou devrait
  exister ; la correction d'un contexte se constate par la baisse de son plafond.
- Les DTO d'entrée valident les codes à la frontière (`Enum.de(code)` → 400 nommé) : plus de
  `NullPointerException` sur un corps incomplet.
- Un contexte converti expose ses enums dans son domaine ; les autres contextes reçoivent des
  codes par DTO, jamais l'enum d'un autre contexte (pas de couplage par le type).
