# Normes de développement Java — Nexu VINA (`backend-java/`)

- **Statut** : norme en vigueur depuis le 16 septembre 2026 ; s'applique à tout code Java produit ou
  modifié, quelle que soit la demande. Elle complète les standards généraux (`CLAUDE.md` :
  architecture propre, SOLID, couverture ≥ 96 % par classe, accessibilité) et les précise pour Java.
- **Porte automatisée** : `backend-java/src/test/java/ca/vina/nexu/NormesJavaTest.java` mesure
  les écarts (chaînes en dur comparées ou aiguillées, listes de codes) et **refuse toute hausse** ;
  `ArchitectureTest` fait respecter les couches, les cycles et l'état statique.
- **Décision** : [ADR 0030](adr/0030-normes-java-enums-et-porte-cliquet.md).

## 1. Aucune chaîne en dur pour une valeur du domaine — des `enum`

Une valeur qui fait partie d'un **ensemble fermé** (statut, rôle, canal, type, mode, code de
catalogue fixe, nom de colonne d'un contrat de sortie) est une constante typée : un `enum` avec
son code de sérialisation, jamais une chaîne répétée dans le code.

```java
// ✗ proscrit
if ("approved".equals(a.get("status"))) { … }
switch (canal) { case "courriel" -> …; case "texto" -> …; }
List.of("draft", "active", "closed", "cancelled").contains(statut);

// ✓ attendu
public enum StatutContrat {
    DRAFT("draft"), ACTIVE("active"), CLOSED("closed"), CANCELLED("cancelled");
    private final String code;
    StatutContrat(String code) { this.code = code; }
    public String code() { return code; }
    /** Le code venu du client ou de la base ; {@link IllegalArgumentException} nommée sinon (400 à la frontière). */
    public static StatutContrat de(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("invalid status"));
    }
}
if (StatutContrat.de(statut) == StatutContrat.APPROVED) { … }
```

- Le **code** de l'enum est celui du contrat HTTP et de la base (`"approved"`), stable ; le nom Java
  est en majuscules. La conversion se fait **une fois, à la frontière** (`de(code)`), jamais dans le
  métier.
- Un `switch` sur un enum est **exhaustif** (expression `switch` sans `default`) : un cas ajouté
  casse la compilation, pas la production.
- Les rôles (`admin`, `manager`, `rh`, `employee`, `superadmin`…), les canaux (`courriel`, `texto`,
  `poussee`), les statuts (`sent`, `failed`, `desactive` ; `pending`, `approved`, `rejected` ;
  `draft`, `active`, `closed`, `cancelled`), les modes (`observer`, `bloquer`) : **enums**.
- Ce qui reste une chaîne : un texte lu ou rendu (message, libellé, sujet), un identifiant, un
  chemin de route dans son annotation, une clé de carte JSON à la frontière. Les **messages
  d'erreur du contrat** (« invalid status ») vivent dans la classe qui les lève, à un seul endroit.

## 2. Pas de « magic number », pas de chaîne de configuration en dur

- Un seuil, une taille, un délai : une constante nommée (`private static final int LONGUEUR_MAX =
  480;`) ou une propriété (`application.properties`, variable d'environnement lue en un seul
  endroit de configuration), jamais un littéral dans le flot.
- Une URL, un chemin de fichier, un nom d'en-tête HTTP : constante nommée ou propriété.

## 3. SOLID, appliqué

| Principe | Ce que ça veut dire ici |
|---|---|
| **S** — une raison de changer | Un gestionnaire d'application par cas d'utilisation (`GestionnaireFeuilles.executer(SoumettreFeuille)`), un contrôleur qui traduit HTTP et rien d'autre, un dépôt par agrégat. Une classe ≤ 300 lignes, une méthode ≤ 20 lignes (bloquant à 40), ≤ 4 paramètres, imbrication ≤ 3. |
| **O** — ouvert / fermé | Étendre par un nouvel `enum`, une nouvelle implémentation d'un port, une entrée de registre — jamais par un `if` ou un `case` de plus dans le code existant. |
| **L** — substitution | Toute implémentation d'un port respecte le contrat, **erreurs comprises** ; une suite de tests de contrat par port, jouée contre chaque implémentation (`FournisseurFactice`, simulateurs). |
| **I** — interfaces étroites | Un port lit **ou** écrit (`Personnel.Personnes` ≠ dépôt complet) ; le domaine reçoit ce dont il a besoin, pas un `JdbcClient`. |
| **D** — inversion | Le domaine et l'application ne dépendent que d'interfaces (`partage.avis.PublicationAvis`, `Courrier`, `Horloge`) ; l'implémentation est choisie dans `infrastructure/configuration`. Horloge, aléatoire, UUID, E/S : **injectés**. |

## 4. Architecture — la découpe que les essais vérifient

- `domaine/` (pur : ni Spring, ni JDBC, ni HTTP) → `application/` (commandes, requêtes, DTO) →
  `infrastructure/{web,persistance,configuration}`. Un contexte ne touche ni aux entités ni aux
  tables d'un autre : port de lecture rendant un DTO, ou événement (`partage`).
- Aucun cycle entre contextes (`ArchitectureTest.aucunCycleEntreContextes`) ; aucun champ statique
  mutable ; JDBC confiné à `persistance`.
- Un contrôleur d'un contexte CQRS parle aux gestionnaires de l'application, jamais à un dépôt.

## 5. Clean code

- **Nommage** en français, dans les mots du métier (`FeuillesDeTemps.soumettre`, `Avis`,
  `refusDeDeclaration`) ; pas de préfixe `I` sur les interfaces ; booléens `est`/`a`/`peut`.
- **Records** pour les valeurs (immuables), `final` par défaut, pas de setter.
- **Exceptions typées et métier** (`IntrouvableException`, `HorsPerimetreException`,
  `VerrouException`) traduites en code HTTP à la frontière ; jamais `null` silencieux, jamais un
  code d'erreur numérique interne.
- **Pas de code mort**, pas de code commenté, pas de `TODO` sans référence. Un commentaire dit
  **pourquoi** (règle, contrainte, piège rencontré), jamais **quoi**.
- Pas de `@Autowired` sur les champs ; injection par constructeur. `@Autowired` sur un
  constructeur seulement quand Spring ne peut pas choisir (deux constructeurs publics), et le
  second constructeur existe pour un essai.
- Java 25 : `switch` en expression, `record`, `sealed` pour les hiérarchies fermées, `var` seulement
  quand le type se lit sur la même ligne.

## 6. Tests et porte de qualité

- Couverture **≥ 96 % de lignes par classe** (JaCoCo `check`, `ELEMENT=CLASS`) et ≥ 92 % de
  branches ; domaine à 100 %. Une classe dispensée est nommée avec son motif dans le `pom.xml`.
- Un essai par décision : le chemin nominal, chaque refus nommé, chaque branche d'un `switch`.
- **Frontière** : tout corps HTTP est validé avant d'entrer (champ requis, énumération, borne) et
  la réponse est un 400 **nommé** — jamais un `NullPointerException` devenu 500 (trouvé par la
  campagne JMeter du 16 septembre sur `PATCH /contracts/{id}/status`).
- `mvn -q -o verify` vert avant tout commit ; `rm -rf backend-java/target/classes …` d'abord (l'IDE
  compile en ecj et pose des `$SWITCH_TABLE$` qu'ArchUnit refuse).

## 7. Le cliquet — comment la norme s'applique à l'existant

Le code hérité de la migration Node → Java (940 classes, 91 500 lignes le 16 septembre 2026)
compare et aiguille encore des chaînes : **475** `"…".equals(…)`, **154** `case "…"`, **253**
`List.of("…", "…")` de codes. Les réécrire d'un bloc n'est ni sûr ni utile ; la norme s'impose
par un **cliquet** :

1. `NormesJavaTest` compte ces écarts par contexte et échoue si un compte **monte** — le code
   nouveau et le code touché suivent la norme, l'ancien ne s'aggrave jamais.
2. Chaque intervention dans une classe héritée **convertit ses chaînes en enum** avant d'ajouter
   quoi que ce soit (la règle du campeur : on laisse plus propre qu'on a trouvé).
3. Les enums partagés (rôles, statuts de flux, canaux) vivent dans le domaine du contexte qui les
   possède ; un autre contexte les reçoit par un DTO, pas par la chaîne.
4. Le plan par contexte, du plus exposé au moins exposé : `notifications` (fait le 16 septembre),
   `paie` (statuts de feuille, d'absence, de contrat), `acces`/`auth` (rôles), `scolaire`
   (statuts de besoin, de recherche), `varpay`, puis le reste. Chaque contexte converti fait
   baisser sa ligne dans `NormesJavaTest` — et le nouveau plafond devient la règle.
