package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEchangesEffectifs;
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
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesEffectifs;
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
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEchangesEffectifs;
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
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEchangesEffectifs;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les échanges de quarts par HTTP</strong> (lot R13, jumeau de {@code effectifs-echanges.integration.test.ts}).
 * Julie et Marc s'échangent un quart de la même semaine : Marc accepte, les places s'échangent. Ils recommencent
 * entre deux périodes : la règle de l'organisme dit « restreint », l'échange attend la validation du chef. Marc veut
 * reprendre un quart du soir collé à sa journée : la règle de repos refuse, <strong>en le nommant</strong>. Horloge
 * figée au 15 janvier 2027 ; les semaines échangées sont en 2030.
 */
@DisplayName("/api/effectifs/echanges — proposer, accepter, valider, refuser, annuler, rejeter")
class ControleurEchangesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String API = E + "/planification";
    private static final String X = E + "/echanges";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_echanges\"]";
    private static final String MAINTENANT = "2027-01-15T10:00:00Z";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = employe("julie");
    private static final ContexteRequete MARC = employe("marc");
    private static final ContexteRequete ANA = employe("ana");
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireRegles regles;
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

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (String nom : new String[]{"julie", "marc", "ana", "chef"}) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', 'employee', 'x')")
                    .params("usr-" + nom, nom, T).update();
        }
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
        GestionnaireEquipages equipages = new GestionnaireEquipages(depotEqp, new GestionnaireEquipages.Lectures(lectEqp, lectPrd, lectPos, referentiels, milieux),
                journal, seq);
        EvaluateurRegles evaluateur = new EvaluateurRegles(depotRegles, referentiels, journal, seq);
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal);
        regles = new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq);
        ControleurPlanificationEffectifs planification = new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq),
                        new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq),
                        new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq),
                        equipages, places, new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq)));

        DepotEchangesEffectifs depotX = new DepotEchangesEffectifs(jdbc, new SchemaEchangesEffectifs(jdbc), schemaPlanif);
        LectureEchangesEffectifs lectX = new LectureEchangesEffectifs(depotX, lectRes, perimetres);
        GestionnaireEchangesEffectifs gestX = new GestionnaireEchangesEffectifs(new GestionnaireEchangesEffectifs.Lectures(lectX, lectRes, perimetres, lectEqp),
                evaluateur, depotX, journal, seq);
        // « Mon horaire » lit la même chose sous sa propre garde : le même décor l'éprouve.
        http = new ClientHttpEssai(socle, planification, new ControleurEchangesEffectifs(garde, lectX, gestX),
                new ControleurMonHoraire(garde, lectX));
        socle();
    }

    // ── Le décor : deux semaines publiées, quatre personnes, deux règles d'échange ──

    private String creer(String chemin, String json) throws Exception {
        return JsonPath.read(http.post(chemin, ADMIN, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private void etat(String periode, String etat) throws Exception {
        http.post(API + "/periodes/" + periode + "/etat", ADMIN, "{\"etat\":\"" + etat + "\"}").andExpect(status().isOk());
    }

    /** Un équipage d'une place, et la place ; attribuée si {@code ressource} n'est pas nulle. */
    private String place(String periode, String date, int debut, int fin, String corps, String ressource) throws Exception {
        String e = http.post(API + "/equipages", ADMIN, "{\"periodeId\":\"" + periode + "\",\"date\":\"" + date + "\",\"debut\":" + debut + ",\"fin\":" + fin
                        + ",\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\",\"composition\":[{\"corpsEmploiCode\":\""
                        + corps + "\",\"nombre\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(e, "$.places[0].id");
        if (ressource != null) {
            http.post(API + "/places/" + id + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get(ressource) + "\"}").andExpect(status().isOk());
        }
        return id;
    }

    private String periode(String nom, String debut, String fin) throws Exception {
        String p = creer(API + "/periodes", "{\"nom\":\"" + nom + "\",\"uniteCode\":\"EST\",\"debut\":\"" + debut + "\",\"fin\":\"" + fin + "\"}");
        etat(p, "CONFECTION");
        return p;
    }

    private void regle(String cle, Map<String, Object> condition, String action, String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", "ECHANGE");
        e.put("cle", cle);
        e.put("condition", condition);
        e.put("action", action);
        e.put("message", message);
        e.put("effectifDepuis", "2027-02-01");
        regles.creer(new Contexte(T, "usr-admin", "admin", MAINTENANT), e);
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("corps_emploi", "{\"code\":\"TECH\",\"libelleFr\":\"Techniciens\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        for (String[] r : new String[][]{{"P1", "Julie", "julie", "PARA"}, {"P2", "Marc", "marc", "PARA"}, {"P3", "Ana", "ana", "PARA"}, {"T1", "Théo", "", "TECH"}}) {
            String compte = r[2].isEmpty() ? "" : ",\"utilisateurId\":\"usr-" + r[2] + "\"";
            ids.put(r[0], creer(API + "/ressources", "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1] + "\",\"corpsEmploiCode\":\"" + r[3]
                    + "\",\"uniteCode\":\"EST\"" + compte + "}"));
        }
        ids.put("s10", periode("Semaine 10", "2030-03-04", "2030-03-10"));
        ids.put("julieLun", place(ids.get("s10"), "2030-03-04", 360, 1080, "PARA", "P1"));
        ids.put("marcMer", place(ids.get("s10"), "2030-03-06", 360, 1080, "PARA", "P2"));
        ids.put("anaMarSoir", place(ids.get("s10"), "2030-03-05", 1080, 1320, "PARA", "P3"));
        ids.put("theoJeu", place(ids.get("s10"), "2030-03-07", 360, 1080, "TECH", "T1"));
        ids.put("s11", periode("Semaine 11", "2030-03-11", "2030-03-17"));
        ids.put("marcLun11", place(ids.get("s11"), "2030-03-11", 360, 1080, "PARA", "P2"));
        etat(ids.get("s10"), "PUBLIEE");
        etat(ids.get("s11"), "PUBLIEE");
        regle("entre-periodes", Map.of("fait", "memePeriode", "op", "=", "valeur", false), "EXIGER_APPROBATION", "échange entre deux périodes : le chef valide");
        regle("repos", Map.of("fait", "reposHeures", "op", "<", "valeur", 8), "BLOQUER", "repos de {reposHeures} h, il en faut 8");
    }

    private String proposer(ContexteRequete qui, String cedee, String voulue) throws Exception {
        return http.post(X, qui, "{\"placeCedeeId\":\"" + ids.get(cedee) + "\",\"placeVoulueId\":\"" + ids.get(voulue) + "\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private void deplacer(String place, String ensemble) {
        jdbc.sql("UPDATE eff_equipages SET " + ensemble + " WHERE tenantId = ? AND id = (SELECT equipageId FROM eff_places WHERE id = ?)").params(T, ids.get(place)).update();
    }

    // ── Les scénarios du Node, dans leur ordre : chacun part de l'état laissé par le précédent ──

    @Test
    @DisplayName("les six scénarios du Node, enchaînés : jugés dans les deux sens (E-GR-7), entre deux périodes selon la règle configurée (E-GR-12)")
    void scenarios() throws Exception {
        quartsVisibles();
        memePeriode();
        entreDeuxPeriodes();
        reposInsuffisant();
        ceQuiNeSEchangePas();
        annulerRejeterHoraireChange();
    }

    private void quartsVisibles() throws Exception {
        http.get(X + "/moi", JULIE).andExpect(jsonPath("$.quarts[*].placeId", Matchers.contains(ids.get("julieLun"))))
                .andExpect(jsonPath("$.ressource.nom").value("Julie"));
        http.get(X + "/places/" + ids.get("julieLun") + "/demandables", JULIE)
                .andExpect(jsonPath("$[*].placeId", Matchers.contains(ids.get("anaMarSoir"), ids.get("marcMer"), ids.get("marcLun11"))))
                .andExpect(jsonPath("$[*].nom", Matchers.contains("Ana", "Marc", "Marc")));
        http.get(X + "/places/" + ids.get("marcMer") + "/demandables", JULIE).andExpect(status().isForbidden());
        http.get(X + "/ressources/" + ids.get("P2") + "/quarts", JULIE).andExpect(status().isForbidden());
        http.get(X + "/ressources/" + ids.get("P2") + "/quarts", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].uniteCode").value("EST")).andExpect(jsonPath("$[0].corpsEmploiCode").value("PARA"));
        http.get(X + "/moi", CHEF).andExpect(jsonPath("$.ressource").isEmpty()).andExpect(jsonPath("$.quarts", Matchers.hasSize(0)));
    }

    private void memePeriode() throws Exception {
        String p = http.post(X, JULIE, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("marcMer") + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("PROPOSE")).andExpect(jsonPath("$.nomA").value("Julie"))
                .andExpect(jsonPath("$.nomB").value("Marc")).andExpect(jsonPath("$.quartVoulu.date").value("2030-03-06"))
                .andExpect(jsonPath("$.actionRegles").value("ACCEPTER")).andExpect(jsonPath("$.motifsRegles", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.decideLe").doesNotExist()).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(p, "$.id");
        http.post(X, JULIE, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("anaMarSoir") + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("l'un de ces quarts fait déjà l'objet d'un échange en cours (" + id + ")"));
        http.post(X + "/" + id + "/accepter", JULIE, null).andExpect(status().isForbidden());
        http.get(X + "/" + id, ANA).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("échange « " + id + " » introuvable"));
        http.post(X + "/" + id + "/accepter", MARC, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("APPLIQUE"))
                .andExpect(jsonPath("$.decidePar").value("usr-marc"));
        http.get(X + "/moi", JULIE).andExpect(jsonPath("$.quarts[*].placeId", Matchers.contains(ids.get("marcMer"))));
        http.post(X + "/" + id + "/annuler", JULIE, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("un échange appliqué ne se prête plus à ce geste (annuler)"));
        http.get(E + "/journal?action=echange.applique", ADMIN).andExpect(jsonPath("$[0].objetId").value(id))
                .andExpect(jsonPath("$[0].origine").value("UTILISATEUR"));
        http.get(API + "/periodes/" + ids.get("s10") + "/equipages?date=2030-03-06", ADMIN).andExpect(jsonPath("$[0].places[0].ressourceNom").value("Julie"))
                .andExpect(jsonPath("$[0].places[0].attribueePar").value("UTILISATEUR"));
    }

    private void entreDeuxPeriodes() throws Exception {
        String p = http.post(X, JULIE, "{\"placeCedeeId\":\"" + ids.get("marcMer") + "\",\"placeVoulueId\":\"" + ids.get("marcLun11") + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.actionRegles").value("EXIGER_APPROBATION"))
                .andExpect(jsonPath("$.motifsRegles[*].message", Matchers.contains("Pour Marc : échange entre deux périodes : le chef valide",
                        "Pour Julie : échange entre deux périodes : le chef valide"))).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(p, "$.id");
        http.post(X + "/" + id + "/accepter", MARC, "{}").andExpect(jsonPath("$.statut").value("A_VALIDER"));
        http.post(X + "/" + id + "/valider", JULIE, null).andExpect(status().isForbidden());
        http.post(X + "/" + id + "/valider", CHEF, null).andExpect(status().isNotFound());
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
        http.get(X + "?statut=A_VALIDER", CHEF).andExpect(jsonPath("$[*].id", Matchers.contains(id)));
        http.post(X + "/" + id + "/valider", CHEF, "{\"motif\":\"Remplacement couvert\"}").andExpect(jsonPath("$.statut").value("APPLIQUE"))
                .andExpect(jsonPath("$.motifDecision").value("Remplacement couvert"));
        http.get(X + "/moi", JULIE).andExpect(jsonPath("$.quarts[*].placeId", Matchers.contains(ids.get("marcLun11"))));
    }

    private void reposInsuffisant() throws Exception {
        // Marc (lundi 06–18, mercredi 06–18) prendrait le mardi 18–22 d'Ana : 8 h de repos avant mercredi, la règle passe.
        String r = proposer(MARC, "julieLun", "anaMarSoir");
        http.post(X + "/" + JsonPath.read(r, "$.id") + "/refuser", ANA, "{\"motif\":\"Pas disponible\"}").andExpect(jsonPath("$.statut").value("REFUSE"))
                .andExpect(jsonPath("$.motifDecision").value("Pas disponible"));
        // Resserrons l'horaire de Marc : son mercredi devient un mardi 06–19, et le soir d'Ana un mardi 19–23.
        deplacer("anaMarSoir", "debut = 1140, fin = 1380");
        deplacer("marcMer", "date = '2030-03-05', debut = 360, fin = 1140");
        http.post(X, MARC, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("anaMarSoir") + "\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.verdict.motifs[*].message", Matchers.contains("Pour Marc : repos de 0 h, il en faut 8")))
                .andExpect(jsonPath("$.verdict.action").value("BLOQUER")).andExpect(jsonPath("$.errors[0]").value("Pour Marc : repos de 0 h, il en faut 8"));
    }

    private void ceQuiNeSEchangePas() throws Exception {
        http.post(X, ADMIN, "{\"placeCedeeId\":\"inconnue\",\"placeVoulueId\":\"" + ids.get("julieLun") + "\"}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("place « inconnue » introuvable"));
        http.post(X, ADMIN, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("julieLun") + "\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("on n'échange pas un quart avec soi-même"));
        http.post(X, ADMIN, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("theoJeu") + "\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Marc et Théo ne sont pas admissibles aux places l'une de l'autre (corps d'emploi différents)"));
        ids.put("s12", periode("Semaine 12", "2030-03-18", "2030-03-24"));
        ids.put("vide", place(ids.get("s12"), "2030-03-18", 360, 1080, "PARA", null));
        http.post(X, ADMIN, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("vide") + "\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("vacante")));
        http.get(X + "/places/" + ids.get("vide") + "/demandables", ADMIN).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("cette place est vacante : elle ne s'échange pas"));
        http.post(API + "/places/" + ids.get("vide") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\"}").andExpect(status().isOk());
        http.post(X, ADMIN, "{\"placeCedeeId\":\"" + ids.get("julieLun") + "\",\"placeVoulueId\":\"" + ids.get("vide") + "\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le quart du 2030-03-18 appartient à une période qui n'est pas publiée : on n'échange que des quarts publiés"));
    }

    private void annulerRejeterHoraireChange() throws Exception {
        String p = JsonPath.read(proposer(JULIE, "marcLun11", "julieLun"), "$.id");
        http.post(X + "/" + p + "/annuler", MARC, null).andExpect(status().isForbidden());
        http.post(X + "/" + p + "/annuler", JULIE, null).andExpect(jsonPath("$.statut").value("ANNULE"));
        String q = JsonPath.read(proposer(JULIE, "marcLun11", "julieLun"), "$.id");
        http.post(X + "/" + q + "/accepter", MARC, null).andExpect(jsonPath("$.statut").value("A_VALIDER"));
        http.post(X + "/" + q + "/rejeter", CHEF, "{\"motif\":\"Semaine chargée\"}").andExpect(jsonPath("$.statut").value("REJETE"));
        String r = JsonPath.read(proposer(JULIE, "marcLun11", "julieLun"), "$.id");
        jdbc.sql("UPDATE eff_places SET ressourceId = ? WHERE tenantId = ? AND id = ?").params(ids.get("P3"), T, ids.get("julieLun")).update();
        http.post(X + "/" + r + "/accepter", MARC, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("l'horaire a changé")));
        http.get(X, JULIE).andExpect(jsonPath("$.length()", Matchers.greaterThanOrEqualTo(5)));
        http.get(X, ANA).andExpect(jsonPath("$[*].id", Matchers.not(Matchers.hasItem(p))));
        http.get(X, ADMIN).andExpect(jsonPath("$.length()").value(6));
        http.get(X + "/" + p, ADMIN).andExpect(jsonPath("$.statut").value("ANNULE"));
        http.get(X + "?statut=", CHEF).andExpect(jsonPath("$.length()").value(6));
    }

    // ── Ce que le Node couvre par ailleurs : garde de module, introuvables, rejugement à l'acceptation ──

    @Test
    @DisplayName("garde de module : sans effectifs_echanges, 403 nommé ; introuvables : 404 nommés")
    void gardesEtIntrouvables() throws Exception {
        http.get(X + "/ressources/inconnue/quarts", ADMIN).andExpect(status().isNotFound());
        http.get(X + "/inconnu", ADMIN).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("échange « inconnu » introuvable"));
        http.post(X + "/inconnu/refuser", ADMIN, null).andExpect(status().isNotFound());
        http.post(X, ADMIN, "{}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("place « undefined » introuvable"));
        http.post(X + "/", ADMIN, null).andExpect(status().isNotFound());
        http.post(X, ADMIN, "{\"placeCedeeId\":3}").andExpect(jsonPath("$.errors[0]").value("place « 3 » introuvable"));
        http.get(X + "/places/inconnue/demandables", ADMIN).andExpect(status().isNotFound());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_planification\"]", T).update();
        http.get(X + "/moi", JULIE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("effectifs_echanges")));
        http.post(X + "/x/valider", ADMIN, null).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("l'acceptation rejuge sur l'horaire du moment : un chevauchement la rend impossible, un repos trop court la bloque")
    void acceptationRejugee() throws Exception {
        String id = JsonPath.read(proposer(MARC, "marcMer", "anaMarSoir"), "$.id");
        deplacer("marcLun11", "date = '2030-03-05', debut = 1000, fin = 1200");
        http.post(X + "/" + id + "/accepter", ANA, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le quart du 2030-03-05 chevaucherait un autre quart de la même personne"));
        deplacer("marcLun11", "date = '2030-03-05', debut = 360, fin = 1080");
        http.post(X + "/" + id + "/accepter", ANA, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.verdict.motifs[0].message").value("Pour Marc : repos de 0 h, il en faut 8"));
        http.get(X + "/" + id, MARC).andExpect(jsonPath("$.statut").value("PROPOSE"));
        http.post(X + "/" + id + "/refuser", ANA, "{\"motif\":\"   \"}").andExpect(jsonPath("$.statut").value("REFUSE"))
                .andExpect(jsonPath("$.motifDecision").doesNotExist());
    }
    @Test
    @DisplayName("mon horaire : mes quarts publiés, avec leur site — et rien de ceux des autres (maquette s03)")
    void monHoraire() throws Exception {
        // Sans session, rien : un horaire est nominatif, et il n'y a aucun paramètre pour en
        // demander un autre.
        http.get("/api/effectifs/mon-horaire", null).andExpect(status().isUnauthorized());

        String corps = http.get("/api/effectifs/mon-horaire", employe("ana")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // La ressource de l'appelant, puis ses quarts : c'est ce que l'écran rend.
        assertThat(JsonPath.<String>read(corps, "$.ressource.nom")).isNotBlank();
        assertThat(JsonPath.<java.util.List<Object>>read(corps, "$.quarts")).isNotEmpty();
        // Le **site** fait partie du quart : c'est la colonne de la planche qui manquait.
        assertThat(JsonPath.<java.util.List<Object>>read(corps, "$.quarts[*].milieuId")).isNotEmpty();

        // Chaque quart rendu est bien le sien : l'horaire d'autrui ne s'obtient pas par cette route.
        String ressourceId = JsonPath.read(corps, "$.ressource.id");
        for (Object r : JsonPath.<java.util.List<Object>>read(corps, "$.quarts[*].ressourceId")) {
            assertThat(r).isEqualTo(ressourceId);
        }

        /*
          **La fenêtre de dates** (8 novembre 2026).

          La lecture était figée à « aujourd'hui et après », parce qu'elle servait d'abord à
          proposer un échange — on ne cède pas un quart déjà travaillé. Mais c'est au moment où sa
          feuille de temps lui demande de vérifier le *planifié* de la semaine écoulée que
          l'employée a besoin de cet horaire, et il avait disparu.
        */
        String jour = JsonPath.read(corps, "$.quarts[0].date");
        http.get("/api/effectifs/mon-horaire?du=" + jour + "&au=" + jour, employe("ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quarts.length()").value(greaterThan(0)));
        // La veille seule : la fenêtre borne **par le haut** aussi, sinon elle ne bornerait rien.
        String veille = java.time.LocalDate.parse(jour).minusDays(1).toString();
        http.get("/api/effectifs/mon-horaire?du=" + veille + "&au=" + veille, employe("ana")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quarts.length()").value(0));

        // Les trois bornes absurdes, refusées **nommément** : chacune rendrait sinon un résultat
        // plausible et faux, et un horaire faux ne se corrige pas, il s'applique.
        http.get("/api/effectifs/mon-horaire?du=" + jour, employe("ana")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« du » et « au » vont de pair : une fenêtre a deux bornes"));
        http.get("/api/effectifs/mon-horaire?du=" + jour + "&au=" + veille, employe("ana")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« du » (" + jour + ") ne peut suivre « au » (" + veille + ")"));
        http.get("/api/effectifs/mon-horaire?du=2030-01-01&au=2031-01-01", employe("ana")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("la fenêtre ne peut dépasser 92 jours (demandé : 366)"));
        http.get("/api/effectifs/mon-horaire?du=2030-13-99&au=2030-03-04", employe("ana")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« du » : date AAAA-MM-JJ attendue"));
    }

}
