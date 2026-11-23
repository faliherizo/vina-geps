package ca.vina.nexu;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Règles d'architecture, vérifiées à chaque exécution des tests.
 *
 * <p>Les standards du dépôt l'exigent explicitement : les règles de dépendance se vérifient
 * <strong>automatiquement en intégration continue, pas par revue</strong>. Une règle qu'on ne
 * peut qu'espérer n'est pas une règle — elle cède au premier délai serré, et personne ne s'en
 * aperçoit avant que la dette soit payée.
 *
 * <p>Chaque contexte métier est découpé en {@code domaine}, {@code application} et
 * {@code infrastructure}, et les dépendances pointent vers l'intérieur.
 */
@DisplayName("Architecture — la règle de dépendance est un test, pas une intention")
class ArchitectureTest {

    private static final String RACINE = "ca.vina.nexu";
    private static JavaClasses classes;

    @BeforeAll
    static void importer() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RACINE);
    }

    @Test
    @DisplayName("le domaine ne dépend d'aucun framework")
    void domaineSansFramework() {
        // C'est la règle qui porte tout le reste : un domaine qui connaît Spring ou Jackson n'est
        // plus testable sans eux, et le prochain changement de version devient un chantier.
        noClasses().that().resideInAPackage("..domaine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "javax.sql..",
                        "tools.jackson..", "io.jsonwebtoken..", "liquibase..", "org.sqlite..")
                .because("le domaine ne dépend de rien : ni framework, ni ORM, ni HTTP, ni base")
                .check(classes);
    }

    @Test
    @DisplayName("le domaine ignore l'infrastructure")
    void domaineIgnoreInfrastructure() {
        noClasses().that().resideInAPackage("..domaine..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("les dépendances pointent vers l'intérieur : Infrastructure → Application → Domaine")
                .check(classes);
    }

    @Test
    @DisplayName("le domaine ignore la couche application")
    void domaineIgnoreApplication() {
        noClasses().that().resideInAPackage("..domaine..")
                .should().dependOnClassesThat().resideInAPackage("..application..")
                .because("un cas d'utilisation dépend du domaine, jamais l'inverse (ADR 0025)")
                .check(classes);
    }

    @Test
    @DisplayName("la couche application ne connaît ni framework ni infrastructure")
    void applicationSansFrameworkNiInfrastructure() {
        // Un gestionnaire de commande qui importe Spring ou JDBC n'est plus un cas d'utilisation :
        // c'est un contrôleur ou un dépôt déguisé. Le câblage est un @Configuration d'infrastructure.
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta..", "java.sql..", "javax.sql..", "tools.jackson..", "..infrastructure..")
                .because("Infrastructure → Application → Domaine : l'application ne regarde que vers l'intérieur (ADR 0025)")
                .check(classes);
    }

    /**
     * Les contextes dont <strong>tous</strong> les contrôleurs passent par la couche application.
     * La liste ne rétrécit jamais (cliquet, ADR 0025) : un contexte y entre quand sa refonte est
     * finie, et la règle interdit dès lors à ses contrôleurs de toucher un dépôt.
     */
    static final java.util.Set<String> CONTEXTES_CQRS = java.util.Set.of("alertes", "approbations", "communication", "effectifs", "exploitation", "ordonnanceur", "organisation", "organisme", "pays", "securite", "soutien", "superadmin", "personnel", "habilitations", "paie");

    @Test
    @DisplayName("dans un contexte conforme, un contrôleur ne parle qu'aux gestionnaires de l'application")
    void controleursDesContextesConformesNeParlentQuAuxGestionnaires() {
        for (String contexte : CONTEXTES_CQRS) {
            noClasses().that().resideInAPackage(RACINE + "." + contexte + ".infrastructure..")
                    .and().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                    .should().dependOnClassesThat().haveSimpleNameStartingWith("Depot")
                    .because("un contrôleur traduit HTTP en commande ou requête ; l'orchestration est dans l'application (ADR 0025)")
                    .allowEmptyShould(true)
                    .check(classes);
        }
    }

    @Test
    @DisplayName("aucun accès à la base hors de l'infrastructure")
    void baseDeDonneesConfineeAInfrastructure() {
        // Un `JdbcClient` appelé depuis un contrôleur ou depuis le domaine ferait fuir la
        // persistance dans toutes les couches — c'est ainsi que meurt une architecture hexagonale.
        noClasses().that().resideOutsideOfPackages("..infrastructure..", "..partage.web..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .because("l'accès aux données appartient aux adaptateurs, pas au métier")
                .check(classes);
    }

    @Test
    @DisplayName("les contrôleurs HTTP vivent dans infrastructure.web")
    void controleursDansInfrastructure() {
        // Le socle `partage` garde sa propre découpe (web, securite, courrier…) ; chaque contexte
        // métier range ses adaptateurs par nature : web, persistance, configuration, services.
        classes().that().areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
                .should().resideInAnyPackage("..infrastructure.web..", "..partage.web..")
                .because("un contrôleur est un adaptateur d'entrée : il traduit HTTP vers le métier, et se trouve toujours au même endroit")
                .check(classes);
    }

    @Test
    @DisplayName("les dépôts et les adaptateurs vivent dans infrastructure.persistance, la configuration dans infrastructure.configuration")
    void adaptateursEtConfigurationRanges() {
        classes().that().areAnnotatedWith(org.springframework.stereotype.Repository.class)
                .and().resideOutsideOfPackage("..partage..")
                .should().resideInAPackage("..infrastructure.persistance..")
                .because("un dépôt est un adaptateur de sortie : on le trouve sans chercher")
                .check(classes);
        classes().that().areAnnotatedWith(org.springframework.context.annotation.Configuration.class)
                .and().resideOutsideOfPackage("..partage..")
                .should().resideInAPackage("..infrastructure.configuration..")
                .because("le câblage d'un contexte est déclaré en un seul endroit")
                .check(classes);
        classes().that().resideInAPackage("..infrastructure..").and().resideOutsideOfPackage("..partage..")
                .should().resideInAnyPackage("..infrastructure.web..", "..infrastructure.persistance..", "..infrastructure.configuration..", "..infrastructure.services..")
                .because("infrastructure/{web,persistance,configuration} est la découpe documentée ; `services` est le sas transitoire des orchestrations qui n'ont pas encore rejoint l'application (voir CONTEXTES_CQRS)")
                .check(classes);
    }

    /**
     * Les orchestrations Spring qui n'ont pas encore rejoint {@code application} — le sas
     * {@code infrastructure/services}. La liste ne grossit jamais (cliquet) : une orchestration
     * nouvelle naît dans {@code application}, sans Spring ; une classe qui sort d'ici en est retirée.
     */
    static final java.util.Set<String> SERVICES_TRANSITOIRES = java.util.Set.of(
            "ca.vina.nexu.auth.infrastructure.services.ServiceConnexion",
            "ca.vina.nexu.auth.infrastructure.services.ServiceSso");

    @Test
    @DisplayName("le sas infrastructure.services ne grossit pas")
    void leSasDesServicesNeGrossitPas() {
        classes().that().resideInAPackage("..infrastructure.services..")
                .should(new com.tngtech.archunit.lang.ArchCondition<com.tngtech.archunit.core.domain.JavaClass>("figurer dans SERVICES_TRANSITOIRES") {
                    @Override
                    public void check(com.tngtech.archunit.core.domain.JavaClass c, com.tngtech.archunit.lang.ConditionEvents events) {
                        String nom = c.getName().contains("$") ? c.getName().substring(0, c.getName().indexOf('$')) : c.getName();
                        if (!SERVICES_TRANSITOIRES.contains(nom)) {
                            events.add(com.tngtech.archunit.lang.SimpleConditionEvent.violated(c, c.getName() + " est une orchestration nouvelle : elle doit naître dans application, sans Spring (ADR 0025)"));
                        }
                    }
                })
                .because("infrastructure/services est un sas transitoire, pas une couche")
                .check(classes);
    }

    @Test
    @DisplayName("JDBC ne sort pas de la persistance")
    void jdbcConfineALaPersistance() {
        // Un contrôleur ou un service d'infrastructure qui tient un JdbcClient est un dépôt qui
        // s'ignore : la requête SQL doit vivre dans un adaptateur nommé, testé sur SQLite réel.
        // La configuration passe le JdbcClient aux dépôts qu'elle câble sans jamais l'interroger.
        noClasses().that().resideInAPackage("..infrastructure..").and().resideOutsideOfPackages("..infrastructure.persistance..", "..infrastructure.configuration..", "..partage..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .because("l'accès aux données appartient aux dépôts et adaptateurs de persistance")
                .check(classes);
    }

    @Test
    @DisplayName("aucun cycle entre les contextes métier")
    void aucunCycleEntreContextes() {
        // Un cycle entre `pays` et `paie` signifierait que les deux ne sont plus séparables — et
        // qu'on ne peut plus migrer l'un sans l'autre. C'est exactement ce que l'étranglement
        // module par module exige d'éviter.
        SlicesRuleDefinition.slices()
                .matching(RACINE + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }

    @Test
    @DisplayName("les champs statiques sont tous finaux — aucun état statique mutable")
    void champsStatiquesFinaux() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields()
                .that().areStatic().and().areDeclaredInClassesThat().resideInAPackage(RACINE + "..")
                .should().beFinal()
                .because("un champ statique mutable est un singleton déguisé — proscrit par les standards du dépôt")
                .check(classes);
    }
}
