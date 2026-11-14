package ca.vina.nexu.bdd;

import io.cucumber.java.fr.Alors;
import io.cucumber.java.fr.Et;
import io.cucumber.java.fr.Quand;
import io.cucumber.java.fr.Soit;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Les étapes des scénarios d'exigences : un organisme, un acteur qui appelle l'API, et un attendu
 * qui porte sur une <b>valeur</b> rendue — un statut, un motif, un compte. Un attendu qui se
 * contenterait d'une présence passerait quoi qu'il arrive.
 *
 * <p>Cucumber crée une instance par scénario : l'organisme et les valeurs retenues ne fuient donc
 * jamais d'un scénario à l'autre.
 */
public class EtapesExigences {

    private static final String AMORCE = "/amorce/organisme-effectifs.json";

    private Organisme organisme;
    private ClientApi.Reponse derniere;

    @Soit("un organisme d'Urgences-santé neuf")
    public void unOrganismeNeuf() {
        organisme = Organisme.creer();
    }

    /** Les référentiels, le milieu C12 et les ressources de Julie (P1) et Marc (P2), reliées à leurs comptes. */
    @Soit("un organisme d'Urgences-santé amorcé")
    public void unOrganismeAmorce() {
        unOrganismeNeuf();
        amorcer();
    }

    /**
     * Un second organisme, amorcé comme le premier, dans le même scénario : ses acteurs et ses valeurs
     * portent le suffixe donné ({@code adminB}, {@code julieB}, {@code {{P1B}}}). C'est ce qui permet
     * de montrer qu'un organisme ne voit rien de l'autre (E-TN-6).
     */
    @Soit("un second organisme d'Urgences-santé amorcé, dont les acteurs et les valeurs portent le suffixe {word}")
    public void unSecondOrganisme(String suffixe) {
        Organisme premier = organisme;
        unOrganismeAmorce();
        premier.adjoindre(organisme, suffixe);
        organisme = premier;
    }

    private void amorcer() {
        for (JsonNode e : lireAmorce()) {
            appeler(Organisme.ADMIN, e.get("methode").asString(), e.get("chemin").asString(), e.get("corps"));
            assertThat(derniere.statut()).as("amorce %s : %s", e.get("chemin").asString(), derniere.texte()).isLessThan(300);
            if (e.has("capturer")) {
                e.get("capturer").properties().forEach(c -> organisme.vars.put(c.getKey(), Gabarits.capturer(derniere.json(), c.getValue().asString())));
            }
        }
    }

    private static JsonNode lireAmorce() {
        try (InputStream in = EtapesExigences.class.getResourceAsStream(AMORCE)) {
            return ClientApi.JSON.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void appeler(String acteur, String methode, String chemin, JsonNode corps) {
        ClientApi.Session s = organisme.sessions.get(acteur);
        assertThat(s).as("acteur « %s » : inconnu (connus : %s)", acteur, organisme.sessions.keySet()).isNotNull();
        derniere = ClientApi.appeler(s, methode, Gabarits.rendreTexte(chemin, organisme.vars), Gabarits.rendre(corps, organisme.vars));
    }

    @Quand("{word} envoie {word} {word}")
    public void envoie(String acteur, String methode, String chemin) {
        appeler(acteur, methode, chemin, null);
    }

    @Quand("{word} envoie {word} {word} avec :")
    public void envoieAvec(String acteur, String methode, String chemin, String corps) {
        appeler(acteur, methode, chemin, ClientApi.JSON.readTree(corps));
    }

    @Alors("la réponse a le statut {int}")
    public void statut(int attendu) {
        assertThat(derniere.statut()).as("statut de la réponse — %s", derniere.texte()).isEqualTo(attendu);
    }

    @Et("la réponse contient :")
    public void contient(String attendu) {
        List<String> ecarts = Gabarits.ecarts(Gabarits.rendre(ClientApi.JSON.readTree(attendu), organisme.vars), derniere.json(), "$");
        assertThat(ecarts).as("réponse : %s", derniere.texte()).isEmpty();
    }

    @Et("la réponse correspond à {string}")
    public void correspond(String expression) {
        assertThat(Pattern.compile(Gabarits.rendreTexte(expression, organisme.vars), Pattern.MULTILINE).matcher(derniere.texte()).find())
                .as("/%s/ dans : %s", expression, derniere.texte()).isTrue();
    }

    @Et("la réponse ne correspond pas à {string}")
    public void neCorrespondPas(String expression) {
        assertThat(Pattern.compile(Gabarits.rendreTexte(expression, organisme.vars), Pattern.MULTILINE).matcher(derniere.texte()).find())
                .as("/%s/ ne devait pas paraître dans : %s", expression, derniere.texte()).isFalse();
    }

    @Et("je retiens {string} sous le nom {string}")
    public void retiens(String chemin, String nom) {
        organisme.vars.put(nom, Gabarits.capturer(derniere.json(), chemin));
    }

    @Et("la réponse compte {int} élément(s)")
    public void compte(int nombre) {
        assertThat(derniere.json()).as("réponse : %s", derniere.texte()).isNotNull();
        assertThat(derniere.json().size()).as("réponse : %s", derniere.texte()).isEqualTo(nombre);
    }

    @Et("la réponse arrive en moins de {int} millisecondes")
    public void delai(int millisecondes) {
        assertThat(derniere.dureeMs()).as("durée de la dernière réponse").isLessThan(millisecondes);
    }
}
