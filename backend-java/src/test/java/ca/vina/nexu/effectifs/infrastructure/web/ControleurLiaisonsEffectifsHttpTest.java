package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAlignementOrganigramme;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureAlignementOrganigramme;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurOrganigrammePourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.organisation.application.commandes.GestionnaireOrganisation;
import ca.vina.nexu.organisation.application.requetes.LectureOrganisation;
import ca.vina.nexu.organisation.infrastructure.persistance.DepotOrganisation;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.hamcrest.Matchers;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Liaison unités ↔ organigramme par HTTP</strong> (lot R13, jumeau de
 * {@code effectifs-liaison-organisation.integration.test.ts}) : l'organigramme de l'organisme crée ses unités ;
 * l'administration des effectifs en voit le plan, puis l'aligne — les unités suivent, avec leur historique.
 * Horloge figée au 15 janvier 2027.
 */
@DisplayName("/api/effectifs/liaisons/organisation — aperçu et alignement des unités")
class ControleurLiaisonsEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String L = "/api/effectifs/liaisons/organisation";
    private static final String AUJOURDHUI = "2027-01-15";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;

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
                return "secret-" + octets;
            }
        };
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, "[\"personnel\",\"effectifs\"]").update();
        Clock horloge = Clock.fixed(Instant.parse(AUJOURDHUI + "T10:00:00Z"), ZoneOffset.UTC);
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMil = new DepotMilieux(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMil, perimetres);
        GestionnaireReferentiels gestionRef = new GestionnaireReferentiels(depotRef, referentiels, journal, seq);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc), horloge);
        ControleurSocleEffectifs socle = new ControleurSocleEffectifs(garde, referentiels, gestionRef, perimetres,
                new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMil, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres));

        DepotOrganisation depotOrg = new DepotOrganisation(jdbc);
        LectureOrganisation lectureOrg = new LectureOrganisation(depotOrg, horloge);
        AtomicInteger n = new AtomicInteger();
        GestionnaireOrganisation organisation = new GestionnaireOrganisation(depotOrg, lectureOrg, horloge, () -> "orgv-" + n.incrementAndGet());
        LectureAlignementOrganigramme lecture = new LectureAlignementOrganigramme(new AdaptateurOrganigrammePourEffectifs(lectureOrg), referentiels);
        http = new ClientHttpEssai(socle, new ControleurLiaisonsEffectifs(garde, lecture, new GestionnaireAlignementOrganigramme(lecture, gestionRef)));

        String us = organisation.appliquer(T, Map.of("operation", "creation", "effetLe", "2026-01-01", "motif", "structure initiale",
                "cibles", List.of(Map.of("uniteId", "u-us", "code", "US", "libelle", "Urgences-santé"))), "admin").creees().get(0).id();
        organisation.appliquer(T, Map.of("operation", "creation", "effetLe", "2026-01-01", "motif", "opérations",
                "cibles", List.of(Map.of("uniteId", "u-est", "code", "EST", "libelle", "Opérations Est", "parentVersionId", us))), "admin");
        http.post("/api/effectifs/referentiels/unites", ADMIN, "{\"code\":\"EST\",\"libelleFr\":\"Est\",\"effectifDepuis\":\"2026-01-01\"}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("montre le plan, puis aligne : l'unité manquante est créée, l'autre remplacée à la date")
    void aligne() throws Exception {
        http.get(L, ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.date").value(AUJOURDHUI))
                .andExpect(jsonPath("$.creer[*].code", Matchers.contains("US"))).andExpect(jsonPath("$.modifier[0].code").value("EST"))
                .andExpect(jsonPath("$.modifier[0].parent").value("US")).andExpect(jsonPath("$.modifier[0].avant.parent").doesNotExist())
                .andExpect(jsonPath("$.modifier[0].avant.libelle").value("Est"));
        http.get(L + "?date=2020-01-01", ADMIN).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("ne se fait pas dans le passé")));
        http.post(L, ADMIN, "").andExpect(content().json("{\"date\":\"" + AUJOURDHUI + "\",\"creees\":1,\"modifiees\":1,\"ignorees\":0}", true));
        http.get("/api/effectifs/referentiels/unites", ADMIN).andExpect(jsonPath("$[?(@.code == 'EST')].libelleFr", Matchers.contains("Opérations Est")))
                .andExpect(jsonPath("$[?(@.code == 'EST')].attributs.parent", Matchers.contains("US")));
        http.get(L + "?date=", ADMIN).andExpect(jsonPath("$.identiques", Matchers.containsInAnyOrder("EST", "US")));
        http.post(L, ADMIN, "{\"date\":\"2027-02-01\"}").andExpect(content().json("{\"date\":\"2027-02-01\",\"creees\":0,\"modifiees\":0,\"ignorees\":0}", true));
        http.post(L, ADMIN, "{\"date\":\"abc\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« date » : date AAAA-MM-JJ attendue"));
    }

    @Test
    @DisplayName("garde : réservé à effectifs.configure, et au module effectifs")
    void gardes() throws Exception {
        http.get(L, new ContexteRequete("usr-m", "m", "manager", null, T)).andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.post(L, ADMIN, "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'effectifs' non activé pour cet organisme"));
    }
}
