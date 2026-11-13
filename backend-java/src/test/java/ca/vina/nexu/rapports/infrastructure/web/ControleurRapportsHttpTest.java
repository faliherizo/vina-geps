package ca.vina.nexu.rapports.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotRapportsEnregistres;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import ca.vina.nexu.rapports.infrastructure.persistance.MoteurRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/rapports} par l'API.
 *
 * <p>La règle qui compte ici est celle que le moteur porte et que l'API doit conserver : <b>une
 * source dont le module est coupé est refusée avant d'atteindre son fournisseur de faits</b>. Le domaine ne connaît
 * pas les modules de l'organisme, et c'est très bien ainsi — c'est le contrôleur qui le sait.
 */
@DisplayName("API rapports — sources filtrées par module, plan validé, export branché")
class ControleurRapportsHttpTest {

    private static final ContexteRequete COMPLET = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete RESTREINT = ClientHttpEssai.session("admin", "t-2");

    private ClientHttpEssai http;

    /** Des identifiants prévisibles : l'essai les relit. */
    static ca.vina.nexu.partage.identifiants.domaine.Sequence sequence() {
        java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
        return new ca.vina.nexu.partage.identifiants.domaine.Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + "-" + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "uuid-" + n.incrementAndGet();
            }

            @Override
            public String jetonSignature() {
                return "jeton";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return "secret";
            }
        };
    }

    private static final String PERIODE = "{\"champ\":\"jour\",\"operateur\":\"entre\",\"valeurs\":[\"2031-01-01\",\"2031-01-31\"]}";

    /** Une définition sur le comblement des quarts, période comprise ; {@code reste} complète l'objet. */
    private static String places(String colonnes, String reste) {
        return "{\"source\":\"effectifs_places\",\"colonnes\":[" + colonnes + "],\"filtres\":[" + PERIODE + "]" + reste + "}";
    }

    private static String placesFiltrees(String colonnes, String filtre) {
        return "{\"source\":\"effectifs_places\",\"colonnes\":[" + colonnes + "],\"filtres\":[" + PERIODE + "," + filtre + "]}";
    }

    /** Le fournisseur témoin : deux quarts, deux unités. */
    private static final FournisseurFaits FOURNISSEUR = new FournisseurFaits() {
        @Override
        public Set<String> sources() {
            return Set.of("effectifs_places");
        }

        @Override
        public List<Map<String, Object>> faits(String source, Demandeur demandeur, CatalogueRapports.Periode periode) {
            return List.of(Map.of("jour", "2031-01-06", "unite", "EST", "places", 4),
                    Map.of("jour", "2031-01-07", "unite", "OUEST", "places", 2));
        }
    };

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, country) VALUES ('t-1', 'Ville', 'CA'), ('t-2', 'Voisine', 'CA')")
                .update();
        // Les modules ouverts vivent dans les quotas : le contrôleur les lit par le même chemin
        // que la garde de module, pour qu'une source offerte corresponde à une route permise.
        // La colonne `modules` porte un **tableau JSON** : le dépôt des quotas la lit ainsi, et
        // une liste séparée par des virgules ferait échouer la lecture — donc toutes les sources.
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules)"
                + " VALUES ('t-1', 10, 10, '[\"effectifs_temps\",\"effectifs_presence\",\"effectifs_planification\",\"effectifs_absences\"]'),"
                + " ('t-2', 10, 10, '[\"effectifs_temps\"]')").update();

        http = new ClientHttpEssai(new ControleurRapports(new MoteurRapports(List.of(FOURNISSEUR)), new GardeAcces(new RegistrePolitiqueAcces()),
                new ControleurRapports.Ports(new DepotQuotas(jdbc), new DepotRapportsEnregistres(jdbc),
                        Clock.fixed(Instant.parse("2031-01-20T12:00:00Z"), ZoneOffset.UTC), sequence())));
    }

    @Test
    @DisplayName("les sources offertes suivent les modules ouverts de l'organisme, avec leur période et leur capacité")
    void sourcesFiltrees() throws Exception {
        http.get("/api/rapports/sources", COMPLET).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
        http.get("/api/rapports/sources", RESTREINT).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].code").value("effectifs_heures"))
                .andExpect(jsonPath("$[0].faits.periode").value("jour"))
                .andExpect(jsonPath("$[0].capacite").value("effectifs.manage"));
        http.get("/api/rapports/sources", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("les agrégats, opérateurs et formats **branchés** sont annoncés")
    void catalogues() throws Exception {
        http.get("/api/rapports/agregats", COMPLET).andExpect(jsonPath("$", hasSize(6))).andExpect(jsonPath("$[5]").value("taux"));
        http.get("/api/rapports/operateurs", COMPLET).andExpect(jsonPath("$", hasSize(7)));
        // `xlsx` et `pdf` sont produits par POI et PDFBox (P-RF-1) : annoncés parce que branchés.
        http.get("/api/rapports/formats", COMPLET).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0]").value("csv")).andExpect(jsonPath("$[1]").value("xlsx")).andExpect(jsonPath("$[2]").value("pdf"));
    }

    @Test
    @DisplayName("une définition se valide sans être exécutée, et un champ inconnu est nommé")
    void validation() throws Exception {
        http.post("/api/rapports/valider", COMPLET, places("{\"champ\":\"unite\"}", ",\"limite\":50"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limite").value(50));
        http.post("/api/rapports/valider", COMPLET, places("{\"champ\":\"salaire\"}", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("« salaire »")));
        // Un agrégat inconnu est converti **à la frontière** : un 400 nommé, jamais un 500.
        http.post("/api/rapports/valider", COMPLET, places("{\"champ\":\"places\",\"agregat\":\"mediane\"}", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("agrégat inconnu")));
        http.post("/api/rapports/valider", COMPLET, null).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("l'exécution rend les lignes du fournisseur, et refuse une source dont le module est coupé")
    void execution() throws Exception {
        http.post("/api/rapports/executer", COMPLET,
                        places("{\"champ\":\"unite\"},{\"champ\":\"places\"}", ",\"tri\":{\"champ\":\"unite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes", hasSize(2)))
                .andExpect(jsonPath("$.lignes[0].unite").value("EST"))
                .andExpect(jsonPath("$.tronque").value(false));

        http.post("/api/rapports/executer", RESTREINT, places("{\"champ\":\"unite\"}", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("n'est pas activé")));
    }

    @Test
    @DisplayName("la troncature est annoncée plutôt que silencieuse, et le filtre est appliqué")
    void troncatureEtFiltres() throws Exception {
        http.post("/api/rapports/executer", COMPLET, places("{\"champ\":\"unite\"}", ",\"limite\":1"))
                .andExpect(jsonPath("$.tronque").value(true)).andExpect(jsonPath("$.total").value(1));

        http.post("/api/rapports/executer", COMPLET, placesFiltrees("{\"champ\":\"unite\"}",
                        "{\"champ\":\"unite\",\"operateur\":\"contient\",\"valeurs\":[\"OUE\"]}"))
                .andExpect(jsonPath("$.lignes", hasSize(1)));
    }

    @Test
    @DisplayName("l'export rend un fichier nommé, et refuse nommément un format non branché")
    void export() throws Exception {
        String definition = places("{\"champ\":\"unite\"}", "");
        http.post("/api/rapports/exporter", COMPLET, "{\"definition\":" + definition + ",\"format\":\"csv\"}")
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Unité")));

        byte[] xlsx = http.post("/api/rapports/exporter", COMPLET, "{\"definition\":" + definition + ",\"format\":\"xlsx\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(xlsx, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        byte[] pdf = http.post("/api/rapports/exporter", COMPLET, "{\"definition\":" + definition + ",\"format\":\"pdf\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        http.post("/api/rapports/exporter", COMPLET, "{\"definition\":" + definition + ",\"format\":\"docx\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("format d'export inconnu : « docx »")));

        http.post("/api/rapports/exporter", RESTREINT, "{\"definition\":" + definition + "}")
                .andExpect(status().isBadRequest());
    }
}
