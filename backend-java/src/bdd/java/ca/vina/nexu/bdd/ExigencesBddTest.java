package ca.vina.nexu.bdd;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * <b>Les exigences de l'avis 26-015-DO, en Gherkin, jouées contre le serveur réel.</b>
 *
 * <p>Chaque scénario de {@code src/bdd/resources/features} porte en étiquette les exigences qu'il
 * prouve ({@code @E-GN-5}, {@code @P-CH-4}…) : on joue une exigence seule par
 * {@code -Dcucumber.filter.tags="@E-GN-5"}. Le serveur démarre une fois, sur une base vierge bâtie
 * par Liquibase ; chaque scénario reçoit son propre organisme, de sorte qu'aucun ne dépend d'un autre.
 */
@Suite
@IncludeEngines("cucumber")
@SelectPackages("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "ca.vina.nexu.bdd")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "summary, html:target/bdd/exigences.html, json:target/bdd/exigences.json")
class ExigencesBddTest {
}
