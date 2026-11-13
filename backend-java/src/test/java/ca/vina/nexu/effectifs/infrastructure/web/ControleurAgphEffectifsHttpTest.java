package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.ClotureAgph;
import ca.vina.nexu.effectifs.application.commandes.DecisionRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.DisponibilitesCampagneAgph;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.commandes.RevisionAgph;
import ca.vina.nexu.effectifs.application.commandes.TourDeRoleAgph;
import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
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
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphRemplacement;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRemplacementsAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotSemainesTypeAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>L'AGPH et les remplacements long terme par HTTP</strong> (lot R13, jumeau de
 * {@code effectifs-agph.integration.test.ts}). Trois paramédics, trois postes. Ana (rang 1) choisit à son tour ; Bob
 * (rang 2) laisse filer le sien : le choix se fait pour lui ; pour Cléo (rang 3), le planificateur tente un temps
 * complet que la règle réserve aux deux premiers rangs — refus motivé — puis son tour expire : passé. À la clôture,
 * chacun reçoit son statut. Horloge HTTP figée au 15 janvier 2027 ; le temps qui passe se rejoue par le service.
 */
@DisplayName("/api/effectifs/agph — campagnes AGPH et remplacements long terme")
class ControleurAgphEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String PLANIF_API = E + "/planification";
    private static final String A = E + "/agph";
    private static final String MAINTENANT = "2027-01-15T10:00:00.000Z";
    private static final String DEMAIN = "2027-01-16T10:00:00.000Z";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_agph\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete PLANIF = new ContexteRequete("usr-planif", "planif", "manager", null, T);
    private static final ContexteRequete ANA = employe("ana");
    private static final ContexteRequete BOB = employe("bob");
    private static final ContexteRequete DAN = employe("dan");
    private static final ContexteRequete EVE = employe("eve");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireRegles regles;
    private GestionnairePostes postes;
    private GestionnaireAgph campagnes;
    private GestionnaireRemplacementsAgph remplacements;
    private final Map<String, String> ids = new HashMap<>();

    private static ContexteRequete employe(String nom) {
        return new ContexteRequete("usr-" + nom, nom, "employee", null, T);
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

    private static Contexte ctx(String maintenant) {
        return new Contexte(T, "planif", "admin", maintenant);
    }

    private static String plus(String iso, int minutes) {
        return Agph.ajouterMinutes(iso, minutes);
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES ('usr-planif', 'planif', ?, 'x', 'manager', 'x')").param(T).update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
        SchemaAgph schemaAgph = new SchemaAgph(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMil = new DepotMilieux(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        DepotRegles depotRegles = new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc));
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMil, perimetres);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse(MAINTENANT), ZoneOffset.UTC));
        ControleurSocleEffectifs socle = new ControleurSocleEffectifs(garde, referentiels, new GestionnaireReferentiels(depotRef, referentiels, journal, seq),
                perimetres, new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMil, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres));

        DepotRessources depotRes = new DepotRessources(jdbc, schemaPlanif);
        DepotPeriodes depotPrd = new DepotPeriodes(jdbc, schemaPlanif);
        DepotPostes depotPos = new DepotPostes(jdbc, schemaPlanif);
        DepotQuotas depotQuo = new DepotQuotas(jdbc, schemaPlanif);
        DepotEquipages depotEqp = new DepotEquipages(jdbc, schemaPlanif);
        LectureRessources lectRes = new LectureRessources(depotRes, perimetres);
        LecturePeriodes lectPrd = new LecturePeriodes(depotPrd, perimetres);
        LecturePostes lectPos = new LecturePostes(depotPos, perimetres);
        LectureQuotas lectQuo = new LectureQuotas(depotQuo, perimetres);
        LectureEquipages lectEqp = new LectureEquipages(depotEqp, lectPrd, lectQuo);
        GestionnaireRessources ressources = new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq);
        GestionnairePeriodes periodes = new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq);
        postes = new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq);
        GestionnaireQuotas quotas = new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq);
        GestionnaireEquipages equipages = new GestionnaireEquipages(depotEqp, new GestionnaireEquipages.Lectures(lectEqp, lectPrd, lectPos, referentiels, milieux),
                journal, seq);
        EvaluateurRegles evaluateur = new EvaluateurRegles(depotRegles, referentiels, journal, seq);
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal);
        regles = new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq);

        DepotAgph depotAgph = new DepotAgph(jdbc, schemaAgph, schemaPlanif);
        LectureAgph lectAgph = new LectureAgph(depotAgph, lectPos, lectRes, perimetres);
        TourDeRoleAgph tour = new TourDeRoleAgph(depotAgph, new TourDeRoleAgph.Collaborateurs(lectAgph, lectRes, evaluateur, postes), journal);
        campagnes = new GestionnaireAgph(depotAgph, lectAgph, tour, new GestionnaireAgph.Socle(referentiels, perimetres, lectRes, journal, seq));
        ClotureAgph cloture = new ClotureAgph(depotAgph, lectAgph, tour, new ClotureAgph.Gestes(ressources, postes, lectPos, referentiels, perimetres, journal));
        DepotRemplacementsAgph depotRlt = new DepotRemplacementsAgph(jdbc, schemaAgph, schemaPlanif);
        DecisionRemplacementsAgph decision = new DecisionRemplacementsAgph(depotRlt, new DecisionRemplacementsAgph.Lectures(lectPos, lectRes, referentiels, evaluateur),
                new DecisionRemplacementsAgph.Quarts(lectEqp, depotEqp), journal);
        remplacements = new GestionnaireRemplacementsAgph(depotRlt, decision, new GestionnaireRemplacementsAgph.Socle(lectPos, lectRes, perimetres, journal, seq));
        RevisionAgph revision = new RevisionAgph(depotAgph, lectAgph, tour, new RevisionAgph.Appuis(lectRes, perimetres, postes, journal));
        DisponibilitesCampagneAgph disponibilites = new DisponibilitesCampagneAgph(new DepotSemainesTypeAgph(jdbc, schemaAgph), lectAgph,
                new DisponibilitesCampagneAgph.Appuis(lectRes, perimetres, referentiels, evaluateur, journal));

        http = new ClientHttpEssai(socle, new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(ressources, periodes, postes, equipages, places, quotas)),
                new ControleurAgphEffectifs(garde, new ControleurAgphEffectifs.Gestes(campagnes, cloture, remplacements, revision, disponibilites)));
        socle();
    }

    private String creer(String chemin, ContexteRequete qui, String json) throws Exception {
        return JsonPath.read(http.post(chemin, qui, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private String lire(String chemin, ContexteRequete qui) throws Exception {
        return http.get(chemin, qui).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private String ressource(String matricule, String nom, double anciennete, String compte, String corps, String statut) throws Exception {
        return creer(PLANIF_API + "/ressources", ADMIN, "{\"matricule\":\"" + matricule + "\",\"nom\":\"" + nom + "\",\"corpsEmploiCode\":\"" + corps
                + "\",\"uniteCode\":\"EST\"" + (statut == null ? "" : ",\"statutEmploiCode\":\"" + statut + "\"") + ",\"anciennete\":" + anciennete
                + (compte == null ? "" : ",\"utilisateurId\":\"" + compte + "\"") + "}");
    }

    private String poste(String code, String statut) throws Exception {
        return creer(PLANIF_API + "/postes", ADMIN, "{\"code\":\"" + code + "\",\"libelle\":\"Poste " + code + "\",\"uniteCode\":\"EST\",\"statutEmploiCode\":\""
                + statut + "\",\"corpsEmploiCode\":\"PARA\",\"milieuId\":\"" + ids.get("c12")
                + "\",\"natureCode\":\"REG\",\"cycle\":[[\"J\",\"J\",\"J\",null,null,null,null]],\"debutCycle\":\"2031-01-06\"}");
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("corps_emploi", "{\"code\":\"TECH\",\"libelleFr\":\"Techniciens\"}");
        ref("corps_emploi", "{\"code\":\"RMU\",\"libelleFr\":\"Répartiteurs\"}");
        ref("statuts_emploi", "{\"code\":\"TC\",\"libelleFr\":\"Temps complet\",\"comportement\":\"TEMPS_COMPLET\"}");
        ref("statuts_emploi", "{\"code\":\"TPR\",\"libelleFr\":\"Temps partiel régulier\",\"comportement\":\"TEMPS_PARTIEL_REGULIER\"}");
        ref("statuts_emploi", "{\"code\":\"TP\",\"libelleFr\":\"Temps partiel\",\"comportement\":\"TEMPS_PARTIEL_OCCASIONNEL\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("plages_horaires", "{\"code\":\"J\",\"libelleFr\":\"Jour\",\"attributs\":{\"debut\":360,\"fin\":1080}}");
        ids.put("c12", creer(E + "/milieux", ADMIN, "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        ids.put("P1", ressource("P1", "Ana", 20, "usr-ana", "PARA", "TP"));
        ids.put("P2", ressource("P2", "Bob", 10, "usr-bob", "PARA", "TP"));
        ids.put("P3", ressource("P3", "Cléo", 5, null, "PARA", "TP"));
        ids.put("T1", ressource("T1", "Dan", 30, "usr-dan", "TECH", "TP"));
        for (String[] p : new String[][]{{"A-TC", "TC"}, {"B-TPR", "TPR"}, {"C-TC", "TC"}}) {
            ids.put(p[0], poste(p[0], p[1]));
        }
        http.post(PLANIF_API + "/postes/publier", ADMIN, "{\"ids\":[\"" + ids.get("A-TC") + "\",\"" + ids.get("B-TPR") + "\",\"" + ids.get("C-TC") + "\"]}")
                .andExpect(status().isOk());
        regle("CHOIX_AGPH", "tc-deux-premiers", Map.of("tous", List.of(Map.of("fait", "statutOffert", "op", "=", "valeur", "TC"),
                Map.of("fait", "rang", "op", ">", "valeur", 2))), "Le temps complet est réservé aux deux premiers rangs (rang {rang})");
    }

    private void regle(String contexte, String cle, Map<String, Object> condition, String message) {
        regle(contexte, cle, condition, "BLOQUER", message);
    }

    private void regle(String contexte, String cle, Map<String, Object> condition, String action, String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", contexte);
        e.put("cle", cle);
        e.put("condition", condition);
        e.put("action", action);
        e.put("message", message);
        e.put("effectifDepuis", "2027-01-15");
        regles.creer(ctx(MAINTENANT), e);
    }

    private void perimetre(String niveau) throws Exception {
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-planif\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"" + niveau
                + "\",\"depuis\":\"2026-01-01\"}").andExpect(status().isCreated());
    }

    private String campagneOuverte() throws Exception {
        String camp = http.post(A + "/campagnes", ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031,\"delaiChoixMinutes\":30}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("PREPARATION")).andExpect(jsonPath("$.nom").value("AGPH 2031"))
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(camp, "$.id");
        http.post(A + "/campagnes/" + id + "/ouvrir", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("DEPOT"))
                .andExpect(jsonPath("$.ouverteLe").value(MAINTENANT));
        return id;
    }

    private String anaAChoisi() throws Exception {
        String camp = campagneOuverte();
        http.post(A + "/campagnes/" + camp + "/demarrer", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CHOIX"));
        http.post(A + "/campagnes/" + camp + "/choix", ANA, "{\"posteId\":\"" + ids.get("B-TPR") + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.choix[0].ressourceId").value(ids.get("P1"))).andExpect(jsonPath("$.choix[0].posteCode").value("B-TPR"))
                .andExpect(jsonPath("$.choix[0].mode").value("EMPLOYE")).andExpect(jsonPath("$.rangs[1].etat").value("A_SON_TOUR"));
        return camp;
    }

    @Test
    @DisplayName("gardes : sans effectifs.manage, 403 ; sans le module effectifs_agph, 403 nommé")
    void gardes() throws Exception {
        http.post(A + "/campagnes", ANA, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031}").andExpect(status().isForbidden());
        http.post(A + "/remplacements", ANA, "{}").andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_planification\"]", T).update();
        http.get(A + "/campagnes", ADMIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("effectifs_agph")));
    }

    @Test
    @DisplayName("crée et ouvre une campagne : validations, doublon, gestes dans l'ordre, rangs figés par ancienneté")
    void creerEtOuvrir() throws Exception {
        String c = A + "/campagnes";
        http.post(c, ADMIN, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« uniteCode » est obligatoire"));
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« corpsEmploiCode » est obligatoire"));
        http.post(c, ADMIN, "{\"uniteCode\":\"XX\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031}").andExpect(status().is(422));
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":\"x\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« annee » : une année de quatre chiffres"));
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":1999}").andExpect(status().is(422));
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031,\"delaiChoixMinutes\":3}").andExpect(status().is(422));
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031,\"nom\":\"  \"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« nom » est obligatoire"));
        http.post(c, PLANIF, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031}").andExpect(status().isForbidden());
        String camp = campagneOuverte();
        http.post(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("une campagne 2031 existe déjà pour EST (PARA)"));
        http.post(c + "/" + camp + "/ouvrir", ADMIN, null).andExpect(status().is(422));

        String rmu = creer(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"RMU\",\"annee\":2031,\"nom\":\"AGPH RMU\"}");
        http.post(c + "/" + rmu + "/demarrer", ADMIN, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une campagne en preparation ne passe pas à choix : il faut d'abord ouvrir"));
        http.post(c + "/" + rmu + "/ouvrir", ADMIN, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("aucune ressource active de RMU dans EST : rien à ordonner"));
        String tech = creer(c, ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"TECH\",\"annee\":2031}");
        http.post(c + "/" + tech + "/ouvrir", ADMIN, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("aucun poste publié et libre de TECH dans EST : rien à offrir"));
        http.post(c + "/" + tech + "/ouvrir", PLANIF, null).andExpect(status().isForbidden());
        http.post(c + "/inconnue/ouvrir", ADMIN, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("campagne « inconnue » introuvable"));

        // L'ancienneté est figée : la modifier après l'ouverture ne change aucun rang.
        jdbc.sql("UPDATE eff_ressources SET anciennete = 99 WHERE id = ?").param(ids.get("P3")).update();
        String v = lire(c + "/" + camp, ADMIN);
        assertThat(JsonPath.<List<String>>read(v, "$.rangs[*].nom")).containsExactly("Ana", "Bob", "Cléo");
        assertThat(JsonPath.<List<Integer>>read(v, "$.rangs[*].rang")).containsExactly(1, 2, 3);
        assertThat(JsonPath.<List<Double>>read(v, "$.rangs[*].anciennete")).containsExactly(20.0, 10.0, 5.0);
        assertThat(JsonPath.<List<String>>read(lire(c, ADMIN), "$[*].id")).containsExactlyInAnyOrder(camp, rmu, tech);
        http.get(c + "/inconnue", ADMIN).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("à son tour, Ana voit les postes et en choisit un ; Bob attend le sien ; ce qu'un choix refuse (P-CH-1, P-CH-2)")
    void tourDeRole() throws Exception {
        String camp = campagneOuverte();
        String choix = A + "/campagnes/" + camp + "/choix";
        http.post(choix, ANA, "{\"posteId\":\"" + ids.get("B-TPR") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la campagne est en depot : le tour de rôle n'est pas en cours"));
        http.post(A + "/campagnes/" + camp + "/demarrer", ADMIN, null).andExpect(status().isOk());
        String d = lire(A + "/campagnes/" + camp + "/postes-disponibles", ANA);
        assertThat(JsonPath.<Boolean>read(d, "$.peutChoisir")).isTrue();
        assertThat(JsonPath.<Integer>read(d, "$.monRang.rang")).isEqualTo(1);
        assertThat(JsonPath.<List<String>>read(d, "$.postes[*].code")).containsExactly("A-TC", "B-TPR", "C-TC");
        assertThat(JsonPath.<List<Boolean>>read(d, "$.postes[*].admissible")).containsExactly(true, true, true);
        http.post(choix, BOB, "{\"posteId\":\"" + ids.get("A-TC") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce n'est pas encore le tour de Bob (rang 2 ; tour en cours : rang 1)"));
        // Hors de la campagne et hors du périmètre : rien à voir, rien à divulguer.
        http.get(A + "/campagnes/" + camp + "/postes-disponibles", DAN).andExpect(status().isNotFound());
        http.get(A + "/campagnes/" + camp + "/postes-disponibles?ressourceId=" + ids.get("T1"), ADMIN)
                .andExpect(jsonPath("$.postes[0].motifs[0]").value("vous ne figurez pas dans cette campagne")).andExpect(jsonPath("$.monRang").isEmpty())
                .andExpect(jsonPath("$.peutChoisir").value(false));
        http.get(A + "/campagnes/" + camp + "/postes-disponibles", ADMIN).andExpect(jsonPath("$.peutChoisir").value(false));
        http.post(choix, ADMIN, "{\"posteId\":\"x\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("votre compte n'est relié à aucune ressource planifiable"));
        http.post(choix, ADMIN, "{\"ressourceId\":\"" + ids.get("T1") + "\",\"posteId\":\"x\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("Dan ne figure pas dans cette campagne"));
        http.post(choix, ANA, "{}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« posteId » est obligatoire"));
        http.post(choix, ANA, "{\"posteId\":\"inconnu\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le poste « inconnu » n'est pas offert ou a déjà été choisi"));
        http.post(choix, ANA, "{\"ressourceId\":\"" + ids.get("P1") + "\",\"posteId\":\"" + ids.get("B-TPR") + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.choix[0].mode").value("EMPLOYE"));
        http.post(choix, ANA, "{\"posteId\":\"" + ids.get("A-TC") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Ana a déjà été servi (poste choisi) : un seul choix par rang"));
    }

    @Test
    @DisplayName("le planificateur saisit pour autrui dans son périmètre ; une exigence de corps refuse en 422, une règle en 409")
    void pourAutrui() throws Exception {
        String camp = anaAChoisi();
        String choix = A + "/campagnes/" + camp + "/choix";
        http.post(choix, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":\"" + ids.get("A-TC") + "\"}").andExpect(status().isNotFound());
        perimetre("PLANIFICATION");
        http.post(choix, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":\"" + ids.get("A-TC") + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.choix[*].mode", Matchers.containsInAnyOrder("EMPLOYE", "PLANIFICATEUR"))).andExpect(jsonPath("$.rangs[2].etat").value("A_SON_TOUR"));
        // Cléo change de corps : l'exigence dure tranche avant le moteur.
        jdbc.sql("UPDATE eff_ressources SET corpsEmploiCode = 'TECH' WHERE id = ?").param(ids.get("P3")).update();
        http.get(A + "/campagnes/" + camp + "/postes-disponibles?ressourceId=" + ids.get("P3"), ADMIN)
                .andExpect(jsonPath("$.postes[0].motifs[0]").value("le poste C-TC requiert le corps PARA")).andExpect(jsonPath("$.postes[0].admissible").value(false));
        http.post(choix, PLANIF, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"posteId\":\"" + ids.get("C-TC") + "\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le poste C-TC requiert le corps PARA"));
        jdbc.sql("UPDATE eff_ressources SET corpsEmploiCode = 'PARA' WHERE id = ?").param(ids.get("P3")).update();
        http.post(choix, PLANIF, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"posteId\":\"" + ids.get("C-TC") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Le temps complet est réservé aux deux premiers rangs (rang 3)"))
                .andExpect(jsonPath("$.verdict.action").value("BLOQUER"));
        http.get(A + "/campagnes", PLANIF).andExpect(jsonPath("$[*].id", Matchers.contains(camp)));
    }

    /** Le tour de Bob expire, puis celui de Cléo ; rend l'identifiant de la campagne en attribution. */
    private String toursExpires() throws Exception {
        String camp = anaAChoisi();
        String bobFin = campagnes.lire(ctx(MAINTENANT), camp).rangs().get(1).tourFin();
        LectureAgph.VueCampagne apresBob = campagnes.lire(ctx(plus(bobFin, 1)), camp);
        assertThat(apresBob.choix().stream().map(x -> x.ressourceId() + "/" + x.posteCode() + "/" + x.mode()).toList())
                .containsExactly(ids.get("P1") + "/B-TPR/EMPLOYE", ids.get("P2") + "/A-TC/AUTOMATIQUE");
        assertThat(apresBob.rangs().get(2).etat()).isEqualTo(Agph.EtatRang.A_SON_TOUR);
        assertThat(apresBob.rangs().get(2).tourDebut()).isEqualTo(bobFin);
        // Le planificateur saisit pour Cléo un temps complet : la règle le réserve aux deux premiers rangs.
        Map<String, Object> pourCleo = Map.of("ressourceId", ids.get("P3"), "posteId", ids.get("C-TC"));
        assertThatThrownBy(() -> campagnes.choisir(ctx(plus(bobFin, 2)), camp, pourCleo)).isInstanceOf(RefusParRegleException.class)
                .hasMessage("Le temps complet est réservé aux deux premiers rangs (rang 3)");
        assertThatThrownBy(() -> campagnes.choisir(new Contexte(T, "usr-dan", "employee", plus(bobFin, 2)), camp, pourCleo))
                .isInstanceOf(IntrouvableException.class);
        LectureAgph.VueCampagne fin = campagnes.lire(ctx(plus(bobFin, 31)), camp);
        assertThat(fin.etat()).isEqualTo(Agph.EtatCampagne.ATTRIBUTION);
        assertThat(fin.rangs().stream().map(r -> r.etat().name()).toList()).containsExactly("A_CHOISI", "A_CHOISI", "PASSE");
        return camp;
    }

    @Test
    @DisplayName("le tour de Bob expire : le choix se fait pour lui ; Cléo se voit refuser un temps complet, puis passe (P-CH-3, P-CH-4)")
    void expiration() throws Exception {
        toursExpires();
        List<String> actions = jdbc.sql("SELECT action FROM eff_journal WHERE tenantId = ? AND action LIKE 'agph.%' AND acteurId = ? ORDER BY action")
                .params(T, TourDeRoleAgph.MECANISME).query(String.class).list();
        assertThat(actions).containsExactly("agph.campagne.attribution", "agph.choix", "agph.tour.passe");
    }

    @Test
    @DisplayName("clore : statuts TPR, TC et temps partiel ; le poste resté libre devient vacant (E-CH-10, P-CH-5, P-CH-6, P-CH-7)")
    void clore() throws Exception {
        String camp = toursExpires();
        String clore = A + "/campagnes/" + camp + "/clore";
        http.post(clore, PLANIF, null).andExpect(status().isForbidden());
        jdbc.sql("UPDATE eff_referentiels SET comportement = 'TEMPS_COMPLET' WHERE tenantId = ? AND code = 'TP'").param(T).update();
        http.post(clore, ADMIN, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]")
                .value("aucun statut d'emploi « temps partiel occasionnel » en vigueur : définissez-le dans les référentiels avant de clore"));
        jdbc.sql("UPDATE eff_referentiels SET comportement = 'TEMPS_PARTIEL_OCCASIONNEL' WHERE tenantId = ? AND code = 'TP'").param(T).update();
        http.post(clore, ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CLOSE")).andExpect(jsonPath("$.closeLe").value(MAINTENANT))
                .andExpect(content().json("{\"postesNonPourvus\":[{\"posteId\":\"" + ids.get("C-TC") + "\",\"posteCode\":\"C-TC\",\"etat\":\"VACANT\"}]}"));
        // Le transfert à la gestion régulière se lit au journal, au nom du mécanisme.
        Map<String, Object> t = jdbc.sql("SELECT acteurId, origine, apres FROM eff_journal WHERE tenantId = ? AND action = 'agph.postes.transferes'").param(T)
                .query().singleRow();
        assertThat(t).containsEntry("acteurId", TourDeRoleAgph.MECANISME).containsEntry("origine", "SYSTEME")
                .containsEntry("apres", "{\"vers\":\"gestion régulière\",\"postes\":[\"C-TC\"]}");
        http.put(A + "/campagnes/" + camp + "/parametres", ADMIN, "{\"budgetHeuresSemaine\":10}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la campagne est close : ses paramètres ne changent plus"));
        assertThat(List.of(statut("P1"), statut("P2"), statut("P3"))).containsExactly("TPR", "TC", "TP");
        assertThat(List.of(poste("A-TC"), poste("B-TPR"), poste("C-TC"))).containsExactly("ATTRIBUE/" + ids.get("P2"), "ATTRIBUE/" + ids.get("P1"), "VACANT/null");
        http.post(clore, ADMIN, null).andExpect(status().is(422));
        http.get(A + "/campagnes", ANA).andExpect(jsonPath("$[*].id", Matchers.contains(camp)));
        http.get(A + "/campagnes", DAN).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(A + "/campagnes/" + camp, ANA).andExpect(status().isOk());
    }

    /**
     * Ce que l'AGPH laisse ouvert passe à la gestion régulière, et ce passage se lit dans les quarts
     * eux-mêmes : le poste resté vacant engendre des places sans titulaire, et chaque quart tombe
     * sous son minimum, là où le comblement les montre. L'attribution qui les reprend en fin de
     * collecte est prouvée par l'essai des disponibilités (P-GH-4).
     */
    @Test
    @DisplayName("à l'issue de l'AGPH, les quarts du poste resté vacant naissent sans titulaire, sous le minimum, et la gestion régulière les prend en charge (E-GR-2)")
    void quartsDuPosteVacant() throws Exception {
        String camp = toursExpires();
        http.post(A + "/campagnes/" + camp + "/clore", ADMIN, null).andExpect(status().isOk());
        String periode = creer(PLANIF_API + "/periodes", ADMIN, "{\"nom\":\"Mars\",\"uniteCode\":\"EST\",\"debut\":\"2031-03-03\",\"fin\":\"2031-03-09\"}");
        http.post(PLANIF_API + "/periodes/" + periode + "/generer", ADMIN, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.equipagesCrees").value(3)).andExpect(jsonPath("$.placesCreees").value(9));
        String c = lire(PLANIF_API + "/periodes/" + periode + "/comblement", ADMIN);
        assertThat(List.<Object>of(JsonPath.read(c, "$.places"), JsonPath.read(c, "$.pourvues"))).containsExactly(9, 6);
        assertThat((List<Object>) JsonPath.read(c, "$.sousMinimum[*].date")).containsExactly("2031-03-03", "2031-03-04", "2031-03-05");
        assertThat((List<Object>) JsonPath.read(c, "$.sousMinimum[*].pourvues")).containsExactly(2, 2, 2);
        String equipages = lire(PLANIF_API + "/periodes/" + periode + "/equipages", ADMIN);
        List<Object> vacantes = JsonPath.read(equipages, "$[*].places[?(@.posteId == '" + ids.get("C-TC") + "')].ressourceId");
        assertThat(vacantes).hasSize(3).containsOnlyNulls();
    }

    private String statut(String cle) {
        return jdbc.sql("SELECT statutEmploiCode FROM eff_ressources WHERE id = ?").param(ids.get(cle)).query(String.class).single();
    }

    private String poste(String cle) {
        return jdbc.sql("SELECT etat || '/' || COALESCE(titulaireId, 'null') FROM eff_postes WHERE id = ?").param(ids.get(cle)).query(String.class).single();
    }

    private String equipage(String periode, String date) throws Exception {
        String e = http.post(PLANIF_API + "/equipages", ADMIN, "{\"periodeId\":\"" + periode + "\",\"date\":\"" + date
                        + "\",\"debut\":360,\"fin\":1080,\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12")
                        + "\",\"modeAttribution\":\"MANUEL\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String place = JsonPath.read(e, "$.places[0].id");
        jdbc.sql("UPDATE eff_places SET posteId = ? WHERE id = ?").params(ids.get("C-TC"), place).update();
        return place;
    }

    private String remplacement(String posteCle, String extra) {
        return "{\"posteId\":\"" + ids.get(posteCle) + "\",\"debut\":\"2031-06-02\",\"fin\":\"2031-06-08\",\"finManifestation\":\"" + DEMAIN + "\"" + extra + "}";
    }

    @Test
    @DisplayName("un remplacement long terme : manifestations, admissibilité, attribution au plus ancien sur les quarts vacants (E-CH-7, E-CH-8, P-CH-9)")
    void remplacementAttribue() throws Exception {
        String periode = creer(PLANIF_API + "/periodes", ADMIN, "{\"nom\":\"Juin\",\"uniteCode\":\"EST\",\"debut\":\"2031-06-02\",\"fin\":\"2031-06-08\"}");
        http.post(PLANIF_API + "/periodes/" + periode + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        equipage(periode, "2031-06-02");
        equipage(periode, "2031-06-03");
        // Un second quart au même moment : il chevaucherait le premier, l'attribution le saute.
        String doublon = equipage(periode, "2031-06-02");
        String r = A + "/remplacements";
        String o = http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("OUVERT")).andExpect(jsonPath("$.posteCode").value("C-TC")).andExpect(jsonPath("$.interesses").value(0))
                .andReturn().getResponse().getContentAsString();
        String rlt = JsonPath.read(o, "$.id");
        refusDOuverture(r);

        http.post(r + "/" + rlt + "/interet", BOB, null).andExpect(status().isOk()).andExpect(jsonPath("$.interesses").value(1));
        http.post(r + "/" + rlt + "/interet", ANA, null).andExpect(status().isOk()).andExpect(jsonPath("$.interesses").value(2));
        http.post(r + "/" + rlt + "/interet", DAN, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("introuvable")));
        http.post(r + "/" + rlt + "/interet", ADMIN, null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur"));
        ressource("A1", "Admin", 1, "usr-admin", "TECH", null);
        http.post(r + "/" + rlt + "/interet", ADMIN, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("le poste C-TC requiert le corps PARA"));
        // Eve, sans statut d'emploi, se manifeste puis se retire : le comportement de son statut est vide.
        ressource("E1", "Eve", 1, "usr-eve", "PARA", null);
        http.post(r + "/" + rlt + "/interet", EVE, null).andExpect(status().isOk()).andExpect(jsonPath("$.interesses").value(3));
        http.delete(r + "/" + rlt + "/interet", EVE).andExpect(status().isOk());
        http.delete(r + "/" + rlt + "/interet", ANA).andExpect(status().isOk()).andExpect(jsonPath("$.interesses").value(1));
        http.delete(r + "/" + rlt + "/interet", ANA).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("vous n'avez pas manifesté d'intérêt pour ce remplacement"));
        http.post(r + "/" + rlt + "/interet", ANA, null).andExpect(status().isOk());
        http.get(r + "/" + rlt + "/interesses", ADMIN).andExpect(jsonPath("$[*].nom", Matchers.contains("Ana", "Bob")));
        http.get(r + "/" + rlt + "/interesses", ANA).andExpect(status().isForbidden());
        http.get(r, ANA).andExpect(jsonPath("$[*].id", Matchers.contains(rlt)));
        http.get(r + "?etat=OUVERT", ADMIN).andExpect(jsonPath("$[*].id", Matchers.contains(rlt)));
        http.get(r, PLANIF).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(r + "/" + rlt, PLANIF).andExpect(status().isNotFound());
        http.post(r + "/" + rlt + "/fermer", PLANIF, null).andExpect(status().isNotFound());

        http.post(r + "/" + rlt + "/fermer", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("ATTRIBUE"))
                .andExpect(jsonPath("$.attribueANom").value("Ana")).andExpect(jsonPath("$.placesAttribuees").value(2)).andExpect(jsonPath("$.decideLe").value(MAINTENANT));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_places WHERE ressourceId = ? AND attribueePar = 'MOTEUR'").param(ids.get("P1")).query(Integer.class).single())
                .isEqualTo(2);
        assertThat(jdbc.sql("SELECT ressourceId FROM eff_places WHERE id = ?").param(doublon).query(String.class).optional()).isEmpty();
        http.post(r + "/" + rlt + "/interet", BOB, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("la fenêtre de manifestation est fermée"));
        http.delete(r + "/" + rlt + "/interet", BOB).andExpect(status().isConflict());
        http.post(r + "/" + rlt + "/fermer", ADMIN, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("ce remplacement est déjà décidé"));
    }

    private void refusDOuverture(String r) throws Exception {
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"ABSENCE\"")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("un remplacement d'absence nomme l'absence remplacée (« referenceId »)"));
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"").replace("\"2031-06-08\"", "\"2031-06-01\"")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la fin (2031-06-01) précède le début (2031-06-02)"));
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"AGPH\"")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« origine » : ABSENCE, POSTE_VACANT"));
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"").replace(DEMAIN, "bientôt")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« finManifestation » : un instant ISO 8601"));
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"").replace(DEMAIN, "2020-01-01T00:00:00Z")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la fenêtre de manifestation se fermerait dans le passé"));
        http.post(r, ADMIN, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"").replace(DEMAIN, "2031-06-03T00:00:00Z")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la fenêtre doit se fermer au plus tard le premier jour du remplacement (2031-06-02)"));
        ids.put("D-TC", poste("D-TC", "TC"));
        http.post(r, ADMIN, remplacement("D-TC", ",\"origine\":\"POSTE_VACANT\"")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le poste « D-TC » n'est pas publié"));
        http.post(r, ADMIN, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("poste «  » introuvable"));
        http.post(r, PLANIF, remplacement("C-TC", ",\"origine\":\"POSTE_VACANT\"")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sans intéressé, le remplacement demeure non comblé et se transfère ; la fenêtre se ferme d'elle-même (E-CH-9, E-CH-10)")
    void remplacementTransfere() throws Exception {
        postes.attribuerTitulaire(ctx(MAINTENANT), ids.get("A-TC"), ids.get("P2"), null);
        String x = creer(A + "/remplacements", ADMIN, "{\"posteId\":\"" + ids.get("A-TC") + "\",\"origine\":\"ABSENCE\",\"referenceId\":\" eabs-1 \","
                + "\"debut\":\"2031-07-01\",\"fin\":\"2031-07-31\",\"finManifestation\":\"" + DEMAIN + "\"}");
        http.post(A + "/remplacements/" + x + "/interet", BOB, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("on ne se remplace pas soi-même"));
        String t = http.post(A + "/remplacements/" + x + "/fermer", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("NON_COMBLE"))
                .andExpect(jsonPath("$.attribueA").isEmpty()).andExpect(jsonPath("$.referenceId").value("eabs-1")).andExpect(jsonPath("$.quartsTransferes").value(0))
                .andExpect(jsonPath("$.motifNonComble").isEmpty()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(t, "$.transfereLe")).isEqualTo(JsonPath.<String>read(t, "$.decideLe")).isEqualTo(MAINTENANT);
        assertThat(jdbc.sql("SELECT action FROM eff_journal WHERE tenantId = ? AND objetId = ? ORDER BY rowid").params(T, x).query(String.class).list())
                .containsExactly("remplacement.ouvert", "remplacement.fenetre.fermee", "remplacement.non_comble", "remplacement.transfere");
        // Une fenêtre qui arrive à terme se traite au premier regard qui suit.
        AgphRemplacement y = remplacements.ouvrir(ctx(MAINTENANT), Map.of("posteId", ids.get("B-TPR"), "origine", "POSTE_VACANT", "debut", "2031-08-01",
                "fin", "2031-08-31", "finManifestation", DEMAIN));
        assertThat(remplacements.lire(ctx(plus(DEMAIN, 1)), y.id()).etat()).isEqualTo(Agph.EtatRemplacement.NON_COMBLE);
        assertThat(remplacements.lister(ctx(plus(DEMAIN, 1)), "NON_COMBLE").stream().map(AgphRemplacement::id).toList()).containsExactlyInAnyOrder(x, y.id());
        assertThat(remplacements.lister(ctx(plus(DEMAIN, 1)), " ")).hasSize(2);
    }

    @Test
    @DisplayName("une règle INTERET_REMPLACEMENT refuse la manifestation : 409 et le verdict ; à la fermeture, personne n'est retenu")
    void remplacementRefuseParRegle() throws Exception {
        String x = creer(A + "/remplacements", ADMIN, remplacement("B-TPR", ",\"origine\":\"POSTE_VACANT\""));
        http.post(A + "/remplacements/" + x + "/interet", BOB, null).andExpect(status().isOk());
        regle("INTERET_REMPLACEMENT", "pas-de-tp", Map.of("fait", "statutEmploi", "op", "=", "valeur", "TEMPS_PARTIEL_OCCASIONNEL"),
                "Le temps partiel occasionnel ne remplace pas à long terme");
        http.post(A + "/remplacements/" + x + "/interet", ANA, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Le temps partiel occasionnel ne remplace pas à long terme"))
                .andExpect(jsonPath("$.verdict.motifs[0].cle").value("pas-de-tp"));
        http.post(A + "/remplacements/" + x + "/fermer", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("NON_COMBLE"));
    }
    private static String j(int jour, int debut, int fin, String type) {
        return "{\"jour\":" + jour + ",\"debut\":" + debut + ",\"fin\":" + fin + ",\"type\":\"" + type + "\"}";
    }

    private static String j(int jour) {
        return j(jour, 360, 1080, "DISPONIBLE");
    }

    private static String semaine(String... plages) {
        return "{\"plages\":[" + String.join(",", plages) + "]}";
    }

    @Test
    @DisplayName("pendant le dépôt, chacun émet sa semaine type, jugée par les règles de convention et le budget fixé par la planification (E-CH-1)")
    void semaineType() throws Exception {
        String camp = http.post(A + "/campagnes", ADMIN, "{\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2031,\"budgetHeuresSemaine\":\"\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.budgetHeuresSemaine").isEmpty()).andExpect(jsonPath("$.reviseeLe").isEmpty())
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(camp, "$.id");
        String d = A + "/campagnes/" + id + "/disponibilites";
        http.put(d + "/" + ids.get("P3"), ADMIN, semaine(j(1), j(2))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la campagne est en preparation : les disponibilités se déposent pendant le dépôt ou la révision"));
        http.post(A + "/campagnes/" + id + "/ouvrir", ADMIN, null).andExpect(status().isOk());
        regle("DEPOT_AGPH", "minimum-quarts", Map.of("fait", "quartsParSemaine", "op", "<", "valeur", 2), "BLOQUER", "Au moins 2 quarts par semaine ({quartsParSemaine} offert)");
        regle("DEPOT_AGPH", "duree-quart", Map.of("fait", "dureeMinutes", "op", "<", "valeur", 480), "BLOQUER", "Un quart dure au moins 8 h");
        regle("DEPOT_AGPH", "budget", Map.of("fait", "tauxBudget", "op", ">", "valeur", 100), "AVERTIR", "Les disponibilités offertes dépassent le budget ({tauxBudget} %)");
        // Le budget se paramètre par la planification, pas par l'employé.
        String p = A + "/campagnes/" + id + "/parametres";
        http.put(p, ANA, "{\"budgetHeuresSemaine\":40}").andExpect(status().isForbidden());
        http.put(p, ADMIN, "{\"budgetHeuresSemaine\":-1}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« budgetHeuresSemaine » : un nombre d'heures de 0 exclu à 100000"));
        http.put(p, ADMIN, "{\"budgetHeuresSemaine\":60}").andExpect(status().isOk()).andExpect(jsonPath("$.budgetHeuresSemaine").value(60.0));
        http.put(d + "/moi", ANA, semaine(j(1))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.verdict.motifs[*].message", Matchers.contains("Au moins 2 quarts par semaine (1 offert)")));
        http.put(d + "/moi", ANA, semaine(j(1), j(2, 360, 600, "DISPONIBLE"))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Un quart dure au moins 8 h"));
        http.put(d + "/moi", ANA, "{}").andExpect(status().is(422));
        http.put(d + "/moi", ANA, semaine(j(1), j(2), j(3, 0, 1440, "DISPONIBLE"))).andExpect(status().isOk()).andExpect(jsonPath("$.conformite").value("CONFORME"))
                .andExpect(jsonPath("$.heures").value(48.0)).andExpect(jsonPath("$.plages", Matchers.hasSize(3))).andExpect(jsonPath("$.deposePar").value("usr-ana"));
        // Bob fait passer la campagne au-delà du budget : averti, pas refusé — la planification arbitre.
        http.put(d + "/moi", BOB, semaine(j(4), j(5))).andExpect(status().isOk()).andExpect(jsonPath("$.conformite").value("AVERTIE"))
                .andExpect(jsonPath("$.motifs[0].message").value("Les disponibilités offertes dépassent le budget (120 %)"));
        http.put(d + "/moi", DAN, semaine(j(1), j(2))).andExpect(status().isNotFound());
        http.put(d + "/moi", ADMIN, semaine(j(1), j(2))).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("votre compte n'est relié à aucune ressource planifiable"));
        http.put(d + "/" + ids.get("T1"), ADMIN, semaine(j(1), j(2))).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("Dan ne figure pas dans cette campagne"));
        http.put(d + "/" + ids.get("P3"), ANA, semaine(j(1), j(2))).andExpect(status().isForbidden());
        http.put(d + "/" + ids.get("P3"), PLANIF, semaine(j(6), j(7))).andExpect(status().isNotFound());
        perimetre("PLANIFICATION");
        http.put(d + "/" + ids.get("P3"), PLANIF, semaine(j(6), j(7))).andExpect(status().isOk()).andExpect(jsonPath("$.deposePar").value("usr-planif"));
        http.put(d + "/" + ids.get("P1"), ANA, semaine(j(1), j(2), j(3, 0, 1440, "DISPONIBLE"))).andExpect(status().isForbidden());
        String l = lire(d, ADMIN);
        assertThat(List.of(JsonPath.<Object>read(l, "$.budgetHeuresSemaine"), JsonPath.read(l, "$.heuresCampagne"), JsonPath.read(l, "$.tauxBudget"),
                JsonPath.read(l, "$.deposes"), JsonPath.read(l, "$.etat"))).containsExactly(60.0, 96.0, 160.0, 3, "DEPOT");
        assertThat(JsonPath.<List<String>>read(l, "$.lignes[*].depot.conformite")).containsExactly("CONFORME", "AVERTIE", "AVERTIE");
        assertThat(JsonPath.<List<String>>read(l, "$.lignes[*].nom")).containsExactly("Ana", "Bob", "Cléo");
        http.get(d, ANA).andExpect(status().isForbidden());
        http.get(d + "/moi", ANA).andExpect(jsonPath("$.plages[*].jour", Matchers.contains(1, 2, 3)));
        http.get(d + "/moi", ADMIN).andExpect(status().isOk()).andExpect(content().string("null"));
        // Sans budget fixé, aucune règle budgétaire ne peut se déclencher.
        http.put(p, ADMIN, "{\"budgetHeuresSemaine\":null}").andExpect(status().isOk()).andExpect(jsonPath("$.budgetHeuresSemaine").isEmpty());
        http.put(d + "/moi", BOB, semaine(j(4), j(5))).andExpect(jsonPath("$.conformite").value("CONFORME"));
        assertThat(lire(d, ADMIN)).contains("\"tauxBudget\":null");
        List<String> actions = jdbc.sql("SELECT action FROM eff_journal WHERE tenantId = ? AND objetId = ? AND action LIKE 'agph.%' ORDER BY rowid").params(T, id)
                .query(String.class).list();
        assertThat(actions.stream().filter("agph.disponibilites.deposees"::equals)).hasSize(4);
        assertThat(actions.stream().filter("agph.campagne.parametree"::equals)).hasSize(2);
        String avantApres = jdbc.sql("SELECT avant || ' ' || apres FROM eff_journal WHERE tenantId = ? AND action = 'agph.campagne.parametree' ORDER BY rowid LIMIT 1")
                .param(T).query(String.class).single();
        assertThat(avantApres).isEqualTo("{\"budget\":null} {\"budget\":60.0}");
    }

    @Test
    @DisplayName("la révision : la planification corrige une attribution, motivée et jugée par les règles ; les disponibilités se corrigent (E-CH-1)")
    void revision() throws Exception {
        String camp = toursExpires();
        String r = A + "/campagnes/" + camp + "/revision/choix";
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"motif\":\"x\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la campagne est en attribution : une attribution se révise pendant la révision"));
        http.post(A + "/campagnes/" + camp + "/reviser", ANA, null).andExpect(status().isForbidden());
        http.post(A + "/campagnes/" + camp + "/reviser", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("REVISION"))
                .andExpect(jsonPath("$.reviseeLe").value(MAINTENANT));
        http.post(A + "/campagnes/" + camp + "/reviser", ADMIN, null).andExpect(status().is(422));
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« motif » est obligatoire"));
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"motif\":\"" + "x".repeat(501) + "\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« motif » : 500 caractères au plus"));
        http.post(r, ADMIN, "{\"motif\":\"x\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« ressourceId » est obligatoire"));
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("T1") + "\",\"motif\":\"x\"}").andExpect(jsonPath("$.errors[0]").value("Dan ne figure pas dans cette campagne"));
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"motif\":\"rien\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("Cléo n'a aucun poste à retirer"));
        // Cléo (rang 3) ne reçoit pas un temps complet en révision plus qu'à son tour : même règle, même motif.
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"posteId\":\"" + ids.get("C-TC") + "\",\"motif\":\"demande\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Le temps complet est réservé aux deux premiers rangs (rang 3)"));
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"posteId\":\"" + ids.get("A-TC") + "\",\"motif\":\"pris\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le poste « " + ids.get("A-TC") + " » n'est pas offert ou est déjà attribué"));
        // Une personne passée à un autre corps depuis l'ouverture n'est plus admissible : refus motivé, hors règles.
        jdbc.sql("UPDATE eff_ressources SET corpsEmploiCode = 'TECH' WHERE id = ?").param(ids.get("P3")).update();
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\",\"posteId\":\"" + ids.get("C-TC") + "\",\"motif\":\"corps\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le poste C-TC requiert le corps PARA"));
        jdbc.sql("UPDATE eff_ressources SET corpsEmploiCode = 'PARA' WHERE id = ?").param(ids.get("P3")).update();
        // Bob perd le poste attribué automatiquement, puis le retrouve après vérification : deux révisions tracées.
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"motif\":\"Choix automatique contesté\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.rangs[1].etat").value("PASSE")).andExpect(jsonPath("$.choix", Matchers.hasSize(1)))
                .andExpect(jsonPath("$.postesNonPourvus[*].posteCode", Matchers.contains("A-TC", "C-TC")));
        assertThat(poste("A-TC")).isEqualTo("VACANT/null");
        http.post(r, ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":\"" + ids.get("A-TC") + "\",\"motif\":\"Vérification faite : le choix automatique tient\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.rangs[1].etat").value("A_CHOISI"))
                .andExpect(jsonPath("$.choix[?(@.ressourceId == '" + ids.get("P2") + "')].mode", Matchers.contains("PLANIFICATEUR")));
        List<String> j = jdbc.sql("SELECT avant || ' ' || apres FROM eff_journal WHERE tenantId = ? AND action = 'agph.revision.choix' ORDER BY rowid").param(T)
                .query(String.class).list();
        assertThat(j).containsExactly(
                "{\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":\"" + ids.get("A-TC") + "\"} {\"ressourceId\":\"" + ids.get("P2")
                        + "\",\"posteId\":null,\"motif\":\"Choix automatique contesté\"}",
                "{\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":null} {\"ressourceId\":\"" + ids.get("P2") + "\",\"posteId\":\"" + ids.get("A-TC")
                        + "\",\"motif\":\"Vérification faite : le choix automatique tient\"}");
        // Les disponibilités rouvrent à la révision.
        http.put(A + "/campagnes/" + camp + "/disponibilites/moi", ANA, semaine(j(1), j(2, 360, 1080, "PREFERENCE"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.heures").value(24.0));
        http.post(A + "/campagnes/" + camp + "/clore", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("CLOSE"));
        http.put(A + "/campagnes/" + camp + "/disponibilites/moi", ANA, semaine(j(1), j(2))).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("l'encadrement laisse un remplacement non comblé, motif à l'appui : ses quarts vacants passent à la gestion régulière (E-CH-9, E-CH-10)")
    void nonComble() throws Exception {
        String periode = creer(PLANIF_API + "/periodes", ADMIN, "{\"nom\":\"Mai\",\"uniteCode\":\"EST\",\"debut\":\"2031-05-05\",\"fin\":\"2031-05-11\"}");
        http.post(PLANIF_API + "/periodes/" + periode + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        for (String date : List.of("2031-05-05", "2031-05-06", "2031-05-07")) {
            equipage(periode, date);
        }
        String x = creer(A + "/remplacements", ADMIN, "{\"posteId\":\"" + ids.get("C-TC") + "\",\"origine\":\"POSTE_VACANT\",\"debut\":\"2031-05-05\","
                + "\"fin\":\"2031-05-11\",\"finManifestation\":\"" + DEMAIN + "\"}");
        String n = A + "/remplacements/" + x + "/non-comble";
        http.post(A + "/remplacements/" + x + "/interet", BOB, null).andExpect(status().isOk());
        http.post(n, ANA, "{\"motif\":\"x\"}").andExpect(status().isForbidden());
        http.post(n, ADMIN, "{\"motif\":\" \"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« motif » est obligatoire : dites pourquoi ce remplacement demeure non comblé"));
        http.post(n, ADMIN, null).andExpect(status().is(422));
        http.post(n, ADMIN, "{\"motif\":\"" + "y".repeat(501) + "\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« motif » : 500 caractères au plus"));
        http.post(n, PLANIF, "{\"motif\":\"x\"}").andExpect(status().isNotFound());
        String motif = "Budget de l'unité atteint : quarts laissés à la gestion régulière";
        http.post(n, ADMIN, "{\"motif\":\"" + motif + "\"}").andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("NON_COMBLE"))
                .andExpect(jsonPath("$.attribueA").isEmpty()).andExpect(jsonPath("$.quartsTransferes").value(3)).andExpect(jsonPath("$.motifNonComble").value(motif))
                .andExpect(jsonPath("$.interesses").value(1)).andExpect(jsonPath("$.transfereLe").value(MAINTENANT));
        http.post(n, ADMIN, "{\"motif\":\"encore\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("ce remplacement est déjà décidé"));
        http.post(A + "/remplacements/" + x + "/interet", ANA, null).andExpect(status().isConflict());
        List<Map<String, Object>> j = jdbc.sql("SELECT action, origine, acteurId, apres FROM eff_journal WHERE tenantId = ? AND objetId = ? AND action LIKE 'remplacement.%' "
                + "ORDER BY rowid").params(T, x).query().listOfRows();
        assertThat(j.subList(j.size() - 2, j.size())).containsExactly(
                Map.of("action", "remplacement.non_comble", "origine", "UTILISATEUR", "acteurId", "usr-admin", "apres", "{\"interesses\":1,\"motif\":\"" + motif + "\"}"),
                Map.of("action", "remplacement.transfere", "origine", "SYSTEME", "acteurId", DecisionRemplacementsAgph.MECANISME,
                        "apres", "{\"vers\":\"gestion régulière\",\"quarts\":3}"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_places WHERE posteId = ? AND ressourceId IS NULL").param(ids.get("C-TC")).query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    @DisplayName("le schéma relit l'ancien état « transféré » comme « non comblé, transféré le… », et ne réajoute pas ses colonnes")
    void migrationTransfere() throws Exception {
        String x = creer(A + "/remplacements", ADMIN, remplacement("B-TPR", ",\"origine\":\"POSTE_VACANT\""));
        jdbc.sql("UPDATE eff_remplacements SET etat = 'TRANSFERE', decideLe = '2027-01-15T11:00:00.000Z' WHERE id = ?").param(x).update();
        new SchemaAgph(jdbc);
        http.get(A + "/remplacements/" + x, ADMIN).andExpect(jsonPath("$.etat").value("NON_COMBLE")).andExpect(jsonPath("$.transfereLe").value("2027-01-15T11:00:00.000Z"));
    }
}
