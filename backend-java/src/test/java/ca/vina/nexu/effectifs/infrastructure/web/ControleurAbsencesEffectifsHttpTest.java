package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs.Declaration;
import ca.vina.nexu.effectifs.application.commandes.DecisionRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireComblementAbsences;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.SuiviComblementAbsence;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotComblementsAbsences;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotSoldesBanques;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotDisponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRemplacementsAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaAgph;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaDisponibilites;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
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
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
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
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.HorsPerimetreException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPlanningAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaAbsencesEffectifs;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les absences d'effectifs par HTTP</strong> (lot R13, jumeau de {@code effectifs-absences.integration.test.ts}).
 * <p>
 * Julie tient les deux quarts du début de semaine. Elle déclare une absence (ici par le gestionnaire, que la façade
 * {@code /api/absences} appelle en Node) : les règles la refusent — le comblement tomberait à 0 % — et le refus reste,
 * avec son motif. Son chef mesure l'impact, déroge avec un motif et un code : l'absence est accordée, et les deux
 * places deviennent vacantes, libérées par le mécanisme et non par Julie. Horloge figée au 15 janvier 2027.
 */
@DisplayName("/api/effectifs/absences — lecture, impact, décisions, annulation")
class ControleurAbsencesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String API = E + "/absences";
    private static final String PLANIF = E + "/planification";
    private static final String LUNDI = "2030-03-04";
    private static final String MARDI = "2030-03-05";
    private static final String MAINTENANT = "2027-01-15T10:00:00Z";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_absences\",\"effectifs_planification\",\"effectifs_agph\"]";
    private DepotSoldesBanques soldes;
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final ContexteRequete MARC = new ContexteRequete("usr-marc", "marc", "employee", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireAbsencesEffectifs absences;
    private LectureAbsencesEffectifs lecture;
    private GestionnaireRegles regles;
    private DepotDisponibilites depotDispo;
    private GestionnaireComblementAbsences comblement;
    /** Les avis publiés, et la panne qu'on peut leur imposer : le module d'avis réel vit dans un autre contexte. */
    private final List<PublicationAvis.Avis> avisPublies = new ArrayList<>();
    private boolean avisEnPanne;
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

    private static Contexte ctx(ContexteRequete qui) {
        return new Contexte(T, qui.userId(), qui.role(), MAINTENANT);
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (String u : new String[]{"julie", "marc", "chef", "lea", "theo"}) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', 'employee', 'x')").params("usr-" + u, u, T).update();
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
        EvaluateurRegles evaluateur = new EvaluateurRegles(depotRegles, referentiels, journal, seq);
        GestionnaireEquipages equipages = new GestionnaireEquipages(depotEqp, new GestionnaireEquipages.Lectures(lectEqp, lectPrd, lectPos, referentiels, milieux),
                journal, seq);
        ControleurPlanificationEffectifs planification = new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq),
                        new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq),
                        new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq),
                        equipages, new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal),
                        new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq)));
        regles = new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq);

        SchemaAbsencesEffectifs schemaAbs = new SchemaAbsencesEffectifs(jdbc);
        DepotAbsencesEffectifs depotAbs = new DepotAbsencesEffectifs(jdbc, schemaPlanif, schemaAbs);
        DepotPlanningAbsencesEffectifs planning = new DepotPlanningAbsencesEffectifs(jdbc, schemaPlanif);
        lecture = new LectureAbsencesEffectifs(depotAbs, planning, lectRes, perimetres);
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal);
        LectureDisponibilites.Lectures lectPlanif = new LectureDisponibilites.Lectures(lectRes, lectPrd, lectEqp, perimetres);
        depotDispo = new DepotDisponibilites(jdbc, new SchemaDisponibilites(jdbc));
        LectureDisponibilites dispo = new LectureDisponibilites(depotDispo, lectPlanif, evaluateur);
        PublicationAvis avis = a -> {
            if (avisEnPanne) {
                throw new IllegalStateException("journal des avis indisponible");
            }
            avisPublies.add(a);
            return a.destinataires().stream().map(d -> new PublicationAvis.Envoi(d, "courriel", "sent", null)).toList();
        };
        DepotComblementsAbsences depotCbl = new DepotComblementsAbsences(jdbc, schemaAbs);
        comblement = new GestionnaireComblementAbsences(new GestionnaireComblementAbsences.Lectures(lectEqp, dispo, lectRes, referentiels),
                new GestionnaireComblementAbsences.Ecritures(depotCbl, new GestionnaireAttributionFinDeCollecte(lectPlanif, dispo, places, journal), avis, journal, seq));
        DepotRemplacementsAgph depotRlt = new DepotRemplacementsAgph(jdbc, new SchemaAgph(jdbc), schemaPlanif);
        GestionnaireRemplacementsAgph remplacements = new GestionnaireRemplacementsAgph(depotRlt, new DecisionRemplacementsAgph(depotRlt,
                new DecisionRemplacementsAgph.Lectures(lectPos, lectRes, referentiels, evaluateur), new DecisionRemplacementsAgph.Quarts(lectEqp, depotEqp), journal),
                new GestionnaireRemplacementsAgph.Socle(lectPos, lectRes, perimetres, journal, seq));
        SuiviComblementAbsence suivi = new SuiviComblementAbsence(new SuiviComblementAbsence.Lectures(depotCbl, perimetres, lectRes, lectPos),
                new SuiviComblementAbsence.Remplacements(depotRlt, remplacements, new AdaptateurModulesPourEffectifs(jdbc), journal), comblement);
        soldes = new DepotSoldesBanques(jdbc, new SchemaEffectifs(jdbc));
        absences = new GestionnaireAbsencesEffectifs(new GestionnaireAbsencesEffectifs.Ports(depotAbs, planning, depotEqp, comblement),
                new GestionnaireAbsencesEffectifs.Lectures(lecture, lectRes, referentiels, perimetres, soldes), evaluateur, journal, seq);
        http = new ClientHttpEssai(socle, planification, new ControleurAbsencesEffectifs(garde, lecture, absences, suivi));
        semaine();
    }

    private String creer(String chemin, String json) throws Exception {
        return JsonPath.read(http.post(chemin, ADMIN, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    /** Le décor du Node : une unité, deux paramédics, deux quarts tenus par Julie, deux règles d'absence. */
    private void semaine() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("titres_emploi", "{\"code\":\"PSA\",\"libelleFr\":\"Paramédic en soins avancés\",\"attributs\":{\"corps\":\"PARA\"}}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("codes_absence", "{\"code\":\"MAL\",\"libelleFr\":\"Maladie\",\"comportement\":\"PAYE\",\"attributs\":{\"duree\":\"PONCTUELLE\"}}");
        ref("codes_absence", "{\"code\":\"FORM\",\"libelleFr\":\"Formation PSA\",\"comportement\":\"PAYE\",\"attributs\":{\"duree\":\"PONCTUELLE\",\"titres\":[\"PSA\"]}}");
        String c12 = creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}");
        ids.put("c12", c12);
        ids.put("P1", creer(PLANIF + "/ressources", "{\"matricule\":\"P1\",\"nom\":\"Julie\",\"corpsEmploiCode\":\"PARA\",\"titreEmploiCode\":\"PSA\","
                + "\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-julie\"}"));
        ids.put("P2", creer(PLANIF + "/ressources", "{\"matricule\":\"P2\",\"nom\":\"Marc\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-marc\"}"));
        ids.put("periode", creer(PLANIF + "/periodes", "{\"nom\":\"Semaine 10\",\"uniteCode\":\"EST\",\"debut\":\"" + LUNDI + "\",\"fin\":\"2030-03-10\"}"));
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        for (String date : new String[]{LUNDI, MARDI}) {
            String e = http.post(PLANIF + "/equipages", ADMIN, "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + date + "\",\"debut\":360,\"fin\":1080,"
                    + "\"natureCode\":\"REG\",\"milieuId\":\"" + c12 + "\",\"modeAttribution\":\"AUTOMATISE\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}")
                    .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
            http.post(PLANIF + "/places/" + JsonPath.read(e, "$.places[0].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P1") + "\"}")
                    .andExpect(status().isOk());
        }
        regle("comblement-minimal", Map.of("fait", "tauxComblementApres", "op", "<", "valeur", 60), "REFUSER", "Le comblement tomberait à {tauxComblementApres} %");
        regle("longue-absence", Map.of("fait", "joursDemandes", "op", ">", "valeur", 3), "EXIGER_APPROBATION", "{joursDemandes} jours : l'accord du chef est requis");
    }

    private void regle(String cle, Map<String, Object> condition, String action, String message) {
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", "ABSENCE");
        e.put("cle", cle);
        e.put("condition", condition);
        e.put("action", action);
        e.put("message", message);
        e.put("effectifDepuis", "2027-02-01");
        regles.creer(ctx(ADMIN), e);
    }

    private GestionnaireAbsencesEffectifs.Resultat declarer(ContexteRequete qui, Object ressourceId, String code, String du, String au, String motif) {
        return absences.declarer(ctx(qui), new Declaration(ressourceId, code, du, au, motif));
    }

    private String decider(ContexteRequete qui, String id, String json, int code) throws Exception {
        return http.post(API + "/" + id + "/decisions", qui, json).andExpect(status().is(code)).andReturn().getResponse().getContentAsString();
    }

    @Test
    @DisplayName("garde de module : fermé, la route répond 403 en nommant effectifs_absences ; décider exige effectifs.manage")
    void gardes() throws Exception {
        http.post(API + "/x/decisions", JULIE, "{\"decision\":\"APPROUVER\"}").andExpect(status().isForbidden());
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_planification\"]", T).update();
        http.get(API, JULIE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("effectifs_absences")));
    }

    @Test
    @DisplayName("déclarer par la route : soi-même par défaut, refus des règles enregistré avec ses motifs, corps vide refusé nommément")
    void declarerParLaRoute() throws Exception {
        http.post(API, JULIE, "{\"motif\":\"MAL\",\"du\":\"" + LUNDI + "\",\"au\":\"" + MARDI + "\",\"commentaire\":\"Grippe\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.categorie").value("effectifs"))
                .andExpect(jsonPath("$.statut").value("REFUSEE_AUTO")).andExpect(jsonPath("$.ids.length()").value(1))
                .andExpect(jsonPath("$.motifs.length()").value(1)).andExpect(jsonPath("$.placesLiberees").value(0)).andExpect(jsonPath("$.comblement").exists())
                .andExpect(jsonPath("$.absences[0].codeAbsence").value("MAL"));
        http.post(API, JULIE, null).andExpect(status().isUnprocessableEntity());
        http.post(API, null, "{}").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("scénario du Node : refus automatique conservé avec son motif (P-GN-2, P-GH-2), périmètre, impact, dérogation, approbation, annulation, refus")
    void scenario() throws Exception {
        refusEnregistre();
        codeEtDatesRefuses();
        perimetreEtImpact();
        derogation();
        longueAbsence();
        refusDUneAbsenceAApprouver();
    }

    /** Un refus des règles est enregistré, avec son motif ; les places restent tenues (E-GN-6). */
    private void refusEnregistre() throws Exception {
        GestionnaireAbsencesEffectifs.Resultat r = declarer(JULIE, null, "MAL", LUNDI, MARDI, " Grippe ");
        assertThat(r.absence().statut().code()).isEqualTo("REFUSEE_AUTO");
        assertThat(r.absence().motifsRegles().get(0).message()).isEqualTo("Le comblement tomberait à 0 %");
        assertThat(r.placesLiberees()).isZero();
        ids.put("refusee", r.absence().id());
        http.get(API, JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("refusee"))))
                .andExpect(jsonPath("$[0].motif").value("Grippe")).andExpect(jsonPath("$[0].ressourceNom").value("Julie"))
                .andExpect(jsonPath("$[0].motifsRegles[0].action").value("REFUSER")).andExpect(jsonPath("$[0].actionRegles").value("REFUSER"));
        http.get(API, ADMIN).andExpect(jsonPath("$", Matchers.hasSize(1)));
    }

    /** Un code réservé à un titre d'emploi est refusé à qui ne le porte pas ; une date invalide aussi ; autrui sans périmètre : 403. */
    private void codeEtDatesRefuses() {
        assertThatThrownBy(() -> declarer(MARC, "", "FORM", "2030-04-01", null, null)).isInstanceOf(EffectifsInvalideException.class)
                .hasMessage("le code « FORM » n'est pas admissible pour le titre d'emploi (aucun) : titres admis PSA");
        assertThatThrownBy(() -> declarer(MARC, null, "MAL", "hier", null, null)).isInstanceOf(EffectifsInvalideException.class);
        assertThatThrownBy(() -> declarer(MARC, null, "INCONNU", "2030-04-01", null, null)).isInstanceOf(EffectifsInvalideException.class);
        assertThatThrownBy(() -> declarer(MARC, null, null, "2030-04-01", null, null)).isInstanceOf(EffectifsInvalideException.class);
        assertThatThrownBy(() -> declarer(MARC, ids.get("P1"), "MAL", "2030-04-01", null, null)).isInstanceOf(HorsPerimetreException.class);
        assertThatThrownBy(() -> declarer(CHEF, null, "MAL", "2030-04-01", null, null)).isInstanceOf(IntrouvableException.class);
    }

    /** Hors périmètre, le chef ne voit rien ; dans son périmètre, il mesure l'impact (E-GR-3). */
    private void perimetreEtImpact() throws Exception {
        String refusee = API + "/" + ids.get("refusee");
        http.get(API, CHEF).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(refusee + "/impact", CHEF).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("absence « " + ids.get("refusee") + " » introuvable"));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
        http.get(API, CHEF).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("refusee"))));
        http.get(refusee + "/impact", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.places", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.places[0].date").value(LUNDI)).andExpect(jsonPath("$.places[0].debut").value(360))
                .andExpect(jsonPath("$.tauxAvant").value(100)).andExpect(jsonPath("$.tauxApres").value(0));
        http.get(refusee, MARC).andExpect(status().isNotFound());
        http.get(refusee, CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("REFUSEE_AUTO"));
        http.get(API + "?du=hier", CHEF).andExpect(status().is(422));
    }

    /** La dérogation exige un motif et un code, accorde l'absence et rend les places vacantes (E-GN-5, E-CH-6). */
    private void derogation() throws Exception {
        String id = ids.get("refusee");
        decider(JULIE, id, "{\"decision\":\"DEROGER\"}", 403);
        decider(CHEF, id, "{\"decision\":\"FORCER\"}", 422);
        decider(CHEF, "inconnue", "{\"decision\":\"FORCER\"}", 422);
        decider(CHEF, id, "{\"decision\":\"DEROGER\",\"motif\":\"court\",\"codeAbsence\":\"MAL\"}", 422);
        decider(CHEF, id, "{\"decision\":\"DEROGER\",\"motif\":\"Hospitalisation confirmée\",\"codeAbsence\":\"XX\"}", 422);
        decider(CHEF, id, "{\"decision\":\"APPROUVER\"}", 422);
        http.post(API + "/" + id + "/decisions", CHEF, "{\"decision\":\"DEROGER\",\"motif\":\"Hospitalisation confirmée par billet médical\",\"codeAbsence\":\"MAL\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ACCEPTEE")).andExpect(jsonPath("$.derogationCode").value("MAL"))
                .andExpect(jsonPath("$.derogationMotif").value("Hospitalisation confirmée par billet médical"))
                .andExpect(jsonPath("$.motifDecision").value("Hospitalisation confirmée par billet médical"))
                .andExpect(jsonPath("$.placesLiberees").value(2)).andExpect(jsonPath("$.decideePar").value("usr-chef"));
        http.get(PLANIF + "/periodes/" + ids.get("periode") + "/equipages", ADMIN)
                .andExpect(jsonPath("$[*].places[0].ressourceId", Matchers.contains(Matchers.nullValue(), Matchers.nullValue())));
        http.get(E + "/journal?action=places.vacantes", ADMIN).andExpect(jsonPath("$[0].origine").value("SYSTEME"))
                .andExpect(jsonPath("$[0].acteurId").value("vacance-sur-absence")).andExpect(jsonPath("$[0].objetId").value(id));
        decider(CHEF, id, "{\"decision\":\"DEROGER\",\"motif\":\"Encore une fois svp\",\"codeAbsence\":\"MAL\"}", 422);
    }

    /** Une longue absence attend l'approbation ; un chevauchement est refusé ; approuvée puis annulée par la personne. */
    private void longueAbsence() throws Exception {
        GestionnaireAbsencesEffectifs.Resultat r = declarer(JULIE, null, "MAL", "2030-03-06", "2030-03-09", null);
        assertThat(r.absence().statut().code()).isEqualTo("A_APPROUVER");
        assertThat(r.absence().motifsRegles().get(0).message()).isEqualTo("4 jours : l'accord du chef est requis");
        String id = r.absence().id();
        assertThat(lecture.etatPourFacade(ctx(JULIE), id)).hasValue(new LectureAbsencesEffectifs.EtatPourFacade(ids.get("P1"), false));
        assertThatThrownBy(() -> declarer(JULIE, null, "MAL", "2030-03-08", null, null)).isInstanceOf(ConflitException.class).hasMessageContaining("couvre déjà");
        http.post(API + "/" + id + "/decisions", CHEF, "{\"decision\":\"APPROUVER\",\"motif\":\"Remplacement trouvé\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE")).andExpect(jsonPath("$.motifDecision").value("Remplacement trouvé"))
                .andExpect(jsonPath("$.placesLiberees").value(0));
        http.post(API + "/" + id + "/annuler", MARC, null).andExpect(status().isNotFound());
        http.post(API + "/" + id + "/annuler", JULIE, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULEE"));
        http.get(API + "?statut=ANNULEE", JULIE).andExpect(jsonPath("$[*].id", Matchers.contains(id)));
        http.get(API + "?du=2030-03-01&au=2030-03-05&statut=", CHEF).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("refusee"))));
    }

    /** Un refus d'une absence à approuver la clôt ; la façade la voit décidée. Une absence acceptée d'emblée libère aussitôt. */
    private void refusDUneAbsenceAApprouver() throws Exception {
        GestionnaireAbsencesEffectifs.Resultat r = declarer(CHEF, ids.get("P2"), "MAL", "2030-05-01", "2030-05-10", null);
        assertThat(r.absence().statut().code()).isEqualTo("A_APPROUVER");
        assertThat(r.absence().ressourceId()).isEqualTo(ids.get("P2"));
        String id = r.absence().id();
        http.post(API + "/" + id + "/decisions", CHEF, "{\"decision\":\"REFUSER\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE")).andExpect(jsonPath("$.motifDecision").value(Matchers.nullValue()));
        assertThat(lecture.etatPourFacade(ctx(MARC), id)).hasValue(new LectureAbsencesEffectifs.EtatPourFacade(ids.get("P2"), true));
        assertThat(lecture.etatPourFacade(ctx(MARC), "inconnue")).isEmpty();
        GestionnaireAbsencesEffectifs.Resultat courte = declarer(MARC, null, "MAL", "2030-06-01", null, null);
        assertThat(courte.absence().statut().code()).isEqualTo("ACCEPTEE");
        assertThat(courte.absence().au()).isEqualTo("2030-06-01");
        assertThat(courte.placesLiberees()).isZero();
    }

    // ── Lot U5 : comblement automatique, avis, absences planifiées et longues ──────────────────────

    private String equipage(String date, int debut, int fin, String mode, String titulaire) throws Exception {
        String e = http.post(PLANIF + "/equipages", ADMIN, "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + date + "\",\"debut\":" + debut + ",\"fin\":" + fin
                        + ",\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"" + mode + "\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String place = JsonPath.read(e, "$.places[0].id");
        http.post(PLANIF + "/places/" + place + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get(titulaire) + "\"}").andExpect(status().isOk());
        return place;
    }

    private void disponible(String ressource, String... dates) {
        List<DepotDisponibilitesPort.PlageIdentifiee> plages = new ArrayList<>();
        for (String d : dates) {
            plages.add(new DepotDisponibilitesPort.PlageIdentifiee("pl-" + ressource + d, new Disponibilites.PlageDisponible(d, 0, 1440, Disponibilites.TypeDisponibilite.DISPONIBLE)));
        }
        depotDispo.remplacer(T, new DepotDisponibilitesPort.NouveauDepot(ids.get("periode"), ids.get(ressource), Disponibilites.Conformite.CONFORME, List.of(),
                MAINTENANT, "usr-admin", plages));
    }

    /** Le décor U5 : Léa (15 ans) et Théo, Léa tient la nuit du lundi, le mardi passe en attribution manuelle, tous déposent. */
    private void decorComblement() throws Exception {
        ids.put("P3", creer(PLANIF + "/ressources", "{\"matricule\":\"P3\",\"nom\":\"Léa\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"anciennete\":15,"
                + "\"utilisateurId\":\"usr-lea\"}"));
        ids.put("P4", creer(PLANIF + "/ressources", "{\"matricule\":\"P4\",\"nom\":\"Théo\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"anciennete\":6}"));
        jdbc.sql("UPDATE users SET employeeId = 'emp-marc' WHERE id = 'usr-marc'").update();
        jdbc.sql("UPDATE eff_equipages SET modeAttribution = 'MANUEL' WHERE tenantId = ? AND date = ?").params(T, MARDI).update();
        ids.put("nuitLea", equipage(LUNDI, 0, 300, "MANUEL", "P3"));
        ids.put("mercredi", equipage("2030-03-06", 360, 1080, "AUTOMATISE", "P4"));
        for (String r : new String[]{"P1", "P2", "P3"}) {
            disponible(r, LUNDI, MARDI, "2030-03-06");
        }
        Map<String, Object> repos = new HashMap<>();
        repos.put("contexte", "ATTRIBUTION");
        repos.put("cle", "repos-minimal");
        repos.put("condition", Map.of("fait", "reposHeures", "op", "<", "valeur", 8));
        repos.put("action", "BLOQUER");
        repos.put("message", "Repos de {reposHeures} h seulement");
        repos.put("effectifDepuis", "2027-02-01");
        regles.creer(ctx(ADMIN), repos);
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("U5 — après la dérogation, le quart automatisé va au prioritaire accepté par les règles, le quart manuel est offert aux admissibles avisés (E-GR-4, P-GN-1, P-GH-3)")
    void comblementApresDerogation() throws Exception {
        decorComblement();
        GestionnaireAbsencesEffectifs.Resultat r = declarer(JULIE, null, "MAL", LUNDI, MARDI, null);
        assertThat(List.of(r.absence().statut().code(), r.comblement())).containsExactly("REFUSEE_AUTO", new AbsencesEffectifs.BilanComblement(0, 0, 0, 0));
        String id = r.absence().id();
        http.post(API + "/" + id + "/decisions", CHEF, "{\"decision\":\"DEROGER\",\"motif\":\"Hospitalisation confirmée par billet médical\",\"codeAbsence\":\"MAL\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.placesLiberees").value(2))
                .andExpect(jsonPath("$.comblement.attribuees").value(1)).andExpect(jsonPath("$.comblement.offertes").value(1))
                .andExpect(jsonPath("$.comblement.sansCandidat").value(0)).andExpect(jsonPath("$.comblement.reservees").value(0));
        http.get(API + "/" + id + "/comblement", CHEF).andExpect(status().isOk())
                .andExpect(jsonPath("$.places[*].resultat", Matchers.contains("ATTRIBUEE", "OFFERTE")))
                .andExpect(jsonPath("$.places[0].mode").value("AUTOMATISE")).andExpect(jsonPath("$.places[0].attribueeANom").value("Marc"))
                .andExpect(jsonPath("$.places[0].occupeeParNom").value("Marc")).andExpect(jsonPath("$.places[0].avis[0].nom").value("Marc"))
                .andExpect(jsonPath("$.places[1].occupeeParId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.places[1].avis[*].nom", Matchers.contains("Léa", "Marc")))
                .andExpect(jsonPath("$.remplacementPossible").value(false))
                .andExpect(jsonPath("$.raisonRemplacement").value("le code « MAL » est ponctuel, ou l'absence reste sous son seuil : ses quarts se comblent un à un"));
        assertThat(avisPublies.stream().map(a -> a.evenement() + ":" + a.destinataires().get(0)).toList())
                .containsExactly("quart.attribue:emp-marc", "quart.offert:usr-lea", "quart.offert:emp-marc");
        assertThat(avisPublies.get(0).sujet()).isEqualTo("Quart attribué le 2030-03-04");
        assertThat(avisPublies.get(1).corps()).startsWith("Un quart le 2030-03-05 de 06:00 à 18:00 (EST) est disponible");
        http.get(E + "/journal?action=places.comblement", ADMIN).andExpect(jsonPath("$[0].acteurId").value("comblement-sur-absence"))
                .andExpect(jsonPath("$[0].objetId").value(id));
        http.get(API + "/" + id + "/comblement", JULIE).andExpect(status().isForbidden());
        http.get(API + "/inconnue/comblement", CHEF).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("U5 — sans admissible la place reste vacante ; sans compte, ou avis en panne, l'échec est consigné ; une absente n'est pas candidate")
    void comblementSansCandidat() throws Exception {
        decorComblement();
        Contexte c = ctx(CHEF);
        AbsenceEffectifs julie = absences.declarer(ctx(ADMIN), new Declaration(ids.get("P1"), "MAL", "2030-03-06", null, null)).absence();
        String mardi = jdbc.sql("SELECT p.id FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId WHERE e.date = ?").params(MARDI).query(String.class).single();
        jdbc.sql("UPDATE eff_places SET ressourceId = NULL WHERE id = ?").params(mardi).update();
        jdbc.sql("UPDATE eff_ressources SET utilisateurId = NULL WHERE id = ?").params(ids.get("P3")).update();
        List<AbsencesEffectifs.PlaceTenue> place = List.of(new AbsencesEffectifs.PlaceTenue(mardi, MARDI, 360, 1080));
        assertThat(comblement.combler(c, julie, place)).isEqualTo(new AbsencesEffectifs.BilanComblement(0, 1, 0, 0));
        avisEnPanne = true;
        comblement.combler(c, julie, place);
        avisEnPanne = false;
        List<Map<String, Object>> lignes = jdbc.sql("SELECT ressourceId, statut, erreur FROM eff_comblement_avis ORDER BY rowid").query().listOfRows();
        // Léa (15 ans) d'abord, puis Marc et Julie à ancienneté égale, Marc ayant moins d'heures : Julie n'est absente que le mercredi.
        assertThat(lignes).extracting(l -> l.get("ressourceId") + "|" + l.get("statut") + "|" + l.get("erreur")).containsExactly(
                ids.get("P3") + "|failed|aucun compte relié à cette ressource : personne à aviser", ids.get("P2") + "|sent|null", ids.get("P1") + "|sent|null",
                ids.get("P3") + "|failed|aucun compte relié à cette ressource : personne à aviser", ids.get("P2") + "|failed|journal des avis indisponible",
                ids.get("P1") + "|failed|journal des avis indisponible");
        jdbc.sql("UPDATE eff_ressources SET actif = 0 WHERE id IN (?, ?, ?)").params(ids.get("P1"), ids.get("P2"), ids.get("P3")).update();
        assertThat(comblement.combler(c, julie, place)).isEqualTo(new AbsencesEffectifs.BilanComblement(0, 0, 1, 0));
        String lundi = jdbc.sql("SELECT p.id FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId WHERE e.date = ? AND e.debut = 360").params(LUNDI)
                .query(String.class).single();
        jdbc.sql("UPDATE eff_places SET ressourceId = NULL WHERE id = ?").params(lundi).update();
        assertThat(comblement.combler(c, julie, List.of(new AbsencesEffectifs.PlaceTenue(lundi, LUNDI, 360, 1080)))).isEqualTo(new AbsencesEffectifs.BilanComblement(0, 0, 1, 0));
        assertThat(comblement.combler(c, julie, List.of())).isEqualTo(new AbsencesEffectifs.BilanComblement(0, 0, 0, 0));
        assertThat(depotDispo.absenteLe(T, ids.get("P1"), "2030-03-06")).isTrue();
        assertThat(depotDispo.absenteLe(T, ids.get("P1"), MARDI)).isFalse();
    }

    @Test
    @DisplayName("U5 — P-GH-1 : un code non planifiable déclaré d'avance est refusé avec un message clair ; le jour même, il passe, soumis au fait « planifiable »")
    void codeNonPlanifiable() throws Exception {
        ref("codes_absence", "{\"code\":\"DEC\",\"libelleFr\":\"Décès\",\"comportement\":\"PAYE\",\"attributs\":{\"duree\":\"PONCTUELLE\",\"planifiable\":false}}");
        Map<String, Object> e = new HashMap<>();
        e.put("contexte", "ABSENCE");
        e.put("cle", "imprevue");
        e.put("condition", Map.of("tous", List.of(Map.of("fait", "planifiable", "op", "=", "valeur", false), Map.of("fait", "absencePlanifiee", "op", "=", "valeur", false))));
        e.put("action", "EXIGER_APPROBATION");
        e.put("message", "Absence imprévue : l'accord du chef est requis");
        e.put("effectifDepuis", "2027-01-15");
        regles.creer(ctx(ADMIN), e);
        assertThatThrownBy(() -> declarer(MARC, null, "DEC", "2030-04-01", null, null)).isInstanceOf(EffectifsInvalideException.class)
                .hasMessageStartingWith("le code « DEC » ne se planifie pas d'avance : il se déclare à partir du premier jour d'absence (2030-04-01, dans ")
                .hasMessageEndingWith("jours). Choisissez un code planifiable, ou déclarez-la le jour venu");
        GestionnaireAbsencesEffectifs.Resultat r = declarer(MARC, null, "DEC", "2027-01-15", null, null);
        assertThat(List.of(r.absence().statut().code(), r.absence().motifsRegles().get(0).message()))
                .containsExactly("A_APPROUVER", "Absence imprévue : l'accord du chef est requis");
        assertThat(declarer(MARC, null, "MAL", "2030-09-02", null, null).absence().statut().code()).isEqualTo("ACCEPTEE");
    }

    @Test
    @DisplayName("U5 — P-CH-8 : une absence de long terme réserve ses places et ouvre un remplacement long terme lié à elle")
    void absenceLongue() throws Exception {
        decorComblement();
        ref("statuts_emploi", "{\"code\":\"TC\",\"libelleFr\":\"Temps complet\",\"comportement\":\"TEMPS_COMPLET\"}");
        ref("plages_horaires", "{\"code\":\"J\",\"libelleFr\":\"Jour\",\"attributs\":{\"debut\":360,\"fin\":1080}}");
        ref("codes_absence", "{\"code\":\"CLD\",\"libelleFr\":\"Congé de longue durée\",\"comportement\":\"NON_PAYE\",\"attributs\":{\"duree\":\"LONG_TERME\"}}");
        String poste = creer(PLANIF + "/postes", "{\"code\":\"EST-1\",\"libelle\":\"Poste EST-1\",\"uniteCode\":\"EST\",\"statutEmploiCode\":\"TC\","
                + "\"corpsEmploiCode\":\"PARA\",\"milieuId\":\"" + ids.get("c12") + "\",\"natureCode\":\"REG\",\"cycle\":[[\"J\",null,null,null,null,null,null]],"
                + "\"debutCycle\":\"2030-03-04\"}");
        String autre = creer(PLANIF + "/postes", "{\"code\":\"EST-0\",\"libelle\":\"Poste EST-0\",\"uniteCode\":\"EST\",\"statutEmploiCode\":\"TC\","
                + "\"corpsEmploiCode\":\"PARA\",\"milieuId\":\"" + ids.get("c12") + "\",\"natureCode\":\"REG\",\"cycle\":[[\"J\",null,null,null,null,null,null]],"
                + "\"debutCycle\":\"2030-03-04\"}");
        http.post(PLANIF + "/postes/publier", ADMIN, "{\"ids\":[\"" + poste + "\",\"" + autre + "\"]}").andExpect(status().isOk());
        jdbc.sql("UPDATE eff_postes SET titulaireId = ? WHERE id = ?").params(ids.get("P4"), poste).update();
        String id = declarer(ADMIN, ids.get("P4"), "CLD", "2030-03-06", "2030-05-31", null).absence().id();
        http.post(API + "/" + id + "/decisions", CHEF, "{\"decision\":\"DEROGER\",\"motif\":\"Congé autorisé par la direction\",\"codeAbsence\":\"CLD\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.comblement.reservees").value(1)).andExpect(jsonPath("$.placesLiberees").value(1));
        http.get(API + "/" + id + "/comblement", CHEF).andExpect(jsonPath("$.places[0].resultat").value("REMPLACEMENT_LONG_TERME"))
                .andExpect(jsonPath("$.remplacementPossible").value(true)).andExpect(jsonPath("$.postesProposes[*].code", Matchers.contains("EST-1", "EST-0")))
                .andExpect(jsonPath("$.postesProposes[0].titulaire").value(true));
        String fenetre = "{\"posteId\":\"" + poste + "\",\"finManifestation\":\"2027-01-20T17:00:00.000Z\"}";
        http.post(API + "/" + id + "/remplacement", CHEF, fenetre).andExpect(status().isCreated())
                .andExpect(jsonPath("$.origine").value("ABSENCE")).andExpect(jsonPath("$.referenceId").value(id))
                .andExpect(jsonPath("$.debut").value("2030-03-06")).andExpect(jsonPath("$.fin").value("2030-05-31")).andExpect(jsonPath("$.etat").value("OUVERT"));
        http.post(API + "/" + id + "/remplacement", CHEF, fenetre).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("un remplacement long terme est déjà ouvert pour cette absence (")));
        http.get(API + "/" + id + "/comblement", CHEF).andExpect(jsonPath("$.remplacement.referenceId").value(id))
                .andExpect(jsonPath("$.remplacementPossible").value(false)).andExpect(jsonPath("$.postesProposes", Matchers.hasSize(0)));
        http.post(API + "/" + id + "/remplacement", JULIE, fenetre).andExpect(status().isForbidden());
        // Les empêchements : une absence ponctuelle, une absence non acceptée, un poste d'une autre unité, le module fermé.
        String ponctuelle = absences.declarer(ctx(ADMIN), new Declaration(ids.get("P2"), "MAL", "2030-09-02", null, null)).absence().id();
        http.post(API + "/" + ponctuelle + "/remplacement", CHEF, fenetre).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le code « MAL » est ponctuel, ou l'absence reste sous son seuil : ses quarts se comblent un à un"));
        String attente = absences.declarer(ctx(ADMIN), new Declaration(ids.get("P2"), "MAL", "2030-10-01", "2030-10-08", null)).absence().id();
        http.post(API + "/" + attente + "/remplacement", CHEF, fenetre).andExpect(jsonPath("$.errors[0]").value("seule une absence acceptée se remplace"));
        String longue = absences.declarer(ctx(ADMIN), new Declaration(ids.get("P2"), "CLD", "2030-11-02", null, null)).absence().id();
        ref("unites", "{\"code\":\"OUEST\",\"libelleFr\":\"Ouest\"}");
        String c40 = creer(E + "/milieux", "{\"code\":\"C40\",\"libelle\":\"Caserne 40\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"OUEST\"}");
        String ouest = creer(PLANIF + "/postes", "{\"code\":\"OUEST-1\",\"libelle\":\"Poste OUEST-1\",\"uniteCode\":\"OUEST\",\"statutEmploiCode\":\"TC\","
                + "\"corpsEmploiCode\":\"PARA\",\"milieuId\":\"" + c40 + "\",\"natureCode\":\"REG\",\"cycle\":[[\"J\",null,null,null,null,null,null]],"
                + "\"debutCycle\":\"2030-03-04\"}");
        http.post(API + "/" + longue + "/remplacement", ADMIN, "{\"posteId\":\"" + ouest + "\",\"finManifestation\":\"2027-01-20T17:00:00.000Z\"}")
                .andExpect(jsonPath("$.errors[0]").value("le poste « OUEST-1 » n'est pas de l'unité de l'absence (EST)"));
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\",\"effectifs\",\"effectifs_absences\",\"effectifs_planification\"]", T).update();
        http.get(API + "/" + longue + "/comblement", ADMIN).andExpect(jsonPath("$.remplacement").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.raisonRemplacement").value("le module « effectifs_agph » (remplacements long terme) est fermé pour cet organisme"));
    }
}
