package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireDossiersPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.Baremes;
import ca.vina.nexu.paie.infrastructure.persistance.BaremesFichiers;
import ca.vina.nexu.paie.infrastructure.persistance.DepotPaie;
import ca.vina.nexu.paie.infrastructure.persistance.UniteDeTravailSpring;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/paie — dossiers, cycles (préparer, recalculer, traiter, annuler), remises, relevés, bulletins de chacun")
class ControleurPaieHttpTest {

    private static final String T = "T1";
    private static final String P = "/api/paie";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "paie.admin", "admin", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("u-julie", "julie", "employee", null, T);
    private static final ContexteRequete MARC = new ContexteRequete("u-marc", "marc", "employee", null, T);
    private static final String PERIODE = "{\"debut\":\"2026-02-15\",\"fin\":\"2026-02-28\",\"datePaie\":\"2026-03-05\"}";

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    /** Les feuilles approuvées que les effectifs montrent ; une feuille retirée de l'ensemble n'est plus approuvée. */
    private final List<HeuresApprouveesPort.FeuilleAPayer> feuilles = new ArrayList<>();
    private final Set<String> approuvees = new HashSet<>();
    /** Approuvées à la lecture, rappelées à l'instant du verrou : la course que le traitement doit refuser en bloc. */
    private final Set<String> rappeleesAuVerrou = new HashSet<>();

    private final HeuresApprouveesPort heures = new HeuresApprouveesPort() {
        @Override
        public List<FeuilleAPayer> approuvees(String tenantId, String debut, String fin) {
            return feuilles.stream().filter(f -> approuvees.contains(f.feuilleId()))
                    .filter(f -> f.debut().compareTo(debut) >= 0 && f.debut().compareTo(fin) <= 0).toList();
        }

        /** Écrit chaque verrou en base avant de refuser : l'essai prouve ainsi que le refus défait ce qui précède. */
        @Override
        public void marquerPayees(Verrou verrou, List<String> ids) {
            for (String id : ids) {
                jdbc.sql("INSERT INTO essai_verrous (feuilleId, cycleId) VALUES (?, ?)").params(id, verrou.cycleId()).update();
                if (rappeleesAuVerrou.contains(id) || !approuvees.remove(id)) {
                    throw new FeuilleNonApprouvee("la feuille " + id + " n'est plus approuvée : elle n'entre pas dans cette paie", null);
                }
            }
        }
    };

    private final PersonnesPourPaiePort personnes = new PersonnesPourPaiePort() {
        @Override
        public List<Personne> personnes(String tenantId) {
            return List.of(new Personne("r-julie", "P1", "Julie Roy"), new Personne("r-marc", "P2", "Marc Dubé"), new Personne("r-lea", "P3", "Léa Côté"));
        }

        @Override
        public Optional<String> ressourceDe(String tenantId, String utilisateurId) {
            return JULIE.userId().equals(utilisateurId) ? Optional.of("r-julie") : Optional.empty();
        }
    };

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        SimpleDriverDataSource source = SchemaEssai.source(dossier);
        jdbc = SchemaEssai.base(source);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"paie\"]')").param(T).update();
        jdbc.sql("CREATE TABLE essai_verrous (feuilleId TEXT, cycleId TEXT)").update();
        DepotPaie depot = new DepotPaie(jdbc);
        AtomicInteger n = new AtomicInteger();
        Sequence sequence = new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "id-" + n.incrementAndGet();
            }

            @Override
            public String jetonSignature() {
                return "x";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return "x";
            }
        };
        Clock horloge = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneOffset.UTC);
        LecturePaie lecture = new LecturePaie(depot, new BaremesFichiers(), personnes);
        ControleurPaie.Cas cas = new ControleurPaie.Cas(new GestionnaireDossiersPaie(depot, personnes),
                new GestionnaireCyclesPaie(new GestionnaireCyclesPaie.Dependances(lecture, depot, heures,
                        new UniteDeTravailSpring(new TransactionTemplate(new DataSourceTransactionManager(source))), sequence)), lecture);
        http = new ClientHttpEssai(new ControleurPaie(new GardePaie(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc), horloge), cas, horloge));
        feuilles.add(feuille("f-julie-1", "r-julie", "2026-02-16", "35", "2"));
        feuilles.add(feuille("f-julie-2", "r-julie", "2026-02-23", "35", "0"));
        feuilles.add(feuille("f-marc", "r-marc", "2026-02-16", "40", "0"));
        feuilles.add(feuille("f-hors", "r-marc", "2026-03-02", "40", "0"));
        feuilles.forEach(f -> approuvees.add(f.feuilleId()));
    }

    private static HeuresApprouveesPort.FeuilleAPayer feuille(String id, String ressourceId, String debut, String regulieres, String supplementaires) {
        return new HeuresApprouveesPort.FeuilleAPayer(id, ressourceId, ressourceId.equals("r-julie") ? "P1" : "P2",
                ressourceId.equals("r-julie") ? "Julie Roy" : "Marc Dubé", debut, List.of(
                new HeuresApprouveesPort.LigneHeures(debut, "REG", HeuresApprouveesPort.NatureHeures.REGULIERE, new BigDecimal(regulieres)),
                new HeuresApprouveesPort.LigneHeures(debut, "HS", HeuresApprouveesPort.NatureHeures.SUPPLEMENTAIRE, new BigDecimal(supplementaires))));
    }

    private static String corps(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String preparerCycle() throws Exception {
        http.put(P + "/dossiers/r-julie", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"aux-deux-semaines\",\"tauxHoraire\":\"30\"}").andExpect(status().isOk());
        return JsonPath.read(corps(http.post(P + "/cycles", ADMIN, PERIODE).andExpect(status().isCreated())), "$.cycle.id");
    }

    @Test
    @DisplayName("les dossiers : chacun des effectifs, avec ou sans dossier ; montants personnels de base par défaut ; saisies vérifiées")
    void dossiers() throws Exception {
        http.get(P + "/dossiers", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].dossier").isEmpty());
        http.put(P + "/dossiers/r-julie", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"aux-deux-semaines\",\"tauxHoraire\":32.5}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.montantPersonnelQuebec").value(18952))
                .andExpect(jsonPath("$.montantPersonnelFederal").value(16452)).andExpect(jsonPath("$.mode").value("horaire"));
        http.put(P + "/dossiers/r-julie", ADMIN, "{\"mode\":\"salaire\",\"frequence\":\"mensuelle\",\"salaireAnnuel\":\"72000\",\"montantPersonnelQuebec\":\"20000\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.frequence").value("mensuelle"));
        http.get(P + "/dossiers", ADMIN).andExpect(jsonPath("$[0].dossier.salaireAnnuel").value(72000))
                .andExpect(jsonPath("$[0].dossier.montantPersonnelQuebec").value(20000)).andExpect(jsonPath("$[0].dossier.tauxHoraire").isEmpty());
        http.put(P + "/dossiers/inconnu", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"mensuelle\",\"tauxHoraire\":30}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("personne « inconnu » introuvable dans les effectifs de l'organisme"));
        http.put(P + "/dossiers/r-marc", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"mensuelle\",\"tauxHoraire\":\"trente\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« tauxHoraire » doit être un nombre"));
        http.put(P + "/dossiers/r-marc", ADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« mode » : horaire ou salaire"));
        http.put(P + "/dossiers/r-marc", CHEF, "{}").andExpect(status().isForbidden());
        http.get(P + "/dossiers", JULIE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("les réglages : ceux du barème tant qu'on n'en a pas enregistré ; une majoration sous 50 % est refusée")
    void reglages() throws Exception {
        http.get(P + "/reglages", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.annee").value(2026))
                .andExpect(jsonPath("$.tauxFss").value(0.0426)).andExpect(jsonPath("$.majorationSupplementaire").value(0.5));
        http.put(P + "/reglages", ADMIN, "{\"tauxFss\":\"0.0165\",\"tauxCnesst\":\"0.031\",\"majorationSupplementaire\":\"0.5\"}").andExpect(status().isOk());
        http.get(P + "/reglages", ADMIN).andExpect(jsonPath("$.tauxFss").value(0.0165)).andExpect(jsonPath("$.tauxCnesst").value(0.031));
        http.put(P + "/reglages", ADMIN, "{\"tauxFss\":\"0.0165\",\"tauxCnesst\":\"0.031\",\"majorationSupplementaire\":\"0.25\"}").andExpect(status().isBadRequest());
        http.get(P + "/bareme", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.rrq.mga").value(74600));
        http.get(P + "/bareme?annee=2031", ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("aucun barème de paie publié pour 2031 : ajoutez paie/bareme-2031.json avant de payer cette année"));
        http.get(P + "/bareme?annee=deux", ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« annee » doit être une année, par exemple 2026"));
    }

    @Test
    @DisplayName("préparer : seules les feuilles approuvées de la période ; sans dossier, nommé et non payé ; rien n'est encore figé")
    void preparerUnCycle() throws Exception {
        http.put(P + "/dossiers/r-julie", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"aux-deux-semaines\",\"tauxHoraire\":\"30\"}").andExpect(status().isOk());
        String json = corps(http.post(P + "/cycles", ADMIN, PERIODE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cycle.etat").value("prepare")).andExpect(jsonPath("$.cycle.preparePar").value("paie.admin"))
                .andExpect(jsonPath("$.bulletins.length()").value(1))
                .andExpect(jsonPath("$.bulletins[0].heuresRegulieres").value(70)).andExpect(jsonPath("$.bulletins[0].heuresSupplementaires").value(2))
                .andExpect(jsonPath("$.bulletins[0].retenues.brut").value(2190.0)).andExpect(jsonPath("$.bulletins[0].feuilles.length()").value(2))
                .andExpect(jsonPath("$.sansDossier[0].nom").value("Marc Dubé")));
        BigDecimal brut = new BigDecimal(JsonPath.read(json, "$.bulletins[0].retenues.brut").toString());
        BigDecimal net = new BigDecimal(JsonPath.read(json, "$.bulletins[0].net").toString());
        BigDecimal retenues = new BigDecimal(JsonPath.read(json, "$.bulletins[0].totalRetenues").toString());
        assertThat(brut.subtract(retenues)).isEqualByComparingTo(net);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM essai_verrous").query(Integer.class).single()).isZero();
        http.post(P + "/cycles", ADMIN, "{\"debut\":\"2026-02-15\",\"fin\":\"2026-02-01\",\"datePaie\":\"2026-03-05\"}").andExpect(status().isBadRequest());
        http.post(P + "/cycles", ADMIN, "{\"debut\":20260215}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« debut » doit être un texte"));
        http.post(P + "/cycles", ADMIN, "{\"debut\":\"2031-02-15\",\"fin\":\"2031-02-28\",\"datePaie\":\"2031-03-05\"}").andExpect(status().isBadRequest());
        http.post(P + "/cycles", CHEF, PERIODE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("recalculer reprend un dossier changé ; traiter fige le cycle et paie les feuilles ; un cycle traité ne bouge plus")
    void traiter() throws Exception {
        String id = preparerCycle();
        http.put(P + "/dossiers/r-marc", ADMIN, "{\"mode\":\"horaire\",\"frequence\":\"aux-deux-semaines\",\"tauxHoraire\":\"25\"}").andExpect(status().isOk());
        http.post(P + "/cycles/" + id + "/recalculer", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.bulletins.length()").value(2))
                .andExpect(jsonPath("$.sansDossier.length()").value(0));
        http.get(P + "/cycles/" + id, ADMIN).andExpect(jsonPath("$.bulletins.length()").value(2)).andExpect(jsonPath("$.bulletins[0].nom").value("Julie Roy"));
        http.post(P + "/cycles/" + id + "/traiter", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("traite"))
                .andExpect(jsonPath("$.traitePar").value("paie.admin")).andExpect(jsonPath("$.traiteLe").value("2026-03-02T15:00:00Z"));
        assertThat(jdbc.sql("SELECT feuilleId FROM essai_verrous ORDER BY feuilleId").query(String.class).list())
                .containsExactly("f-julie-1", "f-julie-2", "f-marc");
        http.post(P + "/cycles/" + id + "/traiter", ADMIN, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("cycle traité : il est figé, avec les feuilles qu'il a payées"));
        http.post(P + "/cycles/" + id + "/recalculer", ADMIN, null).andExpect(status().isConflict());
        http.post(P + "/cycles/" + id + "/annuler", ADMIN, null).andExpect(status().isConflict());
        http.get(P + "/cycles", ADMIN).andExpect(jsonPath("$[0].etat").value("traite"));
        // Le cycle suivant ne retrouve pas les feuilles payées, et part du cumul de l'année.
        String suivant = JsonPath.read(corps(http.post(P + "/cycles", ADMIN, PERIODE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.bulletins.length()").value(0))), "$.cycle.id");
        http.post(P + "/cycles/" + suivant + "/annuler", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("annule"));
        http.get(P + "/cycles/inconnu", ADMIN).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("cycle de paie « inconnu » introuvable"));
        http.post(P + "/cycles/inconnu/traiter", ADMIN, null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("une feuille rappelée avant le traitement : le recalcul du traitement l'écarte, elle n'est pas payée")
    void feuilleRappeleeAvant() throws Exception {
        String id = preparerCycle();
        approuvees.remove("f-julie-2");
        http.post(P + "/cycles/" + id + "/traiter", ADMIN, null).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT feuilleId FROM essai_verrous").query(String.class).list()).containsExactly("f-julie-1");
        http.get(P + "/cycles/" + id, ADMIN).andExpect(jsonPath("$.bulletins[0].feuilles.length()").value(1));
    }

    @Test
    @DisplayName("une feuille rappelée au moment du verrou : 409, et rien n'est figé — ni le cycle, ni la feuille déjà verrouillée")
    void verrouRefuse() throws Exception {
        String id = preparerCycle();
        rappeleesAuVerrou.add("f-julie-2");
        http.post(P + "/cycles/" + id + "/traiter", ADMIN, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la feuille f-julie-2 n'est plus approuvée : elle n'entre pas dans cette paie ; "
                        + "recalculez le cycle, puis traitez-le de nouveau"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM essai_verrous").query(Integer.class).single()).isZero();
        http.get(P + "/cycles/" + id, ADMIN).andExpect(jsonPath("$.cycle.etat").value("prepare")).andExpect(jsonPath("$.bulletins.length()").value(1));
    }

    @Test
    @DisplayName("les remises et les relevés ne comptent que les cycles traités ; chacun lit ses propres bulletins, et seulement eux")
    void declarationsEtMesBulletins() throws Exception {
        String id = preparerCycle();
        http.get(P + "/remises?annee=2026", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.get(P + "/mes-bulletins", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.post(P + "/cycles/" + id + "/traiter", ADMIN, null).andExpect(status().isOk());
        http.get(P + "/remises", ADMIN).andExpect(jsonPath("$[0].mois").value("2026-03")).andExpect(jsonPath("$[0].echeance").value("2026-04-15"));
        http.get(P + "/releves?annee=2026", ADMIN).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].rl1.a").value(2190.0))
                .andExpect(jsonPath("$[0].t4.c14").value(2190.0));
        http.get(P + "/releves?annee=2025", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(P + "/mes-bulletins", JULIE).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].datePaie").value("2026-03-05"));
        http.get(P + "/mes-bulletins", MARC).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.get(P + "/remises", JULIE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le module coupé ferme tout, même ses propres bulletins")
    void moduleCoupe() throws Exception {
        jdbc.sql("UPDATE tenant_quotas SET modules = '[]' WHERE tenantId = ?").param(T).update();
        http.get(P + "/mes-bulletins", JULIE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'paie' not included in plan"));
        http.get(P + "/cycles", ADMIN).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un barème se lit une fois, puis se garde")
    void cacheDesBaremes() {
        Baremes b = new BaremesFichiers();
        Bareme bareme = b.pour(2026).orElseThrow();
        assertThat(b.pour(2026)).containsSame(bareme);
    }
}
