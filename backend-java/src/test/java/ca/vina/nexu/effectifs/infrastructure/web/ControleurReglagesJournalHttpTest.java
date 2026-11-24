package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReglagesJournal;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Le journal des consultations</strong> (addenda 8, M-10) : l'administration l'active ; une lecture réussie d'un
 * dossier ou d'un horaire entre alors au journal avec son lecteur ; rien avant l'activation, rien pour une lecture refusée,
 * une écriture ou un chemin qui n'est pas une consultation.
 */
@DisplayName("/api/effectifs/journal/reglages et le journal des consultations")
class ControleurReglagesJournalHttpTest {

    private static final String T = "t-1";
    private static final String API = "/api/effectifs/journal/reglages";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private JournalDesConsultations consultations;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, '[\"effectifs\"]')").param(T).update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        AtomicInteger n = new AtomicInteger();
        Sequence seq = new Sequence() {
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
                return "s";
            }
        };
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        DepotReglagesJournal reglages = new DepotReglagesJournal(jdbc, schema);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc), HORLOGE);
        http = new ClientHttpEssai(new ControleurReglagesJournal(garde, reglages, journal));
        consultations = new JournalDesConsultations(reglages, journal, HORLOGE);
    }

    private void lire(String methode, String chemin, int statut, ContexteRequete qui) {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, chemin);
        if (qui != null) {
            r.setAttribute(ContexteRequete.ATTRIBUT, qui);
        }
        MockHttpServletResponse rep = new MockHttpServletResponse();
        rep.setStatus(statut);
        consultations.afterCompletion(r, rep, new Object(), null);
    }

    private int consultationsAuJournal() {
        return jdbc.sql("SELECT COUNT(*) FROM eff_journal WHERE action = 'consultation'").query(Integer.class).single();
    }

    @Test
    @DisplayName("désactivé par défaut ; l'administration l'active ; chaque lecture réussie d'un dossier ou d'un horaire est consignée avec son lecteur")
    void consultations() throws Exception {
        http.get(API, ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.consultations").value(false));
        lire("GET", "/api/effectifs/planification/ressources/rs-1/competences", 200, CHEF);
        assertThat(consultationsAuJournal()).isZero();

        http.put(API, CHEF, "{\"consultations\":true}").andExpect(status().isForbidden());
        http.put(API, ADMIN, "{\"consultations\":\"oui\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« consultations » : vrai ou faux"));
        http.put(API, ADMIN, null).andExpect(status().is(422));
        http.put(API, ADMIN, "{\"consultations\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.consultations").value(true));
        http.get(API, ADMIN).andExpect(jsonPath("$.consultations").value(true));

        lire("GET", "/api/effectifs/planification/ressources/rs-1/competences", 200, CHEF);
        lire("GET", "/api/effectifs/planification/periodes/pr-1/equipages", 200, CHEF);
        lire("GET", "/api/effectifs/planification/periodes/pr-1/equipages", 403, CHEF);
        lire("PUT", "/api/effectifs/planification/ressources/rs-1/competences", 200, CHEF);
        lire("GET", "/api/effectifs/journal", 200, CHEF);
        lire("GET", "/api/effectifs/planification/ressources", 200, null);
        lire("GET", "/api/effectifs/planification/ressources", 200, new ContexteRequete("sa", "sa", "superadmin", null, null));
        assertThat(jdbc.sql("SELECT acteurId || ' ' || objetType || ' ' || objetId FROM eff_journal WHERE action = 'consultation' ORDER BY horodatage, id")
                .query(String.class).list()).containsExactlyInAnyOrder("usr-chef dossier rs-1", "usr-chef horaire pr-1");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_journal WHERE action = 'journal.reglages'").query(Integer.class).single()).isEqualTo(1);
    }
}
