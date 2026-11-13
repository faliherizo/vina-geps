package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.PromotionConfiguration;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-TN-1 de bout en bout : la configuration montée dans l'environnement de tests (organisme
 * « essai ») s'exporte, se simule puis s'applique dans celui de production (organisme « prod ») ;
 * réimportée, elle ne change plus rien ; modifiée à la source, elle se remplace à la date dite.
 * L'annulation d'un import interrompu tient au {@code @Transactional} du contrôleur, que seul le
 * contexte Spring applique : elle n'est pas éprouvée ici, où le contrôleur est monté seul.
 */
@DisplayName("/api/effectifs/configuration — promouvoir la configuration d'un environnement à l'autre (P-TN-1)")
class ControleurConfigurationEffectifsHttpTest {

    private static final String P = "/api/effectifs/configuration/paquet";
    private static final String MAINTENANT = "2027-01-10T08:00:00Z";
    private static final ContexteRequete ADMIN_ESSAI = ClientHttpEssai.session("admin", "essai");
    private static final ContexteRequete ADMIN_PROD = ClientHttpEssai.session("admin", "prod");
    private static final ContexteRequete CHEF_PROD = new ContexteRequete("usr-chef", "chef", "manager", null, "prod");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireReferentiels referentiels;
    private GestionnaireRegles regles;

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

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        for (String t : new String[] {"essai", "prod"}) {
            jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)")
                    .params(t, "[\"personnel\",\"effectifs\",\"effectifs_regles\"]").update();
        }
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        LectureReferentiels lectureRef = new LectureReferentiels(depotRef);
        DepotRegles depotRegles = new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc));
        LectureRegles lectureRegles = new LectureRegles(depotRegles);
        referentiels = new GestionnaireReferentiels(depotRef, lectureRef, journal, seq);
        regles = new GestionnaireRegles(depotRegles, lectureRegles, lectureRef, journal, seq);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse(MAINTENANT), ZoneOffset.UTC));
        http = new ClientHttpEssai(new ControleurConfigurationEffectifs(garde, new PromotionConfiguration(lectureRef, referentiels, lectureRegles, regles)));
        Contexte essai = new Contexte("essai", "admin-essai", "admin", "2027-01-01T00:00:00Z");
        referentiels.creer(essai, "unites", m("code", "US", "libelleFr", "Urgences-santé", "effectifDepuis", "2027-01-01"));
        referentiels.creer(essai, "unites", m("code", "EST", "libelleFr", "Est", "attributs", m("parent", "US"), "effectifDepuis", "2027-01-01"));
        referentiels.creer(essai, "statuts_emploi", m("code", "TPR", "libelleFr", "Temps partiel", "comportement", "TEMPS_PARTIEL_REGULIER", "effectifDepuis", "2027-01-01"));
        regles.creer(essai, m("contexte", "ECHANGE", "cle", "contigu", "condition", m("fait", "contigu", "op", "=", "valeur", true),
                "action", "AVERTIR", "message", "Quart contigu", "priorite", 5));
    }

    private String exporter() throws Exception {
        return http.get(P, ADMIN_ESSAI).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"configuration-effectifs-2027-01-10.json\""))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("exporter, simuler, appliquer, réimporter sans effet, puis remplacer ce qui a changé à la source")
    void promouvoir() throws Exception {
        String paquet = exporter();
        assertThat(paquet).contains("\"format\":\"nexu-effectifs-configuration\"", "\"organisme\":\"essai\"");
        assertThat(paquet.indexOf("\"code\":\"US\"")).isLessThan(paquet.indexOf("\"code\":\"EST\""));
        // Simulation : rien n'est écrit.
        http.post(P, ADMIN_PROD, paquet).andExpect(status().isOk()).andExpect(jsonPath("$.applique").value(false))
                .andExpect(jsonPath("$.creer").value(4)).andExpect(jsonPath("$.priseDEffet").value("2027-01-11"))
                .andExpect(jsonPath("$.lignes[0].objet").value("referentiels.unites")).andExpect(jsonPath("$.lignes[3].objet").value("regle"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_referentiels WHERE tenantId = 'prod'").query(Integer.class).single()).isZero();
        // Application.
        http.post(P + "?appliquer=true", ADMIN_PROD, paquet).andExpect(status().isOk()).andExpect(jsonPath("$.applique").value(true)).andExpect(jsonPath("$.creer").value(4));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_referentiels WHERE tenantId = 'prod'").query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_journal WHERE tenantId = 'prod'").query(Integer.class).single()).isEqualTo(4);
        // Réimporté tel quel : rien ne change.
        http.post(P + "?appliquer=true", ADMIN_PROD, paquet).andExpect(jsonPath("$.inchange").value(4)).andExpect(jsonPath("$.creer").value(0));
        // Modifié à la source : remplacé à la date demandée, le reste laissé.
        Contexte essai = new Contexte("essai", "admin-essai", "admin", MAINTENANT);
        referentiels.remplacer(essai, "statuts_emploi", "TPR", m("libelleFr", "Temps partiel régulier", "comportement", "TEMPS_PARTIEL_REGULIER", "depuis", "2027-01-10"));
        String regleId = jdbc.sql("SELECT id FROM eff_regles WHERE tenantId = 'essai'").query(String.class).single();
        regles.publierVersion(essai, regleId, m("condition", m("fait", "contigu", "op", "=", "valeur", true), "action", "BLOQUER", "message", "Quart contigu", "priorite", 5,
                "depuis", "2027-01-10"));
        http.post(P + "?appliquer=true&depuis=2027-02-01", ADMIN_PROD, exporter()).andExpect(jsonPath("$.remplacer").value(2)).andExpect(jsonPath("$.inchange").value(2))
                .andExpect(jsonPath("$.priseDEffet").value("2027-02-01"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_regle_versions WHERE tenantId = 'prod'").query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("refus nommés : élément invalide, document étranger, sections mal formées, date passée ; réservé à qui configure")
    void refus() throws Exception {
        String entete = "\"format\":\"nexu-effectifs-configuration\",\"version\":1";
        // Chaque élément repasse par la validation de la saisie : une unité dont la parente n'existe pas est refusée.
        http.post(P + "?appliquer=true", ADMIN_PROD, "{" + entete + ",\"referentiels\":{\"unites\":[{\"code\":\"EST\",\"libelleFr\":\"Est\",\"attributs\":{\"parent\":\"ZZ\"}}]}}")
                .andExpect(status().is4xxClientError());
        http.post(P, ADMIN_PROD, "{\"format\":\"autre\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("pas un paquet de configuration")));
        http.post(P, ADMIN_PROD, null).andExpect(status().is(422));
        http.post(P, ADMIN_PROD, "{" + entete + ",\"referentiels\":[]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« referentiels » doit être un objet"));
        http.post(P, ADMIN_PROD, "{" + entete + ",\"regles\":{}}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« regles » doit être une liste d'objets"));
        http.post(P, ADMIN_PROD, "{" + entete + "}").andExpect(status().isOk()).andExpect(jsonPath("$.lignes").isEmpty());
        http.post(P + "?depuis=2026-01-01", ADMIN_PROD, "{" + entete + "}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une configuration promue ne prend pas effet dans le passé (2026-01-01)"));
        http.get(P, CHEF_PROD).andExpect(status().isForbidden());
        http.post(P, CHEF_PROD, "{" + entete + "}").andExpect(status().isForbidden());
        http.get(P, null).andExpect(status().isUnauthorized());
    }
}
