package ca.vina.nexu.integrations.domaine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Équivalence du module « intégrations » avec le backend Node.
 *
 * <p>Les sorties attendues ne sont pas transcrites : elles sont <strong>produites par le code en
 * service</strong> puis figées dans {@code integrations-node.json} (voir le script d'extraction).
 * Écrire à la main ce que rend {@code toXml} reviendrait à tester ma lecture du JavaScript.
 *
 * <p>Ce que ces vecteurs attrapent, et qu'aucune relecture n'aurait vu : {@code String(20.0)}
 * rend « 20 » alors que Java rend « 20.0 » ; un objet imbriqué devient « [object Object] » ; un
 * tableau vide devient une chaîne vide ; et une liste d'enregistrements vide produit une ligne
 * blanche entre les balises racine.
 */
@DisplayName("Intégrations — équivalence avec le backend Node")
class EquivalenceIntegrationsTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode vecteurs() throws Exception {
        try (InputStream flux = EquivalenceIntegrationsTest.class.getResourceAsStream("/integrations-node.json")) {
            assertThat(flux).as("vecteurs de référence absents du classpath").isNotNull();
            return MAPPER.readTree(flux);
        }
    }

    private static List<Object> casDe(String section) throws Exception {
        List<Object> liste = new ArrayList<>();
        vecteurs().get(section).forEach(liste::add);
        return liste;
    }

    /** Conversion fidèle : l'ordre d'insertion est du contrat, les entiers restent entiers. */
    @SuppressWarnings("unchecked")
    private static Object valeur(JsonNode n) {
        if (n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            return n.asText();
        }
        if (n.isBoolean()) {
            return n.asBoolean();
        }
        if (n.isIntegralNumber()) {
            return n.asLong();
        }
        if (n.isNumber()) {
            return n.asDouble();
        }
        if (n.isArray()) {
            List<Object> l = new ArrayList<>();
            n.forEach(e -> l.add(valeur(e)));
            return l;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        n.properties().forEach(e -> m.put(e.getKey(), valeur(e.getValue())));
        return m;
    }

    // ── Export XML ──────────────────────────────────────────────────────────

    static List<Object> xml() throws Exception {
        return casDe("xml");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("xml")
    @DisplayName("XML identique à celui du Node, octet pour octet")
    @SuppressWarnings("unchecked")
    void xmlIdentique(JsonNode cas) {
        List<Map<String, Object>> enregistrements = new ArrayList<>();
        cas.get("records").forEach(r -> enregistrements.add((Map<String, Object>) valeur(r)));

        String produit = SerialisationXml.versXml(enregistrements,
                cas.get("root").asText(), cas.get("item").asText());

        assertThat(produit).as(cas.get("libelle").asText()).isEqualTo(cas.get("attendu").asText());
    }

    // ── SSO → rôle ──────────────────────────────────────────────────────────

    static List<Object> role() throws Exception {
        return casDe("role");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("role")
    @DisplayName("rôle résolu comme par le Node")
    @SuppressWarnings("unchecked")
    void roleIdentique(JsonNode cas) {
        Map<String, Object> revendications = (Map<String, Object>) valeur(cas.get("claims"));
        JsonNode m = cas.get("mapping");

        Map<String, String> table = new LinkedHashMap<>();
        m.get("map").properties().forEach(e -> table.put(e.getKey(), e.getValue().asText()));
        String repli = m.has("fallback") ? m.get("fallback").asText() : null;

        String produit = MappageRole.resoudre(revendications, new MappageRole.Regle(
                m.get("claim").asText(), table, repli));

        JsonNode attendu = cas.get("attendu");
        if (attendu.isNull()) {
            assertThat(produit).as(cas.get("libelle").asText()).isNull();
        } else {
            assertThat(produit).as(cas.get("libelle").asText()).isEqualTo(attendu.asText());
        }
    }

    // ── Courriel entrant ────────────────────────────────────────────────────

    static List<Object> courriel() throws Exception {
        return casDe("courriel");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("courriel")
    @DisplayName("courriel routé comme par le Node")
    void courrielIdentique(JsonNode cas) {
        JsonNode e = cas.get("email");
        List<RoutageCourriel.Route> routes = new ArrayList<>();
        cas.get("routes").forEach(r -> routes.add(new RoutageCourriel.Route(
                r.get("subjectPattern").asText(), r.get("action").asText())));

        RoutageCourriel.Resultat produit = RoutageCourriel.router(
                new RoutageCourriel.Courriel(e.get("from").asText(), e.get("subject").asText(),
                        e.get("body").asText()),
                routes);

        JsonNode attendu = cas.get("attendu");
        String libelle = cas.get("libelle").asText();
        if (attendu.get("action").isNull()) {
            assertThat(produit.action()).as(libelle).isNull();
            assertThat(produit.matchedPattern()).as(libelle).isNull();
        } else {
            assertThat(produit.action()).as(libelle).isEqualTo(attendu.get("action").asText());
            assertThat(produit.matchedPattern()).as(libelle)
                    .isEqualTo(attendu.get("matchedPattern").asText());
        }
    }

    // ── Portées ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("les portées : défaut « read », inconnues écartées, doublons supprimés")
    void assainissementDesPortees() {
        assertThat(ClientApi.assainirPortees(null)).containsExactly("read");
        assertThat(ClientApi.assainirPortees(List.of())).containsExactly("read");
        assertThat(ClientApi.assainirPortees(List.of("write"))).containsExactly("write");
        assertThat(ClientApi.assainirPortees(List.of("write", "read", "write")))
                .containsExactly("write", "read");
        // Une portée inconnue est écartée en silence, comme côté Node…
        assertThat(ClientApi.assainirPortees(List.of("admin", "read"))).containsExactly("read");
    }

    @Test
    @DisplayName("une demande sans aucune portée valide est refusée, pas ramenée au défaut")
    void aucunePorteeValide() {
        // …mais si *aucune* ne survit au filtrage, la demande est refusée. Retomber sur « read »
        // accorderait un accès en lecture à un client qui n'a jamais demandé de portée connue.
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> ClientApi.assainirPortees(List.of("admin", "1")))
                .isInstanceOf(ClientApi.PorteeInvalideException.class)
                .hasMessage("at least one valid scope required (read|write)");
    }

    // ── Bornes non couvertes par les vecteurs ───────────────────────────────

    @Test
    @DisplayName("un tableau contenant un élément nul rend une chaîne vide pour cet élément")
    void tableauAvecElementNul() {
        // `String([null, 1])` rend « ,1 » en JavaScript : l'élément nul devient vide, il ne
        // disparaît pas. Le séparateur reste, et le partenaire compte les champs.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tab", java.util.Arrays.asList(null, 1L));
        assertThat(SerialisationXml.versXml(List.of(r), "records", "record"))
                .contains("<tab>,1</tab>");
    }

    @Test
    @DisplayName("un entier au-delà de 10²¹ garde la notation de Java")
    void tresGrandEntier() {
        // Limite assumée du portage : JavaScript écrirait « 1e+21 ». Aucun document JSON reçu ne
        // porte un tel nombre — il est signalé ici plutôt que traité à l'aveugle.
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("n", 1e21);
        assertThat(SerialisationXml.versXml(List.of(r), "records", "record")).contains("<n>1.0E21</n>");
    }

    @Test
    @DisplayName("une table de mappage absente ne fait pas échouer la résolution")
    void mappageSansTable() {
        assertThat(MappageRole.resoudre(Map.of("roles", "X"),
                new MappageRole.Regle("roles", null, "employee"))).isEqualTo("employee");
    }

    @Test
    @DisplayName("un courriel sans sujet ne correspond à aucune route")
    void courrielSansSujet() {
        // Un sujet nul ne doit pas lever : un serveur de messagerie peut livrer un message sans
        // sujet, et le flux ne doit pas s\u0027interrompre pour autant.
        RoutageCourriel.Resultat r = RoutageCourriel.router(
                new RoutageCourriel.Courriel("a@b.c", null, ""),
                List.of(new RoutageCourriel.Route("facture", "facture")));
        assertThat(r.action()).isNull();
    }
}
