package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRetentionEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRetentionEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRetentionEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEchangesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaRetentionEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaTempsEffectifs;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>La rétention des effectifs par HTTP</strong> (lot R11, jumeau de {@code effectifs-retention.integration.test.ts}).
 * <p>
 * Des données de chaque classe, anciennes (2019) et récentes (le jour même), posées en SQL dans les tables des
 * autres lots ; une feuille ancienne jamais approuvée. Sans politique rien ne se purge ; une politique partielle
 * n'épargne que ses classes ; avec toutes, l'aperçu dit combien, la purge ne touche que l'ancien (jamais la
 * feuille ouverte), et le journal garde le décompte, attribué au mécanisme. Horloge figée au 15 janvier 2027.
 */
@DisplayName("/api/effectifs/retention — politique, aperçu, purge")
class ControleurRetentionEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String R = "/api/effectifs/retention";
    private static final String MAINTENANT = "2027-01-15T10:00:00Z";
    private static final String ANCIEN = "2019-06-01";
    private static final String RECENT = "2027-01-15";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final List<String> TABLES = List.of("eff_equipages", "eff_places", "eff_echanges", "eff_pointages", "eff_pointage_preuves",
            "eff_horodatages_externes", "eff_feuilles", "eff_segments_temps", "eff_feuille_decisions", "eff_absences", "eff_regle_evaluations");

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
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, "[\"personnel\",\"effectifs\"]").update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        new SchemaReglesEffectifs(jdbc);
        new SchemaPlanification(jdbc);
        new SchemaPresenceEffectifs(jdbc);
        new SchemaTempsEffectifs(jdbc);
        new SchemaAbsencesEffectifs(jdbc);
        new SchemaEchangesEffectifs(jdbc);
        SchemaRetentionEffectifs schemaRetention = new SchemaRetentionEffectifs(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMil = new DepotMilieux(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMil, perimetres);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse(MAINTENANT), ZoneOffset.UTC));
        ControleurSocleEffectifs socle = new ControleurSocleEffectifs(garde, referentiels, new GestionnaireReferentiels(depotRef, referentiels, journal, seq),
                perimetres, new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMil, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres));
        DepotRetentionEffectifs depot = new DepotRetentionEffectifs(jdbc, schemaRetention);
        LectureRetentionEffectifs lecture = new LectureRetentionEffectifs(depot);
        http = new ClientHttpEssai(socle, new ControleurRetentionEffectifs(garde, lecture, new GestionnaireRetentionEffectifs(lecture, depot, journal)));
        peupler();
    }

    /** Insère une ligne : les colonnes obligatoires reçoivent une valeur neutre, {@code valeurs} fixe le reste (comme le Node). */
    private void inserer(String table, Object... valeurs) {
        Map<String, Object> ligne = new LinkedHashMap<>();
        jdbc.sql("PRAGMA table_info(" + table + ")").query((rs, i) -> {
            if (rs.getInt("notnull") == 1 && rs.getObject("dflt_value") == null) {
                ligne.put(rs.getString("name"), "INTEGER".equals(rs.getString("type")) ? (Object) 0 : "x");
            }
            return null;
        }).list();
        ligne.put("tenantId", T);
        for (int i = 0; i < valeurs.length; i += 2) {
            ligne.put((String) valeurs[i], valeurs[i + 1]);
        }
        String marques = String.join(",", ligne.keySet().stream().map(k -> "?").toList());
        jdbc.sql("INSERT INTO " + table + " (" + String.join(",", ligne.keySet()) + ") VALUES (" + marques + ")").params(List.copyOf(ligne.values())).update();
    }

    private void peupler() {
        for (String[] q : new String[][]{{ANCIEN, "a"}, {RECENT, "r"}}) {
            String quand = q[0];
            String s = q[1];
            String instant = quand + "T10:00:00.000Z";
            inserer("eff_equipages", "id", "eq-" + s, "date", quand);
            inserer("eff_places", "id", "pl-" + s, "equipageId", "eq-" + s);
            inserer("eff_echanges", "id", "ec-" + s, "proposeLe", instant);
            inserer("eff_pointages", "id", "po-" + s, "horodatage", instant);
            inserer("eff_pointage_preuves", "pointageId", "po-" + s, "mecanisme", "GEOREPERAGE");
            inserer("eff_horodatages_externes", "id", "hx-" + s, "cle", s, "horodatage", instant);
            inserer("eff_feuilles", "id", "fe-" + s, "ressourceId", s, "debut", quand, "fin", quand, "statut", "APPROUVEE");
            inserer("eff_segments_temps", "id", "sg-" + s, "feuilleId", "fe-" + s);
            inserer("eff_feuille_decisions", "id", "fd-" + s, "feuilleId", "fe-" + s);
            inserer("eff_absences", "id", "ab-" + s, "du", quand, "au", quand);
            inserer("eff_regle_evaluations", "id", "ev-" + s, "horodatage", instant);
            inserer("eff_journal", "id", "jo-" + s, "horodatage", instant);
        }
        // Une feuille ancienne jamais approuvée : elle n'est pas une archive, elle reste.
        inserer("eff_feuilles", "id", "fe-ouverte", "ressourceId", "z", "debut", ANCIEN, "fin", ANCIEN, "statut", "SOUMISE");
    }

    private int compter(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE tenantId = ?").param(T).query(Integer.class).single();
    }

    private Map<String, Integer> comptes() {
        Map<String, Integer> m = new LinkedHashMap<>();
        TABLES.forEach(t -> m.put(t, compter(t)));
        return m;
    }

    private List<String> ids(String table) {
        return jdbc.sql("SELECT id FROM " + table + " WHERE tenantId = ? ORDER BY id").param(T).query(String.class).list();
    }

    private void politique(String json) throws Exception {
        http.put(R, ADMIN, "{\"politique\":" + json + "}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("réservée à l'administration, derrière le module effectifs")
    void gardes() throws Exception {
        http.get(R, JULIE).andExpect(status().isForbidden());
        http.put(R, JULIE, "{\"politique\":{\"TEMPS\":5}}").andExpect(status().isForbidden());
        http.post(R + "/appliquer", JULIE, "{}").andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.get(R, ADMIN).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs' non activé pour cet organisme"));
        assertThat(compter("eff_retention")).isZero();
    }

    @Test
    @DisplayName("sans politique, rien n'est désigné ni purgé ; la purge vide est tout de même journalisée")
    void sansPolitique() throws Exception {
        Map<String, Integer> avant = comptes();
        http.get(R, ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].classe").value(Matchers.contains("HORAIRES", "PRESENCE", "TEMPS", "ABSENCES", "JOURNAUX")))
                .andExpect(jsonPath("$[*].annees").value(Matchers.everyItem(Matchers.nullValue())))
                .andExpect(jsonPath("$[*].limite").value(Matchers.everyItem(Matchers.nullValue())))
                .andExpect(jsonPath("$[*].aPurger").value(Matchers.everyItem(Matchers.is(0))))
                .andExpect(jsonPath("$[0].libelle").value("Horaires (quarts, places, échanges)"));
        http.post(R + "/appliquer", ADMIN, "{}").andExpect(status().isOk()).andExpect(content().json("{\"purges\":{}}", true));
        assertThat(comptes()).isEqualTo(avant);
        assertThat(compter("eff_journal")).isEqualTo(3);
    }

    @Test
    @DisplayName("une politique invalide est refusée en 422 avec le message du Node, et rien n'est posé")
    void invalide() throws Exception {
        String[][] cas = {
                {"{\"politique\":{\"HORAIRES\":0}}", "« HORAIRES » : un nombre entier d'années, de 1 à 30"},
                {"{\"politique\":{\"TEMPS\":5,\"JOURNAUX\":2.5}}", "« JOURNAUX » : un nombre entier d'années, de 1 à 30"},
                {"{\"politique\":{\"TOUT\":5}}", "classe : HORAIRES, PRESENCE, TEMPS, ABSENCES, JOURNAUX"},
                {"{\"politique\":[5]}", "« politique » : un objet { classe: années }"},
                {"{}", "« politique » : un objet { classe: années }"}};
        for (String[] c : cas) {
            http.put(R, ADMIN, c[0]).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors[0]").value(c[1]));
        }
        http.put(R, ADMIN, null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« politique » : un objet { classe: années }"));
        assertThat(compter("eff_retention")).isZero();
    }

    @Test
    @DisplayName("une politique partielle ne purge que ses classes : les autres sont épargnées, la feuille ouverte aussi")
    void partielle() throws Exception {
        politique("{\"HORAIRES\":5,\"TEMPS\":5}");
        http.get(R, ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].aPurger").value(Matchers.contains(3, 0, 3, 0, 0)))
                .andExpect(jsonPath("$[*].annees").value(Matchers.contains(5, null, 5, null, null)))
                .andExpect(jsonPath("$[0].limite").value("2022-01-15"))
                .andExpect(jsonPath("$[1].limite").value(Matchers.nullValue()));
        http.post(R + "/appliquer", ADMIN, "{}").andExpect(status().isOk()).andExpect(content().json("{\"purges\":{\"HORAIRES\":3,\"TEMPS\":3}}", true));
        assertThat(ids("eff_equipages")).containsExactly("eq-r");
        assertThat(ids("eff_places")).containsExactly("pl-r");
        assertThat(ids("eff_echanges")).containsExactly("ec-r");
        assertThat(ids("eff_feuilles")).containsExactly("fe-ouverte", "fe-r");
        assertThat(ids("eff_segments_temps")).containsExactly("sg-r");
        assertThat(ids("eff_feuille_decisions")).containsExactly("fd-r");
        for (String epargnee : List.of("eff_pointages", "eff_pointage_preuves", "eff_horodatages_externes", "eff_absences", "eff_regle_evaluations")) {
            assertThat(compter(epargnee)).as(epargnee).isEqualTo(2);
        }
        assertThat(ids("eff_journal")).contains("jo-a", "jo-r");
    }

    @Test
    @DisplayName("la politique désigne l'ancien, la purge ne touche qu'à lui, le journal garde le décompte")
    void complete() throws Exception {
        http.put(R, ADMIN, "{\"politique\":{\"HORAIRES\":5,\"PRESENCE\":5,\"TEMPS\":5,\"ABSENCES\":5,\"JOURNAUX\":5}}").andExpect(status().isOk())
                .andExpect(jsonPath("$[*].aPurger").value(Matchers.contains(3, 3, 3, 1, 2)))
                .andExpect(jsonPath("$[*].limite").value(Matchers.everyItem(Matchers.is("2022-01-15"))));
        http.post(R + "/appliquer", ADMIN, "{}").andExpect(status().isOk())
                .andExpect(content().json("{\"purges\":{\"HORAIRES\":3,\"PRESENCE\":3,\"TEMPS\":3,\"ABSENCES\":1,\"JOURNAUX\":2}}", true));
        assertThat(List.of(compter("eff_equipages"), compter("eff_places"), compter("eff_pointages"), compter("eff_feuilles"), compter("eff_absences")))
                .containsExactly(1, 1, 1, 2, 1);
        assertThat(ids("eff_feuilles")).containsExactly("fe-ouverte", "fe-r");
        assertThat(jdbc.sql("SELECT pointageId FROM eff_pointage_preuves WHERE tenantId = ?").param(T).query(String.class).list()).containsExactly("po-r");
        assertThat(ids("eff_horodatages_externes")).containsExactly("hx-r");
        assertThat(ids("eff_regle_evaluations")).containsExactly("ev-r");
        assertThat(ids("eff_journal")).doesNotContain("jo-a").contains("jo-r");
        http.get("/api/effectifs/journal?action=retention.appliquee", ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].origine").value("SYSTEME"))
                .andExpect(jsonPath("$[0].acteurId").value(GestionnaireRetentionEffectifs.MECANISME));
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'retention.appliquee'").query(String.class).single())
                .isEqualTo("{\"purges\":{\"HORAIRES\":3,\"PRESENCE\":3,\"TEMPS\":3,\"ABSENCES\":1,\"JOURNAUX\":2},\"limites\":{\"HORAIRES\":\"2022-01-15\","
                        + "\"PRESENCE\":\"2022-01-15\",\"TEMPS\":\"2022-01-15\",\"ABSENCES\":\"2022-01-15\",\"JOURNAUX\":\"2022-01-15\"}}");
        http.get(R, ADMIN).andExpect(jsonPath("$[*].aPurger").value(Matchers.everyItem(Matchers.is(0))));
        http.put(R, ADMIN, "{\"politique\":{\"JOURNAUX\":null,\"TEMPS\":7}}").andExpect(status().isOk())
                .andExpect(jsonPath("$[4].annees").value(Matchers.nullValue())).andExpect(jsonPath("$[4].limite").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[2].annees").value(7)).andExpect(jsonPath("$[2].limite").value("2020-01-15"));
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'retention.politique' ORDER BY rowid DESC LIMIT 1").query(String.class).single())
                .isEqualTo("{\"JOURNAUX\":null,\"TEMPS\":7}");
        assertThat(jdbc.sql("SELECT annees, modifiePar FROM eff_retention WHERE classe = 'TEMPS'").query((rs, i) -> rs.getInt(1) + "/" + rs.getString(2)).single())
                .isEqualTo("7/" + ADMIN.userId());
    }
}
