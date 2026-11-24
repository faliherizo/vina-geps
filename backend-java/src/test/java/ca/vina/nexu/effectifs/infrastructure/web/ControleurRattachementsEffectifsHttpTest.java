package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRattachements;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRattachements;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Une personne dans deux unités</strong> (addenda 8, M-09) : le chef d'EST rattache Julie à OUEST pour un
 * trimestre ; pendant ce temps, Julie figure parmi les ressources d'OUEST, et le chef d'OUEST la voit ; le chef d'OUEST
 * ne peut pas la rattacher lui-même ; le rattachement se retire.
 */
@DisplayName("/api/effectifs/planification/ressources/{id}/rattachements")
class ControleurRattachementsEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String CREE = "2026-01-01T00:00:00Z";
    private static final String API = "/api/effectifs/planification/ressources/rs-julie/rattachements";
    private static final ContexteRequete CHEF_EST = new ContexteRequete("usr-est", "est", "manager", null, T);
    private static final ContexteRequete CHEF_OUEST = new ContexteRequete("usr-ouest", "ouest", "manager", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private LectureRessources ressources;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, '[\"effectifs\",\"effectifs_planification\"]')").param(T).update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        AtomicInteger n = new AtomicInteger();
        Sequence seq = new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + "-" + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "u-" + n.incrementAndGet();
            }

            @Override
            public String jetonSignature() {
                return "j";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return "s";
            }
        };
        LectureReferentiels referentiels = new LectureReferentiels(new DepotReferentiels(jdbc, schema));
        LecturePerimetres perimetres = new LecturePerimetres(new DepotPerimetres(jdbc, schema), referentiels);
        DepotRattachements depot = new DepotRattachements(jdbc, schema);
        ressources = new LectureRessources(new DepotRessources(jdbc, new SchemaPlanification(jdbc)), perimetres, depot);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse("2027-02-15T10:00:00Z"), ZoneOffset.UTC));
        http = new ClientHttpEssai(new ControleurRattachementsEffectifs(garde, new GestionnaireRattachements(depot,
                new GestionnaireRattachements.Lectures(ressources, referentiels, perimetres), new DepotJournalEffectifs(jdbc, seq, schema))));
        for (String u : new String[]{"EST", "OUEST"}) {
            jdbc.sql("INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, attributs, effectifDepuis, creeLe, creePar) VALUES (?, ?, 'unites', ?, ?, '{}', '2026-01-01', ?, 'adm')")
                    .params("r-" + u, T, u, u, CREE).update();
        }
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, creeLe) VALUES ('rs-julie', ?, 'P1', 'Julie Roy', 'usr-julie', 'EST', 'PARA', ?)")
                .params(T, CREE).update();
        jdbc.sql("INSERT INTO eff_perimetres (id, tenantId, utilisateurId, uniteCode, etendue, niveau, depuis, creeLe) VALUES "
                + "('p1', ?, 'usr-est', 'EST', 'UNITE', 'GESTION', '2026-01-01', ?), ('p2', ?, 'usr-ouest', 'OUEST', 'UNITE', 'GESTION', '2026-01-01', ?)")
                .params(T, CREE, T, CREE).update();
    }

    private java.util.List<String> ressourcesVues(String acteur, String unite) {
        return ressources.lister(new Contexte(T, acteur, "manager", "2027-02-15T10:00:00Z"), unite, null).stream().map(Ressource::nom).toList();
    }

    @Test
    @DisplayName("rattachée pour un trimestre, Julie compte dans OUEST ; le chef d'OUEST la voit ; le rattachement se retire")
    void parcours() throws Exception {
        assertThat(ressourcesVues("usr-ouest", null)).isEmpty();
        http.post(API, CHEF_OUEST, "{\"uniteCode\":\"OUEST\",\"depuis\":\"2027-01-01\"}").andExpect(status().isForbidden());
        http.post(API, CHEF_EST, "{\"uniteCode\":\"OUEST\",\"depuis\":\"2027-01-01\",\"jusqua\":\"2027-03-31\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.uniteCode").value("OUEST")).andExpect(jsonPath("$.actif").value(true));
        http.post(API, CHEF_EST, "{\"uniteCode\":\"OUEST\",\"depuis\":\"2027-03-01\"}").andExpect(status().isConflict());
        http.post(API, CHEF_EST, "{\"uniteCode\":\"NULLE\",\"depuis\":\"2027-04-01\"}").andExpect(status().is4xxClientError());
        http.post(API, CHEF_EST, "{\"uniteCode\":\"EST\",\"depuis\":\"2027-04-01\"}").andExpect(status().is(422));
        http.post(API, CHEF_EST, null).andExpect(status().is(422));
        http.get(API, CHEF_EST).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(1))).andExpect(jsonPath("$[0].jusqua").value("2027-03-31"));

        assertThat(ressourcesVues("usr-ouest", null)).containsExactly("Julie Roy");
        assertThat(ressourcesVues("usr-ouest", "OUEST")).containsExactly("Julie Roy");
        assertThat(ressourcesVues("usr-est", "EST")).containsExactly("Julie Roy");
        assertThat(ressourcesVues("usr-est", "NORD")).isEmpty();

        http.delete(API + "/OUEST?depuis=2027-01-01", CHEF_OUEST).andExpect(status().isForbidden());
        http.delete(API + "/OUEST?depuis=2027-01-01", CHEF_EST).andExpect(status().isOk()).andExpect(jsonPath("$.retire").value("OUEST"));
        http.delete(API + "/OUEST?depuis=2027-01-01", CHEF_EST).andExpect(status().isNotFound());
        http.delete(API + "/OUEST", CHEF_EST).andExpect(status().is(422));
        assertThat(ressourcesVues("usr-ouest", null)).isEmpty();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_journal WHERE action IN ('rattachement.ajoute', 'rattachement.retire')").query(Integer.class).single()).isEqualTo(2);
    }
}
