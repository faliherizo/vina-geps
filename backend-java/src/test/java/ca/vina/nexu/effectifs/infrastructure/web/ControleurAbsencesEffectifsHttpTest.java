package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.effectifs.domaine.AbsenceEffectifs;
import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireCongesAnnuels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs.Declaration;
import ca.vina.nexu.effectifs.application.commandes.DecisionRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.AvisDeComblement;
import ca.vina.nexu.effectifs.application.commandes.CascadeOffresQuart;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireComblementAbsences;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurOrdonnanceurOffres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotOffresQuart;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotParametresOffres;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaOffresQuart;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
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
import ca.vina.nexu.effectifs.application.commandes.GestesEchangesApi;
import ca.vina.nexu.effectifs.application.commandes.JournalAvecAvisDesQuarts;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesApi;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEchangesApi;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import ca.vina.nexu.partage.web.GardeMachine;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureCongesAnnuels;
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
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotCongesAnnuels;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

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
    /** AD-06c : les avis de quart que le journal a émis, et l'API d'échange appelée par l'application de l'organisme. */
    private record AvisQuart(String evenement, String cle, Map<String, Object> donnees) { }

    private final List<AvisQuart> avisQuarts = new ArrayList<>();
    private static final JetonClient JETONS = new JetonClient("secret-d-essai-de-l-api-d-echange-assez-long");
    private ClientHttpEssai machine;

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
        DepotEchangesApi echanges = new DepotEchangesApi(jdbc, schemaPlanif, schema);
        // AD-06c : le journal de tous les gestes annonce les quarts publiés qu'ils touchent, comme en production.
        JournalEffectifsPort journal = new JournalAvecAvisDesQuarts(new DepotJournalEffectifs(jdbc, seq, schema), echanges, (t, evenement, donnees, cle, instant) -> {
            avisQuarts.add(new AvisQuart(evenement, cle, donnees));
            return 1;
        });
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
                new GestionnaireComblementAbsences.Ecritures(depotCbl, new GestionnaireAttributionFinDeCollecte(lectPlanif, dispo, places, journal), avis, journal, seq,
                        cascade(lectRes, depotCbl, avis, lectEqp, journal, seq)));
        DepotRemplacementsAgph depotRlt = new DepotRemplacementsAgph(jdbc, new SchemaAgph(jdbc), schemaPlanif);
        GestionnaireRemplacementsAgph remplacements = new GestionnaireRemplacementsAgph(depotRlt, new DecisionRemplacementsAgph(depotRlt,
                new DecisionRemplacementsAgph.Lectures(lectPos, lectRes, referentiels, evaluateur), new DecisionRemplacementsAgph.Quarts(lectEqp, depotEqp), journal),
                new GestionnaireRemplacementsAgph.Socle(lectPos, lectRes, perimetres, journal, seq));
        SuiviComblementAbsence suivi = new SuiviComblementAbsence(new SuiviComblementAbsence.Lectures(depotCbl, perimetres, lectRes, lectPos),
                new SuiviComblementAbsence.Remplacements(depotRlt, remplacements, new AdaptateurModulesPourEffectifs(jdbc), journal), comblement);
        soldes = new DepotSoldesBanques(jdbc, new SchemaEffectifs(jdbc));
        absences = new GestionnaireAbsencesEffectifs(new GestionnaireAbsencesEffectifs.Ports(depotAbs, planning, depotEqp, comblement),
                new GestionnaireAbsencesEffectifs.Lectures(lecture, lectRes, referentiels, perimetres, soldes), evaluateur, journal, seq);
        // Addenda 8, M-02 : le choix annuel des congés, dont l'octroi passe par la déclaration d'absence ci-dessus.
        DepotCongesAnnuels depotCng = new DepotCongesAnnuels(jdbc, schema);
        LectureCongesAnnuels lectCng = new LectureCongesAnnuels(depotCng, lectRes, perimetres);
        http = new ClientHttpEssai(socle, planification, new ControleurAbsencesEffectifs(garde, lecture, absences, suivi),
                new ControleurCongesAnnuels(garde, lectCng, new GestionnaireCongesAnnuels(new GestionnaireCongesAnnuels.Lectures(lectCng, lectRes, referentiels, perimetres),
                        new GestionnaireCongesAnnuels.Ecritures(depotCng, journal, seq, absences))));
        GestesEchangesApi gestes = new GestesEchangesApi(new GestesEchangesApi.Ports(
                new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq), lectRes,
                new DepotPresenceEffectifs(jdbc, new SchemaPresenceEffectifs(jdbc), schemaPlanif, schema), soldes), new GestesEchangesApi.Absences(absences, echanges), journal);
        machine = new ClientHttpEssai(new ControleurEchangesApi(new GardeMachine(JETONS), new AdaptateurModulesPourEffectifs(jdbc),
                new ControleurEchangesApi.Services(new LectureEchangesApi(echanges), gestes, Clock.fixed(Instant.parse(MAINTENANT), ZoneOffset.UTC))));
        semaine();
    }

    /** L'offre en cascade, réglée par défaut en offre simultanée : ces essais tiennent le comportement d'avant AD-07. */
    private CascadeOffresQuart cascade(LectureRessources lectRes, DepotComblementsAbsences depotCbl, PublicationAvis avis, LectureEquipages lectEqp,
                                       JournalEffectifsPort journal, Sequence seq) {
        SchemaOffresQuart schemaOfr = new SchemaOffresQuart(jdbc);
        return new CascadeOffresQuart(new CascadeOffresQuart.Ports(new DepotOffresQuart(jdbc, schemaOfr), new DepotParametresOffres(jdbc, schemaOfr),
                new AdaptateurOrdonnanceurOffres(new DepotTaches(jdbc, new RegistreExecuteurs()), new RegistreExecuteurs())),
                new CascadeOffresQuart.Collaborateurs(new AvisDeComblement(lectRes, depotCbl, avis), lectEqp, journal, seq));
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

    private ResultActions transmettre(String matricule, String json, String... portees) throws Exception {
        MockHttpServletRequestBuilder r = MockMvcRequestBuilders.post("/api/echanges/v1/employes/" + matricule + "/absences")
                .header("Authorization", "Bearer " + JETONS.emettre("cli-rao", T, List.of(portees)));
        return machine.envoyer(json == null ? r : r.contentType(MediaType.APPLICATION_JSON).content(json), null);
    }

    private String quartDu(String date) {
        return jdbc.sql("SELECT id FROM eff_equipages WHERE tenantId = ? AND date = ?").params(T, date).query(String.class).single();
    }

    @Test
    @DisplayName("AD-06c : absence constatée aux opérations — acceptée malgré un refus des règles, place vacante, renvoi sans effet, refus nommés")
    void absenceConstateeAuxOperations() throws Exception {
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        assertThat(avisQuarts).as("une période en confection n'est pas annoncée").isEmpty();
        String lundi = quartDu(LUNDI);
        String corps = "{\"quartId\":\"" + lundi + "\",\"codeAbsence\":\"MAL\",\"motif\":\"Appel au répartiteur\"}";
        transmettre("P1", corps, "read").andExpect(status().isForbidden());
        String id = JsonPath.read(transmettre("P1", corps, "write").andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("ACCEPTEE")).andExpect(jsonPath("$.actionRegles").value("REFUSER"))
                .andExpect(jsonPath("$.du").value(LUNDI)).andExpect(jsonPath("$.au").value(LUNDI))
                .andExpect(jsonPath("$.declareePar").value("application:cli-rao")).andExpect(jsonPath("$.placesLiberees").value(1))
                .andReturn().getResponse().getContentAsString(), "$.id");
        transmettre("P1", corps, "write").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.placesLiberees").value(0));
        transmettre("P1", "{\"du\":\"" + LUNDI + "\",\"codeAbsence\":\"FORM\"}", "write").andExpect(status().isConflict());
        transmettre("P2", "{\"quartId\":\"" + quartDu(MARDI) + "\",\"codeAbsence\":\"MAL\"}", "write").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("ne tient aucune place")));
        transmettre("P1", "{\"quartId\":\"eq-inconnu\",\"codeAbsence\":\"MAL\"}", "write").andExpect(status().isNotFound());
        transmettre("P1", "{\"quartId\":\"" + lundi + "\",\"au\":\"" + MARDI + "\",\"codeAbsence\":\"MAL\"}", "write").andExpect(status().isUnprocessableEntity());
        transmettre("P9", corps, "write").andExpect(status().isNotFound());
        transmettre("P1", null, "write").andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_absences WHERE tenantId = ?").params(T).query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    @DisplayName("AD-06c : la place vacante sort parmi les correctifs, et l'organisme en est avisé une fois par quart, quart publié seulement")
    void placeVacanteAnnoncee() throws Exception {
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        transmettre("P1", "{\"du\":\"" + LUNDI + "\",\"au\":\"" + MARDI + "\",\"codeAbsence\":\"MAL\"}", "write").andExpect(status().isCreated())
                .andExpect(jsonPath("$.placesLiberees").value(2));
        String place = jdbc.sql("SELECT p.id FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId WHERE e.date = ?").params(LUNDI).query(String.class).single();
        machine.envoyer(MockMvcRequestBuilders.get("/api/echanges/v1/quarts/modifications")
                        .header("Authorization", "Bearer " + JETONS.emettre("cli-rao", T, List.of("read"))), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifications[?(@.action == 'places.vacantes')].objetId", Matchers.hasItem(place)))
                .andExpect(jsonPath("$.modifications[?(@.action == 'places.vacantes')].quart.places[0].ressourceId", Matchers.everyItem(Matchers.nullValue())));
        List<AvisQuart> vacantes = avisQuarts.stream().filter(a -> "places.vacantes".equals(a.donnees().get("action"))).toList();
        assertThat(vacantes).extracting(a -> a.donnees().get("quartId")).containsExactlyInAnyOrder(quartDu(LUNDI), quartDu(MARDI));
        assertThat(vacantes).allSatisfy(a -> {
            assertThat(a.evenement()).isEqualTo(JournalAvecAvisDesQuarts.EVENEMENT);
            assertThat(a.donnees()).containsEntry("lire", JournalAvecAvisDesQuarts.LECTURE).containsEntry("uniteCode", "EST");
        });
    }

    @Test
    @DisplayName("addenda 8, M-02 : une campagne s'ouvre, chacun classe ses semaines, l'octroi suit l'ancienneté sous les plafonds, "
            + "et chaque semaine octroyée devient une absence acceptée")
    void congesAnnuels() throws Exception {
        String api = "/api/effectifs/absences/conges";
        jdbc.sql("UPDATE eff_ressources SET anciennete = 9 WHERE id = ?").param(ids.get("P2")).update();
        String campagne = "{\"nom\":\"Été 2027\",\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2027,\"du\":\"2027-07-05\","
                + "\"au\":\"2027-07-25\",\"codeAbsence\":\"MAL\",\"plafondParSemaine\":1,\"maxSemainesParPersonne\":1}";
        http.post(api, JULIE, campagne).andExpect(status().isForbidden());
        http.post(api, ADMIN, campagne.replace("2027-07-25", "2027-07-06")).andExpect(status().is(422));
        String id = JsonPath.read(http.post(api, ADMIN, campagne).andExpect(status().isCreated()).andExpect(jsonPath("$.etat").value("BROUILLON"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        http.get(api + "/miennes", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.put(api + "/" + id + "/choix", JULIE, "{\"semaines\":[\"2027-07-05\"]}").andExpect(status().isConflict());
        http.post(api + "/" + id + "/etat", ADMIN, "{\"etat\":\"FERMEE\"}").andExpect(status().isConflict());
        http.post(api + "/" + id + "/etat", ADMIN, "{\"etat\":\"OUVERTE\"}").andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("OUVERTE"));
        http.get(api + "/miennes", JULIE).andExpect(jsonPath("$[0].semaines", Matchers.contains("2027-07-05", "2027-07-12", "2027-07-19")));
        http.put(api + "/" + id + "/choix", JULIE, "{\"semaines\":[\"2027-07-06\"]}").andExpect(status().is(422));
        http.put(api + "/" + id + "/choix", JULIE, "{\"semaines\":[\"2027-07-05\",\"2027-07-12\"]}").andExpect(status().isOk());
        http.put(api + "/" + id + "/choix", MARC, "{\"semaines\":[\"2027-07-05\",\"2027-07-19\"]}").andExpect(status().isOk());
        http.post(api + "/" + id + "/octroyer", ADMIN, "{}").andExpect(status().isConflict());
        http.post(api + "/" + id + "/etat", ADMIN, "{\"etat\":\"FERMEE\"}").andExpect(status().isOk());
        http.post(api + "/" + id + "/octroyer", ADMIN, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.octroyes").value(2))
                .andExpect(jsonPath("$.refuses").value(2)).andExpect(jsonPath("$.campagne.etat").value("OCTROYEE"));
        // Marc (9 ans) d'abord : sa semaine du 5, puis son maximum d'une semaine ; Julie : la semaine du 5 au plafond, celle du 12 octroyée.
        http.get(api + "/" + id + "/resultats", ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].ressourceNom", Matchers.contains("Marc", "Marc", "Julie", "Julie")))
                .andExpect(jsonPath("$[*].statut", Matchers.contains("OCTROYE", "REFUSE", "REFUSE", "OCTROYE")))
                .andExpect(jsonPath("$[1].motif").value("au plus 1 semaine(s) par personne"))
                .andExpect(jsonPath("$[2].motif").value("plafond de 1 absent(s) atteint cette semaine"));
        // La semaine octroyée est déclarée comme absence : les règles d'absence de l'organisme la jugent encore — ici, une règle
        // de cet essai la soumet à approbation, et le choix le dit.
        http.get(api + "/miennes", JULIE).andExpect(jsonPath("$[0].choix[1].statut").value("OCTROYE"))
                .andExpect(jsonPath("$[0].choix[1].motif").value("absence à approuver selon les règles"));
        assertThat(jdbc.sql("SELECT du || ' ' || statut FROM eff_absences WHERE ressourceId = ? AND motif LIKE 'Congé annuel%'").param(ids.get("P1"))
                .query(String.class).list()).containsExactly("2027-07-12 A_APPROUVER");
        http.get(api, ADMIN).andExpect(jsonPath("$[0].etat").value("OCTROYEE"));
        http.get(api, JULIE).andExpect(status().isForbidden());
        http.get(api + "/inconnue/resultats", ADMIN).andExpect(status().isNotFound());
        http.post(api + "/" + id + "/etat", ADMIN, "{\"etat\":\"PEUT-ETRE\"}").andExpect(status().is(422));
    }

    @Test
    @DisplayName("addenda 8, M-02 : la déclaration de chaque semaine octroyée décide encore — acceptée sans règle, refusée par une règle "
            + "ou par une absence qui la couvre ; et la campagne ne reçoit que les choix du groupe qu'elle vise")
    void congesAnnuelsJugesParLaDeclaration() throws Exception {
        // Janvier 2027, avant l'entrée en vigueur des règles de l'essai (1er février) : rien ne s'oppose à l'absence.
        String janvier = campagneOuverte("Hiver 2027", "2027-01-18", "2027-01-31");
        http.put(CONGES + "/" + janvier + "/choix", JULIE, "{\"semaines\":[\"2027-01-18\"]}").andExpect(status().isOk());
        http.put(CONGES + "/" + janvier + "/choix", MARC, "{\"semaines\":[\"2027-01-25\"]}").andExpect(status().isOk());
        // Marc a déjà une absence dans sa semaine : la déclaration refuse, et le choix porte son motif.
        declarer(MARC, null, "MAL", "2027-01-26", null, null);
        octroyer(janvier);
        http.get(CONGES + "/" + janvier + "/resultats", ADMIN)
                .andExpect(jsonPath("$[?(@.ressourceNom == 'Julie')].statut").value("OCTROYE"))
                .andExpect(jsonPath("$[?(@.ressourceNom == 'Julie')].motif").value(Matchers.contains(Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.ressourceNom == 'Marc')].statut").value("REFUSE"))
                .andExpect(jsonPath("$[?(@.ressourceNom == 'Marc')].motif").value(Matchers.contains(Matchers.containsString("couvre déjà"))));
        assertThat(jdbc.sql("SELECT statut FROM eff_absences WHERE ressourceId = ? AND motif LIKE 'Congé annuel%'").param(ids.get("P1"))
                .query(String.class).list()).containsExactly("ACCEPTEE");

        // Une règle de l'organisme refuse toute absence de plus de six jours : la semaine n'est pas octroyée.
        regle("semaine-refusee", Map.of("fait", "joursDemandes", "op", ">", "valeur", 6), "REFUSER", "Pas d'absence d'une semaine");
        String aout = campagneOuverte("Août 2027", "2027-08-02", "2027-08-08");
        http.put(CONGES + "/" + aout + "/choix", JULIE, "{\"semaines\":[\"2027-08-02\"]}").andExpect(status().isOk());
        octroyer(aout);
        http.get(CONGES + "/" + aout + "/resultats", ADMIN).andExpect(jsonPath("$[0].statut").value("REFUSE"))
                .andExpect(jsonPath("$[0].motif").value("refusée par les règles de l'organisme"));

        // Marc change de corps d'emploi : la campagne des paramédics ne le vise plus.
        String autre = campagneOuverte("Automne 2027", "2027-10-04", "2027-10-17");
        jdbc.sql("UPDATE eff_ressources SET corpsEmploiCode = 'REP' WHERE id = ?").param(ids.get("P2")).update();
        http.put(CONGES + "/" + autre + "/choix", MARC, "{\"semaines\":[\"2027-10-04\"]}").andExpect(status().isForbidden());
    }

    private static final String CONGES = "/api/effectifs/absences/conges";

    private String campagneOuverte(String nom, String du, String au) throws Exception {
        String corps = "{\"nom\":\"" + nom + "\",\"uniteCode\":\"EST\",\"corpsEmploiCode\":\"PARA\",\"annee\":2027,\"du\":\"" + du
                + "\",\"au\":\"" + au + "\",\"codeAbsence\":\"MAL\",\"plafondParSemaine\":2}";
        String id = JsonPath.read(http.post(CONGES, ADMIN, corps).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        http.post(CONGES + "/" + id + "/etat", ADMIN, "{\"etat\":\"OUVERTE\"}").andExpect(status().isOk());
        return id;
    }

    private void octroyer(String id) throws Exception {
        http.post(CONGES + "/" + id + "/etat", ADMIN, "{\"etat\":\"FERMEE\"}").andExpect(status().isOk());
        http.post(CONGES + "/" + id + "/octroyer", ADMIN, "{}").andExpect(status().isOk());
    }
}
