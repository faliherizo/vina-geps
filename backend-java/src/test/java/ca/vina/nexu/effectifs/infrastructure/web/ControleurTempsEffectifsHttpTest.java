package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.QualificationPointages;
import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotProfilsPointage;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GenerationSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDecisionsPresence;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePointagesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePreparationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireSegmentsTemps;
import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.FeuillesPourPaie;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireDossiersPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.CalculRetenues;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.Frequence;
import ca.vina.nexu.paie.domaine.ModeRemuneration;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.paie.domaine.UniteDeTravail;
import ca.vina.nexu.paie.infrastructure.persistance.BaremesFichiers;
import ca.vina.nexu.paie.infrastructure.persistance.DepotPaie;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.application.requetes.RapportsTemps;
import ca.vina.nexu.effectifs.application.requetes.SourcesFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
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
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaTempsEffectifs;
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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les feuilles de temps par HTTP</strong> (lot R13, jumeau de {@code effectifs-temps.integration.test.ts}) : semaine du
 * 13 janvier 2031. Julie a pointé son quart du 15 (entrée à 5 h 50 : présence préalable) et pas celui du 16, férié : ce
 * jour-là, le temps vient de l'horaire. Marc est entré en retard (pointage à approuver) et n'est pas sorti. Les feuilles se
 * préparent, se corrigent en versions, se jugent par règles en temps réel, se soumettent, s'approuvent en deux paliers par
 * deux personnes différentes, puis partent à la paie codées et signées. Les étapes du Node s'enchaînent : un seul récit.
 */
@DisplayName("/api/effectifs/temps — préparation, saisie en versions, règles, paliers, lot de paie, relevés")
class ControleurTempsEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String PL = E + "/planification";
    private static final String TP = E + "/temps";
    private static final String SEMAINE = "\"debut\":\"2031-01-13\",\"fin\":\"2031-01-19\"";
    /** L'empreinte que le Node calcule pour le lot de paie du récit (même texte, même SHA-256). */
    private static final String EMPREINTE_NODE = "5f4a7fb0c7444c7cc544b3143d8fedb5c2f302a98196f4a394cb20afc4065e40";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_presence\",\"effectifs_temps\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = compte("julie", "employee");
    private static final ContexteRequete MARC = compte("marc", "employee");
    private static final ContexteRequete CHEF = compte("chef", "manager");
    private static final ContexteRequete CHEF2 = compte("chef2", "manager");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnairePointagesEffectifs pointages;
    private GestionnaireDecisionsPresence decisionsPresence;
    private LectureFeuillesTemps lectureTemps;
    private FeuillesPourPaie feuillesPourPaie;
    private final Map<String, String> ids = new HashMap<>();

    private static ContexteRequete compte(String nom, String role) {
        return new ContexteRequete("usr-" + nom, nom, role, null, T);
    }

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

    private static Contexte ctx(String qui, String maintenant) {
        return new Contexte(T, "usr-" + qui, qui.startsWith("chef") ? "manager" : "employee", maintenant);
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (ContexteRequete c : List.of(JULIE, MARC, CHEF, CHEF2)) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', ?, 'x')")
                    .params(c.userId(), c.username(), T, c.role()).update();
        }
        cabler();
        socle();
        planifier();
        // Julie pointe le 15 (5 h 50 → 18 h 10 à Montréal) ; Marc entre à 6 h 30 et ne sort pas.
        pointages.pointer(ctx("julie", "2031-01-15T10:50:00.000Z"), Map.of("sens", "ENTREE"), null);
        pointages.pointer(ctx("julie", "2031-01-15T23:10:00.000Z"), Map.of("sens", "SORTIE"), null);
        ids.put("entreeMarc", (String) pointages.pointer(ctx("marc", "2031-01-15T11:30:00.000Z"), Map.of("sens", "ENTREE"), null).get("id"));
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
        ControleurReglesEffectifs regles = new ControleurReglesEffectifs(garde, new LectureRegles(depotRegles),
                new GestionnaireRegles(depotRegles, new LectureRegles(depotRegles), referentiels, journal, seq), evaluateur);

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
        LecturePresenceEffectifs presence = new LecturePresenceEffectifs(depotPresence, quarts,
                new LecturePresenceEffectifs.Socle(lectRes, perimetres, milieux, depotMilieux));
        FluxEssai.Cablage flux = FluxEssai.cabler(new FluxEssai.Socle(jdbc, horloge, perimetres, evaluateur, journal));
        LectureProfilsPointage profils = new LectureProfilsPointage(new DepotProfilsPointage(jdbc, schemaPresence), evaluateur, lectRes);
        pointages = new GestionnairePointagesEffectifs(new GestionnairePointagesEffectifs.Lectures(presence, quarts, lectRes, perimetres, milieux, depotMilieux,
                new QualificationPointages(profils, referentiels)),
                new GestionnairePointagesEffectifs.Ecritures(depotPresence, evaluateur, journal, seq), flux.approbation());
        decisionsPresence = new GestionnaireDecisionsPresence(new LecturePointagesADecider(presence, flux.lecture()), lectRes, flux.flux(),
                new GestionnaireDecisionsPresence.Ecritures(depotPresence, journal));

        DepotFeuillesTemps depot = new DepotFeuillesTemps(jdbc, new SchemaTempsEffectifs(jdbc), schemaPresence, schemaPlanif);
        lectureTemps = new LectureFeuillesTemps(depot, lectRes, perimetres, referentiels);
        ExportPaieTemps export = new ExportPaieTemps(lectureTemps, journal);
        SourcesFeuillesTemps sources = new SourcesFeuillesTemps(depot, quarts);
        CalculFeuillesTemps calcul = new CalculFeuillesTemps(lectureTemps, sources, evaluateur, flux.lecture());
        GenerationSegmentsTemps generation = new GenerationSegmentsTemps(lectureTemps, sources);
        ControleurTempsEffectifs temps = new ControleurTempsEffectifs(garde, new ControleurTempsEffectifs.Lectures(lectureTemps, calcul, new RapportsTemps(lectureTemps, calcul),
                flux.lecture()),
                new ControleurTempsEffectifs.Gestes(new GestionnaireParametresTemps(lectureTemps, depot, journal),
                        new GestionnairePreparationTemps(new GestionnairePreparationTemps.Lectures(lectureTemps, calcul, generation, perimetres),
                                new GestionnairePreparationTemps.Ecritures(depot, journal, seq)),
                        new GestionnaireSegmentsTemps(lectureTemps, calcul, flux.restriction(), new GestionnaireSegmentsTemps.Ecritures(depot, journal, seq)),
                        new GestionnaireApprobationTemps(lectureTemps, calcul, flux.flux(), new GestionnaireApprobationTemps.Ecritures(depot, journal, seq)),
                        export, flux.flux()));
        feuillesPourPaie = new FeuillesPourPaie(new FeuillesPourPaie.Dependances(lectureTemps, export, depot, journal, depotRes), () -> "2031-01-25T12:00:00.000Z");
        // `/ma-ressource` : la lecture que les écrans personnels interrogent avant d'offrir un geste.
        ControleurMaRessource maRessource = new ControleurMaRessource(garde, lectRes);
        http = new ClientHttpEssai(socle, regles, planification, temps, maRessource);
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

    private void regle(String contexte, String cle, String condition, String action, String message) throws Exception {
        http.post(E + "/regles", ADMIN, "{\"contexte\":\"" + contexte + "\",\"cle\":\"" + cle + "\",\"condition\":" + condition + ",\"action\":\"" + action
                + "\",\"message\":\"" + message + "\",\"effectifDepuis\":\"2027-01-01\"}").andExpect(status().isCreated());
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\",\"attributs\":{\"presencePrealableMinutes\":15}}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("jours_feries", "{\"code\":\"2031-01-16\",\"libelleFr\":\"Férié d'essai\"}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        for (String[] r : new String[][]{{"P1", "Julie", "julie"}, {"P2", "Marc", "marc"}, {"P9", "Chef Deux", "chef2"}}) {
            ids.put(r[0], creer(PL + "/ressources", "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1]
                    + "\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-" + r[2] + "\"}"));
        }
        for (String u : List.of("chef", "chef2")) {
            http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-" + u + "\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                    .andExpect(status().isCreated());
        }
        regle("POINTAGE", "retard", "{\"fait\":\"ecartDebutMinutes\",\"op\":\">\",\"valeur\":10}", "EXIGER_APPROBATION", "retard de {ecartDebutMinutes} min");
        regle("FEUILLE_DE_TEMPS", "journee-max", "{\"fait\":\"heuresJour\",\"op\":\">\",\"valeur\":16}", "BLOQUER", "plus de 16 h déclarées le même jour ({heuresJour} h)");
        regle("HEURES_SUPPLEMENTAIRES", "ferie", "{\"fait\":\"jourFerie\",\"op\":\"=\",\"valeur\":true}", "AVERTIR", "travail un jour férié : majoration à vérifier");
    }

    private void planifier() throws Exception {
        String p = creer(PL + "/periodes", "{\"nom\":\"Semaine 3\",\"uniteCode\":\"EST\"," + SEMAINE + "}");
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        for (String[] q : new String[][]{{"2031-01-15", "P1"}, {"2031-01-16", "P1"}, {"2031-01-15", "P2"}}) {
            String e = corps(http.post(PL + "/equipages", ADMIN, "{\"periodeId\":\"" + p + "\",\"date\":\"" + q[0] + "\",\"debut\":360,\"fin\":1080,"
                    + "\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\","
                    + "\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}").andExpect(status().isCreated()));
            http.post(PL + "/places/" + JsonPath.read(e, "$.places[0].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get(q[1]) + "\"}")
                    .andExpect(status().isOk());
        }
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
    }

    private String feuille(String mat) throws Exception {
        return corps(http.get(TP + "/feuilles/" + ids.get("f_" + mat), ADMIN).andExpect(status().isOk()));
    }

    private int evaluationsDeFeuilles() {
        return jdbc.sql("SELECT COUNT(*) FROM eff_regle_evaluations WHERE objetType = 'feuille'").query(Integer.class).single();
    }

    @Test
    @DisplayName("le récit complet : préparation, lecture, saisie, pointages, paliers, paie, relevés")
    void recit() throws Exception {
        preparation();
        feuilleDeJulie();
        saisieEtCorrection();
        pointagesBloquants();
        soumissionEtPaliers();
        lotDePaie();
        releves();
        saisieEnAttente();
    }

    /** Prépare les feuilles d'une unité depuis l'horaire et les pointages ; rejouer ne double rien (E-RF-1). */
    private void preparation() throws Exception {
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\"," + SEMAINE + "}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("aucun type d'heure de comportement TRAVAILLEE n'est en vigueur : configurez-le dans "
                        + "Paramétrage des effectifs › Référentiels › Types d'heures"));
        ref("types_heure", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"TRAVAILLEE\",\"attributs\":{\"codePaie\":\"100\"}}");
        ref("types_heure", "{\"code\":\"PNP\",\"libelleFr\":\"Pause non payée\",\"comportement\":\"PAUSE_NON_PAYEE\"}");
        ref("types_heure", "{\"code\":\"PREA\",\"libelleFr\":\"Présence préalable\",\"comportement\":\"PRESENCE_PREALABLE\",\"attributs\":{\"codePaie\":\"105\"}}");
        ref("types_heure", "{\"code\":\"HS\",\"libelleFr\":\"Heures supplémentaires\",\"comportement\":\"SUPPLEMENTAIRE\","
                + "\"attributs\":{\"codePaie\":\"150\",\"majorationPourcent\":50}}");
        http.post(TP + "/feuilles/preparer", JULIE, "{\"uniteCode\":\"EST\"," + SEMAINE + "}").andExpect(status().isForbidden());
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\",\"debut\":\"2031-01-01\",\"fin\":\"2031-02-15\"}")
                .andExpect(jsonPath("$.errors[0]").value("une période de paie couvre 31 jours au plus"));
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\"," + SEMAINE + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.creees", Matchers.hasSize(3))).andExpect(jsonPath("$.existantes", Matchers.hasSize(0)));
        String encore = corps(http.post(TP + "/feuilles/preparer", JULIE, "{" + SEMAINE + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.creees", Matchers.hasSize(0))).andExpect(jsonPath("$.existantes", Matchers.hasSize(1))));
        ids.put("f_P1", JsonPath.read(encore, "$.existantes[0]"));
        String liste = corps(http.get(TP + "/feuilles?debut=2031-01-13", CHEF).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nom", Matchers.containsInAnyOrder("Chef Deux", "Julie", "Marc")))
                .andExpect(jsonPath("$[*].statut", Matchers.everyItem(Matchers.is("BROUILLON")))));
        List<Map<String, Object>> feuilles = JsonPath.read(liste, "$");
        feuilles.forEach(f -> ids.put("f_" + f.get("matricule"), (String) f.get("id")));
        http.get(TP + "/moi", JULIE).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("f_P1"))));
        http.get(TP + "/moi", CHEF).andExpect(content("[]"));
        // Pour une personne nommée : soi-même, ou quelqu'un de son unité avec le niveau PLANIFICATION.
        http.post(TP + "/feuilles/preparer", JULIE, "{\"ressourceId\":\"" + ids.get("P2") + "\"," + SEMAINE + "}").andExpect(status().isForbidden());
        http.post(TP + "/feuilles/preparer", JULIE, "{\"ressourceId\":\"" + ids.get("P1") + "\"," + SEMAINE + "}")
                .andExpect(jsonPath("$.existantes", Matchers.contains(ids.get("f_P1"))));
        http.post(TP + "/feuilles/preparer", CHEF, "{\"ressourceId\":\"" + ids.get("P2") + "\"," + SEMAINE + "}")
                .andExpect(jsonPath("$.existantes", Matchers.contains(ids.get("f_P2"))));
        // Sans personne ni unité, la demande est « la mienne » : le refus doit parler du dossier
        // qui manque, pas d'une demande mal formée (mesuré sur /admin/matemps le 2 novembre 2026).
        http.post(TP + "/feuilles/preparer", CHEF, "{" + SEMAINE + "}").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]")
                        .value("votre compte n'est relié à aucune ressource planifiable : demandez-le à votre planificateur"));
        // Et l'écran l'apprend avant d'offrir le geste.
        http.get(E + "/ma-ressource", CHEF).andExpect(jsonPath("$.ressource").value(Matchers.nullValue()));
        http.get(E + "/ma-ressource", JULIE).andExpect(jsonPath("$.ressource.matricule").value("P1"));
        http.get(TP + "/feuilles?statut=APPROUVEE", ADMIN).andExpect(content("[]"));
        http.get(E + "/journal?action=temps.preparation", ADMIN).andExpect(jsonPath("$[0].objetId").value("2031-01-13:2031-01-19"))
                .andExpect(jsonPath("$[0].acteurId").value(GestionnairePreparationTemps.MECANISME));
    }

    private static org.springframework.test.web.servlet.ResultMatcher content(String attendu) {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(attendu);
    }

    /** Pointé le 15, planifié le 16 férié ; heures supplémentaires et anomalies calculées (E-GP-8, E-RF-5, E-RF-9, E-RF-11). */
    private void feuilleDeJulie() throws Exception {
        http.get(TP + "/feuilles/" + ids.get("f_P1"), JULIE).andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[*].date", Matchers.contains("2031-01-15", "2031-01-15", "2031-01-16")))
                .andExpect(jsonPath("$.segments[*].typeHeureCode", Matchers.contains("PREA", "REG", "REG")))
                .andExpect(jsonPath("$.segments[*].source", Matchers.contains("POINTAGE", "POINTAGE", "GENERE")))
                .andExpect(jsonPath("$.segments[*].debut", Matchers.contains("2031-01-15T10:50:00.000Z", "2031-01-15T11:00:00.000Z", "2031-01-16T11:00:00.000Z")))
                .andExpect(jsonPath("$.segments[*].fin", Matchers.contains("2031-01-15T11:00:00.000Z", "2031-01-15T23:10:00.000Z", "2031-01-16T23:00:00.000Z")))
                .andExpect(jsonPath("$.totaux.PREA").value(10)).andExpect(jsonPath("$.totaux.REG").value(1450))
                .andExpect(jsonPath("$.heuresSupplementaires.parJour['2031-01-15']").value(260))
                .andExpect(jsonPath("$.heuresSupplementaires.parJour['2031-01-16']").value(240)).andExpect(jsonPath("$.heuresSupplementaires.total").value(500))
                .andExpect(jsonPath("$.ecarts[*].planifie", Matchers.contains(720, 720))).andExpect(jsonPath("$.ecarts[*].declare", Matchers.contains(740, 720)))
                .andExpect(jsonPath("$.ecarts[*].ecart", Matchers.contains(20, 0))).andExpect(jsonPath("$.ecarts[0].natureCode").value("REG"))
                .andExpect(jsonPath("$.anomalies[*].date", Matchers.contains("2031-01-15", "2031-01-16", "2031-01-16")))
                .andExpect(jsonPath("$.anomalies[*].code", Matchers.contains("ECART_PLANIFIE", "REGLE", "SANS_POINTAGE")))
                .andExpect(jsonPath("$.anomalies[*].bloquante", Matchers.contains(false, false, false)))
                .andExpect(jsonPath("$.anomalies[1].message").value("travail un jour férié : majoration à vérifier"))
                .andExpect(jsonPath("$.instantane").value(Matchers.nullValue())).andExpect(jsonPath("$.decisions", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.segments[0].comportement").value("PRESENCE_PREALABLE")).andExpect(jsonPath("$.segments[0].version").value(1))
                .andExpect(jsonPath("$.nom").value("Julie")).andExpect(jsonPath("$.matricule").value("P1")).andExpect(jsonPath("$.soumiseLe").value(Matchers.nullValue()));
        // Une lecture ne laisse aucune trace d'évaluation : seules les décisions en laissent.
        assertThat(evaluationsDeFeuilles()).isZero();
        http.get(TP + "/feuilles/" + ids.get("f_P1"), MARC).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("feuille « " + ids.get("f_P1") + " » introuvable"));
        http.get(TP + "/feuilles/inconnue", ADMIN).andExpect(status().isNotFound());
    }

    /** Saisie et correction jugées en temps réel, en versions, motivées quand il le faut (E-RF-2, E-RF-3, E-RF-14, E-GP-5). */
    private void saisieEtCorrection() throws Exception {
        String s = TP + "/feuilles/" + ids.get("f_P1") + "/segments";
        http.post(s, JULIE, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T13:00:00Z\",\"fin\":\"2031-01-17T17:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.segments", Matchers.hasSize(4)));
        http.post(s, JULIE, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T17:00:00Z\",\"fin\":\"2031-01-18T06:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("plus de 16 h déclarées le même jour (17 h)"))
                .andExpect(jsonPath("$.verdict.action").value("BLOQUER")).andExpect(jsonPath("$.verdict.motifs[0].cle").value("feuille"));
        assertThat(JsonPath.<List<Object>>read(feuille("P1"), "$.segments")).hasSize(4);
        http.post(s, JULIE, "{\"date\":\"2031-01-20\",\"debut\":\"2031-01-20T13:00:00Z\",\"fin\":\"2031-01-20T14:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(jsonPath("$.errors[0]").value("le 2031-01-20 est hors de la période de la feuille (2031-01-13 au 2031-01-19)"));
        http.post(s, JULIE, "{\"date\":\"2031-01-12\",\"debut\":\"2031-01-12T13:00:00Z\",\"fin\":\"2031-01-12T14:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().is(422));
        http.post(s, JULIE, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T18:00:00Z\",\"fin\":\"2031-01-17T19:00:00Z\",\"typeHeureCode\":\"XX\"}")
                .andExpect(jsonPath("$.errors[0]").value("type d'heure « XX » inconnu ou hors vigueur"));
        http.post(s, CHEF, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T18:00:00Z\",\"fin\":\"2031-01-17T19:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().is(422));
        http.post(s, MARC, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T18:00:00Z\",\"fin\":\"2031-01-17T19:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isNotFound());

        List<Map<String, Object>> segments = JsonPath.read(feuille("P1"), "$.segments[?(@.typeHeureCode == 'REG' && @.source == 'POINTAGE')]");
        String pointe = (String) segments.getFirst().get("id");
        http.put(s + "/" + pointe, JULIE, "{\"fin\":\"2031-01-15T23:00:00Z\"}")
                .andExpect(jsonPath("$.errors[0]").value("« motif » : une correction du temps d'autrui ou d'un temps pointé se motive"));
        String corrige = corps(http.put(s + "/" + pointe, JULIE, "{\"fin\":\"2031-01-15T23:00:00Z\",\"motif\":\"Sortie pointée en retard\"}").andExpect(status().isOk()));
        Map<String, Object> v2 = JsonPath.<List<Map<String, Object>>>read(corrige, "$.segments[?(@.remplaceId == '" + pointe + "')]").getFirst();
        assertThat(List.of(v2.get("version"), v2.get("fin"), v2.get("source"), v2.get("motif")))
                .containsExactly(2, "2031-01-15T23:00:00.000Z", "POINTAGE", "Sortie pointée en retard");
        http.put(s + "/" + pointe, JULIE, "{\"fin\":\"2031-01-15T22:00:00Z\",\"motif\":\"encore une fois\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce segment a déjà été remplacé : corrigez sa dernière version"));
        http.get(s + "/" + v2.get("id") + "/historique", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[*].version", Matchers.contains(2, 1)))
                .andExpect(jsonPath("$[*].fin", Matchers.contains("2031-01-15T23:00:00.000Z", "2031-01-15T23:10:00.000Z")))
                .andExpect(jsonPath("$[*].actif", Matchers.contains(1, 0))).andExpect(jsonPath("$[0].comportement").value("TRAVAILLEE"))
                .andExpect(jsonPath("$[0].tenantId").value(T));
        http.get(s + "/inconnu/historique", JULIE).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("segment « inconnu » introuvable"));
        http.put(s + "/inconnu", JULIE, null).andExpect(status().isNotFound());

        String saisi = JsonPath.<List<String>>read(corrige, "$.segments[?(@.date == '2031-01-17')].id").getFirst();
        http.delete(s + "/" + saisi, JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.segments", Matchers.hasSize(3)));
        http.delete(s + "/" + v2.get("id"), JULIE).andExpect(jsonPath("$.errors[0]").value("« motif » : une correction du temps d'autrui ou d'un temps pointé se motive"));
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'temps.segment.corrige'").query(String.class).single())
                .isEqualTo("{\"debut\":\"2031-01-15T11:00:00.000Z\",\"fin\":\"2031-01-15T23:00:00.000Z\",\"typeHeureCode\":\"REG\",\"motif\":\"Sortie pointée en retard\"}");
    }

    /** Un pointage en attente et un quart sans sortie bloquent la soumission ; régénérer reprend les pointages du moment. */
    private void pointagesBloquants() throws Exception {
        http.get(TP + "/feuilles/" + ids.get("f_P2"), MARC).andExpect(jsonPath("$.anomalies[?(@.bloquante == true)].code",
                Matchers.contains("POINTAGE_EN_ATTENTE", "POINTAGE_INCOMPLET")));
        http.post(TP + "/feuilles/" + ids.get("f_P2") + "/soumettre", MARC, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("à corriger avant de soumettre : un pointage du 2031-01-15 attend l'approbation de l'encadrement ; "
                        + "le quart du 2031-01-15 n'a pas de sortie pointée"));
        decisionsPresence.decider(ctx("chef", "2031-01-16T00:00:00.000Z"), ids.get("entreeMarc"), PresenceEffectifs.DecisionPointage.APPROUVER, Map.of());
        pointages.pointer(ctx("marc", "2031-01-15T23:00:00.000Z"), Map.of("sens", "SORTIE"), null);
        // Une journée déclarée après coup, approuvée par l'encadrement, entre aussi dans la feuille.
        pointages.saisirAPosteriori(ctx("chef", "2031-01-20T00:00:00.000Z"), Map.of("ressourceId", ids.get("P2"), "justification", "Formation à l'extérieur",
                "segments", List.of(Map.of("debut", "2031-01-18T13:00:00.000Z", "fin", "2031-01-18T17:00:00.000Z", "type", "TRAVAIL"))));
        String r = corps(http.post(TP + "/feuilles/" + ids.get("f_P2") + "/regenerer", MARC, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[*].date", Matchers.contains("2031-01-15", "2031-01-18")))
                .andExpect(jsonPath("$.segments[*].source", Matchers.contains("POINTAGE", "POINTAGE")))
                .andExpect(jsonPath("$.anomalies[?(@.bloquante == true)]", Matchers.hasSize(0))));
        assertThat(JsonPath.<Object>read(r, "$.segments[0].placeId")).isNotNull();
        assertThat(JsonPath.<Object>read(r, "$.segments[1].placeId")).isNull();
        http.get(E + "/journal?action=temps.regeneration", ADMIN).andExpect(jsonPath("$[0].objetId").value(ids.get("f_P2")));
        // Régénérer encore garde les places déjà reprises : rien ne double.
        http.post(TP + "/feuilles/" + ids.get("f_P2") + "/regenerer", CHEF, null).andExpect(jsonPath("$.segments", Matchers.hasSize(2)));
    }

    /** Soumise, la feuille se verrouille ; deux paliers, deux personnes ; jamais sa propre feuille (E-GP-3, E-GP-6, E-RF-4). */
    private void soumissionEtPaliers() throws Exception {
        String f1 = TP + "/feuilles/" + ids.get("f_P1");
        String f9 = TP + "/feuilles/" + ids.get("f_P9");
        http.put(TP + "/parametres", CHEF, "{\"paliersApprobation\":2}").andExpect(status().isForbidden());
        assertThat(corps(http.put(TP + "/parametres", ADMIN, "{\"paliersApprobation\":2,\"toleranceEcartMinutes\":30}").andExpect(status().isOk())))
                .isEqualTo("{\"seuilJourHeures\":8,\"seuilSemaineHeures\":40,\"paliersApprobation\":2,\"toleranceEcartMinutes\":30,"
                        + "\"toleranceCorroborationMinutes\":5,\"actionNonCorrobore\":\"SIGNALER\",\"actionSourcesDivergentes\":\"BLOQUER\"}");
        http.get(TP + "/parametres", JULIE).andExpect(jsonPath("$.paliersApprobation").value(2));
        http.put(TP + "/parametres", ADMIN, "{\"paliersApprobation\":9}").andExpect(status().is(422));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("SOUMISE"))
                .andExpect(jsonPath("$.palier").value(1)).andExpect(jsonPath("$.paliersRequis").value(2))
                .andExpect(jsonPath("$.instantane[*].code", Matchers.contains("REGLE", "SANS_POINTAGE")))
                .andExpect(jsonPath("$.soumiseLe").value("2026-09-30T12:00:00.000Z"));
        assertThat(evaluationsDeFeuilles()).isEqualTo(4);
        http.post(f1 + "/segments", JULIE, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T13:00:00Z\",\"fin\":\"2031-01-17T14:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(jsonPath("$.errors[0]").value("feuille soumise : elle attend une décision ; rappelez-la ou faites-la retourner pour la corriger"));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(jsonPath("$.errors[0]").value("une feuille déjà soumise ne se soumet pas"));
        http.post(f1 + "/approuver", JULIE, null).andExpect(status().isForbidden());
        http.post(f1 + "/approuver", CHEF, null).andExpect(jsonPath("$.statut").value("SOUMISE")).andExpect(jsonPath("$.palier").value(2));
        http.post(f1 + "/approuver", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous avez déjà approuvé un palier de cette feuille : le palier suivant revient à une autre personne"));
        http.post(f1 + "/retourner", CHEF2, null).andExpect(jsonPath("$.errors[0]").value("« motif » : dites ce qu'il faut corriger"));
        http.post(f1 + "/approuver", CHEF2, null).andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.approuveeLe").value("2026-09-30T12:00:00.000Z"))
                .andExpect(jsonPath("$.decisions[*].palier", Matchers.contains(1, 2))).andExpect(jsonPath("$.decisions[*].decision", Matchers.contains("APPROUVER", "APPROUVER")));
        http.post(f1 + "/regenerer", JULIE, null).andExpect(jsonPath("$.errors[0]").value("feuille approuvée : rouvrez-la ou rappelez-la avant la paie pour la corriger"));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(jsonPath("$.errors[0]").value("une feuille approuvée ne se soumet pas"));
        http.post(f1 + "/approuver", CHEF, null).andExpect(status().isConflict());
        http.post(f1 + "/retourner", CHEF, "{\"motif\":\"trop tard\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("seule une feuille soumise se retourne"));

        http.post(f9 + "/soumettre", CHEF2, null).andExpect(status().isOk());
        http.post(f9 + "/approuver", CHEF2, null).andExpect(jsonPath("$.errors[0]").value("on n'approuve pas sa propre feuille"));
        http.post(f9 + "/retourner", CHEF, "{\"motif\":\"Ajoutez la réunion du 14\"}").andExpect(jsonPath("$.statut").value("RETOURNEE"))
                .andExpect(jsonPath("$.palier").value(0)).andExpect(jsonPath("$.decisions[0].motif").value("Ajoutez la réunion du 14"));
        http.post(f9 + "/approuver", CHEF, null).andExpect(jsonPath("$.errors[0]").value("seule une feuille soumise s'approuve"));
        http.post(f9 + "/segments", CHEF2, "{\"date\":\"2031-01-14\",\"debut\":\"2031-01-14T14:00:00Z\",\"fin\":\"2031-01-14T16:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isCreated());
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'temps.retourner'").query(String.class).single())
                .isEqualTo("{\"statut\":\"RETOURNEE\",\"palier\":0,\"motif\":\"Ajoutez la réunion du 14\"}");
    }

    /** Le lot de paie : feuilles approuvées, codes de paie, heures supplémentaires reclassées, empreinte (E-RF-12). */
    private void lotDePaie() throws Exception {
        http.get(TP + "/export-paie?debut=2031-01-13", JULIE).andExpect(status().isForbidden());
        String x = corps(http.get(TP + "/export-paie?debut=2031-01-13", CHEF).andExpect(status().isOk()));
        assertThat(x).isEqualTo("{\"debut\":\"2031-01-13\",\"feuilles\":[\"" + ids.get("f_P1") + "\"],\"lignes\":["
                + "{\"matricule\":\"P1\",\"date\":\"2031-01-15\",\"codePaie\":\"100\",\"heures\":7.83},"
                + "{\"matricule\":\"P1\",\"date\":\"2031-01-15\",\"codePaie\":\"105\",\"heures\":0.17},"
                + "{\"matricule\":\"P1\",\"date\":\"2031-01-15\",\"codePaie\":\"150\",\"heures\":4.17},"
                + "{\"matricule\":\"P1\",\"date\":\"2031-01-16\",\"codePaie\":\"100\",\"heures\":8},"
                + "{\"matricule\":\"P1\",\"date\":\"2031-01-16\",\"codePaie\":\"150\",\"heures\":4}],\"empreinte\":\"" + EMPREINTE_NODE + "\"}");
        http.get(TP + "/export-paie?debut=2031-01-13&uniteCode=OUEST", ADMIN).andExpect(jsonPath("$.lignes", Matchers.hasSize(0)));
        http.get(TP + "/export-paie?debut=2031-01-13&uniteCode=EST", ADMIN).andExpect(jsonPath("$.lignes", Matchers.hasSize(5)));
        http.get(TP + "/export-paie", ADMIN).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« debut » : date AAAA-MM-JJ attendue"));
        http.get(E + "/journal?action=temps.export", ADMIN).andExpect(jsonPath("$[0].objetId").value("2031-01-13"));
    }

    /** Les relevés se regroupent par unité, personne, type d'heure ou semaine (E-RF-7, P-RF-1). */
    private void releves() throws Exception {
        String r = TP + "/rapports?debut=2031-01-13&regroupement=";
        assertThat(corps(http.get(r + "unite", CHEF).andExpect(status().isOk())))
                .isEqualTo("[{\"groupe\":\"EST\",\"feuilles\":3,\"planifie\":2160,\"declare\":2500,\"supplementaires\":700,\"anomalies\":2}]");
        http.get(TP + "/rapports?debut=2031-01-13", CHEF).andExpect(jsonPath("$[0].groupe").value("EST"));
        http.get(r + "ressource", CHEF).andExpect(jsonPath("$[*].groupe", Matchers.contains("P1 — Julie", "P2 — Marc", "P9 — Chef Deux")))
                .andExpect(jsonPath("$[*].declare", Matchers.contains(1450, 930, 120)));
        assertThat(corps(http.get(r + "typeHeure", CHEF))).isEqualTo("[{\"groupe\":\"PREA\",\"feuilles\":0,\"planifie\":0,\"declare\":10,\"supplementaires\":0,"
                + "\"anomalies\":0},{\"groupe\":\"REG\",\"feuilles\":0,\"planifie\":0,\"declare\":2490,\"supplementaires\":0,\"anomalies\":0}]");
        http.get(r + "semaine", CHEF).andExpect(jsonPath("$[*].groupe", Matchers.contains("2031-01-13"))).andExpect(jsonPath("$[*].declare", Matchers.contains(2500)));
        http.get(r + "mois", CHEF).andExpect(status().is(422));
        http.get(TP + "/rapports?debut=2031-01-13", JULIE).andExpect(status().isForbidden());
        relevesExportes();
    }

    /** P-TN-8 : le relevé s'emporte dans les quatre formats, en heures décimales hors de l'écran. */
    private void relevesExportes() throws Exception {
        String r = TP + "/rapports?debut=2031-01-13&regroupement=unite&format=";
        http.get(r + "csv", CHEF).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
                        "attachment; filename=\"releve-temps-2031-01-13.csv\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        "Groupe,Feuilles,Planifié (h),Déclaré (h),Supplémentaires (h),Anomalies\nEST,3,36.0,41.67,11.67,2\n"));
        byte[] xlsx = http.get(r + "xlsx", CHEF).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        byte[] pdf = http.get(r + "pdf", CHEF).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        http.get(r + "json", CHEF).andExpect(jsonPath("$[0].declare").value(2500));
        http.get(r + "docx", CHEF).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("format inconnu : « docx » (attendu : json, csv, xlsx, pdf)"));
        // Un format valide ne contourne pas la garde : l'employée reste refusée.
        http.get(r + "pdf", JULIE).andExpect(status().isForbidden());
    }

    /** Une saisie a posteriori en attente, hors quart, bloque aussi : datée du jour local de son premier pointage. */
    private void saisieEnAttente() throws Exception {
        pointages.saisirAPosteriori(ctx("marc", "2031-01-20T00:00:00.000Z"), Map.of("justification", "Oubli du téléphone",
                "segments", List.of(Map.of("debut", "2031-01-19T13:00:00.000Z", "fin", "2031-01-19T14:00:00.000Z", "type", "TRAVAIL"))));
        http.get(TP + "/feuilles/" + ids.get("f_P2"), CHEF).andExpect(jsonPath("$.anomalies[?(@.code == 'POINTAGE_EN_ATTENTE')].date",
                Matchers.contains("2031-01-19", "2031-01-19")));
        assertThat(lectureTemps.parametres("inconnu").paliersApprobation()).isEqualTo(1);
        assertThat(TempsEffectifs.StatutFeuille.valueOf(JsonPath.read(feuille("P9"), "$.statut"))).isEqualTo(TempsEffectifs.StatutFeuille.RETOURNEE);
    }


    @Test
    @DisplayName("les champs de l'organisme : l'administration les définit ; la saisie les vérifie ; la correction garde la description")
    void champsEtTaches() throws Exception {
        preparation();
        http.post(TP + "/champs", JULIE, "{\"code\":\"vehicule\",\"libelle\":\"Véhicule\",\"type\":\"liste\",\"valeurs\":[\"A-12\"]}")
                .andExpect(status().isForbidden());
        http.post(TP + "/champs", ADMIN, "{\"code\":\"vehicule\",\"libelle\":\"Véhicule\",\"type\":\"liste\",\"valeurs\":[\"A-12\",\"B-07\"],\"requis\":true}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.ordre").value(0)).andExpect(jsonPath("$.actif").value(true));
        http.post(TP + "/champs", ADMIN, "{\"code\":\"km\",\"libelle\":\"Kilomètres\",\"type\":\"nombre\"}").andExpect(status().isCreated());
        http.post(TP + "/champs", ADMIN, "{\"code\":\"km\",\"libelle\":\"Encore\",\"type\":\"nombre\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le champ « km » existe déjà : désactivez-le plutôt que de le redéfinir"));
        http.post(TP + "/champs", ADMIN, "{\"code\":\"X\",\"libelle\":\"x\",\"type\":\"texte\"}").andExpect(status().is(422));
        http.get(TP + "/champs", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[*].code", Matchers.contains("vehicule", "km")))
                .andExpect(jsonPath("$[0].valeurs", Matchers.contains("A-12", "B-07")));

        String s = TP + "/feuilles/" + ids.get("f_P1") + "/segments";
        String seg = "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T13:00:00Z\",\"fin\":\"2031-01-17T17:00:00Z\",\"typeHeureCode\":\"REG\"";
        http.post(s, JULIE, seg + "}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« Véhicule » est requis"));
        http.post(s, JULIE, seg + ",\"attributs\":{\"vehicule\":\"Z-99\"}}").andExpect(jsonPath("$.errors[0]").value("« Véhicule » : l'un de A-12, B-07"));
        http.post(s, JULIE, seg + ",\"attributs\":{\"vehicule\":\"A-12\",\"inconnu\":1}}")
                .andExpect(jsonPath("$.errors[0]").value("« attributs.inconnu » : champ inconnu ou désactivé"));
        http.post(s, JULIE, seg + ",\"attributs\":{\"vehicule\":\"A-12\",\"km\":\"dix\"}}").andExpect(jsonPath("$.errors[0]").value("« Kilomètres » : un nombre"));
        http.post(s, JULIE, seg + ",\"description\":\"" + "x".repeat(501) + "\",\"attributs\":{\"vehicule\":\"A-12\"}}")
                .andExpect(jsonPath("$.errors[0]").value("« description » : 500 caractères au plus"));
        String cree = corps(http.post(s, JULIE, seg + ",\"description\":\" Transport inter-hospitalier \",\"attributs\":{\"vehicule\":\"A-12\",\"km\":42}}")
                .andExpect(status().isCreated()));
        Map<String, Object> ajoute = JsonPath.<List<Map<String, Object>>>read(cree, "$.segments[?(@.date == '2031-01-17')]").getFirst();
        assertThat(List.of(ajoute.get("description"), ajoute.get("attributs"))).containsExactly("Transport inter-hospitalier", Map.of("vehicule", "A-12", "km", 42));

        // La correction sans valeurs garde celles de la version remplacée ; avec des valeurs, elle les remplace.
        String corrige = corps(http.put(s + "/" + ajoute.get("id"), JULIE, "{\"fin\":\"2031-01-17T16:00:00Z\"}").andExpect(status().isOk()));
        Map<String, Object> v2 = JsonPath.<List<Map<String, Object>>>read(corrige, "$.segments[?(@.remplaceId == '" + ajoute.get("id") + "')]").getFirst();
        assertThat(List.of(v2.get("description"), v2.get("attributs"))).containsExactly("Transport inter-hospitalier", Map.of("vehicule", "A-12", "km", 42));
        String encore = corps(http.put(s + "/" + v2.get("id"), JULIE, "{\"description\":\"Retour à la base\",\"attributs\":{\"vehicule\":\"B-07\"}}")
                .andExpect(status().isOk()));
        Map<String, Object> v3 = JsonPath.<List<Map<String, Object>>>read(encore, "$.segments[?(@.remplaceId == '" + v2.get("id") + "')]").getFirst();
        assertThat(List.of(v3.get("description"), v3.get("attributs"))).containsExactly("Retour à la base", Map.of("vehicule", "B-07"));

        // Désactiver : le champ ne s'exige plus, ni ne s'accepte ; ce qui est saisi reste lisible.
        http.patch(TP + "/champs/vehicule", ADMIN, "{\"actif\":\"non\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« actif » : vrai ou faux"));
        http.patch(TP + "/champs/absent", ADMIN, "{\"actif\":false}").andExpect(status().isNotFound());
        http.patch(TP + "/champs/vehicule", JULIE, "{\"actif\":false}").andExpect(status().isForbidden());
        http.patch(TP + "/champs/vehicule", ADMIN, "{\"actif\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        http.post(s, JULIE, "{\"date\":\"2031-01-18\",\"debut\":\"2031-01-18T13:00:00Z\",\"fin\":\"2031-01-18T14:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isCreated());
        http.get(TP + "/feuilles/" + ids.get("f_P1"), JULIE).andExpect(jsonPath("$.segments[?(@.date == '2031-01-17')].attributs.vehicule", Matchers.contains("B-07")));
    }

    /**
     * <strong>Avant la paie, une feuille se reprend ; après, jamais</strong> (14 novembre 2026). La personne rappelle sa feuille
     * soumise ou approuvée ; l'encadrement rouvre une feuille approuvée, avec un motif ; une feuille payée ne bouge plus.
     */
    @Test
    @DisplayName("rappeler, rouvrir, puis payer : la feuille se reprend avant la paie, plus jamais après")
    void rappelReouvertureEtPaie() throws Exception {
        preparation();
        String f1 = TP + "/feuilles/" + ids.get("f_P1");
        http.post(f1 + "/rappeler", JULIE, null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une feuille qui n'est pas soumise n'a rien à rappeler"));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(jsonPath("$.statut").value("SOUMISE"));
        // Rappelée : brouillon, circuit retiré ; l'approbateur n'a plus rien à décider.
        http.post(f1 + "/rappeler", MARC, null).andExpect(status().isNotFound());
        http.post(f1 + "/rappeler", JULIE, "{\"motif\":\"j'ai oublié la formation du 17\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("BROUILLON")).andExpect(jsonPath("$.flux.statut").value("RETIRE"))
                .andExpect(jsonPath("$.decisions[-1].decision").value("RAPPELER")).andExpect(jsonPath("$.decisions[-1].motif").value("j'ai oublié la formation du 17"));
        http.get(TP + "/feuilles?statut=SOUMISE", CHEF).andExpect(jsonPath("$[*].id", Matchers.not(Matchers.hasItem(ids.get("f_P1")))));
        http.post(f1 + "/approuver", CHEF, null).andExpect(status().is4xxClientError());
        http.post(f1 + "/segments", JULIE, "{\"date\":\"2031-01-17\",\"debut\":\"2031-01-17T13:00:00Z\",\"fin\":\"2031-01-17T15:00:00Z\",\"typeHeureCode\":\"REG\"}")
                .andExpect(status().isCreated());
        http.post(f1 + "/soumettre", JULIE, null).andExpect(jsonPath("$.statut").value("SOUMISE")).andExpect(jsonPath("$.flux.statut").value("EN_COURS"));
        http.post(f1 + "/approuver", CHEF, null).andExpect(jsonPath("$.statut").value("APPROUVEE"));

        // Rouverte par l'encadrement, avec un motif ; l'employée ne rouvre pas.
        http.post(f1 + "/rouvrir", JULIE, "{\"motif\":\"x\"}").andExpect(status().isForbidden());
        http.post(f1 + "/rouvrir", CHEF, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« motif » : dites ce qu'il faut corriger"));
        http.post(f1 + "/rouvrir", CHEF, "{\"motif\":\"La formation du 17 est en heures régulières\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("RETOURNEE")).andExpect(jsonPath("$.approuveeLe").isEmpty())
                .andExpect(jsonPath("$.decisions[-1].decision").value("ROUVRIR"));
        http.post(f1 + "/rouvrir", CHEF, "{\"motif\":\"encore\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("seule une feuille approuvée se rouvre ; une feuille soumise se retourne"));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(status().isOk());
        http.post(f1 + "/approuver", CHEF, null).andExpect(jsonPath("$.statut").value("APPROUVEE"));
        // Approuvée, pas encore payée : la personne peut encore la rappeler.
        http.post(f1 + "/rappeler", JULIE, null).andExpect(jsonPath("$.statut").value("BROUILLON")).andExpect(jsonPath("$.approuveeLe").isEmpty());
        http.post(f1 + "/soumettre", JULIE, null).andExpect(status().isOk());
        http.post(f1 + "/approuver", CHEF, null).andExpect(jsonPath("$.statut").value("APPROUVEE"));

        // La paie la prend : plus rien ne la change, et rien ne se paie deux fois.
        // La paie la voit par le port partagé (ADR 0076) : ses heures régulières, la personne, son compte.
        List<HeuresApprouveesPort.FeuilleAPayer> aPayer = feuillesPourPaie.approuvees(T, "2031-01-13", "2031-01-19");
        HeuresApprouveesPort.FeuilleAPayer sienne = aPayer.stream().filter(f -> f.feuilleId().equals(ids.get("f_P1"))).findFirst().orElseThrow();
        assertThat(sienne.lignes()).isNotEmpty().allSatisfy(l -> assertThat(l.heures()).isPositive());
        assertThat(sienne.lignes()).extracting(HeuresApprouveesPort.LigneHeures::nature).contains(HeuresApprouveesPort.NatureHeures.REGULIERE);
        assertThat(feuillesPourPaie.approuvees(T, "2031-01-20", "2031-01-26")).isEmpty();
        // Sans type d'heure supplémentaire en vigueur, les heures ne se calculent pas : le motif passe dans le
        // vocabulaire du port, que la paie rend à l'administration (et non une erreur 500).
        String sansHs = "UPDATE eff_referentiels SET comportement = ? WHERE tenantId = ? AND type = 'types_heure' AND code = 'HS'";
        jdbc.sql(sansHs).params("TRAVAILLEE", T).update();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> feuillesPourPaie.approuvees(T, "2031-01-13", "2031-01-19"))
                .isInstanceOf(HeuresApprouveesPort.HeuresIncalculables.class).hasMessageContaining("SUPPLEMENTAIRE");
        jdbc.sql(sansHs).params("SUPPLEMENTAIRE", T).update();
        assertThat(feuillesPourPaie.personnes(T)).extracting(PersonnesPourPaiePort.Personne::ressourceId).contains(sienne.ressourceId());
        assertThat(feuillesPourPaie.ressourceDe(T, JULIE.userId())).contains(sienne.ressourceId());
        assertThat(feuillesPourPaie.ressourceDe(T, "inconnu")).isEmpty();
        // La paie elle-même, contre les vraies feuilles : un dossier, un cycle préparé puis traité. Le barème 2026 sert
        // pour 2031 — l'essai porte sur la chaîne, CalculRetenuesTest porte sur les montants.
        String cycleId = payer(sienne.ressourceId());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_journal WHERE action = 'temps.payee' AND objetId = ?").param(ids.get("f_P1")).query(Integer.class).single()).isOne();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> feuillesPourPaie.marquerPayees(
                        new HeuresApprouveesPort.Verrou(T, "admin", "x", "paie-2"), List.of(ids.get("f_P1"))))
                .isInstanceOf(HeuresApprouveesPort.FeuilleNonApprouvee.class).hasMessageContaining("n'est plus approuvée");
        http.get(f1, JULIE).andExpect(jsonPath("$.statut").value("PAYEE"));
        String payee = TempsEffectifs.FEUILLE_PAYEE;
        http.post(f1 + "/rappeler", JULIE, null).andExpect(jsonPath("$.errors[0]").value(payee));
        http.post(f1 + "/rouvrir", CHEF, "{\"motif\":\"x\"}").andExpect(jsonPath("$.errors[0]").value(payee));
        http.post(f1 + "/soumettre", JULIE, null).andExpect(jsonPath("$.errors[0]").value(payee));
        http.post(f1 + "/regenerer", JULIE, null).andExpect(jsonPath("$.errors[0]").value(payee));
        assertThat(jdbc.sql("SELECT paieId || '|' || payeeLe FROM eff_feuilles WHERE id = ?").param(ids.get("f_P1")).query(String.class).single())
                .isEqualTo(cycleId + "|2031-01-25T12:00:00.000Z");
        // Le lot de paie ne prend que les feuilles approuvées non payées.
        http.get(TP + "/export-paie?debut=2031-01-13", ADMIN).andExpect(jsonPath("$.feuilles", Matchers.not(Matchers.hasItem(ids.get("f_P1")))));
    }

    /** Prépare et traite la paie de la semaine du 13 janvier 2031 ; rend l'identifiant du cycle traité. */
    private String payer(String ressourceId) {
        DepotPaie depotPaie = new DepotPaie(jdbc);
        Trace trace = new Trace(T, "admin", "2031-01-25T12:00:00.000Z");
        Bareme bareme = new BaremesFichiers().pour(2026).orElseThrow();
        LecturePaie lecturePaie = new LecturePaie(depotPaie, annee -> Optional.of(bareme), feuillesPourPaie);
        new GestionnaireDossiersPaie(depotPaie, feuillesPourPaie).enregistrer(trace, new DossierRemuneration(ressourceId, ModeRemuneration.HORAIRE,
                Frequence.AUX_DEUX_SEMAINES, new BigDecimal("30"), null, CalculRetenues.MontantsPersonnels.deBase(bareme)));
        UniteDeTravail direct = new UniteDeTravail() {
            @Override
            public <R> R executer(java.util.function.Supplier<R> geste) {
                return geste.get();
            }
        };
        GestionnaireCyclesPaie cycles = new GestionnaireCyclesPaie(new GestionnaireCyclesPaie.Dependances(lecturePaie, depotPaie, feuillesPourPaie, direct, sequence()));
        GestionnaireCyclesPaie.Preparation p = cycles.preparer(trace, new CyclePaie.Periode("2031-01-13", "2031-01-19", "2031-01-23"));
        assertThat(p.bulletins()).singleElement().satisfies(b -> {
            assertThat(b.feuilles()).containsExactly(ids.get("f_P1"));
            assertThat(b.heuresRegulieres()).isPositive();
            assertThat(b.retenues().net()).isPositive().isLessThan(b.retenues().brut());
        });
        assertThat(cycles.traiter(trace, p.cycle().id()).etat()).isEqualTo(EtatCycle.TRAITE);
        assertThat(lecturePaie.mesBulletins(T, JULIE.userId())).hasSize(1);
        return p.cycle().id();
    }

    @Test
    @DisplayName("le module coupé ferme tout")
    void moduleCoupe() throws Exception {
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.get(TP + "/moi", JULIE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs_temps' non activé pour cet organisme"));
    }
}
