package ca.vina.nexu.partage.securite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le tout premier test de la migration : les mots de passe existants doivent continuer à
 * fonctionner.
 *
 * <p>Les empreintes en base ont été produites par {@code bcryptjs} (coût 8, variante {@code $2b} —
 * c'est ce qu'on observe dans {@code backend/data/payroll.db}). Si {@code BCryptPasswordEncoder}
 * ne les vérifie pas à l'identique, <strong>tous les comptes sont inaccessibles le jour de la
 * bascule</strong> et aucune autre partie de la migration n'a d'importance.
 *
 * <p>Les vecteurs de {@code empreintes-bcryptjs.json} ont été produits par la bibliothèque du
 * projet Node elle-même. Ils ne se régénèrent pas : ce sont eux la référence. Les cas retenus ne
 * sont pas décoratifs — accents québécois et mots de passe longs sont précisément là où deux
 * implémentations de bcrypt divergent, sur l'encodage UTF-8 et sur la troncature à 72 octets.
 */
@DisplayName("Compatibilité des empreintes bcryptjs → Spring Security")
class CompatibiliteEmpreintesTest {

    private final BCryptPasswordEncoder encodeur = new BCryptPasswordEncoder();

    record Vecteur(String libelle, String motDePasse, int octets, String empreinte) {
        @Override
        public String toString() {
            return libelle + " (" + octets + " octets)";
        }
    }

    static List<Vecteur> vecteurs() throws Exception {
        try (InputStream flux = CompatibiliteEmpreintesTest.class
                .getResourceAsStream("/empreintes-bcryptjs.json")) {
            assertThat(flux).as("vecteurs de référence absents du classpath").isNotNull();
            JsonNode racine = new ObjectMapper().readTree(flux);
            List<Vecteur> liste = new ArrayList<>();
            for (JsonNode v : racine.get("vecteurs")) {
                liste.add(new Vecteur(
                        v.get("libelle").asText(),
                        v.get("motDePasse").asText(),
                        v.get("octets").asInt(),
                        v.get("empreinte").asText()));
            }
            return liste;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vecteurs")
    @DisplayName("vérifie une empreinte produite par bcryptjs")
    void verifieUneEmpreinteDeBcryptjs(Vecteur v) {
        assertThat(encodeur.matches(v.motDePasse(), v.empreinte()))
                .as("le mot de passe « %s » doit être vérifié par son empreinte bcryptjs", v.libelle())
                .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vecteurs")
    @DisplayName("rejette un mot de passe voisin mais différent")
    void rejetteUnMotDePasseVoisin(Vecteur v) {
        // Sans ce contrôle, un encodeur qui répondrait « vrai » à tout ferait passer le test
        // précédent en beauté.
        //
        // La variante est **préfixée**, pas suffixée : bcrypt ne lit que les 72 premiers octets,
        // donc ajouter un caractère à la fin d'un mot de passe déjà long ne le change pas — et
        // l'empreinte continuerait légitimement de correspondre. Le préfixe garantit que la
        // différence tombe dans la partie effectivement lue.
        assertThat(encodeur.matches("x" + v.motDePasse(), v.empreinte()))
                .as("un mot de passe différent ne doit pas être accepté")
                .isFalse();
    }

    @Test
    @DisplayName("les empreintes de référence sont bien en variante $2b et en coût 8")
    void empreintesEnVariante2bEtCout8() throws Exception {
        // Le jour où quelqu'un régénère les vecteurs avec un autre coût, ce test le dit —
        // sinon la compatibilité serait « prouvée » contre autre chose que la production.
        for (Vecteur v : vecteurs()) {
            assertThat(v.empreinte()).as(v.libelle()).startsWith("$2b$08$");
        }
    }

    @Test
    @DisplayName("l'encodeur Java produit des empreintes que bcryptjs pourrait relire")
    void produitDesEmpreintesRelisiblesParBcryptjs() {
        // La compatibilité doit valoir dans les deux sens : pendant l'étranglement, un mot de
        // passe changé côté Java devra rester vérifiable côté Node, qui sert encore les autres
        // modules. Le format de sortie est donc autant un contrat que le format d'entrée.
        BCryptPasswordEncoder cout8 = new BCryptPasswordEncoder(8);
        String empreinte = cout8.encode("Motdepasse123!Fort");
        assertThat(empreinte).matches("^\\$2[aby]\\$08\\$.{53}$");
        assertThat(cout8.matches("Motdepasse123!Fort", empreinte)).isTrue();
    }

    @Test
    @DisplayName("documente la troncature de bcrypt à 72 octets")
    void documenteLaTroncatureA72Octets() throws Exception {
        // bcrypt ne considère que les 72 premiers octets. Deux mots de passe qui ne diffèrent
        // qu'au-delà sont donc équivalents — comportement partagé par les deux implémentations.
        // Le vérifier ici évite qu'on le « corrige » plus tard côté Java en croyant bien faire :
        // ce serait rendre invérifiables les empreintes existantes.
        Vecteur au80 = vecteurs().stream()
                .filter(v -> v.libelle().equals("au-dela-de-72")).findFirst().orElseThrow();
        assertThat("B".repeat(80).getBytes(StandardCharsets.UTF_8)).hasSize(80);
        assertThat(encodeur.matches("B".repeat(80), au80.empreinte())).isTrue();
        assertThat(encodeur.matches("B".repeat(72), au80.empreinte()))
                .as("les 72 premiers octets suffisent : c'est la définition de bcrypt")
                .isTrue();
    }
}
