package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.FinDeCollecteAutomatique;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDisponibilites;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.requetes.LectureAttributionAutomatique;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurOrdonnanceurFinDeCollecte;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotDisponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaDisponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les disponibilités par HTTP</strong> (lot R13, jumeau de {@code effectifs-disponibilites.integration.test.ts}) :
 * deux paramédics déposent pendant la collecte — l'un se voit refuser un dépôt trop maigre puis passe avec un
 * avertissement, l'autre dépose un dépôt qui exige une approbation et reste hors du bassin tant qu'il n'est pas
 * approuvé. La collecte se ferme, et l'attribution de fin de collecte donne chaque place au bon candidat. Horloge
 * figée au 15 janvier 2027 ; la semaine planifiée est en 2030.
 */
@DisplayName("/api/effectifs/disponibilites — dépôts, approbation, bassin, attribution de fin de collecte")
class ControleurDisponibilitesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String API = E + "/disponibilites";
    private static final String PLANIF = E + "/planification";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_disponibilites\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete CHEF = employe("chef", "manager");
    private static final ContexteRequete JULIE = employe("julie", "employee");
    private static final ContexteRequete MARC = employe("marc", "employee");
    private static final ContexteRequete ANA = employe("ana", "employee");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireRegles regles;
    private GestionnaireDisponibilites depots;
    private LectureDisponibilites lecture;
    private LectureDisponibilites.Lectures planification;
    private DepotJournalEffectifs journal;
    private GestionnaireEquipages equipages;
    private LectureEquipages lectEqp;
    private LectureRessources lectRes;
    private LecturePerimetres perimetres;
    private EvaluateurRegles evaluateur;
    private DepotEquipages depotEqp;
    private DepotTaches taches;
    private GestionnairePeriodes periodes;
    private FinDeCollecteAutomatique finDeCollecte;
    private final Map<String, String> ids = new HashMap<>();

    private static ContexteRequete employe(String nom, String role) {
        return new ContexteRequete("usr-" + nom, nom, role, null, T);
    }

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

    private static Contexte ctx(String acteur, String role, String maintenant) {
        return new Contexte(T, acteur, role, maintenant);
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (String u : List.of("chef", "julie", "marc", "ana")) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', 'employee', 'x')").params("usr-" + u, u, T).update();
        }
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMil = new DepotMilieux(jdbc, schema);
        journal = new DepotJournalEffectifs(jdbc, seq, schema);
        DepotRegles depotRegles = new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc));
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMil, perimetres);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
        ControleurSocleEffectifs socle = new ControleurSocleEffectifs(garde, referentiels, new GestionnaireReferentiels(depotRef, referentiels, journal, seq),
                perimetres, new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMil, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres));

        DepotRessources depotRes = new DepotRessources(jdbc, schemaPlanif);
        DepotPeriodes depotPrd = new DepotPeriodes(jdbc, schemaPlanif);
        DepotPostes depotPos = new DepotPostes(jdbc, schemaPlanif);
        DepotQuotas depotQuo = new DepotQuotas(jdbc, schemaPlanif);
        depotEqp = new DepotEquipages(jdbc, schemaPlanif);
        lectRes = new LectureRessources(depotRes, perimetres);
        LecturePeriodes lectPrd = new LecturePeriodes(depotPrd, perimetres);
        LecturePostes lectPos = new LecturePostes(depotPos, perimetres);
        LectureQuotas lectQuo = new LectureQuotas(depotQuo, perimetres);
        lectEqp = new LectureEquipages(depotEqp, lectPrd, lectQuo);
        GestionnaireRessources ressources = new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq);
        RegistreExecuteurs executeurs = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, executeurs);
        AdaptateurOrdonnanceurFinDeCollecte ordonnanceur = new AdaptateurOrdonnanceurFinDeCollecte(taches, executeurs, jdbc, schema);
        periodes = new GestionnairePeriodes(depotPrd, lectPrd, new GestionnairePeriodes.Socle(referentiels, perimetres, journal, seq), ordonnanceur);
        GestionnairePostes postes = new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq);
        GestionnaireQuotas quotas = new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq);
        equipages = new GestionnaireEquipages(depotEqp, new GestionnaireEquipages.Lectures(lectEqp, lectPrd, lectPos, referentiels, milieux), journal, seq);
        evaluateur = new EvaluateurRegles(depotRegles, referentiels, journal, seq);
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal);
        regles = new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq);

        planification = new LectureDisponibilites.Lectures(lectRes, lectPrd, lectEqp, perimetres);
        lecture = new LectureDisponibilites(new DepotDisponibilites(jdbc, new SchemaDisponibilites(jdbc)), planification, evaluateur);
        depots = new GestionnaireDisponibilites(new GestionnaireDisponibilites.Lectures(lecture, planification, referentiels),
                new GestionnaireDisponibilites.Ecritures(new DepotDisponibilites(jdbc, new SchemaDisponibilites(jdbc)), evaluateur, journal, seq));
        GestionnaireAttributionFinDeCollecte attribution = new GestionnaireAttributionFinDeCollecte(planification, lecture, places, journal);
        finDeCollecte = new FinDeCollecteAutomatique(periodes, attribution);
        ordonnanceur.enregistrerExecuteur(finDeCollecte::executer);
        http = new ClientHttpEssai(socle, new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(ressources, periodes, postes, equipages, places, quotas)),
                new ControleurDisponibilitesEffectifs(garde, lecture, depots, attribution, new LectureAttributionAutomatique(lectPrd, ordonnanceur)));
        socle();
    }

    private String creer(String chemin, String json) throws Exception {
        return JsonPath.read(http.post(chemin, ADMIN, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private String equipage(String date, int debut, int fin, String mode) throws Exception {
        return creer(PLANIF + "/equipages", "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + date + "\",\"debut\":" + debut + ",\"fin\":" + fin
                + ",\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"" + mode
                + "\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}");
    }

    private void regle(String contexte, String cle, Map<String, Object> condition, String action, String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", contexte);
        e.put("cle", cle);
        e.put("condition", condition);
        e.put("action", action);
        e.put("message", message);
        e.put("effectifDepuis", "2027-02-01");
        regles.creer(ctx("usr-admin", "admin", "2027-01-15T10:00:00Z"), e);
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("unites", "{\"code\":\"OUEST\",\"libelleFr\":\"Ouest\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("statuts_emploi", "{\"code\":\"TPR\",\"libelleFr\":\"TPR\",\"comportement\":\"TEMPS_PARTIEL_REGULIER\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        for (String[] r : new String[][]{{"P1", "Julie", "julie", "3", "EST"}, {"P2", "Marc", "marc", "9", "EST"}, {"P9", "Ana", "ana", "20", "OUEST"}}) {
            ids.put(r[0], creer(PLANIF + "/ressources", "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1] + "\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"" + r[4]
                    + "\",\"statutEmploiCode\":\"TPR\",\"anciennete\":" + r[3] + ",\"utilisateurId\":\"usr-" + r[2] + "\"}"));
        }
        ids.put("periode", creer(PLANIF + "/periodes", "{\"nom\":\"Semaine 10\",\"uniteCode\":\"EST\",\"debut\":\"2030-03-04\",\"fin\":\"2030-03-10\"}"));
        equipage("2030-03-04", 360, 1080, "AUTOMATISE");
        equipage("2030-03-05", 360, 1080, "AUTOMATISE");
        regle("DISPONIBILITE", "minimum-quarts", Map.of("fait", "quartsParSemaine", "op", "<", "valeur", 2), "BLOQUER", "Au moins 2 quarts par semaine ({quartsParSemaine} offert)");
        regle("DISPONIBILITE", "peu-d-heures", Map.of("fait", "heuresParPeriode", "op", "<", "valeur", 30), "AVERTIR", "Seulement {heuresParPeriode} h offertes");
        regle("DISPONIBILITE", "fins-de-semaine", Map.of("fait", "finsDeSemaine", "op", ">", "valeur", 0), "EXIGER_APPROBATION",
                "Une fin de semaine offerte demande l'accord du chef");
    }

    private static String jour(String date, String type) {
        return "{\"date\":\"" + date + "\",\"debut\":360,\"fin\":1080,\"type\":\"" + type + "\"}";
    }

    private static String plages(String... p) {
        return "{\"plages\":[" + String.join(",", p) + "]}";
    }

    private String moi() {
        return API + "/moi/" + ids.get("periode");
    }

    private String depotDe(String matricule) {
        return API + "/periodes/" + ids.get("periode") + "/ressources/" + ids.get(matricule);
    }

    private void perimetre(String niveau) throws Exception {
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"" + niveau
                + "\",\"depuis\":\"2026-01-01\"}").andExpect(status().isCreated());
    }

    private void horsCollecte() throws Exception {
        http.put(moi(), JULIE, plages(jour("2030-03-04", "DISPONIBLE"))).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.matchesPattern(".*collecte.*n'est pas ouverte.*")));
        http.get(depotDe("P1"), ADMIN).andExpect(status().isOk()).andExpect(content().string("null"));
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-02-28\"}").andExpect(status().isOk());
        // L'ouverture de la collecte a planifié sa fin : l'attribution partira seule au lendemain de la date de fin (P-GH-4).
        String auto = API + "/periodes/" + ids.get("periode") + "/attribution-automatique";
        http.get(auto, JULIE).andExpect(status().isForbidden());
        http.get(auto, ADMIN).andExpect(status().isOk()).andExpect(content().json("{\"periodeId\":\"" + ids.get("periode") + "\",\"finCollecte\":\"2030-02-28\","
                + "\"echeanceLe\":\"2030-03-01T00:00:00.000Z\",\"statut\":\"en_attente\",\"derniere\":null}", true));
    }

    private void vueDeLEmploye() throws Exception {
        http.get(API + "/moi", JULIE).andExpect(jsonPath("$.ressource.matricule").value("P1"))
                .andExpect(jsonPath("$.periodes[*].id", Matchers.contains(ids.get("periode")))).andExpect(jsonPath("$.periodes[0].depot").doesNotExist())
                /*
                  **Les règles sont annoncées avant le dépôt** (E-GN-16, planche s02).

                  Elles ne se connaissaient qu'au refus : on déposait, on lisait le motif, on
                  corrigeait. La liste rendue est celle que le serveur **emploiera** pour décider —
                  la règle bloquante comprise, sans quoi la seule qui refuse resterait à découvrir
                  en se faisant refuser.
                */
                .andExpect(jsonPath("$.periodes[0].regles[*].cle",
                        Matchers.containsInAnyOrder("minimum-quarts", "peu-d-heures", "fins-de-semaine")))
                .andExpect(jsonPath("$.periodes[0].regles[?(@.cle=='minimum-quarts')].action").value("BLOQUER"))
                // Un message entier s'affiche…
                .andExpect(jsonPath("$.periodes[0].regles[?(@.cle=='fins-de-semaine')].message")
                        .value("Une fin de semaine offerte demande l'accord du chef"))
                // …un modèle à jetons, non : « ({quartsParSemaine} offert) » se lirait comme une panne.
                .andExpect(jsonPath("$.periodes[0].regles[?(@.cle=='minimum-quarts')].message").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$.periodes[0].regles[?(@.cle=='peu-d-heures')].porteeType").value("ORGANISME"));
        ContexteRequete sans = employe("sansressource", "employee");
        http.get(API + "/moi", sans).andExpect(content().json("{\"ressource\":null,\"periodes\":[]}"));
        http.put(moi(), sans, plages()).andExpect(status().isNotFound());
    }

    private void depotsDesEmployes() throws Exception {
        http.put(moi(), JULIE, plages(jour("2030-03-04", "DISPONIBLE"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.verdict.motifs[0].message").value("Au moins 2 quarts par semaine (1 offert)"));
        http.put(moi(), JULIE, plages(jour("2030-03-04", "DISPONIBLE"), jour("2030-03-05", "DISPONIBLE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.conformite").value("AVERTIE")).andExpect(jsonPath("$.motifs[0].message").value("Seulement 24 h offertes"))
                .andExpect(jsonPath("$.plages", Matchers.hasSize(2))).andExpect(jsonPath("$.deposePar").value("usr-julie"));
        http.put(moi(), JULIE, plages(jour("2030-04-01", "DISPONIBLE"))).andExpect(status().is(422));
        http.put(moi(), MARC, plages(jour("2030-03-04", "PREFERENCE"), jour("2030-03-05", "PREFERENCE"), jour("2030-03-09", "DISPONIBLE"),
                "{\"date\":\"2030-03-06\",\"debut\":0,\"fin\":1440,\"type\":\"INDISPONIBLE\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.conformite").value("APPROBATION_REQUISE"));
        http.put(moi(), ANA, plages(jour("2030-03-04", "DISPONIBLE"))).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« Ana » n'appartient pas à l'unité de cette période (EST)"));
        http.get(API + "/moi", MARC).andExpect(jsonPath("$.periodes[0].depot.conformite").value("APPROBATION_REQUISE"));
    }

    private void vueDuPlanificateur() throws Exception {
        String p = API + "/periodes/" + ids.get("periode");
        http.get(p, CHEF).andExpect(status().isForbidden());
        perimetre("PLANIFICATION");
        http.get(p, CHEF).andExpect(jsonPath("$[*].matricule", Matchers.contains("P1", "P2")))
                .andExpect(jsonPath("$[*].depot.conformite", Matchers.contains("AVERTIE", "APPROBATION_REQUISE"))).andExpect(jsonPath("$[0].depot.plages").value(2));
        http.get(depotDe("P1"), CHEF).andExpect(jsonPath("$.plages", Matchers.hasSize(2)));
        http.get(depotDe("P9"), ADMIN).andExpect(status().is(422));
        http.put(depotDe("P9"), CHEF, plages()).andExpect(status().isForbidden());
        http.get(API + "/moi", CHEF).andExpect(jsonPath("$.ressource").doesNotExist());
    }

    private void bassinEtApprobation() throws Exception {
        String eqp = http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages", ADMIN).andReturn().getResponse().getContentAsString();
        ids.put("placeLundi", JsonPath.read(eqp, "$[0].places[0].id"));
        String candidats = API + "/places/" + ids.get("placeLundi") + "/candidats";
        http.get(candidats, CHEF).andExpect(jsonPath("$.admissibles[*].nom", Matchers.contains("Julie")))
                .andExpect(jsonPath("$.exclus", Matchers.hasSize(1))).andExpect(jsonPath("$.exclus[0].ressourceId").value(ids.get("P2")))
                .andExpect(jsonPath("$.exclus[0].nom").value("Marc"))
                .andExpect(jsonPath("$.exclus[0].motif").value(Matchers.matchesPattern(".*en attente d'approbation.*fin de semaine.*")));
        String approuver = depotDe("P2") + "/approuver";
        http.post(approuver, CHEF, "").andExpect(status().isForbidden());
        perimetre("GESTION");
        http.post(approuver, CHEF, "").andExpect(status().isOk()).andExpect(jsonPath("$.conformite").value("CONFORME"))
                .andExpect(jsonPath("$.approuveLe").isString()).andExpect(jsonPath("$.approuvePar").value("usr-chef"));
        http.post(approuver, CHEF, "").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("ce dépôt n'attend aucune approbation"));
        http.post(depotDe("P9") + "/approuver", ADMIN, "").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("aucun dépôt pour cette personne dans cette période"));
        http.get(candidats, CHEF).andExpect(jsonPath("$.admissibles[*].nom", Matchers.contains("Marc", "Julie")))
                .andExpect(jsonPath("$.admissibles[*].preference", Matchers.contains(true, false)));
    }

    private void attributionFinDeCollecte() throws Exception {
        String attribuer = API + "/periodes/" + ids.get("periode") + "/attribuer";
        http.post(attribuer, CHEF, "").andExpect(status().is(422));
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        http.post(attribuer, CHEF, "").andExpect(content().json("{\"attribuees\":2,\"restees\":0}", true));
        http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages", ADMIN).andExpect(jsonPath("$[*].places[0].ressourceNom", Matchers.contains("Marc", "Marc")))
                .andExpect(jsonPath("$[*].places[0].attribueePar", Matchers.contains("MOTEUR", "MOTEUR")));
        http.get(E + "/journal?action=periode.attribution.disponibilites", ADMIN).andExpect(jsonPath("$[0].origine").value("SYSTEME"))
                .andExpect(jsonPath("$[0].acteurId").value("attribution-fin-de-collecte"));
        http.get(API + "/periodes/" + ids.get("periode") + "/attribution-automatique", CHEF).andExpect(jsonPath("$.derniere.le").isString())
                .andExpect(jsonPath("$.derniere.attribuees").value(2)).andExpect(jsonPath("$.derniere.restees").value(0));
        http.post(attribuer, CHEF, "").andExpect(content().json("{\"attribuees\":0,\"restees\":0}", true));
    }

    private void refusParLesRegles() throws Exception {
        regle("ATTRIBUTION", "plafond", Map.of("fait", "heuresSemaine", "op", ">", "valeur", 20), "BLOQUER", "Plafond de 20 h");
        ids.put("samedi", equipage("2030-03-09", 360, 1080, "AUTOMATISE"));
        http.post(API + "/periodes/" + ids.get("periode") + "/attribuer", CHEF, "").andExpect(content().json("{\"attribuees\":0,\"restees\":1}", true));
    }

    @Test
    @DisplayName("une période de collecte : refus hors collecte (E-GR-11), dépôt bloqué ou averti selon les règles (E-GN-13, E-GN-15), "
            + "approbation, bassin et motifs d'exclusion (E-GN-14), attribution de fin de collecte")
    void collecte() throws Exception {
        horsCollecte();
        vueDeLEmploye();
        depotsDesEmployes();
        vueDuPlanificateur();
        bassinEtApprobation();
        attributionFinDeCollecte();
        refusParLesRegles();
        exclusions();
        incident();
        apresLaFinDeCollecte();
    }

    /** Chaque motif d'exclusion du bassin, un par un. */
    private void exclusions() throws Exception {
        String samediPlace = JsonPath.read(http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages?date=2030-03-09", ADMIN).andReturn().getResponse()
                .getContentAsString(), "$[0].places[0].id");
        http.get(API + "/places/" + samediPlace + "/candidats", CHEF).andExpect(jsonPath("$.admissibles[*].nom", Matchers.contains("Marc")))
                .andExpect(jsonPath("$.exclus[0].motif").value("aucune plage de disponibilité ne couvre ce quart"));
        String mercredi = equipage("2030-03-06", 360, 1080, "MANUEL");
        String placeMercredi = JsonPath.read(http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages?date=2030-03-06", ADMIN).andReturn().getResponse()
                .getContentAsString(), "$[0].places[0].id");
        assertThat(mercredi).isNotBlank();
        http.get(API + "/places/" + placeMercredi + "/candidats", CHEF).andExpect(jsonPath("$.admissibles", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.exclus[*].motif", Matchers.containsInAnyOrder("aucune plage de disponibilité ne couvre ce quart",
                        "s'est déclarée indisponible sur ce quart")));
        equipage("2030-03-04", 600, 900, "MANUEL");
        String placeLundiMidi = JsonPath.read(http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages?date=2030-03-04", ADMIN).andReturn().getResponse()
                .getContentAsString(), "$[1].places[0].id");
        http.put(PLANIF + "/ressources/" + ids.get("P1"), ADMIN, "{\"nom\":\"Julie\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-julie\",\"actif\":false}")
                .andExpect(status().isOk());
        http.get(API + "/places/" + placeLundiMidi + "/candidats", CHEF).andExpect(jsonPath("$.admissibles", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.exclus[*].motif", Matchers.containsInAnyOrder("ressource inactive", "déjà affectée à un quart qui chevauche")));
        http.get(API + "/places/inconnue/candidats", CHEF).andExpect(status().isNotFound());
    }

    /** Un incident pendant l'attribution n'est pas un refus : il remonte, rien n'est tu. */
    private void incident() {
        GestionnairePlaces enPanne = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal) {
            @Override
            public Attribution attribuer(Contexte c, String placeId, Map<String, Object> entree, String systeme) {
                throw new IllegalStateException("disque plein");
            }
        };
        GestionnaireAttributionFinDeCollecte a = new GestionnaireAttributionFinDeCollecte(planification, lecture, enPanne, journal);
        assertThatThrownBy(() -> a.attribuer(ctx("usr-chef", "admin", "2027-01-15T10:00:00Z"), ids.get("periode"))).hasMessage("disque plein");
    }

    private void apresLaFinDeCollecte() {
        jdbc.sql("UPDATE eff_periodes SET etat = 'COLLECTE' WHERE id = ?").param(ids.get("periode")).update();
        Contexte julie = ctx("usr-julie", "employee", "2030-03-01T12:00:00Z");
        Map<String, Object> corps = Map.of("plages", List.of(Map.of("date", "2030-03-04", "debut", 360, "fin", 1080, "type", "DISPONIBLE")));
        assertThatThrownBy(() -> depots.deposer(julie, ids.get("periode"), ids.get("P1"), corps)).hasMessage("la collecte s'est terminée le 2030-02-28");
    }

    @Test
    @DisplayName("garde : sans le module effectifs_disponibilites, 403 nommé ; un employé n'a pas effectifs.manage")
    void gardes() throws Exception {
        http.get(API + "/periodes/" + ids.get("periode"), JULIE).andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_planification\"]", T).update();
        http.get(API + "/moi", JULIE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("effectifs_disponibilites")));
    }

    @Test
    @DisplayName("le planificateur dépose pour autrui ; une ressource sans statut d'emploi est jugée sans comportement")
    void depotPourAutrui() throws Exception {
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-02-28\"}").andExpect(status().isOk());
        jdbc.sql("UPDATE eff_ressources SET statutEmploiCode = NULL WHERE id = ?").param(ids.get("P1")).update();
        perimetre("PLANIFICATION");
        http.put(depotDe("P1"), CHEF, plages(jour("2030-03-04", "DISPONIBLE"), jour("2030-03-05", "DISPONIBLE"), jour("2030-03-06", "DISPONIBLE")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conformite").value("CONFORME")).andExpect(jsonPath("$.deposePar").value("usr-chef"));
        http.get(E + "/journal?action=disponibilites.deposees", ADMIN).andExpect(jsonPath("$[0].apres.plages").value(3))
                .andExpect(jsonPath("$[0].objetId").value(ids.get("periode") + ":" + ids.get("P1")));
    }
    @Test
    @DisplayName("la fin de collecte part d'elle-même à l'échéance, idempotente ; publiée, la période n'est plus touchée et sa tâche s'annule (P-GH-4)")
    void finDeCollecteAutomatique() throws Exception {
        String periode = ids.get("periode");
        http.get(API + "/periodes/" + periode + "/attribution-automatique", ADMIN).andExpect(content().json("{\"periodeId\":\"" + periode
                + "\",\"finCollecte\":null,\"echeanceLe\":null,\"statut\":null,\"derniere\":null}", true));
        http.post(PLANIF + "/periodes/" + periode + "/etat", ADMIN, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-02-28\"}").andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT echeanceLe || ' ' || statut || ' ' || creePar || ' ' || charge FROM taches_differees WHERE tenantId = ? AND type = ? AND cibleId = ?")
                .params(T, "effectifs_fin_collecte", periode).query(String.class).single())
                .isEqualTo("2030-03-01T00:00:00.000Z en_attente usr-admin {\"finCollecte\":\"2030-02-28\"}");
        http.put(moi(), JULIE, plages(jour("2030-03-04", "DISPONIBLE"), jour("2030-03-05", "DISPONIBLE"))).andExpect(status().isOk());
        // La veille de l'échéance, rien ne part ; à l'échéance, sans geste de personne, tout part.
        assertThat(taches.tic("2030-02-28T23:59:59.000Z", PolitiqueRetentative.PAR_DEFAUT, "essai").prises()).isZero();
        BilanTic tic = taches.tic("2030-03-01T00:00:01.000Z", PolitiqueRetentative.PAR_DEFAUT, "essai");
        assertThat(List.of(tic.prises(), tic.faites())).containsExactly(1, 1);
        http.get(PLANIF + "/periodes/" + periode + "/equipages", ADMIN).andExpect(jsonPath("$[*].places[0].ressourceNom", Matchers.contains("Julie", "Julie")));
        List<String> j = jdbc.sql("SELECT action || ' ' || acteurId || ' ' || origine FROM eff_journal WHERE tenantId = ? AND objetId = ? "
                + "AND action IN ('periode.etat', 'periode.attribution.disponibilites') ORDER BY rowid").params(T, periode).query(String.class).list();
        assertThat(j).containsExactly("periode.etat usr-admin UTILISATEUR", "periode.etat attribution-fin-de-collecte SYSTEME",
                "periode.attribution.disponibilites attribution-fin-de-collecte SYSTEME");
        http.get(API + "/periodes/" + periode + "/attribution-automatique", ADMIN).andExpect(jsonPath("$.statut").value("faite"))
                .andExpect(jsonPath("$.derniere.le").value("2030-03-01T00:00:01.000Z")).andExpect(jsonPath("$.derniere.attribuees").value(2));
        // Idempotente : rejouée, elle ne trouve plus rien ; une période publiée n'est plus touchée.
        assertThat(finDeCollecte.executer(T, periode, "2030-03-01T01:00:00.000Z")).contains(new GestionnaireAttributionFinDeCollecte.Bilan(0, 0));
        http.post(PLANIF + "/periodes/" + periode + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        assertThat(finDeCollecte.executer(T, periode, "2030-03-01T02:00:00.000Z")).isEmpty();
        String autre = creer(PLANIF + "/periodes", "{\"nom\":\"Avril\",\"uniteCode\":\"EST\",\"debut\":\"2030-04-07\",\"fin\":\"2030-04-13\"}");
        for (String etat : List.of("{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-03-01\"}", "{\"etat\":\"CONFECTION\"}", "{\"etat\":\"PUBLIEE\"}")) {
            http.post(PLANIF + "/periodes/" + autre + "/etat", ADMIN, etat).andExpect(status().isOk());
        }
        assertThat(jdbc.sql("SELECT statut FROM taches_differees WHERE tenantId = ? AND cibleId = ?").params(T, autre).query(String.class).single()).isEqualTo("annulee");
        http.get(API + "/periodes/" + autre + "/attribution-automatique", ADMIN).andExpect(jsonPath("$.statut").value("annulee"))
                .andExpect(jsonPath("$.derniere").isEmpty());
        // La période d'une autre unité ne se lit pas sans son périmètre.
        http.get(API + "/periodes/" + autre + "/attribution-automatique", CHEF).andExpect(status().isForbidden());
    }
}
