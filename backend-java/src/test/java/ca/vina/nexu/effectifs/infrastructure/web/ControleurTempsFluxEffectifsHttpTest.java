package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.QualificationPointages;
import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotProfilsPointage;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireProfilsPointage;
import ca.vina.nexu.effectifs.application.requetes.LectureAuditLocalisations;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GenerationSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDecisionsPresence;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
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
import ca.vina.nexu.effectifs.application.requetes.FeuilleDetailleeTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
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
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobationsPort;
import ca.vina.nexu.effectifs.domaine.ModificationHeures;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
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

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Feuilles de temps — règles de modification, flux d'approbation, sources multiples</strong> (E-GP-3, E-GP-6, E-RF-13),
 * jumeau de {@code effectifs-temps-flux.integration.test.ts}. Semaine du 10 mars 2031 (heure avancée : 6 h à Montréal = 10 h
 * UTC). Julie a pointé le 11 ; le 12 et le 13, la borne et le RAO l'ont vue — d'accord le 12, en désaccord le 13. Marc a
 * pointé un quart de 12 heures ; Léa, un quart de 4 heures avec un retard. Les cas du Node s'enchaînent : un seul récit.
 */
@DisplayName("/api/effectifs/temps — modification restreinte par règles, flux d'approbation, corrélation de sources")
class ControleurTempsFluxEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String PL = E + "/planification";
    private static final String TP = E + "/temps";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_presence\",\"effectifs_temps\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final Map<String, String> ROLES = Map.of("julie", "employee", "marc", "employee", "lea", "employee", "chef", "manager", "chef2", "manager",
            "rh", "manager", "dir", "director");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnairePointagesEffectifs pointages;
    private GestionnaireSegmentsTemps segments;
    private FluxEssai.Cablage flux;
    private final Map<String, String> ids = new HashMap<>();

    private static ContexteRequete compte(String nom) {
        return new ContexteRequete("usr-" + nom, nom, ROLES.get(nom), null, T);
    }

    private static Contexte ctx(String qui, String maintenant) {
        return new Contexte(T, "usr-" + qui, ROLES.get(qui), maintenant);
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

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (String nom : List.of("julie", "marc", "lea", "chef", "chef2", "rh", "dir")) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', ?, 'x')")
                    .params("usr-" + nom, nom, T, ROLES.get(nom)).update();
        }
        cabler();
        socle();
        planifier();
        pointerEtImporter();
    }

    private void cabler() {
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
        Sequence seq = sequence();
        Clock horloge = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC);
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
        flux = FluxEssai.cabler(new FluxEssai.Socle(jdbc, horloge, perimetres, evaluateur, journal));

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
        LectureProfilsPointage profils = new LectureProfilsPointage(new DepotProfilsPointage(jdbc, schemaPresence), evaluateur, lectRes);
        pointages = new GestionnairePointagesEffectifs(new GestionnairePointagesEffectifs.Lectures(presence, quarts, lectRes, perimetres, milieux, depotMilieux,
                new QualificationPointages(profils, referentiels)),
                new GestionnairePointagesEffectifs.Ecritures(depotPresence, evaluateur, journal, seq), flux.approbation());
        LecturePointagesADecider aDecider = new LecturePointagesADecider(presence, flux.lecture());
        ControleurPresenceEffectifs presenceHttp = new ControleurPresenceEffectifs(garde,
                new ControleurPresenceEffectifs.Lectures(presence, profils, new LectureAuditLocalisations(presence)), aDecider,
                new ControleurPresenceEffectifs.Gestes(pointages,
                        new GestionnaireDecisionsPresence(aDecider, lectRes, flux.flux(), new GestionnaireDecisionsPresence.Ecritures(depotPresence, journal)),
                        new GestionnaireHorodatagesExternes(new GestionnaireHorodatagesExternes.Lectures(depotPresence, lectRes, perimetres), depotPresence, journal, seq),
                        new GestionnaireProfilsPointage(profils, new DepotProfilsPointage(jdbc, schemaPresence), journal)));

        DepotFeuillesTemps depot = new DepotFeuillesTemps(jdbc, new SchemaTempsEffectifs(jdbc), schemaPresence, schemaPlanif);
        LectureFeuillesTemps lectureTemps = new LectureFeuillesTemps(depot, lectRes, perimetres, referentiels);
        SourcesFeuillesTemps sources = new SourcesFeuillesTemps(depot, quarts);
        CalculFeuillesTemps calcul = new CalculFeuillesTemps(lectureTemps, sources, evaluateur, flux.lecture());
        segments = new GestionnaireSegmentsTemps(lectureTemps, calcul, flux.restriction(), new GestionnaireSegmentsTemps.Ecritures(depot, journal, seq));
        ControleurTempsEffectifs temps = new ControleurTempsEffectifs(garde, new ControleurTempsEffectifs.Lectures(lectureTemps, calcul,
                new RapportsTemps(lectureTemps, calcul), flux.lecture()),
                new ControleurTempsEffectifs.Gestes(new GestionnaireParametresTemps(lectureTemps, depot, journal),
                        new GestionnairePreparationTemps(new GestionnairePreparationTemps.Lectures(lectureTemps, calcul, new GenerationSegmentsTemps(lectureTemps, sources),
                                perimetres), new GestionnairePreparationTemps.Ecritures(depot, journal, seq)),
                        segments, new GestionnaireApprobationTemps(lectureTemps, calcul, flux.flux(), new GestionnaireApprobationTemps.Ecritures(depot, journal, seq)),
                        new ExportPaieTemps(lectureTemps, journal), flux.flux()));
        http = new ClientHttpEssai(socle, regles, planification, presenceHttp, temps);
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
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("types_heure", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"TRAVAILLEE\",\"attributs\":{\"codePaie\":\"100\"}}");
        ref("types_heure", "{\"code\":\"HS\",\"libelleFr\":\"Heures supplémentaires\",\"comportement\":\"SUPPLEMENTAIRE\",\"attributs\":{\"codePaie\":\"150\"}}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        for (String[] r : new String[][]{{"P1", "Julie", "julie"}, {"P2", "Marc", "marc"}, {"P3", "Léa", "lea"}}) {
            ids.put(r[0], creer(PL + "/ressources", "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1]
                    + "\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-" + r[2] + "\"}"));
        }
        // Une personne sans compte : c'est son planificateur qui soumet sa feuille.
        ids.put("P4", creer(PL + "/ressources", "{\"matricule\":\"P4\",\"nom\":\"Sans Compte\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\"}"));
        for (String u : List.of("chef", "chef2")) {
            http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-" + u + "\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                    .andExpect(status().isCreated());
        }
    }

    private void planifier() throws Exception {
        ids.put("periode", creer(PL + "/periodes", "{\"nom\":\"Semaine 11\",\"uniteCode\":\"EST\",\"debut\":\"2031-03-10\",\"fin\":\"2031-03-16\"}"));
        http.post(PL + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        for (Object[] q : new Object[][]{{"2031-03-11", "P1", 360, 1080}, {"2031-03-12", "P1", 360, 1080}, {"2031-03-13", "P1", 360, 1080},
                {"2031-03-11", "P2", 360, 1080}, {"2031-03-11", "P3", 480, 720}}) {
            String e = corps(http.post(PL + "/equipages", ADMIN, "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + q[0] + "\",\"debut\":" + q[2]
                    + ",\"fin\":" + q[3] + ",\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\","
                    + "\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}").andExpect(status().isCreated()));
            http.post(PL + "/places/" + JsonPath.read(e, "$.places[0].id") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get((String) q[1]) + "\"}")
                    .andExpect(status().isOk());
        }
        http.post(PL + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
        regle("POINTAGE", "retard", "{\"fait\":\"ecartDebutMinutes\",\"op\":\">\",\"valeur\":10}", "EXIGER_APPROBATION", "retard de {ecartDebutMinutes} min");
    }

    private void importer(String source, String[][] lignes) throws Exception {
        StringBuilder l = new StringBuilder();
        for (String[] x : lignes) {
            l.append(l.isEmpty() ? "" : ",").append("{\"cle\":\"").append(x[0]).append("\",\"matricule\":\"P1\",\"sens\":\"").append(x[1])
                    .append("\",\"horodatage\":\"").append(x[2]).append("\"}");
        }
        http.post(E + "/presence/horodatages", ADMIN, "{\"source\":\"" + source + "\",\"lignes\":[" + l + "]}").andExpect(status().isCreated());
    }

    /** Les pointages : Julie le 11, Marc le 11 (12 heures), Léa le 11 avec 12 minutes de retard ; puis les horodatages tiers de Julie. */
    private void pointerEtImporter() throws Exception {
        pointages.pointer(ctx("julie", "2031-03-11T10:00:00.000Z"), Map.of("sens", "ENTREE"), null);
        pointages.pointer(ctx("julie", "2031-03-11T22:00:00.000Z"), Map.of("sens", "SORTIE"), null);
        pointages.pointer(ctx("marc", "2031-03-11T10:00:00.000Z"), Map.of("sens", "ENTREE"), null);
        pointages.pointer(ctx("marc", "2031-03-11T22:00:00.000Z"), Map.of("sens", "SORTIE"), null);
        ids.put("retardLea", (String) pointages.pointer(ctx("lea", "2031-03-11T12:12:00.000Z"), Map.of("sens", "ENTREE"), null).get("id"));
        pointages.pointer(ctx("lea", "2031-03-11T16:00:00.000Z"), Map.of("sens", "SORTIE"), null);
        importer("BORNE", new String[][]{{"b1", "ENTREE", "2031-03-11T10:20:00Z"}, {"b2", "ENTREE", "2031-03-12T10:00:00Z"}, {"b3", "SORTIE", "2031-03-12T22:01:00Z"},
                {"b4", "ENTREE", "2031-03-13T11:15:00Z"}, {"b5", "SORTIE", "2031-03-13T22:00:00Z"}});
        importer("RAO", new String[][]{{"r1", "ENTREE", "2031-03-12T10:02:00Z"}, {"r2", "SORTIE", "2031-03-12T21:58:00Z"},
                {"r3", "ENTREE", "2031-03-13T10:00:00Z"}, {"r4", "SORTIE", "2031-03-13T22:00:00Z"}});
    }

    private String feuille(String mat, ContexteRequete qui) throws Exception {
        return corps(http.get(TP + "/feuilles/" + ids.get("f_" + mat), qui).andExpect(status().isOk()));
    }

    /** Les anomalies comme le Node les résume : « date CODE », suivi de « ! » quand elle bloque. */
    private static List<String> codes(String feuille) {
        List<Map<String, Object>> a = JsonPath.read(feuille, "$.anomalies");
        return a.stream().map(x -> x.get("date") + " " + x.get("code") + (Boolean.TRUE.equals(x.get("bloquante")) ? " !" : "")).toList();
    }

    @Test
    @DisplayName("le récit complet : sources tierces, paramètres, confirmation, flux configuré, paliers, pointages, restrictions par règles")
    void recit() throws Exception {
        sourcesTierces();
        corroborationParametree();
        confirmationEtCorrection();
        fluxConfigure();
        paliersDesFeuilles();
        fluxDesPointages();
        restrictionsParRegles();
        periodeFermee();
    }

    /**
     * <strong>Le supérieur hiérarchique</strong> (14 novembre 2026) : une règle qui ne nomme personne d'avance, résolue à la
     * soumission depuis l'organigramme. Julie relève de « rh » : sa feuille va à rh, et au niveau GESTION personne d'autre ne
     * la décide. Marc n'a pas de supérieur connu : sa feuille revient au niveau GESTION, jamais sans approbateur.
     */
    @Test
    @DisplayName("supérieur hiérarchique : la feuille va au gestionnaire de l'organigramme ; sans lui, au niveau GESTION de l'unité")
    void superieurHierarchique() throws Exception {
        for (String[] e : new String[][]{{"e-rh", null}, {"e-julie", "e-rh"}, {"e-marc", null}}) {
            jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, managerId) VALUES (?, 'x', 'y', ?, ?)").params(e[0], T, e[1]).update();
            jdbc.sql("UPDATE users SET employeeId = ? WHERE id = ?").params(e[0], "usr-" + e[0].substring(2)).update();
        }
        String superieur = "{\"type\":\"SUPERIEUR\"}";
        String r = corps(poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", superieur), ADMIN).andExpect(status().isCreated()));
        assertThat(r).contains("\"approbateur\":{\"type\":\"SUPERIEUR\",\"valeur\":\"direct\"}");
        http.get(TP + "/flux", compte("chef")).andExpect(jsonPath("$.regles[0].approbateur.type").value("SUPERIEUR"));
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\",\"debut\":\"2031-03-10\",\"fin\":\"2031-03-16\"}").andExpect(status().isCreated());
        List<Map<String, Object>> liste = JsonPath.read(corps(http.get(TP + "/feuilles?debut=2031-03-10", ADMIN)), "$");
        liste.forEach(f -> ids.put("f_" + f.get("matricule"), (String) f.get("id")));

        http.post(TP + "/feuilles/" + ids.get("f_P2") + "/soumettre", compte("marc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.flux.etapes[*].libelle", Matchers.contains("le niveau GESTION de l'unité")));
        // La feuille d'une ressource dont le compte relève de rh par l'organigramme : rh la porte nommément, le niveau GESTION non.
        jdbc.sql("UPDATE users SET employeeId = 'e-julie' WHERE id = 'usr-chef2'").update();
        jdbc.sql("UPDATE eff_ressources SET utilisateurId = 'usr-chef2' WHERE id = ?").param(ids.get("P4")).update();
        http.post(TP + "/feuilles/" + ids.get("f_P4") + "/soumettre", compte("chef2"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.flux.etapes[*].libelle", Matchers.contains("rh (personne désignée)")));
        http.post(decider("P4", "approuver"), compte("chef"), null).andExpect(status().isForbidden());
        http.post(decider("P4", "approuver"), compte("rh"), null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("APPROUVEE"));
    }

    /**
     * <strong>La suppléance</strong> (14 novembre 2026) : rh, supérieur de Marc, s'absente et délègue à la directrice, qui n'a
     * aucun périmètre sur l'unité. La feuille de Marc lui parvient par la seule délégation, l'écran de Marc le dit, elle la
     * décide pour le compte de rh. Une délégation qui ne commence que plus tard ne fait rien.
     */
    @Test
    @DisplayName("suppléance : la feuille va au délégataire du supérieur absent, l'écran le nomme, il décide pour son compte")
    void suppleance() throws Exception {
        for (String[] e : new String[][]{{"e-rh", null}, {"e-marc", "e-rh"}}) {
            jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, managerId) VALUES (?, 'x', 'y', ?, ?)").params(e[0], T, e[1]).update();
            jdbc.sql("UPDATE users SET employeeId = ? WHERE id = ?").params(e[0], "usr-" + e[0].substring(2)).update();
        }
        poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", "{\"type\":\"SUPERIEUR\"}"), ADMIN).andExpect(status().isCreated());
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\",\"debut\":\"2031-03-10\",\"fin\":\"2031-03-16\"}").andExpect(status().isCreated());
        List<Map<String, Object>> liste = JsonPath.read(corps(http.get(TP + "/feuilles?debut=2031-03-10", ADMIN)), "$");
        liste.forEach(f -> ids.put("f_" + f.get("matricule"), (String) f.get("id")));
        String feuilleMarc = TP + "/feuilles/" + ids.get("f_P2");
        http.post(feuilleMarc + "/soumettre", compte("marc"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.flux.etapes[*].libelle", Matchers.contains("rh (personne désignée)")));

        // Une absence à venir : rien ne change encore.
        flux.moteur().deleguer(T, new Approbations.Delegation("usr-rh", "usr-dir", "2099-01-01", null));
        http.get(TP + "/feuilles?statut=SOUMISE", compte("dir")).andExpect(jsonPath("$[*].id", Matchers.not(Matchers.hasItem(ids.get("f_P2")))));
        http.get(feuilleMarc, compte("dir")).andExpect(status().isNotFound());

        // L'absence commence : la directrice reçoit la feuille, et Marc sait vers qui elle est partie.
        flux.moteur().deleguer(T, new Approbations.Delegation("usr-rh", "usr-dir", null, "2099-12-31"));
        http.get(feuilleMarc, compte("marc")).andExpect(jsonPath("$.flux.etapes[0].libelle")
                .value("rh (personne désignée) — en son absence : dir (jusqu'au 2099-12-31)"));
        http.get(TP + "/feuilles?statut=SOUMISE", compte("dir")).andExpect(jsonPath("$[*].id", Matchers.hasItem(ids.get("f_P2"))));
        http.get(TP + "/feuilles?statut=SOUMISE", compte("chef2")).andExpect(jsonPath("$[*].id", Matchers.hasItem(ids.get("f_P2"))));
        http.post(feuilleMarc + "/approuver", compte("chef2"), null).andExpect(status().isForbidden());
        http.post(feuilleMarc + "/approuver", compte("dir"), null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.flux.etapes[0].decidePar").value("usr-dir")).andExpect(jsonPath("$.flux.etapes[0].pourLeCompteDe").value("usr-rh"))
                .andExpect(jsonPath("$.flux.etapes[0].libelle").value("rh (personne désignée)"));
    }

    /** Sans pointage, une source tierce propose le temps, à confirmer ; deux sources se corrèlent entre elles (E-RF-13). */
    private void sourcesTierces() throws Exception {
        http.post(TP + "/feuilles/preparer", ADMIN, "{\"uniteCode\":\"EST\",\"debut\":\"2031-03-10\",\"fin\":\"2031-03-16\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.creees", Matchers.hasSize(4)));
        List<Map<String, Object>> liste = JsonPath.read(corps(http.get(TP + "/feuilles?debut=2031-03-10", ADMIN)), "$");
        liste.forEach(f -> ids.put("f_" + f.get("matricule"), (String) f.get("id")));
        String f = feuille("P1", compte("julie"));
        List<Map<String, Object>> s = JsonPath.read(f, "$.segments");
        assertThat(s.stream().map(x -> java.util.Arrays.asList(x.get("date"), x.get("source"), x.get("sourceExterne"), x.get("debut"), x.get("fin"), x.get("aConfirmer"),
                x.get("corrobore"))).toList()).containsExactly(
                java.util.Arrays.asList("2031-03-11", "POINTAGE", null, "2031-03-11T10:00:00.000Z", "2031-03-11T22:00:00.000Z", false, false),
                java.util.Arrays.asList("2031-03-12", "EXTERNE", "BORNE", "2031-03-12T10:00:00.000Z", "2031-03-12T22:00:00.000Z", true, null),
                java.util.Arrays.asList("2031-03-13", "EXTERNE", "BORNE", "2031-03-13T11:15:00.000Z", "2031-03-13T22:00:00.000Z", true, null));
        assertThat(corps(http.get(TP + "/feuilles/" + ids.get("f_P1"), ADMIN))).contains("\"correlation\":[{\"date\":\"2031-03-12\",\"sources\":[\"BORNE\",\"RAO\"],"
                + "\"concordants\":2,\"divergents\":0},{\"date\":\"2031-03-13\",\"sources\":[\"BORNE\",\"RAO\"],\"concordants\":1,\"divergents\":2}],\"flux\":null}");
        assertThat(codes(f)).containsExactly("2031-03-11 NON_CORROBORE", "2031-03-12 EXTERNE_A_CONFIRMER !", "2031-03-12 SANS_POINTAGE",
                "2031-03-13 ECART_PLANIFIE", "2031-03-13 EXTERNE_A_CONFIRMER !", "2031-03-13 SANS_POINTAGE", "2031-03-13 SOURCES_DIVERGENTES !");
        assertThat(JsonPath.<List<String>>read(f, "$.anomalies[?(@.code == 'EXTERNE_A_CONFIRMER')].message").getFirst())
                .isEqualTo("le 2031-03-12 : temps proposé depuis l'horodatage de « BORNE » seul, sans pointage — confirmez-le ou corrigez-le avant de soumettre");
        http.post(TP + "/feuilles/" + ids.get("f_P1") + "/soumettre", compte("julie"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("confirmez-le ou corrigez-le avant de soumettre")));
    }

    private List<String> corroboration() throws Exception {
        return codes(feuille("P1", ADMIN)).stream().filter(c -> c.matches(".*(CORROBORE|DIVERGENTES).*")).toList();
    }

    /** La corroboration se paramètre : tolérance, sort d'un pointage non confirmé, de deux sources en désaccord (E-RF-13). */
    private void corroborationParametree() throws Exception {
        String p = TP + "/parametres";
        http.put(p, ADMIN, "{\"actionNonCorrobore\":\"PEUT-ETRE\"}").andExpect(jsonPath("$.errors[0]").value("« actionNonCorrobore » : IGNORER, SIGNALER, BLOQUER"));
        http.put(p, ADMIN, "{\"toleranceCorroborationMinutes\":90}").andExpect(jsonPath("$.errors[0]").value("« toleranceCorroborationMinutes » : entier de 0 à 60"));
        http.put(p, compte("chef"), "{\"actionNonCorrobore\":\"BLOQUER\"}").andExpect(status().isForbidden());
        http.put(p, ADMIN, "{\"actionNonCorrobore\":\"BLOQUER\",\"actionSourcesDivergentes\":\"SIGNALER\"}").andExpect(status().isOk());
        assertThat(corroboration()).containsExactly("2031-03-11 NON_CORROBORE !", "2031-03-13 SOURCES_DIVERGENTES");
        http.put(p, ADMIN, "{\"actionNonCorrobore\":\"IGNORER\",\"actionSourcesDivergentes\":\"IGNORER\"}").andExpect(status().isOk());
        assertThat(corroboration()).isEmpty();
        // Une tolérance de 30 minutes : la borne confirme l'entrée du 11 ; à 0, plus rien ne concorde le 12.
        http.put(p, ADMIN, "{\"actionNonCorrobore\":\"SIGNALER\",\"actionSourcesDivergentes\":\"SIGNALER\",\"toleranceCorroborationMinutes\":30}").andExpect(status().isOk());
        String large = feuille("P1", ADMIN);
        assertThat(List.of(JsonPath.read(large, "$.segments[0].corrobore"), JsonPath.read(large, "$.segments[0].sourcesCorroborantes")))
                .containsExactly(true, List.of("BORNE"));
        assertThat(codes(large).stream().filter(c -> c.contains("CORROBORE")).toList()).isEmpty();
        http.put(p, ADMIN, "{\"toleranceCorroborationMinutes\":0}").andExpect(status().isOk());
        assertThat(JsonPath.<Map<String, Object>>read(feuille("P1", ADMIN), "$.correlation[0]"))
                .isEqualTo(Map.of("date", "2031-03-12", "sources", List.of("BORNE", "RAO"), "concordants", 0, "divergents", 4));
        http.put(p, ADMIN, "{\"toleranceCorroborationMinutes\":5}").andExpect(jsonPath("$.toleranceCorroborationMinutes").value(5))
                .andExpect(jsonPath("$.actionNonCorrobore").value("SIGNALER")).andExpect(jsonPath("$.actionSourcesDivergentes").value("SIGNALER"));
    }

    /** Un temps proposé se confirme ou se corrige ; corrigé, il est confirmé (E-RF-13). */
    private void confirmationEtCorrection() throws Exception {
        String s = TP + "/feuilles/" + ids.get("f_P1") + "/segments";
        List<String> segs = JsonPath.read(feuille("P1", ADMIN), "$.segments[*].id");
        http.post(s + "/" + segs.get(0) + "/confirmer", compte("julie"), null).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("seul un temps tiré d'un horodatage externe se confirme"));
        http.post(s + "/" + segs.get(1) + "/confirmer", compte("marc"), null).andExpect(status().isNotFound());
        http.post(s + "/" + segs.get(1) + "/confirmer", compte("julie"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.segments[1].aConfirmer").value(false)).andExpect(jsonPath("$.segments[1].confirmePar").value("usr-julie"));
        http.post(s + "/" + segs.get(1) + "/confirmer", compte("julie"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce temps est déjà confirmé"));
        String corrige = corps(http.put(s + "/" + segs.get(2), compte("julie"), "{\"debut\":\"2031-03-13T10:00:00.000Z\",\"motif\":\"Entrée réelle à 6 h, selon le RAO\"}")
                .andExpect(status().isOk()));
        Map<String, Object> v2 = JsonPath.<List<Map<String, Object>>>read(corrige, "$.segments[?(@.remplaceId == '" + segs.get(2) + "')]").getFirst();
        assertThat(java.util.Arrays.asList(v2.get("source"), v2.get("sourceExterne"), v2.get("version"), v2.get("aConfirmer"), v2.get("confirmePar")))
                .containsExactly("EXTERNE", "BORNE", 2, false, "usr-julie");
        assertThat(codes(corrige).stream().filter(c -> c.endsWith("!")).toList()).isEmpty();
        http.get(E + "/journal?action=temps.segment.confirme", ADMIN).andExpect(jsonPath("$[0].objetId").value(segs.get(1)));
        http.get(s + "/" + v2.get("id") + "/historique", compte("julie")).andExpect(jsonPath("$[0].sourceExterne").value("BORNE"))
                .andExpect(jsonPath("$[0].confirmePar").value("usr-julie"));
    }

    private ResultActions poser(String json, ContexteRequete qui) throws Exception {
        return http.post(TP + "/flux", qui, json);
    }

    private static String regleFlux(String objet, int palier, String champ, String operateur, String valeur, String approbateur) {
        return "{\"objet\":\"" + objet + "\",\"palier\":" + palier + ",\"champ\":\"" + champ + "\",\"operateur\":\"" + operateur + "\",\"valeur\":" + valeur
                + ",\"approbateur\":" + approbateur + "}";
    }

    /** Le flux d'approbation se configure : paliers, approbateurs, conditions de routage (E-GP-6). */
    private void fluxConfigure() throws Exception {
        String gestion = "{\"type\":\"PERIMETRE\",\"valeur\":\"GESTION\"}";
        String directeur = "{\"type\":\"ROLE\",\"valeur\":\"director\"}";
        String rh = "{\"type\":\"PERSONNE\",\"valeur\":\"usr-rh\"}";
        assertThat(corps(http.get(TP + "/flux", compte("chef")).andExpect(status().isOk()))).startsWith("{\"regles\":[],\"champs\":{\"FEUILLE\":[{\"code\":\"toujours\","
                + "\"libelle\":\"Toujours (1)\",\"type\":\"nombre\"}").endsWith("\"parDefaut\":{\"FEUILLE\":true,\"POINTAGE\":true}}");
        poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", gestion), compte("chef")).andExpect(status().isForbidden());
        poser(regleFlux("FEUILLE", 1, "couleur", "eq", "1", directeur), ADMIN).andExpect(jsonPath("$.errors[0]").value("« champ » : toujours, heuresDeclarees, "
                + "heuresSupplementaires, anomalies, anomaliesNonCorroborees, segmentsExternes, segmentsCorriges, ecartPlanifieMaxMinutes, uniteCode"));
        poser(regleFlux("FEUILLE", 1, "uniteCode", "gte", "\"EST\"", directeur), ADMIN).andExpect(jsonPath("$.errors[0]").value("« uniteCode » se compare par eq ou neq"));
        poser(regleFlux("FEUILLE", 11, "toujours", "eq", "1", directeur), ADMIN).andExpect(jsonPath("$.errors[0]").value("« palier » : entier de 1 à 10"));
        poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", "{\"type\":\"PERSONNE\",\"valeur\":\"inconnu\"}"), ADMIN)
                .andExpect(jsonPath("$.errors[0]").value("« approbateur.valeur » : utilisateur inconnu dans cet établissement"));
        poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", "{\"type\":\"PERIMETRE\",\"valeur\":\"TOUT\"}"), ADMIN)
                .andExpect(jsonPath("$.errors[0]").value("« approbateur.valeur » : LECTURE, PLANIFICATION, GESTION"));
        String r1 = corps(poser(regleFlux("FEUILLE", 1, "toujours", "eq", "1", gestion), ADMIN).andExpect(status().isCreated()));
        assertThat(r1).startsWith("{\"objet\":\"FEUILLE\",\"palier\":1,\"champ\":\"toujours\",\"operateur\":\"eq\",\"valeur\":1,"
                + "\"approbateur\":{\"type\":\"PERIMETRE\",\"valeur\":\"GESTION\"},\"id\":\"");
        poser(regleFlux("FEUILLE", 2, "heuresSupplementaires", "gte", "4", rh), ADMIN).andExpect(status().isCreated());
        poser(regleFlux("FEUILLE", 3, "anomalies", "gte", "1", directeur), ADMIN).andExpect(status().isCreated());
        poser(regleFlux("POINTAGE", 1, "toujours", "eq", "1", gestion), ADMIN).andExpect(status().isCreated());
        poser(regleFlux("POINTAGE", 2, "saisieAPosteriori", "eq", "1", rh), ADMIN).andExpect(status().isCreated());
        String jetee = JsonPath.read(corps(poser(regleFlux("POINTAGE", 3, "uniteCode", "eq", "\"OUEST\"", directeur), ADMIN).andExpect(status().isCreated())), "$.id");
        assertThat(corps(http.delete(TP + "/flux/" + jetee, ADMIN).andExpect(status().isOk()))).isEqualTo("{\"retiree\":\"" + jetee + "\"}");
        http.delete(TP + "/flux/" + jetee, ADMIN).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("règle de flux « " + jetee + " » introuvable"));
        String lu = corps(http.get(TP + "/flux", compte("chef")));
        List<Map<String, Object>> regles = JsonPath.read(lu, "$.regles");
        assertThat(regles.stream().map(r -> List.of(r.get("objet"), r.get("palier"), r.get("champ"), r.get("operateur"), r.get("valeur"),
                ((Map<?, ?>) r.get("approbateur")).get("type"))).toList()).containsExactly(List.of("FEUILLE", 1, "toujours", "eq", 1, "PERIMETRE"),
                List.of("FEUILLE", 2, "heuresSupplementaires", "gte", 4, "PERSONNE"), List.of("FEUILLE", 3, "anomalies", "gte", 1, "ROLE"),
                List.of("POINTAGE", 1, "toujours", "eq", 1, "PERIMETRE"), List.of("POINTAGE", 2, "saisieAPosteriori", "eq", 1, "PERSONNE"));
        assertThat(JsonPath.<Map<String, Object>>read(lu, "$.parDefaut")).isEqualTo(Map.of("FEUILLE", false, "POINTAGE", false));
        assertThat(corps(http.get(TP + "/flux", compte("julie")))).contains("errors");
        http.get(E + "/journal?action=flux.regle.retiree", ADMIN).andExpect(jsonPath("$[0].objetId").value(jetee));
        assertThatThrownBy(() -> flux.moteur().retirerRegle(T, jetee)).hasMessage("approval rule not found");
    }

    private String decider(String mat, String geste) {
        return TP + "/feuilles/" + ids.get("f_" + mat) + "/" + geste;
    }

    /** Chaque feuille suit les paliers que ses conditions lui donnent, dans l'ordre, par qui les porte (E-GP-6). */
    private void paliersDesFeuilles() throws Exception {
        http.post(TP + "/feuilles/" + ids.get("f_P1") + "/soumettre", compte("julie"), null).andExpect(status().isOk()).andExpect(jsonPath("$.paliersRequis").value(3))
                .andExpect(jsonPath("$.flux.etapes[*].libelle", Matchers.contains("le niveau GESTION de l'unité", "rh (personne désignée)", "le rôle « director »")));
        http.post(TP + "/feuilles/" + ids.get("f_P2") + "/soumettre", compte("marc"), null).andExpect(jsonPath("$.paliersRequis").value(2));
        http.post(decider("P1", "approuver"), compte("dir"), null).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("feuille « " + ids.get("f_P1") + " » introuvable"));
        assertThat(corps(http.get(TP + "/feuilles?statut=SOUMISE", compte("rh")))).isEqualTo("[]");
        http.post(decider("P1", "approuver"), compte("chef"), null).andExpect(jsonPath("$.statut").value("SOUMISE")).andExpect(jsonPath("$.palier").value(2))
                .andExpect(jsonPath("$.flux.palierCourant").value(2));
        http.get(TP + "/feuilles?statut=SOUMISE", compte("rh")).andExpect(jsonPath("$[*].id", Matchers.contains(ids.get("f_P1"))));
        http.get(TP + "/feuilles/" + ids.get("f_P1"), compte("rh")).andExpect(status().isOk()).andExpect(jsonPath("$.flux.palierCourant").value(2));
        http.post(decider("P1", "approuver"), compte("chef2"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le palier 2 est porté par rh (personne désignée) : vous ne le portez pas"));
        http.post(decider("P1", "approuver"), compte("rh"), null).andExpect(jsonPath("$.statut").value("SOUMISE")).andExpect(jsonPath("$.palier").value(3));
        http.post(decider("P1", "approuver"), compte("dir"), null).andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.decisions[*].palier", Matchers.contains(1, 2, 3)))
                .andExpect(jsonPath("$.flux.etapes[*].statut", Matchers.contains("APPROUVEE", "APPROUVEE", "APPROUVEE")))
                .andExpect(jsonPath("$.flux.etapes[*].decidePar", Matchers.contains("usr-chef", "usr-rh", "usr-dir")));
        http.post(decider("P1", "approuver"), compte("chef"), null).andExpect(jsonPath("$.errors[0]").value("feuille déjà approuvée : il n'y a plus rien à décider"));
        // Marc : un retour rouvre la feuille, la resoumission rouvre un flux.
        http.post(decider("P2", "approuver"), compte("chef"), null).andExpect(status().isOk());
        http.post(decider("P2", "retourner"), compte("rh"), "{\"motif\":\"Justifiez les 4 heures supplémentaires\"}").andExpect(jsonPath("$.statut").value("RETOURNEE"))
                .andExpect(jsonPath("$.flux.statut").value("REJETE"));
        http.post(TP + "/feuilles/" + ids.get("f_P2") + "/soumettre", compte("marc"), null).andExpect(jsonPath("$.palier").value(1))
                .andExpect(jsonPath("$.flux.statut").value("EN_COURS")).andExpect(jsonPath("$.flux.etapes[*].statut", Matchers.contains("EN_ATTENTE", "EN_ATTENTE")));
        // L'administration porte tous les paliers, mais la séparation des tâches du moteur lui interdit d'en décider deux.
        http.post(decider("P2", "approuver"), ADMIN, null).andExpect(status().isOk());
        http.post(decider("P2", "retourner"), ADMIN, "{\"motif\":\"Un second palier\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("segregation of duties: an approver cannot decide more than one level of the same request"));
        // La délégation du moteur générique vaut ici aussi : rh délègue à chef2, qui décide « pour le compte de » rh.
        flux.moteur().deleguer(T, new Approbations.Delegation("usr-rh", "usr-chef2", null, null));
        http.post(decider("P2", "approuver"), compte("chef2"), null).andExpect(jsonPath("$.statut").value("APPROUVEE"))
                .andExpect(jsonPath("$.flux.etapes[1].pourLeCompteDe").value("usr-rh"));
        // Qui a soumis ne décide pas : le planificateur qui soumet la feuille d'une personne sans compte.
        http.post(TP + "/feuilles/" + ids.get("f_P4") + "/soumettre", compte("chef"), null).andExpect(jsonPath("$.paliersRequis").value(1));
        http.post(decider("P4", "approuver"), compte("chef"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("qui a soumis ne décide pas de cette approbation : un autre approbateur doit trancher"));
        http.post(decider("P4", "approuver"), compte("chef2"), null).andExpect(jsonPath("$.statut").value("APPROUVEE"));
        // Une étape déjà décidée ne se redécide pas : le moteur le refuse, avec son motif.
        String etape = flux.lecture().fluxDe(T, FluxApprobation.ObjetFlux.FEUILLE, ids.get("f_P4")).orElseThrow().etapes().getFirst().id();
        assertThatThrownBy(() -> flux.lecture().moteur().decider(T, etape, new FluxApprobationsPort.DecisionEtape(DemandesFlux.StatutApprobation.APPROUVE,
                "usr-dir", "director", a -> true))).isInstanceOf(DemandesFlux.EtapeRefuseeException.class).hasMessage("step already decided");
    }

    /** Un pointage en attente suit le flux des pointages ; un retard n'a qu'un palier, une saisie après coup en a deux (E-GP-6). */
    private void fluxDesPointages() throws Exception {
        String pt = E + "/presence/pointages";
        http.get(pt + "/" + ids.get("retardLea"), compte("chef")).andExpect(jsonPath("$.statut").value("A_APPROUVER"))
                .andExpect(jsonPath("$.flux.etapes", Matchers.hasSize(1)));
        List<Map<String, Object>> saisie = pointages.saisirAPosteriori(ctx("julie", "2031-03-15T20:00:00.000Z"), Map.of("justification", "Formation à la caserne",
                "segments", List.of(Map.of("debut", "2031-03-15T13:00:00.000Z", "fin", "2031-03-15T17:00:00.000Z", "type", "TRAVAIL"))));
        String id = (String) saisie.getFirst().get("id");
        // rh a délégué à chef2 sans date de fin (paliers des feuilles, plus haut) : l'étape qui l'attend le dit.
        http.get(pt + "/" + id, compte("chef")).andExpect(jsonPath("$.flux.etapes[*].libelle", Matchers.contains("le niveau GESTION de l'unité",
                "rh (personne désignée) — en son absence : chef2")));
        assertThat(corps(http.get(pt + "?statut=A_APPROUVER", compte("rh")))).isEqualTo("[]");
        http.post(pt + "/" + id + "/approuver", compte("chef"), null).andExpect(jsonPath("$[*].statut", Matchers.contains("A_APPROUVER", "A_APPROUVER")));
        http.post(pt + "/" + id + "/approuver", compte("chef"), null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous avez déjà approuvé un palier de ce pointage : le palier suivant revient à une autre personne"));
        http.get(pt + "?statut=A_APPROUVER", compte("rh")).andExpect(jsonPath("$[*].id", Matchers.containsInAnyOrder(saisie.stream().map(p -> p.get("id")).toArray())));
        http.get(pt + "/" + id, compte("rh")).andExpect(status().isOk());
        http.post(pt + "/" + id + "/approuver", compte("rh"), null).andExpect(jsonPath("$[*].statut", Matchers.contains("APPROUVE", "APPROUVE")))
                .andExpect(jsonPath("$[*].decidePar", Matchers.contains("usr-rh", "usr-rh")));
        http.post(pt + "/" + ids.get("retardLea") + "/rejeter", compte("chef"), "{\"motif\":\"Retard non justifié\"}").andExpect(jsonPath("$[*].statut",
                Matchers.contains("REJETE")));
        assertThat(flux.lecture().fluxDe(T, FluxApprobation.ObjetFlux.POINTAGE, ids.get("retardLea")).map(DemandesFlux.Flux::statut))
                .contains(DemandesFlux.StatutFlux.REJETE);
        http.post(pt + "/" + id + "/approuver", compte("chef"), null).andExpect(status().isConflict());
    }

    /** La modification des heures se restreint par règles : délai après le quart, rôle, niveau, saisie tardive (E-GP-3). */
    private void restrictionsParRegles() throws Exception {
        regle("MODIFICATION_HEURES", "delai-employe", "{\"tous\":[{\"fait\":\"parAutrui\",\"op\":\"=\",\"valeur\":false},{\"fait\":\"roleActeur\",\"op\":\"=\","
                + "\"valeur\":\"employee\"},{\"fait\":\"heuresDepuisLeQuart\",\"op\":\">\",\"valeur\":72}]}", "BLOQUER",
                "au-delà de 72 h après le quart, seul votre planificateur corrige ({heuresDepuisLeQuart} h écoulées)");
        regle("MODIFICATION_HEURES", "retrait-gestion", "{\"tous\":[{\"fait\":\"operation\",\"op\":\"=\",\"valeur\":\"RETRAIT\"},{\"fait\":\"niveauActeur\","
                + "\"op\":\"hors\",\"valeur\":[\"GESTION\",\"ETABLISSEMENT\"]}]}", "BLOQUER", "seul le niveau GESTION retire un segment");
        regle("MODIFICATION_HEURES", "saisie-tardive", "{\"tous\":[{\"fait\":\"operation\",\"op\":\"=\",\"valeur\":\"SAISIE_A_POSTERIORI\"},"
                + "{\"fait\":\"heuresDepuisLeQuart\",\"op\":\">\",\"valeur\":48}]}", "BLOQUER", "une saisie après coup se fait dans les 48 heures ({heuresDepuisLeQuart} h)");
        String fP3 = ids.get("f_P3");
        FeuilleDetailleeTemps ajoute = segments.ajouter(ctx("lea", "2031-03-14T20:00:00.000Z"), fP3, Map.of("date", "2031-03-14",
                "debut", "2031-03-14T13:00:00.000Z", "fin", "2031-03-14T17:00:00.000Z", "typeHeureCode", "REG"));
        String saisi = ajoute.segments().stream().filter(x -> x.date().equals("2031-03-14")).findFirst().orElseThrow().id();
        // Quatre jours plus tard : l'employée ne corrige plus elle-même, son planificateur oui.
        assertThatThrownBy(() -> segments.corriger(ctx("lea", "2031-03-18T17:00:00.000Z"), fP3, saisi, Map.of("fin", "2031-03-14T16:00:00.000Z")))
                .isInstanceOf(RefusParRegleException.class)
                .hasMessage("modification des heures refusée : au-delà de 72 h après le quart, seul votre planificateur corrige (96 h écoulées)")
                .satisfies(e -> assertThat(((RefusParRegleException) e).verdict().motifs().getFirst().cle()).isEqualTo("delai-employe"));
        FeuilleDetailleeTemps corrige = segments.corriger(ctx("chef", "2031-03-18T17:00:00.000Z"), fP3, saisi, Map.of("fin", "2031-03-14T16:00:00.000Z",
                "motif", "Départ à 12 h, vérifié"));
        FeuilleDetailleeTemps.SegmentDetaille v2 = corrige.segments().stream().filter(x -> saisi.equals(x.remplaceId())).findFirst().orElseThrow();
        assertThat(v2.fin()).isEqualTo("2031-03-14T16:00:00.000Z");
        // Le retrait : le niveau de périmètre décide, pas la propriété de la feuille.
        assertThatThrownBy(() -> segments.retirer(ctx("lea", "2031-03-14T21:00:00.000Z"), fP3, v2.id(), Map.of()))
                .hasMessage("modification des heures refusée : seul le niveau GESTION retire un segment");
        assertThat(segments.retirer(ctx("chef", "2031-03-14T21:00:00.000Z"), fP3, v2.id(), Map.of("motif", "Saisi par erreur")).segments())
                .noneMatch(x -> x.id().equals(v2.id()));
        // La saisie de pointage après coup : dans les 48 heures.
        Map<String, Object> reunion = Map.of("justification", "Réunion non pointée", "segments",
                List.of(Map.of("debut", "2031-03-15T13:00:00.000Z", "fin", "2031-03-15T15:00:00.000Z", "type", "TRAVAIL")));
        assertThatThrownBy(() -> pointages.saisirAPosteriori(ctx("lea", "2031-03-18T12:00:00.000Z"), reunion))
                .hasMessage("modification des heures refusée : une saisie après coup se fait dans les 48 heures (69 h)");
        assertThat(pointages.saisirAPosteriori(ctx("lea", "2031-03-16T12:00:00.000Z"), reunion)).extracting(p -> p.get("statut").toString())
                .containsExactly("A_APPROUVER", "A_APPROUVER");
        // Chaque jugement est tracé avec ses faits, acceptation comprise.
        String traces = corps(http.get(E + "/regles/evaluations?objetType=modification-heures", ADMIN).andExpect(status().isOk()));
        assertThat(JsonPath.<List<Object>>read(traces, "$")).hasSizeGreaterThanOrEqualTo(6);
        Map<String, Object> attendus = new LinkedHashMap<>();
        attendus.put("operation", "CORRECTION");
        attendus.put("heuresDepuisLeQuart", 96);
        attendus.put("periodeEtat", "PUBLIEE");
        attendus.put("parAutrui", false);
        attendus.put("roleActeur", "employee");
        attendus.put("niveauActeur", "AUCUN");
        attendus.put("sourceSegment", "SAISIE");
        assertThat(JsonPath.<List<Map<String, Object>>>read(traces, "$[?(@.action == 'BLOQUER' && @.faits.operation == 'CORRECTION')].faits").getFirst())
                .isEqualTo(attendus);
    }

    /** Une période fermée ne se modifie plus, même par l'encadrement (E-GP-3). */
    private void periodeFermee() throws Exception {
        regle("MODIFICATION_HEURES", "periode-fermee", "{\"fait\":\"periodeEtat\",\"op\":\"=\",\"valeur\":\"CLOSE\"}", "BLOQUER",
                "la période de l'horaire est fermée : les heures ne se modifient plus");
        assertThat(List.of(flux.restriction().etatDeLaPeriode(T, "EST", "2031-03-12"), flux.restriction().etatDeLaPeriode(T, "EST", "2031-04-01")))
                .containsExactly("PUBLIEE", "AUCUNE");
        assertThat(List.of(flux.restriction().niveauDeLActeur(ctx("chef", "2031-03-12T00:00:00.000Z"), "EST"),
                flux.restriction().niveauDeLActeur(new Contexte(T, "usr-chef", "admin", "2031-03-12T00:00:00.000Z"), "EST")))
                .containsExactly(ModificationHeures.NiveauActeur.GESTION, ModificationHeures.NiveauActeur.ETABLISSEMENT);
        http.post(PL + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"CLOSE\"}").andExpect(status().isOk());
        http.post(TP + "/feuilles/" + ids.get("f_P3") + "/segments", compte("chef"), "{\"date\":\"2031-03-12\",\"debut\":\"2031-03-12T13:00:00Z\","
                + "\"fin\":\"2031-03-12T14:00:00Z\",\"typeHeureCode\":\"REG\",\"motif\":\"Réunion d'équipe\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("modification des heures refusée : la période de l'horaire est fermée : les heures ne se modifient plus"))
                .andExpect(jsonPath("$.verdict.motifs[0].cle").value("periode-fermee"));
        // Le catalogue du moteur expose le contexte et ses faits : c'est ce que l'écran des règles offre.
        http.get(E + "/regles/faits", ADMIN).andExpect(jsonPath("$.MODIFICATION_HEURES[*].code", Matchers.contains("operation", "heuresDepuisLeQuart", "periodeEtat",
                "parAutrui", "roleActeur", "niveauActeur", "sourceSegment")));
    }
}
