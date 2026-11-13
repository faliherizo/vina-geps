package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
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
import ca.vina.nexu.effectifs.application.commandes.RepercussionPostes;
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
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRepercussion;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
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
 * <strong>La planification des effectifs par HTTP</strong> (lot R13, jumeau de
 * {@code effectifs-planification.integration.test.ts}) : une semaine à la Caserne 12 — postes publiés, période qui
 * avance, quarts générés (idempotents), attribution refusée par la règle de repos puis forcée avec justification,
 * quart ad hoc, ajustement, lot tout-ou-rien, comblement relu contre un quota surchargé. Horloge figée au
 * 15 janvier 2027 ; la semaine planifiée est en 2030.
 */
@DisplayName("/api/effectifs/planification — ressources, périodes, postes, équipages, places, quotas")
class ControleurPlanificationEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String API = E + "/planification";
    private static final String DEBUT = "2030-03-04";
    private static final String FIN = "2030-03-10";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete PLANIF = new ContexteRequete("usr-planif", "planif", "manager", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireRessources ressources;
    private GestionnairePostes postes;
    private GestionnaireQuotas quotas;
    private GestionnairePeriodes periodes;
    private GestionnaireRegles regles;
    private final Map<String, String> ids = new HashMap<>();

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

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES ('usr-planif', 'planif', ?, 'x', 'manager', 'x')").param(T).update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
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
                Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
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
        ressources = new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq);
        periodes = new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq);
        new SchemaPresenceEffectifs(jdbc);
        RepercussionPostes repercussion = new RepercussionPostes(new RepercussionPostes.Depots(new DepotRepercussion(jdbc, schemaPlanif), depotEqp),
                referentiels, journal, seq);
        postes = new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes),
                new GestionnairePostes.Ecritures(journal, seq, repercussion));
        quotas = new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq);
        GestionnaireEquipages equipages = new GestionnaireEquipages(depotEqp, new GestionnaireEquipages.Lectures(lectEqp, lectPrd, lectPos, referentiels, milieux),
                journal, seq);
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages,
                new EvaluateurRegles(depotRegles, referentiels, journal, seq), journal);
        regles = new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq);
        http = new ClientHttpEssai(socle, new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(ressources, periodes, postes, equipages, places, quotas)));
        socle();
    }

    private String creer(String chemin, ContexteRequete qui, String json) throws Exception {
        return JsonPath.read(http.post(chemin, qui, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"US\",\"libelleFr\":\"Urgences-santé\"}");
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\",\"attributs\":{\"parent\":\"US\"}}");
        ref("unites", "{\"code\":\"OUEST\",\"libelleFr\":\"Ouest\",\"attributs\":{\"parent\":\"US\"}}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("corps_emploi", "{\"code\":\"RMU\",\"libelleFr\":\"Répartiteurs\"}");
        ref("statuts_emploi", "{\"code\":\"TC\",\"libelleFr\":\"Temps complet\",\"comportement\":\"TEMPS_COMPLET\"}");
        ref("statuts_emploi", "{\"code\":\"TPR\",\"libelleFr\":\"Temps partiel régulier\",\"comportement\":\"TEMPS_PARTIEL_REGULIER\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("natures_quart", "{\"code\":\"ADH\",\"libelleFr\":\"Ad hoc\",\"comportement\":\"AD_HOC\"}");
        ref("plages_horaires", "{\"code\":\"J\",\"libelleFr\":\"Jour\",\"attributs\":{\"debut\":360,\"fin\":1080}}");
        ref("plages_horaires", "{\"code\":\"N\",\"libelleFr\":\"Nuit\",\"attributs\":{\"debut\":1080,\"fin\":360}}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("parametres_poste", "{\"code\":\"VEHICULE\",\"libelleFr\":\"Véhicule\",\"attributs\":{\"typeValeur\":\"TEXTE\"}}");
        ref("parametres_poste", "{\"code\":\"ALTERNANCE\",\"libelleFr\":\"Alternance\",\"attributs\":{\"typeValeur\":\"BOOLEEN\"}}");
        ref("parametres_poste", "{\"code\":\"KM\",\"libelleFr\":\"Kilométrage\",\"attributs\":{\"typeValeur\":\"NOMBRE\"}}");
        ids.put("c12", creer(E + "/milieux", ADMIN, "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        for (String[] r : new String[][]{{"P1", "Julie R.", "PARA", "EST"}, {"P2", "Marc D.", "PARA", "EST"}, {"R1", "Luc P.", "RMU", "EST"}, {"P9", "Ana O.", "PARA", "OUEST"}}) {
            ids.put(r[0], creer(API + "/ressources", ADMIN, "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1] + "\",\"corpsEmploiCode\":\"" + r[2]
                    + "\",\"uniteCode\":\"" + r[3] + "\",\"statutEmploiCode\":\"TPR\",\"anciennete\":3}"));
        }
    }

    private String lire(String chemin, ContexteRequete qui) throws Exception {
        return http.get(chemin, qui).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private void perimetre(String niveau, String etendue) throws Exception {
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-planif\",\"uniteCode\":\"EST\",\"etendue\":\"" + etendue + "\",\"niveau\":\"" + niveau
                + "\",\"depuis\":\"2026-01-01\"}").andExpect(status().isCreated());
    }

    @Test
    @DisplayName("garde de module : sans effectifs_planification, 403 nommé ; sans effectifs.manage, 403")
    void gardes() throws Exception {
        http.get(API + "/periodes", new ContexteRequete("usr-e", "e", "employee", null, T)).andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_regles\"]", T).update();
        http.get(API + "/periodes", ADMIN).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("effectifs_planification")));
    }

    @Test
    @DisplayName("ressources : liste filtrée, doublon, corps inconnu, nom vide, matricule, ancienneté ; modifie ; périmètre")
    void ressources() throws Exception {
        http.get(API + "/ressources?uniteCode=EST&corpsEmploiCode=PARA", ADMIN).andExpect(jsonPath("$[*].matricule", Matchers.containsInAnyOrder("P1", "P2")));
        String base = "\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\"";
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"P1\",\"nom\":\"x\"," + base + "}").andExpect(status().isConflict());
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"Z1\",\"nom\":\"x\",\"corpsEmploiCode\":\"XX\",\"uniteCode\":\"EST\"}").andExpect(status().is(422));
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"Z1\",\"nom\":\"\"," + base + "}").andExpect(status().is(422));
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"z 1\",\"nom\":\"x\"," + base + "}").andExpect(status().is(422));
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"Z1\",\"nom\":\"x\"," + base + ",\"anciennete\":-2}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« ancienneté » : nombre positif ou nul"));
        http.post(API + "/ressources", ADMIN, "{\"matricule\":\"Z1\",\"nom\":\"x\"," + base + ",\"anciennete\":\"abc\"}").andExpect(status().is(422));
        http.put(API + "/ressources/" + ids.get("P2"), ADMIN, "{\"nom\":\"Marc Dubé\"," + base
                        + ",\"statutEmploiCode\":\"TC\",\"conventionCode\":\"\",\"titreEmploiCode\":\"\",\"anciennete\":\"5\",\"utilisateurId\":\"usr-marc\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Marc Dubé")).andExpect(jsonPath("$.statutEmploiCode").value("TC"))
                .andExpect(jsonPath("$.anciennete").value(5.0)).andExpect(jsonPath("$.actif").value(true));
        http.put(API + "/ressources/inconnue", ADMIN, "{}").andExpect(status().isNotFound());
        assertThat(ressources.poserStatutEmploi(ctx("2027-01-15T00:00:00Z"), ids.get("P2"), "TPR", "agph").statutEmploiCode()).isEqualTo("TPR");

        http.get(API + "/ressources", PLANIF).andExpect(jsonPath("$", Matchers.hasSize(0)));
        perimetre("PLANIFICATION", "SOUS_ARBRE");
        http.get(API + "/ressources", PLANIF).andExpect(jsonPath("$[*].matricule", Matchers.containsInAnyOrder("P1", "P2", "R1")));
        http.post(API + "/ressources", PLANIF, "{\"matricule\":\"Z2\",\"nom\":\"x\"," + base + "}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("la ressource de l'acteur : reliée à son compte, sinon une erreur qui dit quoi faire ; droit sur soi-même")
    void ressourceDeLActeur() {
        LectureRessources lecture = new LectureRessources(new DepotRessources(jdbc, new SchemaPlanification(jdbc)),
                new LecturePerimetres(new DepotPerimetres(jdbc, new SchemaEffectifs(jdbc)), new LectureReferentiels(new DepotReferentiels(jdbc, new SchemaEffectifs(jdbc)))));
        Contexte marc = new Contexte(T, "usr-marc", "employee", "2027-01-15T00:00:00Z");
        assertThatThrownBy(() -> lecture.exigerDeLActeur(marc)).hasMessageContaining("demandez-le à votre planificateur");
        jdbc.sql("UPDATE eff_ressources SET utilisateurId = 'usr-marc' WHERE id = ?").param(ids.get("P2")).update();
        assertThat(lecture.exigerDeLActeur(marc).matricule()).isEqualTo("P2");
        lecture.exigerDroitSur(marc, lecture.exiger(marc, ids.get("P2")), ca.vina.nexu.effectifs.domaine.Perimetres.Niveau.PLANIFICATION);
        assertThatThrownBy(() -> lecture.exigerDroitSur(marc, lecture.exiger(marc, ids.get("P1")), ca.vina.nexu.effectifs.domaine.Perimetres.Niveau.LECTURE))
                .hasMessageContaining("hors de votre périmètre");
        // Pour autrui, dans son périmètre : l'administration passe.
        lecture.exigerDroitSur(ctx("2027-01-15T00:00:00Z"), lecture.exiger(marc, ids.get("P1")), ca.vina.nexu.effectifs.domaine.Perimetres.Niveau.PLANIFICATION);
        assertThat(lecture.lister(ctx("2027-01-15T00:00:00Z"), "", "")).hasSize(4);
    }

    private void periodeEnConfection() throws Exception {
        perimetre("PLANIFICATION", "SOUS_ARBRE");
        http.post(API + "/periodes", PLANIF, "{\"nom\":\"Ouest\",\"uniteCode\":\"OUEST\",\"debut\":\"" + DEBUT + "\",\"fin\":\"" + FIN + "\"}").andExpect(status().isForbidden());
        http.post(API + "/periodes", PLANIF, "{\"nom\":\"X\",\"uniteCode\":\"EST\",\"debut\":\"" + FIN + "\",\"fin\":\"" + DEBUT + "\"}").andExpect(status().is(422));
        http.post(API + "/periodes", PLANIF, "{\"nom\":\"\",\"uniteCode\":\"EST\",\"debut\":\"" + DEBUT + "\",\"fin\":\"" + FIN + "\"}").andExpect(status().is(422));
        String p = http.post(API + "/periodes", PLANIF, "{\"nom\":\"Semaine 10\",\"uniteCode\":\"EST\",\"debut\":\"" + DEBUT + "\",\"fin\":\"" + FIN + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("PREPARATION")).andReturn().getResponse().getContentAsString();
        ids.put("periode", JsonPath.read(p, "$.id"));
        String etat = API + "/periodes/" + ids.get("periode") + "/etat";
        http.post(etat, PLANIF, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2020-01-01\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la collecte ne peut pas se terminer dans le passé"));
        http.post(etat, PLANIF, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-03-20\"}").andExpect(status().is(422));
        http.post(etat, PLANIF, "{\"etat\":\"COLLECTE\",\"finCollecte\":\"2030-02-25\"}").andExpect(jsonPath("$.etat").value("COLLECTE"))
                .andExpect(jsonPath("$.finCollecte").value("2030-02-25"));
        http.post(etat, PLANIF, "{\"etat\":\"PREPARATION\"}").andExpect(status().is(422));
        http.post(etat, PLANIF, "{\"etat\":\"INCONNU\"}").andExpect(status().is(422));
        http.post(etat, PLANIF, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk()).andExpect(jsonPath("$.finCollecte").value("2030-02-25"));
        http.get(API + "/periodes", PLANIF).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("periode"))));
        http.post(API + "/periodes/inconnue/etat", ADMIN, "{\"etat\":\"CLOSE\"}").andExpect(status().isNotFound());
    }

    private static final String CYCLE = "[[\"J\",\"J\",\"J\",null,null,null,null]]";

    private String poste(String code, String extra) {
        return "{\"code\":\"" + code + "\",\"libelle\":\"PSA jour\",\"uniteCode\":\"EST\",\"statutEmploiCode\":\"TC\",\"corpsEmploiCode\":\"PARA\",\"milieuId\":\""
                + ids.get("c12") + "\",\"natureCode\":\"REG\",\"cycle\":" + CYCLE + ",\"debutCycle\":\"" + DEBUT + "\"" + extra + "}";
    }

    private void postesPublies() throws Exception {
        String a = http.post(API + "/postes", PLANIF, poste("3412-J1", ",\"parametres\":{\"VEHICULE\":\"3412\",\"ALTERNANCE\":false,\"KM\":12.5}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("BROUILLON")).andExpect(jsonPath("$.parametres.VEHICULE").value("3412"))
                .andReturn().getResponse().getContentAsString();
        ids.put("poste1", JsonPath.read(a, "$.id"));
        ids.put("poste2", creer(API + "/postes", PLANIF, poste("3412-J2", "").replace("\"TC\"", "\"TPR\"")));
        http.post(API + "/postes", PLANIF, poste("3412-J1", "")).andExpect(status().isConflict());
        http.post(API + "/postes", PLANIF, poste("X1", "").replace(CYCLE, "[[\"Z\",null,null,null,null,null,null]]")).andExpect(status().is(422));
        http.post(API + "/postes", PLANIF, poste("X1", ",\"parametres\":{\"COULEUR\":\"rouge\"}")).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("paramètre de poste inconnu")));
        http.post(API + "/postes", PLANIF, poste("X1", ",\"parametres\":{\"ALTERNANCE\":\"oui\"}")).andExpect(jsonPath("$.errors[0]").value("« ALTERNANCE » attend vrai ou faux"));
        http.post(API + "/postes", PLANIF, poste("X1", ",\"parametres\":{\"VEHICULE\":12}")).andExpect(jsonPath("$.errors[0]").value("« VEHICULE » attend un texte"));
        http.post(API + "/postes", PLANIF, poste("X1", ",\"parametres\":{\"KM\":\"12\"}")).andExpect(jsonPath("$.errors[0]").value("« KM » attend un nombre"));
        http.post(API + "/postes", PLANIF, poste("X 1", "")).andExpect(status().is(422));
        http.post(API + "/postes", PLANIF, poste("X1", "").replace("\"PSA jour\"", "\"\"")).andExpect(status().is(422));
        http.put(API + "/postes/" + ids.get("poste1"), PLANIF, poste("3412-J1", ",\"parametres\":{\"VEHICULE\":\"3412\",\"ALTERNANCE\":true}").replace("\"PSA jour\"", "\"PSA jour — 3412\""))
                .andExpect(jsonPath("$.libelle").value("PSA jour — 3412")).andExpect(jsonPath("$.parametres.ALTERNANCE").value(true));

        http.post(API + "/postes/publier", PLANIF, "{\"ids\":[]}").andExpect(status().is(422));
        http.post(API + "/postes/publier", PLANIF, "{\"ids\":[\"" + ids.get("poste1") + "\",\"" + ids.get("poste2") + "\"]}")
                .andExpect(jsonPath("$[*].etat", Matchers.contains("PUBLIE", "PUBLIE")));
        http.post(API + "/postes/publier", PLANIF, "{\"ids\":[\"" + ids.get("poste1") + "\"]}").andExpect(jsonPath("$.errors[0]").value("le poste « 3412-J1 » est déjà publie"));
        Contexte c = ctx("2027-01-15T00:00:00Z");
        assertThatThrownBy(() -> postes.attribuerTitulaire(c, ids.get("poste1"), ids.get("R1"), null)).hasMessageContaining("corps d'emploi");
        assertThat(postes.attribuerTitulaire(c, ids.get("poste1"), ids.get("P1"), "agph").etat()).isEqualTo(Planification.EtatPoste.ATTRIBUE);
        assertThat(postes.attribuerTitulaire(c, ids.get("poste2"), null, null).etat()).isEqualTo(Planification.EtatPoste.VACANT);
        http.get(API + "/postes?etat=ATTRIBUE", PLANIF).andExpect(jsonPath("$[*].code", Matchers.contains("3412-J1")));
    }

    private void generation() throws Exception {
        String brouillon = creer(API + "/postes", PLANIF, poste("3412-B", ""));
        assertThatThrownBy(() -> postes.attribuerTitulaire(ctx("2027-01-15T00:00:00Z"), brouillon, null, null)).hasMessageContaining("n'est pas publié");
        String gen = API + "/periodes/" + ids.get("periode") + "/generer";
        http.post(gen, PLANIF, "").andExpect(jsonPath("$.equipagesCrees").value(3)).andExpect(jsonPath("$.placesCreees").value(6));
        http.post(gen, PLANIF, "").andExpect(jsonPath("$.equipagesCrees").value(0)).andExpect(jsonPath("$.placesCreees").value(0));
        String lundi = lire(API + "/periodes/" + ids.get("periode") + "/equipages?date=" + DEBUT, PLANIF);
        assertThat(List.of(JsonPath.read(lundi, "$[0].places.length()"), JsonPath.read(lundi, "$[0].pourvues"), JsonPath.read(lundi, "$[0].statut"),
                JsonPath.read(lundi, "$[0].plageCode"), JsonPath.read(lundi, "$[0].debut"), JsonPath.read(lundi, "$[0].fin"),
                JsonPath.read(lundi, "$[0].places[0].ressourceNom"), JsonPath.read(lundi, "$[0].places[0].attribueePar")))
                .containsExactly(2, 1, "SOUS_MINIMUM", "J", 360, 1080, "Julie R.", "POSTE");
        ids.put("lundi", JsonPath.read(lundi, "$[0].id"));
        ids.put("placeVacanteLundi", JsonPath.read(lundi, "$[0].places[1].id"));
        http.get(API + "/periodes/" + ids.get("periode") + "/equipages?statut=SOUS_MINIMUM", PLANIF).andExpect(jsonPath("$", Matchers.hasSize(3)));
        http.get(API + "/periodes/" + ids.get("periode") + "/equipages?date=2030-13-01", PLANIF).andExpect(status().is(422));
    }

    private String adHoc(String extra) {
        return "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + DEBUT + "\",\"natureCode\":\"ADH\",\"milieuId\":\"" + ids.get("c12")
                + "\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]" + extra + "}";
    }

    private void attribution() throws Exception {
        String vacante = API + "/places/" + ids.get("placeVacanteLundi") + "/attribuer";
        http.post(vacante, PLANIF, "{\"ressourceId\":\"" + ids.get("R1") + "\",\"forcer\":true,\"justification\":\"essai de forçage\"}").andExpect(status().isConflict());
        http.post(vacante, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.equipage.statut").value("OPERATIONNEL")).andExpect(jsonPath("$.verdict.action").value("ACCEPTER"))
                .andExpect(jsonPath("$.force").value(false)).andExpect(jsonPath("$.equipage.places[1].attribueePar").value("UTILISATEUR"));
        String adh = http.post(API + "/equipages", PLANIF, adHoc(",\"debut\":720,\"fin\":900")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        http.post(API + "/places/" + JsonPath.read(adh, "$.places[0].id") + "/attribuer", PLANIF,
                        "{\"ressourceId\":\"" + ids.get("P2") + "\",\"forcer\":true,\"justification\":\"essai de forçage\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("chevauche")));
        http.post(API + "/places/inconnue/attribuer", PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\"}").andExpect(status().isNotFound());
    }

    private void regle(String cle, String condition, String action, String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", "ATTRIBUTION");
        e.put("cle", cle);
        e.put("condition", condition.equals("repos") ? Map.of("fait", "reposHeures", "op", "<", "valeur", 8) : Map.of("fait", "heuresSemaine", "op", ">", "valeur", 20));
        e.put("action", action);
        e.put("message", message);
        e.put("effectifDepuis", "2027-02-01");
        regles.creer(ctx("2027-01-15T10:00:00Z"), e);
    }

    private void forcage() throws Exception {
        regle("repos-minimal", "repos", "BLOQUER", "Repos de {reposHeures} h : il en faut 8");
        String soir = http.post(API + "/equipages", PLANIF, adHoc(",\"debut\":1080,\"fin\":1320,\"plageCode\":\"N\",\"note\":\"Renfort de soirée\""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.modeAttribution").value("MANUEL")).andExpect(jsonPath("$.minimumOperationnel").value(1))
                .andExpect(jsonPath("$.note").value("Renfort de soirée")).andReturn().getResponse().getContentAsString();
        String place = API + "/places/" + JsonPath.read(soir, "$.places[0].id") + "/attribuer";
        http.post(place, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.verdict.motifs[0].message").value("Repos de 0 h : il en faut 8"));
        http.post(place, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"forcer\":true,\"justification\":\"court\"}").andExpect(status().isForbidden());
        perimetre("GESTION", "UNITE");
        http.post(place, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"forcer\":true,\"justification\":\"court\"}").andExpect(status().is(422));
        http.post(place, PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"forcer\":true,\"justification\":\"Aucun autre PSA disponible, appel massif\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.force").value(true))
                .andExpect(jsonPath("$.equipage.places[0].justificationForcage").value("Aucun autre PSA disponible, appel massif"));
        http.get(E + "/journal?action=place.attribution.forcee", ADMIN).andExpect(jsonPath("$[0].apres.motifsEcartes[0].cle").value("repos-minimal"));

        regle("heures-semaine", "heures", "AVERTIR", "{heuresSemaine} h cette semaine");
        String mardi = lire(API + "/periodes/" + ids.get("periode") + "/equipages?date=2030-03-05", PLANIF);
        http.post(API + "/places/" + JsonPath.read(mardi, "$[0].places[1].id") + "/attribuer", PLANIF, "{\"ressourceId\":\"" + ids.get("P2") + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.verdict.action").value("AVERTIR"))
                .andExpect(jsonPath("$.verdict.motifs[0].message").value("28 h cette semaine"));
    }

    private void liberation() throws Exception {
        http.put(API + "/ressources/" + ids.get("P9"), ADMIN, "{\"nom\":\"Ana O.\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"OUEST\",\"actif\":false}")
                .andExpect(jsonPath("$.actif").value(false));
        String mercredi = lire(API + "/periodes/" + ids.get("periode") + "/equipages?date=2030-03-06", ADMIN);
        http.post(API + "/places/" + JsonPath.read(mercredi, "$[0].places[1].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P9") + "\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« Ana O. » est inactive"));
        String pleine = API + "/places/" + JsonPath.read(mercredi, "$[0].places[0].id") + "/liberer";
        http.post(pleine, PLANIF, "{}").andExpect(status().is(422));
        http.post(pleine, PLANIF, "{\"motif\":\"Formation obligatoire\"}").andExpect(jsonPath("$.pourvues").value(0));
        http.post(pleine, PLANIF, "{\"motif\":\"encore\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("cette place est déjà vacante"));
    }

    private void ajustements() throws Exception {
        String lundi = API + "/equipages/" + ids.get("lundi");
        http.patch(lundi, PLANIF, "{\"debut\":420,\"fin\":1080,\"minimumOperationnel\":1}").andExpect(jsonPath("$.debut").value(420))
                .andExpect(jsonPath("$.minimumOperationnel").value(1));
        http.patch(lundi, PLANIF, "{\"debut\":420,\"fin\":1200}").andExpect(status().isConflict());
        http.patch(lundi, PLANIF, "{}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("aucun changement demandé"));
        http.patch(lundi, PLANIF, "{\"minimumOperationnel\":5}").andExpect(status().is(422));
        http.patch(lundi, PLANIF, "{\"note\":\"\"}").andExpect(jsonPath("$.note").doesNotExist());
        http.patch(API + "/equipages/inconnu", PLANIF, "{\"note\":\"x\"}").andExpect(status().isNotFound());

        String tous = lire(API + "/periodes/" + ids.get("periode") + "/equipages", PLANIF);
        List<String> deux = List.of(JsonPath.read(tous, "$[0].id"), JsonPath.read(tous, "$[1].id"));
        http.post(API + "/equipages/lot", PLANIF, "{\"ids\":[\"" + deux.get(0) + "\",\"" + deux.get(1) + "\"],\"changements\":{\"modeAttribution\":\"MANUEL\",\"milieuId\":\""
                + ids.get("c12") + "\"}}").andExpect(jsonPath("$[*].modeAttribution", Matchers.contains("MANUEL", "MANUEL")));
        http.post(API + "/equipages/lot", PLANIF, "{\"ids\":[\"" + deux.get(0) + "\",\"inconnu\"],\"changements\":{\"modeAttribution\":\"AUTOMATISE\"}}")
                .andExpect(status().isNotFound());
        http.get(API + "/periodes/" + ids.get("periode") + "/equipages?date=" + DEBUT, PLANIF).andExpect(jsonPath("$[0].modeAttribution").value("MANUEL"));
        http.post(API + "/equipages/lot", PLANIF, "{\"ids\":[],\"changements\":{}}").andExpect(status().is(422));
        http.post(API + "/equipages/lot", PLANIF, "{\"ids\":[\"" + deux.get(0) + "\"]}").andExpect(status().is(422));

        String base = ",\"debut\":600,\"fin\":660,\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":2}]";
        http.post(API + "/equipages", PLANIF, adHoc(base).replace(DEBUT, "2030-04-01").replace(",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]", ""))
                .andExpect(status().is(422));
        http.post(API + "/equipages", PLANIF, adHoc(",\"debut\":600,\"fin\":660,\"minimumOperationnel\":3")).andExpect(status().is(422));
        http.post(API + "/equipages", PLANIF, adHoc(",\"debut\":600,\"fin\":660,\"plageCode\":\"ZZ\"")).andExpect(status().is(422));
        http.post(API + "/equipages", PLANIF, adHoc(",\"debut\":600,\"fin\":660,\"modeAttribution\":\"AUTOMATISE\"")).andExpect(jsonPath("$.modeAttribution").value("AUTOMATISE"));
    }

    private void quotasEtComblement() throws Exception {
        String q = http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"plageCode\":\"J\",\"natureCode\":\"REG\",\"quota\":1,\"depuis\":\"2030-01-01\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.cle").value("EST|J|REG")).andExpect(jsonPath("$.versions", Matchers.hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        ids.put("quota", JsonPath.read(q, "$.versions[0].id"));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"quota\":1,\"depuis\":\"2020-01-01\"}").andExpect(status().is(422));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"quota\":-1}").andExpect(status().is(422));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"plageCode\":\"ZZ\",\"quota\":1}").andExpect(status().is(422));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"natureCode\":\"ZZ\",\"quota\":1}").andExpect(status().is(422));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"plageCode\":\"J\",\"natureCode\":\"REG\",\"quota\":2,\"depuis\":\"2031-01-01\"}")
                .andExpect(jsonPath("$.versions[*].effectifJusqua", Matchers.contains("2031-01-01", null)));
        http.post(API + "/quotas", PLANIF, "{\"uniteCode\":\"EST\",\"quota\":0}").andExpect(status().isCreated());
        http.get(API + "/quotas", PLANIF).andExpect(jsonPath("$[*].cle", Matchers.containsInAnyOrder("EST|*|*", "EST|J|REG")));

        String bonne = "\"valeur\":2,\"depuis\":\"2030-03-05T00:00:00.000Z\",\"jusqua\":\"2030-03-06T00:00:00.000Z\"";
        String surcharges = API + "/quotas/" + ids.get("quota") + "/surcharges";
        http.post(surcharges, PLANIF, "{" + bonne + ",\"motif\":\"Événement majeur\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.surcharges", Matchers.hasSize(1)));
        http.post(surcharges, PLANIF, "{" + bonne.replace("2030-03-05T", "2020-01-01T") + ",\"motif\":\"x\"}").andExpect(status().is(422));
        http.post(surcharges, PLANIF, "{" + bonne + ",\"motif\":\"\"}").andExpect(status().is(422));
        http.post(API + "/quotas/inconnu/surcharges", PLANIF, "{" + bonne + ",\"motif\":\"x\"}").andExpect(status().isNotFound());

        String c = lire(API + "/periodes/" + ids.get("periode") + "/comblement", PLANIF);
        int places = JsonPath.read(c, "$.places");
        int pourvues = JsonPath.read(c, "$.pourvues");
        assertThat(((Number) JsonPath.read(c, "$.taux")).doubleValue()).isEqualTo(Math.round(pourvues * 1000.0 / places) / 10.0);
        assertThat((List<?>) JsonPath.read(c, "$.sousMinimum")).isNotEmpty();
        assertThat((List<Object>) JsonPath.read(c, "$.quotas[?(@.cle == 'EST|J|REG')].jours[?(@.date == '2030-03-05')].origine")).containsExactly("SURCHARGE");
        assertThat((List<Object>) JsonPath.read(c, "$.quotas[?(@.cle == 'EST|J|REG')].jours[?(@.date == '" + DEBUT + "')].atteint")).containsExactly(true);
        assertThat((List<Object>) JsonPath.read(c, "$.quotas[?(@.cle == 'EST|*|*')].jours[0].quota")).containsExactly(0);

        assertThatThrownBy(() -> quotas.surcharger(ctx("2031-06-01T00:00:00.000Z"), ids.get("quota"),
                Map.of("valeur", 1, "depuis", "2031-05-01T00:00:00.000Z", "jusqua", "2031-06-02T00:00:00.000Z", "motif", "x"))).hasMessageContaining("rétroactive");
        assertThatThrownBy(() -> quotas.definir(ctx("2031-06-01T00:00:00.000Z"), Map.of("uniteCode", "EST", "plageCode", "J", "natureCode", "REG", "quota", 3,
                "depuis", "2031-01-02"))).isInstanceOf(EffectifsInvalideException.class);
    }

    private void cloture() throws Exception {
        http.post(API + "/periodes/" + ids.get("periode") + "/etat", PLANIF, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        periodes.changerEtat(ctx("2030-04-01T00:00:00Z"), ids.get("periode"), Map.of("etat", "CLOSE"));
        http.patch(API + "/equipages/" + ids.get("lundi"), PLANIF, "{\"note\":\"x\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("la période « Semaine 10 » est close : elle ne se modifie plus"));
        http.post(API + "/periodes/" + ids.get("periode") + "/generer", PLANIF, "").andExpect(status().is(422));
        http.get(API + "/periodes", ADMIN).andExpect(jsonPath("$[0].etat").value("CLOSE")).andExpect(jsonPath("$[0].publieeLe").isString())
                .andExpect(jsonPath("$[0].closeLe").isString());
    }

    @Test
    @DisplayName("une semaine à la Caserne 12 : période, postes publiés une fois (E-CH-4), génération, attribution, forçage, "
            + "ajustements, quarts ad hoc et leurs refus (E-GN-3, E-GR-6, E-GR-13), quotas et comblement relu (E-GR-1), clôture")
    void semaine() throws Exception {
        periodeEnConfection();
        postesPublies();
        generation();
        attribution();
        forcage();
        liberation();
        ajustements();
        quotasEtComblement();
        cloture();
    }

    // ── E-CH-3 : modification en lot de postes, répercutée sur leurs quarts futurs ; E-GR-5 : ajustement d'une période publiée ──

    private static final String D14 = "2030-04-01";

    private String posteLot(String code) {
        return "{\"code\":\"" + code + "\",\"libelle\":\"PSA lot\",\"uniteCode\":\"EST\",\"statutEmploiCode\":\"TC\",\"corpsEmploiCode\":\"PARA\",\"milieuId\":\""
                + ids.get("c12") + "\",\"natureCode\":\"REG\",\"cycle\":" + CYCLE + ",\"debutCycle\":\"" + D14 + "\"}";
    }

    /** La place que le poste tient ce jour-là dans la semaine 14, et son équipage. */
    private Map<String, Object> placeDu(String date, String posteId) throws Exception {
        List<Map<String, Object>> eqs = JsonPath.read(lire(API + "/periodes/" + ids.get("p14") + "/equipages?date=" + date, ADMIN), "$");
        for (Map<String, Object> e : eqs) {
            for (Object pl : (List<?>) e.get("places")) {
                Map<?, ?> place = (Map<?, ?>) pl;
                if (posteId.equals(place.get("posteId"))) {
                    Map<String, Object> m = new HashMap<>(e);
                    m.put("place", place);
                    return m;
                }
            }
        }
        return null;
    }

    private void semaine14() throws Exception {
        ids.put("c14", creer(E + "/milieux", ADMIN, "{\"code\":\"C14\",\"libelle\":\"Caserne 14\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        ids.put("A", creer(API + "/postes", ADMIN, posteLot("LOT-A")));
        ids.put("B", creer(API + "/postes", ADMIN, posteLot("LOT-B")));
        http.post(API + "/postes/publier", ADMIN, "{\"ids\":[\"" + ids.get("A") + "\",\"" + ids.get("B") + "\"]}").andExpect(status().isOk());
        postes.attribuerTitulaire(ctx("2027-01-15T00:00:00Z"), ids.get("A"), ids.get("P2"), null);
        ids.put("p14", creer(API + "/periodes", ADMIN, "{\"nom\":\"Semaine 14\",\"uniteCode\":\"EST\",\"debut\":\"" + D14 + "\",\"fin\":\"2030-04-07\"}"));
        String etat = API + "/periodes/" + ids.get("p14") + "/etat";
        http.post(etat, ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        http.post(API + "/periodes/" + ids.get("p14") + "/generer", ADMIN, "").andExpect(jsonPath("$.placesCreees").value(6));
        http.post(etat, ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        Map<?, ?> lundiB = (Map<?, ?>) placeDu(D14, ids.get("B")).get("place");
        jdbc.sql("INSERT INTO eff_pointages (id, tenantId, ressourceId, placeId, milieuId, uniteCode, sens, horodatage, origine, resultat, actionRegles, statut, saisiLe, saisiPar) "
                + "VALUES ('epoi-essai', ?, ?, ?, ?, 'EST', 'ENTREE', '2030-04-01T10:00:00Z', 'APPAREIL', 'CONFORME', 'ACCEPTER', 'ENREGISTRE', '2030-04-01T10:00:00Z', 'essai')")
                .params(T, ids.get("P1"), lundiB.get("id"), ids.get("c12")).update();
        String adh = http.post(API + "/equipages", ADMIN, "{\"periodeId\":\"" + ids.get("p14") + "\",\"date\":\"2030-04-02\",\"debut\":1200,\"fin\":1380,"
                + "\"natureCode\":\"ADH\",\"milieuId\":\"" + ids.get("c14") + "\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        http.post(API + "/places/" + JsonPath.read(adh, "$.places[0].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P2") + "\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("E-CH-3 : postes modifiés en lot (tout ou rien), répercutés sur les quarts futurs ; jamais rétroactif ; E-GR-5 sur une période publiée")
    void repercussion() throws Exception {
        semaine14();
        String a = ids.get("A");
        String b = ids.get("B");
        String deux = "[\"" + a + "\",\"" + b + "\"]";
        http.post(API + "/postes/lot", ADMIN, "{\"ids\":[\"" + a + "\",\"inconnu\"],\"changements\":{\"statutEmploiCode\":\"TPR\"}}").andExpect(status().isNotFound());
        http.post(API + "/postes/lot", ADMIN, "{\"ids\":[\"" + a + "\"],\"changements\":{\"code\":\"X\"}}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("changement de poste inconnu : code (admis : uniteCode, statutEmploiCode")));
        http.post(API + "/postes/lot", ADMIN, "{\"ids\":[\"" + a + "\"],\"changements\":{}}").andExpect(jsonPath("$.errors[0]").value("aucun changement demandé"));
        http.post(API + "/postes/lot", ADMIN, "{\"ids\":[],\"changements\":{\"statutEmploiCode\":\"TPR\"}}").andExpect(jsonPath("$.errors[0]").value("« ids » : au moins un poste"));
        assertThat(JsonPath.<String>read(lire(API + "/postes?etat=ATTRIBUE", ADMIN), "$[0].statutEmploiCode")).isEqualTo("TC");

        String r = http.post(API + "/postes/lot", ADMIN, "{\"ids\":" + deux + ",\"changements\":{\"statutEmploiCode\":\"TPR\",\"cycle\":[[null,\"N\",\"N\",\"J\",null,null,null]]}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].statutEmploiCode").value("TPR")).andExpect(jsonPath("$[0].code").value("LOT-A"))
                .andExpect(jsonPath("$[0].repercussion.deplacees").value(2)).andExpect(jsonPath("$[0].repercussion.retirees").value(1))
                .andExpect(jsonPath("$[0].repercussion.ajoutees").value(1)).andExpect(jsonPath("$[0].repercussion.conflits[0].date").value("2030-04-02"))
                .andExpect(jsonPath("$[0].repercussion.conflits[0].motif").value("tient déjà un quart qui chevauche les nouvelles heures"))
                .andExpect(jsonPath("$[1].repercussion.pointeesIgnorees").value(1)).andExpect(jsonPath("$[1].repercussion.deplacees").value(2))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Object>>read(r, "$[1].repercussion.conflits")).isEmpty();
        assertThat(placeDu(D14, a)).isNull();
        assertThat(placeDu(D14, b).get("plageCode")).isEqualTo("J");
        Map<String, Object> mardi = placeDu("2030-04-02", a);
        assertThat(List.of(mardi.get("plageCode"), String.valueOf(((Map<?, ?>) mardi.get("place")).get("ressourceId")))).containsExactly("N", "null");
        assertThat(((Map<?, ?>) placeDu("2030-04-03", a).get("place")).get("ressourceId")).isEqualTo(ids.get("P2"));
        Map<String, Object> jeudi = placeDu("2030-04-04", a);
        assertThat(List.of(jeudi.get("plageCode"), jeudi.get("minimumOperationnel"), ((List<?>) jeudi.get("places")).size())).containsExactly("J", 2, 2);
        http.get(E + "/journal?action=poste.modifie.lot", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2))).andExpect(jsonPath("$[0].origine").value("UTILISATEUR"));
        http.get(E + "/journal?action=place.repercussion.conflit", ADMIN).andExpect(jsonPath("$[0].avant.ressourceId").value(ids.get("P2")));

        // Jamais rétroactif : le 2 avril, seuls les quarts du 3 et du 4 suivent le poste à la caserne 14.
        Map<String, Object> champs = new HashMap<>();
        champs.put("libelle", "PSA lot");
        champs.put("uniteCode", "EST");
        champs.put("statutEmploiCode", "TPR");
        champs.put("corpsEmploiCode", "PARA");
        champs.put("milieuId", ids.get("c14"));
        champs.put("natureCode", "REG");
        champs.put("cycle", List.of(java.util.Arrays.asList(null, "N", "N", "J", null, null, null)));
        champs.put("debutCycle", D14);
        assertThat(postes.modifier(new Contexte(T, "admin-essai", "admin", "2030-04-02T12:00:00.000Z"), a, champs).repercussion().deplacees()).isEqualTo(2);
        assertThat(List.of(placeDu("2030-04-02", a).get("milieuId"), placeDu("2030-04-03", a).get("milieuId"))).containsExactly(ids.get("c12"), ids.get("c14"));

        // Un corps d'emploi changé requalifie la place et libère le titulaire qui n'en est plus ; la place pointée reste.
        postes.attribuerTitulaire(ctx("2027-01-15T00:00:00Z"), b, ids.get("P1"), null);
        String jeudiB = String.valueOf(((Map<?, ?>) placeDu("2030-04-04", b).get("place")).get("id"));
        http.post(API + "/places/" + jeudiB + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P1") + "\"}").andExpect(status().isOk());
        champs.put("corpsEmploiCode", "RMU");
        champs.put("milieuId", ids.get("c12"));
        http.put(API + "/postes/" + b, ADMIN, tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(champs)).andExpect(status().isOk())
                .andExpect(jsonPath("$.repercussion.requalifiees").value(3)).andExpect(jsonPath("$.repercussion.pointeesIgnorees").value(1))
                .andExpect(jsonPath("$.repercussion.conflits[0].motif").value("n'est plus du corps d'emploi du poste (RMU)"));
        assertThat(((Map<?, ?>) placeDu(D14, b).get("place")).get("corpsEmploiCode")).isEqualTo("PARA");

        // E-GR-5 : ajuster un quart d'une période PUBLIÉE — heures, milieu, minimum, note — est journalisé.
        String mercredi = String.valueOf(placeDu("2030-04-03", a).get("id"));
        http.patch(API + "/equipages/" + mercredi, ADMIN, "{\"debut\":1140,\"fin\":360,\"milieuId\":\"" + ids.get("c12") + "\",\"minimumOperationnel\":1,\"note\":\"Arrivée 19 h\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.debut").value(1140)).andExpect(jsonPath("$.milieuId").value(ids.get("c12")))
                .andExpect(jsonPath("$.minimumOperationnel").value(1)).andExpect(jsonPath("$.note").value("Arrivée 19 h"));
        http.get(E + "/journal?action=equipage.modifie&objetId=" + mercredi, ADMIN).andExpect(jsonPath("$[0].acteurId").value("usr-admin"))
                .andExpect(jsonPath("$[0].origine").value("UTILISATEUR"));
    }
}
