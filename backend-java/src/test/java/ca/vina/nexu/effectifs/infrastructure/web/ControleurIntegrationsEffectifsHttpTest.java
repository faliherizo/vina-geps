package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.BoiteEnvoiIntegration;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GenerationSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.GestesEntrantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestesSortantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireConnecteursIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePreparationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.LivraisonIntegration;
import ca.vina.nexu.effectifs.application.commandes.RelaisJournalIntegration;
import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.application.requetes.RapportsTemps;
import ca.vina.nexu.effectifs.application.requetes.SourcesFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.ConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Rejetee;
import ca.vina.nexu.effectifs.domaine.OrdonnanceurIntegrationPort.TacheLivraison;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurOrdonnanceurIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.ClientHttpIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotBoiteEnvoiIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotConnecteursIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotDonneesIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotFeuillesTemps;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.RegistreConnecteursIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaIntegrationsEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaTempsEffectifs;
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
import org.springframework.test.web.servlet.ResultActions;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les intégrations des effectifs par HTTP</strong> (lot R13, jumeau de {@code effectifs-integrations.integration.test.ts}),
 * sur SQLite réelle, avec l'ordonnanceur réel. La feuille de Julie est approuvée ; le lot de paie part au PFT par la boîte
 * d'envoi, livré au tic de l'ordonnanceur ; un rejet se rejoue, une panne se retente puis s'avoue. Les équipages du jour
 * partent au SAAV, les quarts de Julie à son calendrier, le journal au bus et à l'entrepôt ; les horodatages du RAO et les
 * anciennetés de BD Corpo entrent. Aucun secret n'est gardé en base. Les étapes du Node s'enchaînent : un seul récit.
 */
@DisplayName("/api/effectifs/integrations — connecteurs, boîte d'envoi, ordonnanceur, sources entrantes")
class ControleurIntegrationsEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String PL = E + "/planification";
    private static final String TP = E + "/temps";
    private static final String I = E + "/integrations";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_presence\","
            + "\"effectifs_temps\",\"effectifs_integrations\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);

    private static final tools.jackson.databind.ObjectMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotTaches taches;
    private RegistreConnecteursIntegration registre;
    private LivraisonIntegration livraison;
    private LectureIntegrations lecture;
    /** Le branchement d'essai : une destination imposée à tous les connecteurs, comme {@code brancherIntegrations} du Node. */
    private DestinationIntegrationPort surcharge;
    private final Map<String, String> ids = new HashMap<>();

    private static Sequence sequence() {
        AtomicInteger n = new AtomicInteger(1000);
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
                return "secret-" + n.incrementAndGet();
            }
        };
    }

    /** Le registre réel, sauf quand l'essai impose une destination. */
    private final class RegistreEssai implements ConnecteursIntegrationPort {
        @Override
        public DestinationIntegrationPort destination(String tenantId, ConnecteurEnregistre c) {
            return surcharge != null ? surcharge : registre.destination(tenantId, c);
        }

        @Override
        public SourceHorodatagesIntegrationPort horodatages(String tenantId, ConnecteurEnregistre c) {
            return registre.horodatages(tenantId, c);
        }

        @Override
        public SourceDossiersIntegrationPort dossiers(String tenantId, ConnecteurEnregistre c) {
            return registre.dossiers(tenantId, c);
        }
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (ContexteRequete c : List.of(JULIE, CHEF)) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', ?, 'x')")
                    .params(c.userId(), c.username(), T, c.role()).update();
        }
        cabler();
        socle();
        planifier();
    }

    private void cabler() {
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
        Sequence seq = sequence();
        Clock horloge = Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC);
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMilieux = new DepotMilieux(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        DepotRegles depotRegles = new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc));
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMilieux, perimetres);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc), horloge);
        ControleurSocleEffectifs socle = new ControleurSocleEffectifs(garde, referentiels, new GestionnaireReferentiels(depotRef, referentiels, journal, seq),
                perimetres, new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMilieux, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres));
        EvaluateurRegles evaluateur = new EvaluateurRegles(depotRegles, referentiels, journal, seq);

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
        ControleurPlanificationEffectifs planification = new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq),
                        new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq),
                        new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq),
                        equipages, new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal),
                        new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq)));

        SchemaPresenceEffectifs schemaPresence = new SchemaPresenceEffectifs(jdbc);
        DepotPresenceEffectifs depotPresence = new DepotPresenceEffectifs(jdbc, schemaPresence, schemaPlanif, schema);
        LectureQuartsPresence quarts = new LectureQuartsPresence(depotPresence, referentiels);
        DepotFeuillesTemps depot = new DepotFeuillesTemps(jdbc, new SchemaTempsEffectifs(jdbc), schemaPresence, schemaPlanif);
        LectureFeuillesTemps lectureTemps = new LectureFeuillesTemps(depot, lectRes, perimetres, referentiels);
        SourcesFeuillesTemps sources = new SourcesFeuillesTemps(depot, quarts);
        FluxEssai.Cablage flux = FluxEssai.cabler(new FluxEssai.Socle(jdbc, horloge, perimetres, evaluateur, journal));
        CalculFeuillesTemps calcul = new CalculFeuillesTemps(lectureTemps, sources, evaluateur, flux.lecture());
        GenerationSegmentsTemps generation = new GenerationSegmentsTemps(lectureTemps, sources);
        ExportPaieTemps export = new ExportPaieTemps(lectureTemps, journal);
        ControleurTempsEffectifs temps = new ControleurTempsEffectifs(garde, new ControleurTempsEffectifs.Lectures(lectureTemps, calcul, new RapportsTemps(lectureTemps, calcul),
                flux.lecture()),
                new ControleurTempsEffectifs.Gestes(new GestionnaireParametresTemps(lectureTemps, depot, journal),
                        new GestionnairePreparationTemps(new GestionnairePreparationTemps.Lectures(lectureTemps, calcul, generation, perimetres),
                                new GestionnairePreparationTemps.Ecritures(depot, journal, seq)),
                        new GestionnaireSegmentsTemps(lectureTemps, calcul, flux.restriction(), new GestionnaireSegmentsTemps.Ecritures(depot, journal, seq)),
                        new GestionnaireApprobationTemps(lectureTemps, calcul, flux.flux(), new GestionnaireApprobationTemps.Ecritures(depot, journal, seq)),
                        export, flux.flux()));

        // Le lot R10 : la boîte d'envoi, l'ordonnanceur réel, le registre (branchable), les sources.
        SchemaIntegrationsEffectifs schemaIntegrations = new SchemaIntegrationsEffectifs(jdbc);
        DepotConnecteursIntegration connecteurs = new DepotConnecteursIntegration(jdbc, schemaIntegrations);
        DepotBoiteEnvoiIntegration boite = new DepotBoiteEnvoiIntegration(jdbc, schemaIntegrations);
        DepotDonneesIntegration donnees = new DepotDonneesIntegration(jdbc, schemaPlanif, schema);
        RegistreExecuteurs executeurs = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, executeurs);
        AdaptateurOrdonnanceurIntegration ordonnanceur = new AdaptateurOrdonnanceurIntegration(taches, executeurs);
        registre = new RegistreConnecteursIntegration(nom -> Optional.empty(), ClientHttpIntegration.reseau(HttpClient.newHttpClient()));
        RegistreEssai branchable = new RegistreEssai();
        lecture = new LectureIntegrations(connecteurs, boite);
        livraison = new LivraisonIntegration(lecture, boite, branchable);
        ordonnanceur.enregistrerLivreur(livraison);
        BoiteEnvoiIntegration envoi = new BoiteEnvoiIntegration(lecture, new BoiteEnvoiIntegration.Ecritures(boite, ordonnanceur, journal, seq));
        GestionnaireHorodatagesExternes horodatages = new GestionnaireHorodatagesExternes(
                new GestionnaireHorodatagesExternes.Lectures(depotPresence, lectRes, perimetres), depotPresence, journal, seq);
        ControleurIntegrationsEffectifs integrations = new ControleurIntegrationsEffectifs(garde, lecture, new ControleurIntegrationsEffectifs.Gestes(
                new GestionnaireConnecteursIntegration(lecture, connecteurs, journal), envoi,
                new GestesSortantsIntegration(new GestesSortantsIntegration.Appuis(export, perimetres, lectRes, donnees), envoi, lecture, journal),
                new RelaisJournalIntegration(lecture, donnees, new RelaisJournalIntegration.Ecritures(envoi, connecteurs, journal)),
                new GestesEntrantsIntegration(new GestesEntrantsIntegration.Appuis(branchable, horodatages, donnees, perimetres), lecture, connecteurs, journal)));
        http = new ClientHttpEssai(socle, planification, temps, integrations);
    }

    private String corps(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private String creer(String chemin, String json) throws Exception {
        return JsonPath.read(corps(http.post(chemin, ADMIN, json).andExpect(status().isCreated())), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("types_heure", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"TRAVAILLEE\",\"attributs\":{\"codePaie\":\"100\"}}");
        ref("types_heure", "{\"code\":\"HS\",\"libelleFr\":\"Supplémentaires\",\"comportement\":\"SUPPLEMENTAIRE\",\"attributs\":{\"codePaie\":\"150\"}}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
        ids.put("P1", creer(PL + "/ressources", "{\"matricule\":\"P1\",\"nom\":\"Julie Roy\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"anciennete\":4,"
                + "\"utilisateurId\":\"usr-julie\"}"));
    }

    private void planifier() throws Exception {
        String p = creer(PL + "/periodes", "{\"nom\":\"Semaine 3\",\"uniteCode\":\"EST\",\"debut\":\"2031-01-13\",\"fin\":\"2031-01-19\"}");
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        String e = corps(http.post(PL + "/equipages", ADMIN, "{\"periodeId\":\"" + p + "\",\"date\":\"2031-01-15\",\"debut\":360,\"fin\":1080,"
                + "\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\","
                + "\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}").andExpect(status().isCreated()));
        http.post(PL + "/places/" + JsonPath.read(e, "$.places[0].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P1") + "\"}")
                .andExpect(status().isOk());
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
    }

    private void activer(String code, String extra) throws Exception {
        http.put(I + "/connecteurs/" + code, ADMIN, "{\"actif\":true" + extra + "}").andExpect(status().isOk());
    }

    /** Un message tel que l'API le rend (son JSON relu), lu directement dans la boîte d'envoi. */
    private Map<String, Object> message(String id) {
        return JsonPath.read(JSON.writeValueAsString(lecture.message(T, id)), "$");
    }

    @Test
    @DisplayName("le récit complet : connecteurs, paie, rejet et rejeu, panne, relais, sources entrantes")
    void recit() throws Exception {
        connecteurs();
        paie();
        rejetEtRejeu();
        panne();
        relais();
        sourcesEntrantes();
    }

    /** Sept connecteurs, coupés par défaut ; seule l'administration les configure, sans jamais garder de secret (P-TN-7). */
    private void connecteurs() throws Exception {
        http.get(I + "/connecteurs", JULIE).andExpect(status().isForbidden());
        String liste = corps(http.get(I + "/connecteurs", CHEF).andExpect(status().isOk()));
        assertThat(JsonPath.<List<String>>read(liste, "$[*].code")).containsExactly("RAO", "SAAV", "PFT", "BD_CORPO", "GRAPH", "ESB", "ENTREPOT");
        assertThat(JsonPath.<List<String>>read(liste, "$[*].sens")).containsExactly("ENTRANT", "SORTANT", "SORTANT", "ENTRANT", "SORTANT", "SORTANT", "SORTANT");
        assertThat(JsonPath.<List<String>>read(liste, "$[*].mode")).containsOnly("SIMULATEUR");
        assertThat(JsonPath.<List<Boolean>>read(liste, "$[*].actif")).containsOnly(false);
        assertThat(liste).startsWith("[{\"code\":\"RAO\",\"mode\":\"SIMULATEUR\",\"url\":null,\"jetonRef\":null,\"actif\":false,\"jeuEssai\":[],\"curseur\":null,"
                + "\"libelle\":\"RAO — répartition assistée par ordinateur (horodatages)\",\"sens\":\"ENTRANT\",\"enAttente\":0,\"enEchec\":0}");
        http.put(I + "/connecteurs/PFT", CHEF, "{\"actif\":true}").andExpect(status().isForbidden());
        http.put(I + "/connecteurs/SAP", ADMIN, "{}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("connecteur : RAO, SAAV, PFT, BD_CORPO, GRAPH, ESB, ENTREPOT"));
        http.put(I + "/connecteurs/PFT", ADMIN, "{\"mode\":\"HTTP\",\"url\":\"http://paie.exemple.ca\"}")
                .andExpect(jsonPath("$.errors[0]").value("« url » : un protocole sécurisé (https) est exigé"));
        http.put(I + "/connecteurs/PFT", ADMIN, "{\"jeuEssai\":\"x\"}").andExpect(jsonPath("$.errors[0]").value("« jeuEssai » : une liste de 1 000 éléments au plus"));
        http.put(I + "/connecteurs/PFT", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        http.put(I + "/connecteurs/PFT", ADMIN, "{\"actif\":true,\"jetonRef\":\"PFT_JETON\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(true)).andExpect(jsonPath("$.jetonRef").value("PFT_JETON")).andExpect(jsonPath("$.mode").value("SIMULATEUR"));
        http.get(E + "/journal?action=integration.configuree", ADMIN).andExpect(jsonPath("$[0].objetId").value("PFT"));
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'integration.configuree' ORDER BY rowid DESC LIMIT 1").query(String.class).single())
                .isEqualTo("{\"mode\":\"SIMULATEUR\",\"url\":null,\"jetonRef\":\"PFT_JETON\",\"actif\":true}");
    }

    /** Le lot de paie part au PFT par la boîte d'envoi, livré au tic de l'ordonnanceur ; le même lot ne part qu'une fois (E-RF-12). */
    private void paie() throws Exception {
        http.post(I + "/paie", CHEF, "{\"debut\":\"2031-01-13\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("aucune feuille approuvée à transmettre pour la période du 2031-01-13"));
        String prep = corps(http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\",\"debut\":\"2031-01-13\",\"fin\":\"2031-01-19\"}")
                .andExpect(status().isCreated()));
        String feuille = JsonPath.read(prep, "$.creees[0]");
        http.post(TP + "/feuilles/" + feuille + "/soumettre", JULIE, null).andExpect(status().isOk());
        http.post(TP + "/feuilles/" + feuille + "/approuver", CHEF, null).andExpect(jsonPath("$.statut").value("APPROUVEE"));
        String m = corps(http.post(I + "/paie", CHEF, "{\"debut\":\"2031-01-13\",\"uniteCode\":\"EST\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.connecteur").value("PFT")).andExpect(jsonPath("$.type").value("LOT_PAIE"))
                .andExpect(jsonPath("$.etat").value("EN_ATTENTE")).andExpect(jsonPath("$.charge.lignes", Matchers.hasSize(2)))
                .andExpect(jsonPath("$.charge.lignes[1].heures").value(4)).andExpect(jsonPath("$.tentatives").value(0))
                .andExpect(jsonPath("$.deposePar").value("usr-chef")).andExpect(jsonPath("$.traiteLe").value(Matchers.nullValue())));
        ids.put("lot", JsonPath.read(m, "$.id"));
        http.post(I + "/paie", CHEF, "{\"debut\":\"2031-01-13\"}").andExpect(jsonPath("$.id").value(ids.get("lot")));
        BilanTic bilan = taches.tic("2026-09-30T12:00:01.000Z", PolitiqueRetentative.PAR_DEFAUT, "essai");
        assertThat(bilan.faites()).isGreaterThanOrEqualTo(1);
        assertThat(List.of(message(ids.get("lot")).get("etat"), message(ids.get("lot")).get("refExterne"))).containsExactly("ENVOYE", "PFT-SIM-1");
        String empreinte = JsonPath.read(m, "$.charge.empreinte");
        assertThat(registre.destinationSimulee(T, CodeConnecteur.PFT).recus().get(empreinte).charge()).containsEntry("debut", "2031-01-13");
    }

    /** Un rejet se consigne et se rejoue ; un message accepté ne se rejoue jamais. */
    private void rejetEtRejeu() throws Exception {
        activer("SAAV", "");
        boolean[] refuser = {true};
        surcharge = x -> refuser[0] ? new Rejetee("véhicule inconnu") : registre.destinationSimulee(T, CodeConnecteur.SAAV).livrer(x);
        http.post(I + "/equipages", CHEF, "{\"date\":\"2031-01-16\",\"uniteCode\":\"EST\"}")
                .andExpect(jsonPath("$.errors[0]").value("aucun équipage publié le 2031-01-16 pour l'unité EST"));
        http.post(I + "/equipages", CHEF, "{\"date\":\"2031-01-15\"}").andExpect(jsonPath("$.errors[0]").value("« uniteCode » : l'unité dont on publie les équipages"));
        http.post(I + "/equipages", CHEF, "{\"date\":\"15/01\"}").andExpect(status().is(422));
        http.post(I + "/equipages", CHEF, "{\"date\":\"2031-01-15\",\"uniteCode\":\"OUEST\"}").andExpect(status().isForbidden());
        String m = corps(http.post(I + "/equipages", CHEF, "{\"date\":\"2031-01-15\",\"uniteCode\":\"EST\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.charge.equipages[0].membres[0].corpsEmploi").value("PARA"))
                .andExpect(jsonPath("$.charge.equipages[0].membres[0].matricule").value("P1"))
                .andExpect(jsonPath("$.charge.equipages[0].debut").value(360)));
        String id = JsonPath.read(m, "$.id");
        assertThat((String) JsonPath.read(m, "$.cle")).startsWith("EST:2031-01-15:[{\"id\":\"").endsWith("\"membres\":[{\"corpsEmploi\":\"PARA\",\"matricule\":\"P1\"}]}]");
        livraison.livrer(new TacheLivraison(T, id, 0), "2031-01-15T00:00:00.000Z");
        assertThat(List.of(message(id).get("etat"), message(id).get("motif"))).containsExactly("REJETE", "véhicule inconnu");
        http.get(I + "/messages?etat=REJETE", CHEF).andExpect(jsonPath("$[*].id", Matchers.contains(id)));
        http.get(I + "/messages?etat=PERDU", CHEF).andExpect(status().is(422));
        http.get(I + "/messages?connecteur=SAP", CHEF).andExpect(status().is(422));
        http.get(I + "/messages?connecteur=PFT&etat=ENVOYE", CHEF).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("lot"))));
        http.get(I + "/messages?connecteur=&etat=", CHEF).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.post(I + "/messages/" + ids.get("lot") + "/rejouer", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce message a été accepté : le rejouer le livrerait deux fois"));
        http.post(I + "/messages/inconnu/rejouer", CHEF, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("message « inconnu » introuvable"));
        refuser[0] = false;
        http.post(I + "/messages/" + id + "/rejouer", CHEF, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.tentatives").value(0)).andExpect(jsonPath("$.motif").value(Matchers.nullValue()));
        http.post(I + "/messages/" + id + "/rejouer", CHEF, null).andExpect(jsonPath("$.errors[0]").value("ce message attend déjà sa livraison"));
        assertThat(jdbc.sql("SELECT avant FROM eff_journal WHERE action = 'integration.rejeu'").query(String.class).single())
                .isEqualTo("{\"etat\":\"REJETE\",\"motif\":\"véhicule inconnu\"}");
        livraison.livrer(new TacheLivraison(T, id, 0), "2031-01-15T00:01:00.000Z");
        assertThat(message(id).get("etat")).isEqualTo("ENVOYE");
        // Le rejeu remet le compte à zéro ; une livraison l'a porté à 1. Une tâche en retard ne relivre pas un message traité.
        assertThat(message(id).get("tentatives")).isEqualTo(1);
        livraison.livrer(new TacheLivraison(T, id, 0), "2031-01-15T00:02:00.000Z");
        assertThat(message(id)).containsEntry("tentatives", 1).containsEntry("traiteLe", "2031-01-15T00:01:00.000Z");
        surcharge = null;
    }

    /** Une panne passagère se retente, puis s'avoue en échec après trois essais. */
    private void panne() throws Exception {
        http.post(I + "/calendriers/" + ids.get("P1"), CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le connecteur GRAPH n'est pas activé pour cet organisme : activez-le dans Intégrations"));
        activer("GRAPH", "");
        surcharge = x -> {
            throw new IllegalStateException("Graph injoignable");
        };
        http.post(I + "/calendriers/" + ids.get("P1"), CHEF, null).andExpect(status().isCreated()).andExpect(jsonPath("$.deposes").value(1));
        http.post(I + "/calendriers/" + ids.get("P1"), CHEF, null).andExpect(jsonPath("$.deposes").value(0));
        http.post(I + "/calendriers/inconnue", CHEF, null).andExpect(status().isNotFound());
        String liste = corps(http.get(I + "/messages?connecteur=GRAPH", CHEF));
        String id = JsonPath.read(liste, "$[0].id");
        assertThat(JsonPath.<Map<String, Object>>read(liste, "$[0].charge")).isEqualTo(Map.of("matricule", "P1", "sujet", "Quart — Caserne 12",
                "debut", "2031-01-15T11:00:00.000Z", "fin", "2031-01-15T23:00:00.000Z"));
        assertThatThrownBy(() -> livraison.livrer(new TacheLivraison(T, id, 0), "t")).hasMessage("Graph injoignable");
        assertThat(List.of(message(id).get("etat"), message(id).get("tentatives"))).containsExactly("EN_ATTENTE", 1);
        assertThatThrownBy(() -> livraison.livrer(new TacheLivraison(T, id, 2), "t")).hasMessage("Graph injoignable");
        assertThat(List.of(message(id).get("etat"), message(id).get("motif"))).containsExactly("ECHEC", "Graph injoignable");
        http.post(I + "/calendriers/" + ids.get("P1"), JULIE, null).andExpect(status().isForbidden());
        http.get(I + "/connecteurs", CHEF).andExpect(jsonPath("$[?(@.code == 'GRAPH')].enAttente", Matchers.contains(0)))
                .andExpect(jsonPath("$[?(@.code == 'GRAPH')].enEchec", Matchers.contains(1)));
        surcharge = null;
    }

    /** Le journal part au bus en événements et à l'entrepôt en versements, à partir d'un curseur (E-TN-7, P-TN-7). */
    private void relais() throws Exception {
        http.post(I + "/relais/ESB", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le connecteur ESB n'est pas activé pour cet organisme : activez-le dans Intégrations"));
        activer("ESB", "");
        activer("ENTREPOT", "");
        String esb = corps(http.post(I + "/relais/ESB", CHEF, null).andExpect(status().isOk()).andExpect(jsonPath("$.deposes").value(2)));
        String curseur = JsonPath.read(esb, "$.curseur");
        assertThat(JsonPath.<List<String>>read(corps(http.get(I + "/messages?connecteur=ESB", CHEF)), "$[*].charge.evenement"))
                .containsExactlyInAnyOrder("effectifs.feuille.approuvee", "effectifs.place.attribuee");
        // Le premier relais s'est journalisé (E-GN-9) : le second lit cette seule trace, qui n'est pas un événement publié.
        String second = corps(http.post(I + "/relais/ESB", CHEF, null).andExpect(jsonPath("$.deposes").value(0)));
        assertThat(JsonPath.<String>read(second, "$.curseur")).isGreaterThan(curseur);
        assertThat(JsonPath.<List<String>>read(corps(http.get(E + "/journal?action=integration.relais", ADMIN)), "$[*].objetId")).containsOnly("ESB");
        http.post(I + "/relais/ENTREPOT", CHEF, null).andExpect(jsonPath("$.deposes").value(1));
        String versement = corps(http.get(I + "/messages?connecteur=ENTREPOT", CHEF));
        assertThat(JsonPath.<List<Object>>read(versement, "$[0].charge.entrees")).hasSizeGreaterThan(10);
        assertThat(JsonPath.<List<String>>read(versement, "$[0].charge.entrees[?(@.action == 'integration.configuree')].apres.mode")).contains("SIMULATEUR");
        http.post(I + "/relais/PFT", CHEF, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("relais : ESB ou ENTREPOT"));
    }

    /** Les horodatages du RAO et les anciennetés de BD Corpo entrent ; une source injoignable répond 502. */
    private void sourcesEntrantes() throws Exception {
        activer("RAO", ",\"jeuEssai\":[{\"cle\":\"r1\",\"matricule\":\"P1\",\"sens\":\"ENTREE\",\"horodatage\":\"2031-01-15T10:55:00.000Z\"},"
                + "{\"cle\":\"r2\",\"matricule\":\"Z9\",\"sens\":\"SORTIE\",\"horodatage\":\"2031-01-15T23:02:00.000Z\"}]");
        assertThat(corps(http.post(I + "/horodatages/tirer", CHEF, null).andExpect(status().isOk()))).isEqualTo("{\"lus\":2,\"importes\":1,\"doublons\":0,\"rejets\":1}");
        assertThat(corps(http.post(I + "/horodatages/tirer", CHEF, null))).isEqualTo("{\"lus\":0,\"importes\":0,\"doublons\":0,\"rejets\":0}");
        http.get(I + "/connecteurs", CHEF).andExpect(jsonPath("$[0].curseur").value("2031-01-15T23:02:00.000Z"));
        activer("BD_CORPO", ",\"jeuEssai\":[{\"matricule\":\"P1\",\"anciennete\":15.25},{\"matricule\":\"Z9\",\"anciennete\":3}]");
        String a = I + "/anciennetes/EST";
        assertThat(corps(http.post(a, CHEF, null).andExpect(status().isOk()))).isEqualTo("{\"lus\":1,\"modifiees\":1,\"ignorees\":0}");
        assertThat(corps(http.post(a, CHEF, null))).isEqualTo("{\"lus\":1,\"modifiees\":0,\"ignorees\":0}");
        assertThat(jdbc.sql("SELECT anciennete FROM eff_ressources WHERE id = ?").param(ids.get("P1")).query(Double.class).single()).isEqualTo(15.25);
        assertThat(jdbc.sql("SELECT acteurId || ' ' || avant || ' ' || apres FROM eff_journal WHERE action = 'ressource.anciennete'").query(String.class).single())
                .isEqualTo("integrations-effectifs {\"anciennete\":4} {\"anciennete\":15.25}");
        activer("BD_CORPO", ",\"jeuEssai\":[{\"matricule\":\"P1\",\"anciennete\":-3}]");
        assertThat(corps(http.post(a, CHEF, null))).isEqualTo("{\"lus\":1,\"modifiees\":0,\"ignorees\":1}");
        http.post(I + "/anciennetes/OUEST", CHEF, null).andExpect(status().isForbidden());
        activer("RAO", ",\"mode\":\"HTTP\",\"url\":\"http://127.0.0.1:9\"");
        http.post(I + "/horodatages/tirer", CHEF, null).andExpect(status().is(502)).andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("source injoignable")));
        activer("BD_CORPO", ",\"mode\":\"HTTP\",\"url\":\"http://localhost:9\"");
        http.post(a, CHEF, null).andExpect(status().is(502)).andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("source injoignable")));
        // Le secret du jeton n'existe nulle part en base : seule la variable qui le porte est nommée.
        assertThat(jdbc.sql("SELECT * FROM eff_connecteurs").query().listOfRows().toString()).doesNotContainIgnoringCase("bearer").doesNotContainIgnoringCase("secret");
    }

    @Test
    @DisplayName("le module coupé ferme tout")
    void moduleCoupe() throws Exception {
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.get(I + "/connecteurs", ADMIN).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs_integrations' non activé pour cet organisme"));
    }
}
