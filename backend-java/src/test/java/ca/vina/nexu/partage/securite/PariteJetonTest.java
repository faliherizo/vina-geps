package ca.vina.nexu.partage.securite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parité du jeton de session entre le backend Node et le backend Java.
 *
 * <p>Deuxième condition d'entrée de la migration, après les empreintes de mot de passe : pendant
 * l'étranglement, un même navigateur est servi par les deux backends. Un jeton posé par l'un doit
 * être lu par l'autre — sinon chaque bascule de module déconnecte l'utilisateur.
 *
 * <p>Les jetons de {@code jetons-jsonwebtoken.json} ont été émis par la bibliothèque du projet
 * Node. Ils sont la référence : le Java doit s'y conformer, jamais l'inverse.
 */
@DisplayName("Parité du jeton de session Node ↔ Java")
class PariteJetonTest {

    private static final String SECRET = "secret-de-test-parite-jwt-suffisamment-long-1234";
    /** Valeur par défaut du backend Node : 25 octets, soit 200 bits — sous le seuil HS256. */
    private static final String SECRET_TROP_COURT = "dev-only-secret-change-me";

    private final JetonDeSession jetons = new JetonDeSession(SECRET);

    record Vecteur(String libelle, String jeton, String sub, String username,
                   String role, String employeeId, String tenantId) {
        @Override
        public String toString() {
            return libelle;
        }
    }

    static List<Vecteur> vecteurs() throws Exception {
        try (InputStream flux = PariteJetonTest.class.getResourceAsStream("/jetons-jsonwebtoken.json")) {
            assertThat(flux).as("vecteurs de référence absents du classpath").isNotNull();
            JsonNode racine = new ObjectMapper().readTree(flux);
            List<Vecteur> liste = new ArrayList<>();
            for (JsonNode v : racine.get("vecteurs")) {
                JsonNode c = v.get("charge");
                liste.add(new Vecteur(
                        v.get("libelle").asText(),
                        v.get("jeton").asText(),
                        c.get("sub").asText(),
                        c.get("username").asText(),
                        c.get("role").asText(),
                        c.get("employeeId").isNull() ? null : c.get("employeeId").asText(),
                        c.get("tenantId").isNull() ? null : c.get("tenantId").asText()));
            }
            return liste;
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("vecteurs")
    @DisplayName("lit un jeton émis par le backend Node")
    void litUnJetonDuNode(Vecteur v) {
        JetonDeSession.Revendications r = jetons.lire(v.jeton());

        assertThat(r).as("le jeton du Node doit être accepté").isNotNull();
        assertThat(r.sub()).isEqualTo(v.sub());
        assertThat(r.username()).isEqualTo(v.username());
        assertThat(r.role()).isEqualTo(v.role());
        assertThat(r.employeeId()).isEqualTo(v.employeeId());
        // L'isolation multi-tenant tient à cette seule ligne : un tenant mal lu, et un
        // établissement voit les données d'un autre.
        assertThat(r.tenantId()).isEqualTo(v.tenantId());
    }

    @Test
    @DisplayName("préserve la distinction entre tenant nul et tenant renseigné")
    void distingueTenantNulEtRenseigne() throws Exception {
        // Le modèle de comptes utilise `null` pour les comptes globaux (superadmin) — ce n'est
        // pas la même chose qu'un tenant vide. Confondre les deux donnerait à un superadmin un
        // périmètre d'établissement, ou l'inverse.
        Vecteur global = vecteurs().stream()
                .filter(v -> v.libelle().equals("superadmin-global")).findFirst().orElseThrow();
        assertThat(jetons.lire(global.jeton()).tenantId()).isNull();
        assertThat(jetons.lire(global.jeton()).employeeId()).isNull();
    }

    @Test
    @DisplayName("rejette un jeton dont la signature a été altérée")
    void rejetteUnJetonAltere() throws Exception {
        String bon = vecteurs().getFirst().jeton();
        String[] parties = bon.split("\\.");

        // Charge utile modifiée — le rôle passe à superadmin — signature d'origine conservée.
        String charge = new String(Base64.getUrlDecoder().decode(parties[1]));
        String falsifiee = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(charge.replace("\"role\":\"admin\"", "\"role\":\"superadmin\"")
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(jetons.lire(parties[0] + "." + falsifiee + "." + parties[2]))
                .as("une élévation de privilège par réécriture du jeton doit être refusée")
                .isNull();
    }

    @Test
    @DisplayName("rejette un jeton signé avec un autre secret")
    void rejetteUnAutreSecret() throws Exception {
        // Autre secret, mais de longueur conforme : on teste ici le rejet de signature, pas la
        // validation de longueur — qui a son propre test.
        JetonDeSession autre = new JetonDeSession("un-tout-autre-secret-lui-aussi-assez-long-1234567");
        assertThat(autre.lire(vecteurs().getFirst().jeton())).isNull();
    }

    @Test
    @DisplayName("rejette un jeton expiré")
    void rejetteUnJetonExpire() throws Exception {
        // Les vecteurs de parité portent une validité longue **à dessein** : signés à 8 h comme
        // en production, ils cessaient d'être acceptés le lendemain, et le test échouait sans
        // qu'aucun code n'ait changé. La péremption a donc son propre vecteur, périmé pour de
        // bon — c'est lui, et lui seul, qui prouve qu'elle est appliquée.
        try (InputStream flux = PariteJetonTest.class.getResourceAsStream("/jetons-jsonwebtoken.json")) {
            String perime = new ObjectMapper().readTree(flux).get("jeton_perime").asText();
            assertThat(jetons.lire(perime)).isNull();
        }
    }

    @Test
    @DisplayName("rejette une chaîne qui n'est pas un jeton")
    void rejetteUneChaineQuelconque() {
        assertThat(jetons.lire("pas-un-jeton")).isNull();
        assertThat(jetons.lire("")).isNull();
    }

    @Test
    @DisplayName("refuse de démarrer sur un secret trop court pour HS256")
    void refuseUnSecretTropCourt() {
        // Découverte de la phase 2, et elle vaut au-delà de la migration : le backend Node signe
        // aujourd'hui avec un secret de 200 bits, alors que la RFC 7518 §3.2 en exige 256 pour
        // HS256. `jsonwebtoken` ne le vérifie pas, `jjwt` si. On refuse au **démarrage** — un
        // secret faible ne doit pas se découvrir en production sous forme de refus
        // d'authentification épars.
        assertThatThrownBy(() -> new JetonDeSession(SECRET_TROP_COURT))
                .isInstanceOf(JetonDeSession.SecretTropCourtException.class)
                .hasMessageContaining("200 bits")
                .hasMessageContaining("les deux backends");
    }

    @Test
    @DisplayName("émet un jeton que le Java relit — et au format du Node")
    void emetUnJetonRelisible() {
        // La parité doit valoir dans les deux sens : pendant l'étranglement, une session ouverte
        // côté Java sera présentée au Node, qui sert encore les autres modules.
        JetonDeSession.Revendications origine = new JetonDeSession.Revendications(
                "usr-9", "gestionnaire@@t-004", "manager", "emp_z", "t-004");

        String jeton = jetons.emettre(origine);
        assertThat(jeton.split("\\.")).as("un JWT compte trois parties").hasSize(3);

        String entete = new String(Base64.getUrlDecoder().decode(jeton.split("\\.")[0]));
        assertThat(entete).contains("\"alg\":\"HS256\"");

        assertThat(jetons.lire(jeton)).isEqualTo(origine);
    }
}
