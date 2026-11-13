package ca.vina.nexu.organisation.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.organisation.application.commandes.GestionnaireOrganisation;
import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import ca.vina.nexu.organisation.infrastructure.persistance.DepotOrganisation;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/organisation} au complet — jumeau de {@code backend/src/organisation/routes.ts}.
 *
 * <p>Le chaînage qui compte : une fusion, puis l'organigramme <strong>de la veille</strong> et
 * celui <strong>du jour</strong>. Une structure non datée les rendrait identiques, et la reddition
 * de l'exercice passé invérifiable.
 */
@DisplayName("API Organisation — organigramme à une date, réorganisations, filiation")
class ControleurOrganisationHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete VOISIN = ClientHttpEssai.session("admin", "t-2");
    private static final Pattern IDENTIFIANT = Pattern.compile("\"id\":\"(orgv_[a-z0-9]+)\"");

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        Clock horloge = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
        DepotOrganisation depot = new DepotOrganisation(jdbc);
        LectureOrganisation lecture = new LectureOrganisation(depot, horloge);
        // Des identifiants déterministes : un essai qui ne sait pas ce qu'il a créé ne peut pas
        // l'interroger ensuite, et un aléatoire non injecté rendrait la campagne irrejouable.
        AtomicInteger suite = new AtomicInteger();
        http = new ClientHttpEssai(new ControleurOrganisation(
                new GardeOrganisation(new GardeAcces(new RegistrePolitiqueAcces())), lecture,
                new GestionnaireOrganisation(depot, lecture, horloge,
                        () -> "orgv_%04d".formatted(suite.incrementAndGet()))));
    }

    /** Le premier identifiant créé par une réorganisation. */
    private static String premierCree(MvcResult r) throws Exception {
        Matcher m = IDENTIFIANT.matcher(r.getResponse().getContentAsString());
        if (!m.find()) {
            throw new IllegalStateException("aucune version créée dans " + r.getResponse().getContentAsString());
        }
        return m.group(1);
    }

    private String creer(String unite, String code, String parent, String effet) throws Exception {
        String cible = parent == null
                ? "{\"uniteId\":\"%s\",\"code\":\"%s\",\"libelle\":\"%s\"}".formatted(unite, code, code)
                : "{\"uniteId\":\"%s\",\"code\":\"%s\",\"libelle\":\"%s\",\"parentVersionId\":\"%s\"}"
                        .formatted(unite, code, code, parent);
        return premierCree(http.post("/api/organisation/reorganisations", ADMIN, """
                {"operation":"creation","effetLe":"%s","cibles":[%s],
                 "motif":"Mise en place de la structure initiale"}""".formatted(effet, cible))
                .andExpect(status().isCreated()).andReturn());
    }

    @Nested
    @DisplayName("La garde")
    class Garde {

        @Test
        @DisplayName("refuse sans session")
        void sansSession() throws Exception {
            http.get("/api/organisation/versions", null).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un employé lit l'organigramme : savoir de qui l'on relève n'est pas un privilège")
        void lectureOuverte() throws Exception {
            http.get("/api/organisation/organigramme", EMPLOYE).andExpect(status().isOk());
            http.get("/api/organisation/operations", EMPLOYE).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(4))
                    .andExpect(jsonPath("$[0]").value("creation"));
        }

        @Test
        @DisplayName("un employé ne réorganise pas")
        void ecritureReservee() throws Exception {
            http.post("/api/organisation/reorganisations", EMPLOYE, """
                    {"operation":"creation","effetLe":"2025-04-01",
                     "cibles":[{"uniteId":"u1","code":"MIN","libelle":"Ministère"}],
                     "motif":"Mise en place de la structure"}""")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.capability").value("accounting.govern"));
        }
    }

    @Nested
    @DisplayName("Les réorganisations")
    class Reorganisations {

        @Test
        @DisplayName("**une fusion change l'organigramme du jour, pas celui de la veille**")
        void fusionDatee() throws Exception {
            String min = creer("u1", "MIN", null, "2020-04-01");
            String dg = creer("u2", "DG", min, "2020-04-01");
            String df = creer("u3", "DF", min, "2020-04-01");

            http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"fusion","effetLe":"2025-04-01","sources":["%s","%s"],
                     "cibles":[{"uniteId":"u4","code":"DGF","libelle":"Direction générale et finances","parentVersionId":"%s"}],
                     "motif":"Fusion de la direction générale et de la direction des finances"}"""
                    .formatted(dg, df, min))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.operation").value("fusion"))
                    .andExpect(jsonPath("$.terminees.length()").value(2))
                    .andExpect(jsonPath("$.creees[0].valideDu").value("2025-04-01"))
                    .andExpect(jsonPath("$.creees[0].operation").value("fusion"))
                    .andExpect(jsonPath("$.motifs[0]").value(
                            "Fusion au 2025-04-01 : 2 version(s) terminée(s) le 2025-03-31, 1 créée(s)"));

            http.get("/api/organisation/organigramme?le=2025-03-31", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(3))
                    .andExpect(jsonPath("$[1].chemin").value("MIN › DF"));
            http.get("/api/organisation/organigramme?le=2025-04-01", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[1].chemin").value("MIN › Direction générale et finances"));
        }

        @Test
        @DisplayName("sans date, l'organigramme est celui du jour de l'horloge injectée")
        void sansDate() throws Exception {
            creer("u1", "MIN", null, "2020-04-01");
            http.get("/api/organisation/organigramme", ADMIN).andExpect(jsonPath("$.length()").value(1));
            // Une unité qui ne naît qu'en 2027 n'apparaît pas dans l'organigramme de 2026.
            creer("u2", "FUTURE", null, "2027-04-01");
            http.get("/api/organisation/organigramme", ADMIN).andExpect(jsonPath("$.length()").value(1));
        }

        @Test
        @DisplayName("refuse en 400 nommé une forme qui ne correspond pas à l'opération")
        void formeFausse() throws Exception {
            String min = creer("u1", "MIN", null, "2020-04-01");
            http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"fusion","effetLe":"2025-04-01","sources":["%s"],
                     "cibles":[{"uniteId":"u4","code":"X","libelle":"X"}],
                     "motif":"Un motif suffisamment long"}""".formatted(min))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            "une fusion attend au moins deux sources et une cible ; reçu 1 source(s) et 1 cible(s)"));
        }

        @Test
        @DisplayName("refuse un motif trop court, une opération inconnue et un corps vide")
        void refusDeSaisie() throws Exception {
            String min = creer("u1", "MIN", null, "2020-04-01");
            http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"suppression","effetLe":"2025-04-01","sources":["%s"],"motif":"court"}"""
                    .formatted(min))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            org.hamcrest.Matchers.containsString("au moins dix caractères")));
            http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"deplacement","effetLe":"2025-04-01","motif":"Un motif assez long"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value(
                            org.hamcrest.Matchers.containsString("opération de réorganisation inconnue")));
            http.post("/api/organisation/reorganisations", ADMIN, "{}").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("**n'écrit rien** quand la réorganisation est refusée")
        void refusSansEcriture() throws Exception {
            creer("u1", "MIN", null, "2020-04-01");
            http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"suppression","effetLe":"2025-04-01","sources":["inexistante"],
                     "motif":"Un motif suffisamment long"}""")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("version introuvable : inexistante"));
            http.get("/api/organisation/versions", ADMIN).andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("L'imprévu")
    class Imprevu {

        private ClientHttpEssai avecDepotFautif(RuntimeException faute) {
            ca.vina.nexu.organisation.domaine.DepotOrganisationPort depot =
                    new ca.vina.nexu.organisation.domaine.DepotOrganisationPort() {
                        @Override
                        public java.util.List<ca.vina.nexu.organisation.domaine.Organisation.VersionUnite> lister(String t) {
                            throw faute;
                        }

                        @Override
                        public void appliquer(String t,
                                              ca.vina.nexu.organisation.domaine.Organisation.ResultatReorganisation r,
                                              String motif, String auteurId, String creeLe) {
                            throw faute;
                        }
                    };
            Clock horloge = Clock.fixed(Instant.parse("2026-09-29T12:00:00Z"), ZoneOffset.UTC);
            LectureOrganisation lecture = new LectureOrganisation(depot, horloge);
            return new ClientHttpEssai(new ControleurOrganisation(
                    new GardeOrganisation(new GardeAcces(new RegistrePolitiqueAcces())), lecture,
                    new GestionnaireOrganisation(depot, lecture, horloge, () -> "orgv_essai")));
        }

        @Test
        @DisplayName("une panne du dépôt devient **400 avec son motif**, jamais un 500 muet")
        void panneNommee() throws Exception {
            avecDepotFautif(new IllegalStateException("base verrouillée"))
                    .get("/api/organisation/versions", ADMIN).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("base verrouillée"));
        }

        @Test
        @DisplayName("une panne **sans message** se dit quand même")
        void panneSansMessage() throws Exception {
            avecDepotFautif(new IllegalStateException())
                    .get("/api/organisation/organigramme", ADMIN).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0]").value("erreur inattendue"));
        }
    }

    @Nested
    @DisplayName("L'histoire d'une unité")
    class Histoire {

        @Test
        @DisplayName("l'historique rend les versions successives, et la filiation remonte la fusion")
        void historiqueEtFiliation() throws Exception {
            String dg = creer("u2", "DG", null, "2020-04-01");
            String df = creer("u3", "DF", null, "2020-04-01");
            String fusion = premierCree(http.post("/api/organisation/reorganisations", ADMIN, """
                    {"operation":"fusion","effetLe":"2025-04-01","sources":["%s","%s"],
                     "cibles":[{"uniteId":"u4","code":"DGF","libelle":"DGF"}],
                     "motif":"Fusion des deux directions"}""".formatted(dg, df))
                    .andExpect(status().isCreated()).andReturn());

            http.get("/api/organisation/unites/u3/historique", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].valideAu").value("2025-03-31"))
                    .andExpect(jsonPath("$[0].motif").value("Mise en place de la structure initiale"));

            http.get("/api/organisation/versions/" + fusion + "/filiation", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("DGF"))
                    .andExpect(jsonPath("$.ascendants.length()").value(2))
                    .andExpect(jsonPath("$.ascendants[0].code").value("DG"))
                    .andExpect(jsonPath("$.ascendants[0].operation").value("creation"));
        }

        @Test
        @DisplayName("répond 404 pour une version inconnue, et une liste vide pour une unité inconnue")
        void inconnus() throws Exception {
            creer("u1", "MIN", null, "2020-04-01");
            http.get("/api/organisation/versions/inexistante/filiation", ADMIN)
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errors[0]").value("version introuvable : inexistante"));
            http.get("/api/organisation/unites/u9/historique", ADMIN).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }

        @Test
        @DisplayName("la structure d'un établissement ne se voit pas chez le voisin")
        void cloisonnement() throws Exception {
            creer("u1", "MIN", null, "2020-04-01");
            http.get("/api/organisation/versions", VOISIN).andExpect(jsonPath("$.length()").value(0));
        }
    }
}
