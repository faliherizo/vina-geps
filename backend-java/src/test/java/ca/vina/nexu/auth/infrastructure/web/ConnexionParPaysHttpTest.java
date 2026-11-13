package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotAudit;
import ca.vina.nexu.auth.infrastructure.persistance.DepotCatalogueQuestions;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEmprunts;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEtablissementsConnexion;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.organisme.application.commandes.DeclencheurAlertes;
import ca.vina.nexu.organisme.application.requetes.GardeConnexionPays;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotJournalAlertes;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotPaysAutorises;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotReglesAlerte;
import ca.vina.nexu.organisme.infrastructure.persistance.SchemaOrganisme;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.securite.domaine.PareFeu;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>La connexion se juge au pays</strong> (lot R11, P-TN-11) — la part « connexion » de
 * {@code organisme-options.integration.test.ts} : Julie se connecte du Canada, puis de France (refusée, auditée,
 * et la sécurité reçoit l'alerte), puis sans pays connu ; un compte global n'est pas concerné ; lever la
 * restriction rouvre la connexion.
 */
@DisplayName("POST /api/auth/login — restriction de pays de l'organisme")
class ConnexionParPaysHttpTest {

    private static final String SECRET = "secret-d-essai-de-trente-deux-octets-au-moins";
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String MDP = "Passe!Test2026";
    private static final Modification PAR_DIR = new Modification("2031-01-01T00:00:00.000Z", "usr-dir");

    private JdbcClient jdbc;
    private MockMvc mvc;
    private DepotPaysAutorises pays;
    private CourrierJournal courrier;

    private static Sequence sequence() {
        AtomicInteger n = new AtomicInteger();
        return new Sequence() {
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

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        DepotComptes comptes = new DepotComptes(jdbc, HORLOGE);
        JetonDeSession jetons = new JetonDeSession(SECRET);
        DepotQuestionsSecretes questions = new DepotQuestionsSecretes(jdbc, HORLOGE);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        PareFeu pareFeu = new PareFeu(new PareFeu.Reglages(PareFeu.Mode.BLOQUER, 0, 100, 15), HORLOGE::millis, (a, d) -> { });
        SchemaOrganisme schema = new SchemaOrganisme(jdbc);
        pays = new DepotPaysAutorises(jdbc, schema);
        DepotReglesAlerte regles = new DepotReglesAlerte(jdbc, schema);
        courrier = new CourrierJournal(HORLOGE);
        GardeConnexionPays garde = new GardeConnexionPays(pays, new DeclencheurAlertes(regles, new DepotJournalAlertes(jdbc, schema), courrier, sequence()),
                new ReglagesPays("X-Pays"), HORLOGE);
        ControleurAuth controleur = new ControleurAuth(comptes, questions, new DepotRolesEtablissement(jdbc, acces, HORLOGE), new DepotCatalogueQuestions(jdbc, HORLOGE),
                new DepotAudit(jdbc, new JournalAudit(jdbc), HORLOGE), new DepotEtablissementsConnexion(jdbc), new DepotQuotas(jdbc), new JournalAudit(jdbc),
                new GardeAcces(acces), HORLOGE, pareFeu, new ServiceConnexion(comptes, questions, jetons, HORLOGE), false, HoteEtablissement.selecteur(),
                new DepotEmprunts(jdbc, HORLOGE, comptes, new JournalAudit(jdbc), jetons), garde::refus);
        mvc = MockMvcBuilders.standaloneSetup(controleur).build();
        jdbc.sql("INSERT INTO tenants (id, name, slug, country, status, createdAt) VALUES ('T1', 'Urgences', 'urgences', 'CA', 'active', '2026-02-01T00:00:00.000Z')").update();
        comptes.creer(new DepotComptes.Creation("admin", "admin", "admin", null, null, false, false, null, false));
        comptes.creer(new DepotComptes.Creation("julie", MDP, "employee", null, "T1", false, false, null, false));
        regles.inserer("T1", new RegleAlerte("r-1", EvenementAlerte.CONNEXION_REFUSEE_PAYS, List.of("securite@urgences-sante.ca"), "2031-01-01T00:00:00.000Z", "usr-dir"));
        pays.enregistrer("T1", List.of("CA"), PAR_DIR);
    }

    private ResultActions connexion(String utilisateur, String motDePasse, String tenantId, String paysDOrigine) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"" + utilisateur + "\",\"password\":\"" + motDePasse + "\"" + (tenantId == null ? "" : ",\"tenantId\":\"" + tenantId + "\"") + "}");
        if (paysDOrigine != null) {
            b.header("X-Pays", paysDOrigine);
        }
        return mvc.perform(b);
    }

    private int nombreAudit(String action) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = ?").params(action).query(Integer.class).single();
    }

    @Test
    @DisplayName("Canada admis, France refusée, auditée et signalée, pays inconnu refusé ; lever la restriction rouvre la connexion")
    void connexionJugeeAuPays() throws Exception {
        connexion("julie", MDP, "T1", "CA").andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        connexion("julie", MDP, "T1", "FR").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("connexion refusée depuis ce pays (FR) : cet organisme ne l'autorise pas"));
        assertThat(nombreAudit("auth.login.pays_refuse")).isEqualTo(1);
        assertThat(courrier.envoyes()).hasSize(1);
        assertThat(courrier.envoyes().get(0).to()).isEqualTo("securite@urgences-sante.ca");
        assertThat(courrier.envoyes().get(0).text()).contains("Qui : julie").contains("Quoi : connexion depuis FR");
        connexion("julie", MDP, "T1", null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value(containsString("le pays d'origine n'a pas pu être établi")));
        // Le refus précède la vérification du mot de passe : un mauvais mot de passe hors pays ne dit rien de plus.
        connexion("julie", "faux", "T1", "FR").andExpect(status().isForbidden());
        pays.enregistrer("T1", List.of(), PAR_DIR);
        connexion("julie", MDP, "T1", "FR").andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("un compte global (sans organisme) n'est pas concerné par la restriction d'un organisme")
    void compteGlobal() throws Exception {
        connexion("admin", "admin", null, "FR").andExpect(status().isOk()).andExpect(jsonPath("$.token").exists());
        assertThat(nombreAudit("auth.login.pays_refuse")).isZero();
        assertThat(courrier.envoyes()).isEmpty();
    }
}
