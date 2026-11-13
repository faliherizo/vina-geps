package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.domaine.ActionRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import com.jayway.jsonpath.JsonPath;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Le moteur de règles des effectifs par HTTP</strong> (lot R2, jumeau de
 * {@code effectifs-regles.integration.test.ts}) : la garde du module {@code effectifs_regles}, l'intégrité
 * des portées contre les référentiels, la non-rétroactivité des versions, et surtout <strong>la trace</strong>
 * — chaque décision conservée avec ses versions et ses faits, journalisée sous le mécanisme. Vraie base
 * SQLite, horloge figée au 10 janvier 2027.
 */
@DisplayName("/api/effectifs/regles — moteur de règles (R2)")
class ControleurReglesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String R = "/api/effectifs/regles";
    private static final String MAINTENANT = "2027-01-10T08:00:00Z";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final String BASE = "\"contexte\":\"DISPONIBILITE\",\"cle\":\"minimum-quarts\",\"condition\":{\"fait\":\"quartsParSemaine\",\"op\":\"<\",\"valeur\":3},"
            + "\"message\":\"Au moins 3 quarts par semaine ({quartsParSemaine} déclarés)\",\"effectifDepuis\":\"2030-01-01\"";

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotJournalEffectifs journal;
    private LectureRegles lecture;
    private GestionnaireRegles gestion;
    private EvaluateurRegles evaluateur;

    /** Une séquence déterministe : les identifiants se lisent dans les essais. */
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

    private static Contexte ctx(String maintenant) {
        return new Contexte(T, "admin-essai", "admin", maintenant);
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put((String) kv[i], kv[i + 1]);
        }
        return r;
    }

    private void modules(String json) {
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params(json, T).update();
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)")
                .params(T, "[\"personnel\",\"effectifs\",\"effectifs_regles\"]").update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        journal = new DepotJournalEffectifs(jdbc, seq, schema);
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        DepotRegles depot = new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc));
        lecture = new LectureRegles(depot);
        gestion = new GestionnaireRegles(depot, lecture, referentiels, journal, seq);
        evaluateur = new EvaluateurRegles(depot, referentiels, journal, seq);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse(MAINTENANT), ZoneOffset.UTC));
        http = new ClientHttpEssai(new ControleurReglesEffectifs(garde, lecture, gestion, evaluateur));
        GestionnaireReferentiels gestionRef = new GestionnaireReferentiels(depotRef, referentiels, journal, seq);
        Contexte admin = ctx("2026-06-01T00:00:00Z");
        gestionRef.creer(admin, "unites", m("code", "US", "libelleFr", "Urgences-santé", "effectifDepuis", "2026-01-01"));
        gestionRef.creer(admin, "unites", m("code", "EST", "libelleFr", "Est", "attributs", m("parent", "US"), "effectifDepuis", "2026-01-01"));
        gestionRef.creer(admin, "statuts_emploi", m("code", "TPR", "libelleFr", "TPR", "comportement", "TEMPS_PARTIEL_REGULIER", "effectifDepuis", "2026-01-01"));
        gestionRef.creer(admin, "conventions", m("code", "FSSS", "libelleFr", "FSSS", "effectifDepuis", "2026-01-01"));
    }

    private String creer(String json) throws Exception {
        return JsonPath.read(http.post(R, ADMIN, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private int compte(String sql) {
        return jdbc.sql(sql).query(Integer.class).single();
    }

    @Test
    @DisplayName("sans le module, 403 nommé ; le catalogue de faits ; les capacités configurer et gérer")
    void moduleCatalogueEtCapacites() throws Exception {
        modules("[\"personnel\",\"effectifs\"]");
        http.get(R, ADMIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'effectifs_regles' non activé pour cet organisme"));
        modules("[\"personnel\",\"effectifs\",\"effectifs_regles\"]");
        http.get(R, JULIE).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(R + "/faits", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.DISPONIBILITE[0].code").value("quartsParSemaine"))
                .andExpect(jsonPath("$.DISPONIBILITE[0].type").value("nombre")).andExpect(jsonPath("$.ABSENCE[7].type").value("date"))
                .andExpect(jsonPath("$.ATTRIBUTION[0].type").value("booleen")).andExpect(jsonPath("$.INTERET_REMPLACEMENT[2].libelle").value("Comportement du statut d'emploi"));
        http.post(R, JULIE, "{" + BASE + ",\"action\":\"BLOQUER\"}").andExpect(status().isForbidden());
        http.post(R, CHEF, "{" + BASE + ",\"action\":\"BLOQUER\"}").andExpect(status().isForbidden());
        http.post(R + "/simuler", CHEF, "{}").andExpect(status().isForbidden());
        http.post(R + "/apercu", CHEF, "{}").andExpect(status().isForbidden());
        http.post(R + "/x/versions", CHEF, "{}").andExpect(status().isForbidden());
        http.post(R + "/x/retirer", CHEF, "{}").andExpect(status().isForbidden());
        http.get(R + "/evaluations", JULIE).andExpect(status().isForbidden());
        http.get(R + "/evaluations", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(R + "/x", CHEF).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("crée une règle d'organisme et une de statut de même clé ; refuse doublon, portée inconnue, code d'organisme, date passée, action inconnue")
    void creation() throws Exception {
        http.post(R, ADMIN, "{" + BASE + ",\"action\":\"BLOQUER\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.porteeType").value("ORGANISME"))
                .andExpect(jsonPath("$.porteeCode").value(Matchers.nullValue())).andExpect(jsonPath("$.versionCourante.condition.op").value("<"))
                .andExpect(jsonPath("$.versionCourante.action").value("BLOQUER")).andExpect(jsonPath("$.versionCourante.version").value(1));
        http.post(R, ADMIN, "{" + BASE + ",\"action\":\"BLOQUER\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("une règle « minimum-quarts » existe déjà pour cette portée : publiez-en une nouvelle version"));
        // Pas encore en vigueur : on montre la version à venir, avec sa date — « null » voudrait dire retirée.
        http.post(R, ADMIN, "{" + BASE + ",\"action\":\"AVERTIR\",\"porteeType\":\"STATUT_EMPLOI\",\"porteeCode\":\" TPR \"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.porteeCode").value("TPR")).andExpect(jsonPath("$.versionCourante.effectifDepuis").value("2030-01-01"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"x\",\"action\":\"BLOQUER\",\"porteeType\":\"STATUT_EMPLOI\",\"porteeCode\":\"ZZ\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« clé » : 2 à 60 minuscules, chiffres ou tirets (ex. minimum-quarts-semaine)"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"xx\",\"action\":\"BLOQUER\",\"porteeType\":\"STATUT_EMPLOI\",\"porteeCode\":\"ZZ\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("Statuts d'emploi : « ZZ » n'est pas en vigueur le 2030-01-01"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"xy\",\"action\":\"BLOQUER\",\"porteeType\":\"UNITE\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("Unités organisationnelles : «  » n'est pas en vigueur le 2030-01-01"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"y\",\"action\":\"BLOQUER\",\"porteeCode\":\"TPR\"}").andExpect(status().is(422));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"yy\",\"action\":\"BLOQUER\",\"porteeCode\":\"TPR\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une règle d'organisme ne porte pas de code de portée"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"zz\",\"action\":\"BLOQUER\",\"effectifDepuis\":\"2020-01-01\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une règle ne peut pas prendre effet dans le passé (2020-01-01)"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"zz\",\"action\":\"BLOQUER\",\"effectifDepuis\":\"demain\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« effectifDepuis » : date AAAA-MM-JJ attendue"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"ww\",\"action\":\"IGNORER\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("action inconnu : « IGNORER » (attendu : ACCEPTER, AVERTIR, EXIGER_APPROBATION, EXCLURE_DU_BASSIN, BLOQUER, REFUSER)"));
        http.post(R, ADMIN, "{" + BASE + ",\"cle\":\"ww\",\"action\":\"BLOQUER\",\"porteeType\":\"PAYS\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("portée inconnu : « PAYS »")));
        http.post(R, ADMIN, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("contexte inconnu : « null »")));
        http.post(R, ADMIN, "{\"contexte\":\"ECHANGE\",\"cle\":\"contigu\",\"porteeType\":\"ORGANISME\",\"porteeCode\":\"\",\"effectifDepuis\":\"\","
                        + "\"condition\":{\"fait\":\"contigu\",\"op\":\"=\",\"valeur\":true},\"action\":\"AVERTIR\",\"message\":\"Quart contigu\",\"priorite\":\"5\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.versionCourante.effectifDepuis").value("2027-01-10"))
                .andExpect(jsonPath("$.versionCourante.priorite").value(5)).andExpect(jsonPath("$.versionCourante.effectifJusqua").value(Matchers.nullValue()));

        http.get(R + "?contexte=DISPONIBILITE", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].porteeType").value("ORGANISME")).andExpect(jsonPath("$[1].porteeType").value("STATUT_EMPLOI"));
        http.get(R + "?contexte=PAIE", ADMIN).andExpect(status().is(422));
        http.get(R + "?contexte=", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(3)));
        http.get(R, ADMIN).andExpect(jsonPath("$[0].contexte").value("DISPONIBILITE")).andExpect(jsonPath("$[2].contexte").value("ECHANGE"));
        assertThat(jdbc.sql("SELECT condition FROM eff_regle_versions ORDER BY rowid LIMIT 1").query(String.class).single())
                .isEqualTo("{\"fait\":\"quartsParSemaine\",\"op\":\"<\",\"valeur\":3}");
        assertThat(compte("SELECT COUNT(*) FROM eff_journal WHERE action = 'regle.creee'")).isEqualTo(3);
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'regle.creee' ORDER BY rowid LIMIT 1").query(String.class).single())
                .contains("\"contexte\":\"DISPONIBILITE\"", "\"condition\":{\"fait\":\"quartsParSemaine\"", "\"depuis\":\"2030-01-01\"");
    }

    @Test
    @DisplayName("simule un dossier : la règle particulière remplace la générale, la décision est tracée et journalisée sous le mécanisme")
    void simulation() throws Exception {
        creer("{" + BASE + ",\"action\":\"BLOQUER\"}");
        creer("{" + BASE + ",\"action\":\"AVERTIR\",\"porteeType\":\"STATUT_EMPLOI\",\"porteeCode\":\"TPR\"}");
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"date\":\"2030-02-01\",\"faits\":{\"quartsParSemaine\":2},\"unite\":\"EST\","
                        + "\"portees\":{\"statutEmploi\":\"TPR\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("AVERTIR"))
                .andExpect(jsonPath("$.motifs[0].message").value("Au moins 3 quarts par semaine (2 déclarés)")).andExpect(jsonPath("$.motifs[0].cle").value("minimum-quarts"))
                .andExpect(jsonPath("$.versions", Matchers.hasSize(1)));
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"date\":\"2030-02-01\",\"faits\":{\"quartsParSemaine\":2}}")
                .andExpect(jsonPath("$.action").value("BLOQUER"));
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"faits\":{\"quartsParSemaine\":2}}")
                .andExpect(jsonPath("$.action").value("ACCEPTER")).andExpect(jsonPath("$.motifs", Matchers.hasSize(0)));
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"date\":\"2030-02-01\",\"faits\":{}}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("fait manquant pour évaluer la règle : « quartsParSemaine »"));
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"date\":\"02/01/2030\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« date » : date AAAA-MM-JJ attendue"));
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"PAIE\"}").andExpect(status().is(422));
        // Des entrées d'une autre forme ne font pas tomber le simulateur : elles ne visent rien.
        http.post(R + "/simuler", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"objetId\":42,\"faits\":\"x\",\"unite\":3,\"portees\":{\"statutEmploi\":5}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.action").value("ACCEPTER"));

        http.get(R + "/evaluations?contexte=DISPONIBILITE", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(4)))
                .andExpect(jsonPath("$[0].objetId").value("42")).andExpect(jsonPath("$[3].objetType").value("simulation"))
                .andExpect(jsonPath("$[3].faits.quartsParSemaine").value(2)).andExpect(jsonPath("$[3].motifs[0].action").value("AVERTIR"))
                .andExpect(jsonPath("$[3].versions[0].version").value(1)).andExpect(jsonPath("$[3].declencheur").value("usr-admin"))
                .andExpect(jsonPath("$[3].dateDuFait").value("2030-02-01")).andExpect(jsonPath("$[3].horodatage").value("2027-01-10T08:00:00.000Z"))
                .andExpect(jsonPath("$[3].contexte").value("DISPONIBILITE")).andExpect(jsonPath("$[3].action").value("AVERTIR"));
        http.get(R + "/evaluations?objetType=simulation&objetId=simulation", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(3)));
        http.get(R + "/evaluations?objetId=42", CHEF).andExpect(jsonPath("$", Matchers.hasSize(1)));
        http.get(R + "/evaluations?contexte=ABSENCE", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(R + "/evaluations", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(4)));
        http.get(R + "/evaluations?contexte=PAIE", ADMIN).andExpect(status().is(422));

        List<JournalEffectifs.Entree> decisions = journal.lire(T, new JournalEffectifs.Filtre(null, JournalEffectifs.Origine.SYSTEME, null, null, null, null, null), null, 10);
        assertThat(decisions).hasSize(4);
        assertThat(decisions.get(3).acteurId()).isEqualTo(EvaluateurRegles.MECANISME);
        assertThat(decisions.get(3).action()).isEqualTo("regles.disponibilite");
        assertThat(decisions.get(3).uniteCode()).isEqualTo("EST");
        assertThat(decisions.get(3).apresJson()).isEqualTo("{\"action\":\"AVERTIR\",\"motifs\":[\"minimum-quarts\"],\"declenchePar\":\"usr-admin\"}");
    }

    @Test
    @DisplayName("prévoit l'impact d'une règle en brouillon sans rien écrire")
    void apercu() throws Exception {
        String brouillon = "\"contexte\":\"DISPONIBILITE\",\"condition\":{\"fait\":\"nuitsConsecutives\",\"op\":\">\",\"valeur\":2},\"action\":\"AVERTIR\","
                + "\"message\":\"{nuitsConsecutives} nuits\"";
        http.post(R + "/apercu", ADMIN, "{" + brouillon + ",\"dossiers\":[{\"objetId\":\"d1\",\"faits\":{\"nuitsConsecutives\":3}},"
                        + "{\"objetId\":\"d2\",\"faits\":{\"nuitsConsecutives\":1}}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.parAction.AVERTIR").value(1))
                .andExpect(jsonPath("$.parAction.ACCEPTER").value(1)).andExpect(jsonPath("$.parAction.REFUSER").value(0))
                .andExpect(jsonPath("$.exemples", Matchers.hasSize(1))).andExpect(jsonPath("$.exemples[0].objetId").value("d1"))
                .andExpect(jsonPath("$.exemples[0].message").value("3 nuits"));
        http.post(R + "/apercu", ADMIN, "{" + brouillon + ",\"dossiers\":[{\"faits\":\"x\"}]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("dossier « undefined » : fait « nuitsConsecutives » manquant"));
        http.post(R + "/apercu", ADMIN, "{" + brouillon + "}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« dossiers » : une liste de { objetId, faits } est attendue"));
        http.post(R + "/apercu", ADMIN, "{\"contexte\":\"DISPONIBILITE\",\"condition\":{\"fait\":\"quartsParSemaine\",\"op\":\"<\",\"valeur\":3},\"action\":\"AVERTIR\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« message » est obligatoire : il dit à la personne quoi corriger"));
        http.post(R + "/apercu", ADMIN, null).andExpect(status().is(422));
        assertThat(compte("SELECT COUNT(*) FROM eff_regle_evaluations")).isZero();
        assertThat(compte("SELECT COUNT(*) FROM eff_journal")).isEqualTo(4);
    }

    @Test
    @DisplayName("lit une règle et ses versions, publie une version datée, retire ; 404 et non-rétroactivité")
    void lectureVersionsEtRetrait() throws Exception {
        String id = creer("{" + BASE + ",\"action\":\"BLOQUER\"}");
        http.get(R + "/" + id, JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.versions", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.regle.id").value(id)).andExpect(jsonPath("$.regle.versionCourante.version").value(1))
                .andExpect(jsonPath("$.versions[0].regleId").value(id)).andExpect(jsonPath("$.versions[0].condition.valeur").value(3))
                .andExpect(jsonPath("$.versions[0].cle").value("minimum-quarts")).andExpect(jsonPath("$.versions[0].contexte").value("DISPONIBILITE"));
        http.get(R + "/inconnue", ADMIN).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("règle « inconnue » introuvable"));
        http.post(R + "/inconnue/versions", ADMIN, "{}").andExpect(status().isNotFound());
        http.post(R + "/inconnue/retirer", ADMIN, "{}").andExpect(status().isNotFound());

        String v2 = "\"condition\":{\"fait\":\"quartsParSemaine\",\"op\":\"<\",\"valeur\":4},\"action\":\"BLOQUER\",\"message\":\"Au moins 4 quarts\"";
        http.post(R + "/" + id + "/versions", ADMIN, "{\"depuis\":\"2031-01-01\"," + v2 + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionCourante.version").value(2)).andExpect(jsonPath("$.versionCourante.effectifDepuis").value("2031-01-01"));
        http.post(R + "/" + id + "/versions", ADMIN, "{\"depuis\":\"2000-01-01\",\"condition\":{},\"action\":\"BLOQUER\",\"message\":\"m\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une modification ne peut pas être rétroactive (2000-01-01 < 2027-01-10)"));
        http.post(R + "/" + id + "/versions", ADMIN, "{\"depuis\":\"2031-01-01\"," + v2 + "}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la nouvelle version doit commencer après 2031-01-01"));
        http.post(R + "/" + id + "/versions", ADMIN, "{\"depuis\":\"2032-01-01\",\"condition\":{},\"action\":\"BLOQUER\",\"message\":\"m\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("fait inconnu pour DISPONIBILITE")));
        http.post(R + "/" + id + "/versions", ADMIN, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« depuis » : date AAAA-MM-JJ attendue"));
        http.get(R + "/" + id, ADMIN).andExpect(jsonPath("$.versions", Matchers.hasSize(2))).andExpect(jsonPath("$.versions[0].effectifJusqua").value("2031-01-01"))
                .andExpect(jsonPath("$.versions[1].message").value("Au moins 4 quarts"));

        http.post(R + "/" + id + "/retirer", ADMIN, "{\"date\":\"2032-01-01\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.versionCourante").value(Matchers.nullValue()));
        http.post(R + "/" + id + "/retirer", ADMIN, "{\"date\":\"2033-01-01\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("cette valeur est déjà fermée"));
        String aujourdhui = creer("{" + BASE + ",\"cle\":\"du-jour\",\"action\":\"AVERTIR\",\"effectifDepuis\":null}");
        http.post(R + "/" + aujourdhui + "/retirer", ADMIN, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la fermeture doit suivre le début (2027-01-10)"));
        http.post(R + "/" + aujourdhui + "/retirer", ADMIN, "{\"date\":\"hier\"}").andExpect(status().is(422));

        assertThat(compte("SELECT COUNT(*) FROM eff_journal WHERE action = 'regle.version'")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT avant FROM eff_journal WHERE action = 'regle.version'").query(String.class).single()).contains("\"version\":1", "\"effectifJusqua\":null");
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'regle.retiree'").query(String.class).single()).isEqualTo("{\"date\":\"2032-01-01\"}");
    }

    @Test
    @DisplayName("à heure fixe : une version publiée « demain » ne change pas la décision d'aujourd'hui, et l'unité remonte à l'ancêtre")
    void servicesAHeureFixe() {
        Contexte le10 = ctx("2027-01-10T08:00:00.000Z");
        Contexte le12 = ctx("2027-01-12T08:00:00.000Z");
        Contexte mars = ctx("2027-03-01T00:00:00.000Z");
        String id = (String) gestion.creer(le10, m("contexte", "ABSENCE", "cle", "preavis", "porteeType", "UNITE", "porteeCode", "US",
                "condition", m("fait", "joursPreavis", "op", "<", "valeur", 14), "action", "REFUSER", "message", "Préavis de 14 jours ({joursPreavis})")).get("id");
        gestion.publierVersion(le10, id, m("depuis", "2027-02-01", "condition", m("fait", "joursPreavis", "op", "<", "valeur", 7),
                "action", "REFUSER", "message", "Préavis de 7 jours"));
        Regles.Verdict janvier = evaluateur.evaluerDossier(le12, dossier("2027-01-20"));
        assertThat(janvier.action()).isEqualTo(ActionRegle.REFUSER);
        assertThat(janvier.motifs().get(0).message()).isEqualTo("Préavis de 14 jours (10)");
        assertThat(Regles.estBloquant(janvier.action())).isTrue();
        assertThat(evaluateur.evaluerDossier(le12, dossier("2027-02-10")).action()).isEqualTo(ActionRegle.ACCEPTER);
        assertThat(evaluateur.jugerSansTrace(le12, dossier("2027-01-20")).action()).as("sans trace").isEqualTo(ActionRegle.REFUSER);
        assertThat(evaluateur.jugerSansTrace(le12, new Regles.Dossier(ContexteRegle.ABSENCE, null, null, "2027-01-20", "OUEST", null, null)).action())
                .as("une unité hors de l'arbre n'est pas visée ; des faits nuls sont un dossier vide").isEqualTo(ActionRegle.ACCEPTER);
        assertThatThrownBy(() -> gestion.retirer(mars, id, m("date", "2027-02-15"))).isInstanceOf(EffectifsInvalideException.class).hasMessageContaining("rétroactive");
        assertThat(gestion.retirer(mars, id, Map.of())).containsEntry("versionCourante", null);

        List<JournalEffectifs.Entree> e = journal.lire(T, new JournalEffectifs.Filtre(null, JournalEffectifs.Origine.SYSTEME, null, "absence", null, null, null), null, 10);
        assertThat(e).hasSize(2);
        assertThat(e.get(0).acteurId()).isEqualTo("moteur-de-regles");
        assertThat(e.get(0).apresJson()).contains("\"declenchePar\":\"admin-essai\"");
        assertThat(lecture.evaluations(le12, "absence", null, null)).hasSize(2);
        assertThat(lecture.evaluations(le12, "absence", "a-2027-01-20", "ABSENCE")).hasSize(1);

        assertThat(evaluateur.unitesEtAncetres(T, "EST", "2027-01-01")).containsExactly("EST", "US");
        assertThat(evaluateur.unitesEtAncetres(T, null, "2027-01-01")).isEmpty();
        assertThat(evaluateur.unitesEtAncetres(T, "", "2027-01-01")).isEmpty();
        assertThat(evaluateur.unitesEtAncetres(T, "INCONNUE", "2027-01-01")).containsExactly("INCONNUE");
    }

    private static Regles.Dossier dossier(String date) {
        return new Regles.Dossier(ContexteRegle.ABSENCE, "absence", "a-" + date, date, "EST", Map.of("joursPreavis", 10), null);
    }
}
