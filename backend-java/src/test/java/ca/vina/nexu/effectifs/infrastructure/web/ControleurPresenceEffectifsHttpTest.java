package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireProfilsPointage;
import ca.vina.nexu.effectifs.application.requetes.LectureAuditLocalisations;
import ca.vina.nexu.effectifs.application.commandes.QualificationPointages;
import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotProfilsPointage;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDecisionsPresence;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePointagesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRegles;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>La présence par HTTP</strong> (lot R13, jumeau de {@code effectifs-presence.integration.test.ts}) : mercredi
 * 15 janvier 2031, quart de jour 6 h – 18 h à la caserne 12 (présence préalable de 15 min). Julie pointe à la caserne,
 * avant l'heure : la présence préalable compte. Marc arrive en retard sans position : écart et règle, son pointage
 * attend l'approbation. Ana pointe à 10 km : la règle bloque. Les horodatages d'une borne corroborent ; un quart non
 * pointé se déclare après coup ; l'encadrement décide, jamais de ses propres pointages. L'horloge avance avec le récit.
 */
@DisplayName("/api/effectifs/presence — pointages, preuves, saisies, décisions, horodatages, code QR")
class ControleurPresenceEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String PL = E + "/planification";
    private static final String P = E + "/presence";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_presence\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = compte("julie", "employee");
    private static final ContexteRequete MARC = compte("marc", "employee");
    private static final ContexteRequete ANA = compte("ana", "employee");
    private static final ContexteRequete CHEF = compte("chef", "manager");
    private static final ContexteRequete LEA = compte("lea", "employee");
    private static final String A_LA_CASERNE = "\"latitude\":45.5,\"longitude\":-73.6,\"precisionMetres\":8";

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private final Horloge horloge = new Horloge();
    private DepotMilieux depotMilieux;
    private LecturePresenceEffectifs presence;
    private GestionnairePointagesEffectifs pointages;
    private GestionnaireHorodatagesExternes horodatages;
    private LectureQuartsPresence quarts;
    private final Map<String, String> ids = new HashMap<>();

    private static ContexteRequete compte(String nom, String role) {
        return new ContexteRequete("usr-" + nom, nom, role, null, T);
    }

    /** Une horloge que le récit fait avancer. */
    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse("2026-09-30T12:00:00Z");

        void a(String instant) {
            maintenant = Instant.parse(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
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
                return "secret-" + n.incrementAndGet();
            }
        };
    }

    private static Contexte ctx(String qui, String role, String maintenant) {
        return new Contexte(T, "usr-" + qui, role, maintenant);
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        for (ContexteRequete c : List.of(JULIE, MARC, ANA, CHEF, LEA)) {
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
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        depotMilieux = new DepotMilieux(jdbc, schema);
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

        DepotPresenceEffectifs depot = new DepotPresenceEffectifs(jdbc, new SchemaPresenceEffectifs(jdbc), schemaPlanif, schema);
        quarts = new LectureQuartsPresence(depot, referentiels);
        presence = new LecturePresenceEffectifs(depot, quarts, new LecturePresenceEffectifs.Socle(lectRes, perimetres, milieux, depotMilieux));
        FluxEssai.Cablage flux = FluxEssai.cabler(new FluxEssai.Socle(jdbc, horloge, perimetres, evaluateur, journal));
        DepotProfilsPointage depotProfils = new DepotProfilsPointage(jdbc, new SchemaPresenceEffectifs(jdbc));
        LectureProfilsPointage profils = new LectureProfilsPointage(depotProfils, evaluateur, lectRes);
        pointages = new GestionnairePointagesEffectifs(new GestionnairePointagesEffectifs.Lectures(presence, quarts, lectRes, perimetres, milieux, depotMilieux,
                new QualificationPointages(profils, referentiels)),
                new GestionnairePointagesEffectifs.Ecritures(depot, evaluateur, journal, seq), flux.approbation());
        LecturePointagesADecider aDecider = new LecturePointagesADecider(presence, flux.lecture());
        horodatages = new GestionnaireHorodatagesExternes(new GestionnaireHorodatagesExternes.Lectures(depot, lectRes, perimetres), depot, journal, seq);
        ControleurPresenceEffectifs controleur = new ControleurPresenceEffectifs(garde,
                new ControleurPresenceEffectifs.Lectures(presence, profils, new LectureAuditLocalisations(presence)), aDecider,
                new ControleurPresenceEffectifs.Gestes(pointages,
                        new GestionnaireDecisionsPresence(aDecider, lectRes, flux.flux(), new GestionnaireDecisionsPresence.Ecritures(depot, journal)), horodatages,
                        new GestionnaireProfilsPointage(profils, depotProfils, journal)));
        http = new ClientHttpEssai(socle, regles, planification, controleur);
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
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\",\"attributs\":{\"presencePrealableMinutes\":15}}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\","
                + "\"latitude\":45.5,\"longitude\":-73.6,\"rayonMetres\":100}"));
        http.put(E + "/milieux/" + ids.get("c12") + "/preuves", ADMIN,
                "{\"preuves\":[{\"mecanisme\":\"GEOREPERAGE\",\"exige\":true},{\"mecanisme\":\"CODE_QR_DE_LIEU\",\"exige\":false}]}").andExpect(status().isOk());
        ids.put("c7", creer(E + "/milieux", "{\"code\":\"C7\",\"libelle\":\"Caserne 7\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        http.put(E + "/milieux/" + ids.get("c7") + "/preuves", ADMIN, "{\"preuves\":[{\"mecanisme\":\"CODE_QR_DE_LIEU\",\"exige\":false}]}").andExpect(status().isOk());
        for (String[] r : new String[][]{{"P1", "Julie", "julie"}, {"P2", "Marc", "marc"}, {"P3", "Ana", "ana"}, {"M1", "Chef", "chef"}}) {
            ids.put(r[0], creer(PL + "/ressources", "{\"matricule\":\"" + r[0] + "\",\"nom\":\"" + r[1]
                    + "\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\",\"utilisateurId\":\"usr-" + r[2] + "\"}"));
        }
        regle("{\"contexte\":\"POINTAGE\",\"cle\":\"retard\",\"condition\":{\"fait\":\"ecartDebutMinutes\",\"op\":\">\",\"valeur\":10},"
                + "\"action\":\"EXIGER_APPROBATION\",\"message\":\"retard de {ecartDebutMinutes} min\",\"effectifDepuis\":\"2027-01-01\"}");
        regle("{\"contexte\":\"POINTAGE\",\"cle\":\"trop-loin\",\"condition\":{\"fait\":\"distanceMetres\",\"op\":\">\",\"valeur\":5000},"
                + "\"action\":\"BLOQUER\",\"message\":\"vous êtes à {distanceMetres} m de votre milieu\",\"effectifDepuis\":\"2027-01-01\"}");
    }

    private void regle(String json) throws Exception {
        http.post(E + "/regles", ADMIN, json).andExpect(status().isCreated());
    }

    private void planifier() throws Exception {
        String p = creer(PL + "/periodes", "{\"nom\":\"Semaine 3\",\"uniteCode\":\"EST\",\"debut\":\"2031-01-13\",\"fin\":\"2031-01-19\"}");
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        for (String mat : List.of("P1", "P2", "P3")) {
            String e = corps(http.post(PL + "/equipages", ADMIN, "{\"periodeId\":\"" + p + "\",\"date\":\"2031-01-15\",\"debut\":360,\"fin\":1080,"
                    + "\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\","
                    + "\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}").andExpect(status().isCreated()));
            ids.put("place_" + mat, JsonPath.read(e, "$.places[0].id"));
            http.post(PL + "/places/" + ids.get("place_" + mat) + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get(mat) + "\"}").andExpect(status().isOk());
        }
        http.post(PL + "/periodes/" + p + "/etat", ADMIN, "{\"etat\":\"PUBLIEE\"}").andExpect(status().isOk());
    }

    private ResultActions pointer(ContexteRequete qui, String quand, String json) throws Exception {
        horloge.a(quand);
        return http.post(P + "/pointages", qui, json);
    }

    private String pointageMarc() throws Exception {
        return JsonPath.read(corps(pointer(MARC, "2031-01-15T11:20:00.000Z", "{\"sens\":\"ENTREE\"}").andExpect(status().isCreated())), "$.id");
    }

    private String pointageAnaAuMauvaisMilieu() throws Exception {
        String secret = depotMilieux.secretQr(T, ids.get("c7")).orElseThrow();
        String code = PresenceEffectifs.codeQr(secret, ids.get("c7"), Instant.parse("2031-01-15T11:02:00.000Z").toEpochMilli());
        return JsonPath.read(corps(pointer(ANA, "2031-01-15T11:02:00.000Z", "{\"sens\":\"ENTREE\",\"codeQr\":\"" + code + "\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.resultat").value("ECART")).andExpect(jsonPath("$.ecart").value("MAUVAIS_MILIEU"))
                .andExpect(jsonPath("$.statut").value("A_APPROUVER"))), "$.id");
    }

    private void julieFaitSonQuart() throws Exception {
        pointer(JULIE, "2031-01-15T10:50:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + "}").andExpect(status().isCreated());
        String secret = depotMilieux.secretQr(T, ids.get("c12")).orElseThrow();
        for (String[] s : new String[][]{{"DEBUT_PAUSE", "2031-01-15T16:00:00.000Z"}, {"FIN_PAUSE", "2031-01-15T16:30:00.000Z"}, {"SORTIE", "2031-01-15T23:00:00.000Z"}}) {
            String code = PresenceEffectifs.codeQr(secret, ids.get("c12"), Instant.parse(s[1]).toEpochMilli());
            pointer(JULIE, s[1], "{\"sens\":\"" + s[0] + "\"," + A_LA_CASERNE + ",\"codeQr\":\"" + code + "\"}").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resultat").value("CONFORME")).andExpect(jsonPath("$.preuves[0].mecanisme").value("CODE_QR_DE_LIEU"))
                    .andExpect(jsonPath("$.preuves[0].satisfaite").value(true)).andExpect(jsonPath("$.preuves[0].detail").value("VALIDE"));
        }
    }

    private void perimetreDuChef() throws Exception {
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Julie pointe à la caserne avant l'heure : conforme, la présence préalable compte ; puis pause, reprise, sortie au code QR (E-GP-1, E-GP-9, E-GP-11)")
    void julie() throws Exception {
        pointer(JULIE, "2031-01-15T10:50:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.sens").value("ENTREE")).andExpect(jsonPath("$.resultat").value("CONFORME")).andExpect(jsonPath("$.statut").value("ENREGISTRE"))
                .andExpect(jsonPath("$.distanceMetres").value(0)).andExpect(jsonPath("$.placeId").value(ids.get("place_P1")))
                .andExpect(jsonPath("$.origine").value("APPAREIL")).andExpect(jsonPath("$.nom").value("Julie")).andExpect(jsonPath("$.ecart").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.actionRegles").value("ACCEPTER")).andExpect(jsonPath("$.motifsRegles", Matchers.hasSize(0)))
                .andExpect(jsonPath("$.preuves[*].mecanisme", Matchers.contains("CODE_QR_DE_LIEU", "GEOREPERAGE")))
                .andExpect(jsonPath("$.preuves[0].fournie").value(false)).andExpect(jsonPath("$.preuves[0].distanceMetres").doesNotExist())
                .andExpect(jsonPath("$.preuves[1].exigee").value(true)).andExpect(jsonPath("$.preuves[1].satisfaite").value(true))
                .andExpect(jsonPath("$.preuves[1].distanceMetres").value(0)).andExpect(jsonPath("$.corrobore").value(false));
        // Loi 25 : la position n'est conservée nulle part, seule la distance l'est.
        List<String> colonnes = jdbc.sql("SELECT name FROM pragma_table_info('eff_pointages') UNION ALL SELECT name FROM pragma_table_info('eff_pointage_preuves')")
                .query(String.class).list();
        assertThat(colonnes).isNotEmpty().noneMatch(c -> c.toLowerCase().matches(".*(lat|long).*"));
        horloge.a("2031-01-15T12:00:00.000Z");
        http.get(P + "/moi", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.ressource.id").value(ids.get("P1")))
                .andExpect(jsonPath("$.quart.prochains", Matchers.contains("DEBUT_PAUSE", "SORTIE"))).andExpect(jsonPath("$.quart.debutPrevu").value("2031-01-15T11:00:00.000Z"))
                .andExpect(jsonPath("$.quart.finPrevue").value("2031-01-15T23:00:00.000Z")).andExpect(jsonPath("$.quart.milieu").value("Caserne 12"))
                .andExpect(jsonPath("$.quart.presencePrealableMinutes").value(15)).andExpect(jsonPath("$.quart.minutesPayees").value(70))
                .andExpect(jsonPath("$.quart.segments[*].type", Matchers.contains("PRESENCE_PREALABLE", "TRAVAIL")))
                .andExpect(jsonPath("$.quart.segments[1].fin").value(Matchers.nullValue()));
        pointer(JULIE, "2031-01-15T12:00:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + "}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("après l'entrée, on ne peut pointer que le début de pause ou la sortie"));

        String secret = depotMilieux.secretQr(T, ids.get("c12")).orElseThrow();
        for (String[] s : new String[][]{{"DEBUT_PAUSE", "2031-01-15T16:00:00.000Z"}, {"FIN_PAUSE", "2031-01-15T16:30:00.000Z"}, {"SORTIE", "2031-01-15T23:00:00.000Z"}}) {
            String code = PresenceEffectifs.codeQr(secret, ids.get("c12"), Instant.parse(s[1]).toEpochMilli());
            pointer(JULIE, s[1], "{\"sens\":\"" + s[0] + "\"," + A_LA_CASERNE + ",\"codeQr\":\"" + code + "\"}").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.resultat").value("CONFORME")).andExpect(jsonPath("$.preuves[0].satisfaite").value(true));
        }
        horloge.a("2031-01-15T23:30:00.000Z");
        http.get(P + "/moi", JULIE).andExpect(jsonPath("$.quart.prochains", Matchers.hasSize(0))).andExpect(jsonPath("$.quart.minutesPayees").value(10 + 12 * 60 - 30))
                .andExpect(jsonPath("$.pointages", Matchers.hasSize(4)));
    }

    @Test
    @DisplayName("Marc en retard sans position : preuve absente et règle de retard, en attente (E-GP-4, P-GP-2) ; Ana à 10 km : bloquée, rien d'enregistré (E-GP-10) ; code d'un autre milieu")
    void ecarts() throws Exception {
        pointer(MARC, "2031-01-15T11:20:00.000Z", "{\"sens\":\"ENTREE\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.resultat").value("ECART")).andExpect(jsonPath("$.ecart").value("PREUVE_ABSENTE")).andExpect(jsonPath("$.statut").value("A_APPROUVER"))
                .andExpect(jsonPath("$.actionRegles").value("EXIGER_APPROBATION")).andExpect(jsonPath("$.motifsRegles[*].message", Matchers.contains("retard de 20 min")))
                .andExpect(jsonPath("$.motifsRegles[0].cle").value("retard")).andExpect(jsonPath("$.distanceMetres").value(Matchers.nullValue()));
        pointer(ANA, "2031-01-15T11:00:00.000Z", "{\"sens\":\"ENTREE\",\"latitude\":45.59,\"longitude\":-73.6}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous êtes à 10008 m de votre milieu")).andExpect(jsonPath("$.verdict.action").value("BLOQUER"));
        horloge.a("2031-01-15T11:01:00.000Z");
        http.get(P + "/moi", ANA).andExpect(jsonPath("$.pointages", Matchers.hasSize(0)));
        pointageAnaAuMauvaisMilieu();
        pointer(ANA, "2031-01-15T11:03:00.000Z", "{\"sens\":\"SIESTE\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« sens » : ENTREE, DEBUT_PAUSE, FIN_PAUSE, SORTIE"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM eff_regle_evaluations WHERE contexte = 'POINTAGE'").query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    @DisplayName("hors de tout quart : refus nommé ; sans ressource : rien à pointer")
    void horsQuart() throws Exception {
        pointer(JULIE, "2031-02-01T12:00:00.000Z", "{\"sens\":\"ENTREE\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("aucun quart publié ne vous attend en ce moment : un quart non pointé se déclare en saisie a posteriori"));
        http.post(P + "/pointages", LEA, "{\"sens\":\"ENTREE\"}").andExpect(status().isNotFound());
        assertThat(corps(http.get(P + "/moi", LEA).andExpect(status().isOk()))).isEqualTo("{\"ressource\":null,\"quart\":null,\"pointages\":[]}");
        http.get(P + "/moi", JULIE).andExpect(jsonPath("$.ressource.id").value(ids.get("P1"))).andExpect(jsonPath("$.quart").value(Matchers.nullValue()));
        http.post(P + "/pointages", JULIE, null).andExpect(status().is(422));
    }

    @Test
    @DisplayName("l'encadrement décide, dans son périmètre, avec motif pour un rejet (E-GP-13)")
    void decisions() throws Exception {
        String marc = pointageMarc();
        String ana = pointageAnaAuMauvaisMilieu();
        http.get(P + "/pointages", JULIE).andExpect(status().isForbidden());
        http.post(P + "/pointages/" + marc + "/approuver", CHEF, null).andExpect(status().isNotFound());
        http.get(P + "/pointages/" + marc, JULIE).andExpect(status().isNotFound());
        http.get(P + "/pointages/" + marc, MARC).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("A_APPROUVER"));
        perimetreDuChef();
        // Des pointages mis en attente avant les flux configurables (E-GP-6) : le flux par défaut s'ouvre au premier geste.
        jdbc.sql("DELETE FROM approval_requests").update();
        http.get(P + "/pointages/" + marc, CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.flux").value(Matchers.nullValue()));
        http.get(P + "/pointages?statut=A_APPROUVER", CHEF).andExpect(jsonPath("$[*].nom", Matchers.containsInAnyOrder("Ana", "Marc")));
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(P + "/pointages").param("statut", "  "), CHEF).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.get(P + "/pointages?du=2031-01-15T11:10:00.000Z&au=2031-01-15T11:30:00.000Z", ADMIN).andExpect(jsonPath("$[*].id", Matchers.contains(marc)));
        http.post(P + "/pointages/" + marc + "/rejeter", CHEF, "{}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« motif » : un rejet se motive, la personne le lira"));
        horloge.a("2031-01-16T00:00:00.000Z");
        http.post(P + "/pointages/" + marc + "/approuver", CHEF, "{\"motif\":\"  Retard signalé au répartiteur \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statut").value("APPROUVE")).andExpect(jsonPath("$[0].decidePar").value("usr-chef"))
                .andExpect(jsonPath("$[0].decideLe").value("2031-01-16T00:00:00.000Z")).andExpect(jsonPath("$[0].motifDecision").value("Retard signalé au répartiteur"));
        http.post(P + "/pointages/" + marc + "/approuver", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce pointage n'attend pas de décision (APPROUVE)"));
        http.post(P + "/pointages/" + ana + "/rejeter", CHEF, "{\"motif\":\"Code d'une autre caserne\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$[0].statut").value("REJETE"));
        http.get(E + "/journal?action=presence.approuver", ADMIN).andExpect(jsonPath("$[0].objetId").value(marc));
        assertThat(jdbc.sql("SELECT apres FROM eff_journal WHERE action = 'presence.approuver'").query(String.class).single())
                .isEqualTo("{\"statut\":\"APPROUVE\",\"motif\":\"Retard signalé au répartiteur\",\"pointages\":[\"" + marc + "\"],\"palier\":null}");
        // E-GN-9 : un pointage est le geste d'une personne ; le journal la nomme, jamais un mécanisme.
        http.get(E + "/journal?action=presence.pointage", ADMIN).andExpect(jsonPath("$[0].origine").value("UTILISATEUR"))
                .andExpect(jsonPath("$[0].acteurId").value(org.hamcrest.Matchers.startsWith("usr-")));
        assertThat(presence.exiger(ctx("julie", "admin", "2031-01-16T00:00:00.000Z"), marc).suivi().statut()).isEqualTo(PresenceEffectifs.StatutPointage.APPROUVE);
        assertThatThrownBy(() -> presence.exiger(ctx("julie", "employee", "2031-01-16T00:00:00.000Z"), "epoi-inconnu")).hasMessage("pointage « epoi-inconnu » introuvable");
    }

    @Test
    @DisplayName("les horodatages d'une borne corroborent, sans doublon au rejeu, lignes fautives nommées (E-RF-13)")
    void horodatagesExternes() throws Exception {
        julieFaitSonQuart();
        String lignes = "[{\"cle\":\"k1\",\"matricule\":\"P1\",\"sens\":\"ENTREE\",\"horodatage\":\"2031-01-15T10:52:00.000Z\"},"
                + "{\"cle\":\"k1\",\"matricule\":\"P1\",\"sens\":\"ENTREE\",\"horodatage\":\"2031-01-15T10:52:00.000Z\"},"
                + "{\"cle\":\"k2\",\"matricule\":\"Z9\",\"sens\":\"ENTREE\",\"horodatage\":\"2031-01-15T10:52:00.000Z\"},"
                + "{\"cle\":\"k3\",\"matricule\":\"P1\",\"sens\":\"SIESTE\",\"horodatage\":\"2031-01-15T10:52:00.000Z\"},"
                + "{\"cle\":\"k4\",\"matricule\":\"P1\",\"sens\":\"SORTIE\",\"horodatage\":\"hier\"},"
                + "{\"matricule\":\"P1\",\"sens\":\"SORTIE\",\"horodatage\":\"2031-01-15T23:00:00.000Z\"}, 7]";
        http.post(P + "/horodatages", JULIE, "{\"source\":\"borne\",\"lignes\":" + lignes + "}").andExpect(status().isForbidden());
        http.post(P + "/horodatages", CHEF, "{\"source\":\"borne-C12\",\"lignes\":" + lignes + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.importes").value(0)).andExpect(jsonPath("$.rejets[0].motif").value("hors de votre périmètre"));
        perimetreDuChef();
        http.post(P + "/horodatages", CHEF, "{\"source\":\" borne-C12 \",\"lignes\":" + lignes + "}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.importes").value(1)).andExpect(jsonPath("$.doublons").value(1))
                .andExpect(jsonPath("$.rejets[*].ligne", Matchers.contains(3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.rejets[*].motif", Matchers.contains("matricule « Z9 » inconnu", "« sens » : ENTREE, DEBUT_PAUSE, FIN_PAUSE, SORTIE",
                        "horodatage invalide (ISO 8601)", "clé absente", "clé absente")));
        http.get(P + "/ressources/" + ids.get("P1") + "/pointages", JULIE).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sens", Matchers.contains("SORTIE", "FIN_PAUSE", "DEBUT_PAUSE", "ENTREE")))
                .andExpect(jsonPath("$[*].corrobore", Matchers.contains(false, false, false, true)));
        http.get(P + "/ressources/" + ids.get("P2") + "/pointages", JULIE).andExpect(status().isForbidden());
        http.get(P + "/ressources/" + ids.get("P1") + "/pointages", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(4)));
        http.post(P + "/horodatages", ADMIN, "{\"source\":\" \",\"lignes\":[]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« source » : le nom du système qui a produit ces horodatages"));
        http.post(P + "/horodatages", ADMIN, "{\"source\":\"x\",\"lignes\":[]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« lignes » : de 1 à 5000 horodatages"));
        http.post(P + "/horodatages", ADMIN, "{\"source\":\"x\",\"lignes\":\"k\"}").andExpect(status().is(422));
        GestionnaireHorodatagesExternes.Bilan lea = horodatages.importer(ctx("lea", "manager", "2031-01-16T00:00:00.000Z"), Map.of("source", "x",
                "lignes", List.of(Map.of("cle", "z", "matricule", "P1", "sens", "ENTREE", "horodatage", "2031-01-15T10:52:00.000Z"))));
        assertThat(lea.rejets()).containsExactly(new GestionnaireHorodatagesExternes.Rejet(1, "hors de votre périmètre"));
        http.get(E + "/journal?action=presence.import", ADMIN).andExpect(jsonPath("$[0].objetId").value("x"));
    }

    @Test
    @DisplayName("un quart non pointé se déclare en plusieurs segments justifiés (P-GP-3) ; par l'encadrement, il est approuvé à son nom (E-GP-15, P-GP-1)")
    void saisies() throws Exception {
        perimetreDuChef();
        horloge.a("2031-01-16T00:00:00.000Z");
        String segments = "[{\"debut\":\"2026-09-01T11:00:00.000Z\",\"fin\":\"2026-09-01T15:00:00.000Z\",\"type\":\"TRAVAIL\"},"
                + "{\"debut\":\"2026-09-01T15:00:00.000Z\",\"fin\":\"2026-09-01T15:30:00.000Z\",\"type\":\"PAUSE\"},"
                + "{\"debut\":\"2026-09-01T15:30:00.000Z\",\"fin\":\"2026-09-01T19:00:00.000Z\",\"type\":\"TRAVAIL\"}]";
        http.post(P + "/saisies", JULIE, "{\"segments\":" + segments + ",\"justification\":\"\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« justification » : dites en quelques mots pourquoi le quart n'a pas été pointé"));
        http.post(P + "/saisies", JULIE, "{\"segments\":[{\"debut\":\"2099-01-01T11:00:00.000Z\",\"fin\":\"2099-01-01T12:00:00.000Z\",\"type\":\"TRAVAIL\"}],"
                + "\"justification\":\"Téléphone déchargé\"}").andExpect(jsonPath("$.errors[0]").value("on ne déclare après coup que du temps passé"));
        String s = corps(http.post(P + "/saisies", JULIE, "{\"segments\":" + segments + ",\"justification\":\"Téléphone déchargé\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$[*].sens", Matchers.contains("ENTREE", "DEBUT_PAUSE", "FIN_PAUSE", "SORTIE")))
                .andExpect(jsonPath("$[*].statut", Matchers.everyItem(Matchers.is("A_APPROUVER"))))
                .andExpect(jsonPath("$[*].origine", Matchers.everyItem(Matchers.is("SAISIE"))))
                .andExpect(jsonPath("$[*].resultat", Matchers.everyItem(Matchers.is("NON_VERIFIE"))))
                .andExpect(jsonPath("$[0].placeId").value(Matchers.nullValue())).andExpect(jsonPath("$[0].uniteCode").value("EST"))
                .andExpect(jsonPath("$[0].justification").value("Téléphone déchargé")).andExpect(jsonPath("$[0].preuves", Matchers.hasSize(0))));
        // Approuver l'un approuve la saisie entière.
        http.post(P + "/pointages/" + JsonPath.read(s, "$[2].id") + "/approuver", CHEF, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].statut", Matchers.contains("APPROUVE", "APPROUVE", "APPROUVE", "APPROUVE")));
        http.post(P + "/saisies", JULIE, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"segments\":" + segments + ",\"justification\":\"Pour Marc\"}")
                .andExpect(status().isForbidden());
        http.post(P + "/saisies", CHEF, "{\"ressourceId\":\"" + ids.get("P2") + "\",\"segments\":" + segments + ",\"justification\":\"Borne en panne\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$[0].statut").value("APPROUVE")).andExpect(jsonPath("$[0].saisiPar").value("usr-chef"));
        // Le chef ne décide pas de ses propres pointages.
        String siens = corps(http.post(P + "/saisies", CHEF, "{\"segments\":" + segments + ",\"justification\":\"Réunion à l'extérieur\"}")
                .andExpect(jsonPath("$[0].statut").value("A_APPROUVER")));
        http.post(P + "/pointages/" + JsonPath.read(siens, "$[0].id") + "/approuver", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("on ne décide pas de ses propres pointages"));
        // Un quart déjà pointé se complète (P-GP-4), mais le même temps ne se déclare pas deux fois : le total doublerait.
        pointer(JULIE, "2031-01-15T10:50:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + "}").andExpect(status().isCreated());
        pointer(JULIE, "2031-01-15T15:00:00.000Z", "{\"sens\":\"SORTIE\"," + A_LA_CASERNE + "}").andExpect(status().isCreated());
        String doublon = "[{\"debut\":\"2031-01-15T11:00:00.000Z\",\"fin\":\"2031-01-15T23:00:00.000Z\",\"type\":\"TRAVAIL\"}]";
        horloge.a("2031-01-16T00:00:00.000Z");
        http.post(P + "/saisies", JULIE, "{\"segments\":" + doublon + ",\"justification\":\"Oubli complet\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("chevauche du temps déjà déclaré")));
        String complement = "[{\"debut\":\"2031-01-15T15:30:00.000Z\",\"fin\":\"2031-01-15T23:00:00.000Z\",\"type\":\"TRAVAIL\"}]";
        http.post(P + "/saisies", JULIE, "{\"segments\":" + complement + ",\"justification\":\"Retour après l'appel\"}").andExpect(status().isCreated());
        // Le pointage d'Ana au mauvais milieu, rejeté, libère son quart pour une saisie de l'encadrement.
        String ana = pointageAnaAuMauvaisMilieu();
        horloge.a("2031-01-16T00:00:00.000Z");
        http.post(P + "/pointages/" + ana + "/rejeter", CHEF, "{\"motif\":\"Code d'une autre caserne\"}").andExpect(status().isOk());
        List<Map<String, Object>> surQuart = pointages.saisirAPosteriori(ctx("chef", "manager", "2031-01-16T00:00:00.000Z"), Map.of("ressourceId", ids.get("P3"),
                "segments", List.of(Map.of("debut", "2031-01-15T12:00:00.000Z", "fin", "2031-01-15T23:00:00.000Z", "type", "TRAVAIL")), "justification", "Oubli"));
        assertThat(surQuart).extracting(p -> p.get("placeId")).containsExactly(ids.get("place_P3"), ids.get("place_P3"));
    }

    @Test
    @DisplayName("le code QR d'un milieu s'affiche pour l'encadrement, et change toutes les cinq minutes")
    void codeQr() throws Exception {
        horloge.a("2031-01-15T12:03:00.000Z");
        http.get(P + "/milieux/" + ids.get("c12") + "/code-qr", JULIE).andExpect(status().isForbidden());
        http.get(P + "/milieux/" + ids.get("c12") + "/code-qr", CHEF).andExpect(status().isForbidden());
        perimetreDuChef();
        String secret = depotMilieux.secretQr(T, ids.get("c12")).orElseThrow();
        http.get(P + "/milieux/" + ids.get("c12") + "/code-qr", CHEF).andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(PresenceEffectifs.codeQr(secret, ids.get("c12"), Instant.parse("2031-01-15T12:03:00.000Z").toEpochMilli())))
                .andExpect(jsonPath("$.code").value(Matchers.startsWith(ids.get("c12") + ".")))
                .andExpect(jsonPath("$.valideJusqua").value("2031-01-15T12:05:00.000Z"));
        http.put(E + "/milieux/" + ids.get("c7") + "/preuves", ADMIN, "{\"preuves\":[]}").andExpect(status().isOk());
        http.get(P + "/milieux/" + ids.get("c7") + "/code-qr", ADMIN).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("le milieu « C7 » n'exige pas de code QR de lieu"));
    }

    @Test
    @DisplayName("une nature de quart sans présence préalable en compte zéro")
    void sansPresencePrealable() throws Exception {
        ref("natures_quart", "{\"code\":\"ADH\",\"libelleFr\":\"Ad hoc\",\"comportement\":\"AD_HOC\"}");
        assertThat(quarts.presencePrealable(T, "ADH", "2031-01-15")).isZero();
        assertThat(quarts.presencePrealable(T, "REG", "2031-01-15")).isEqualTo(15);
    }

    @Test
    @DisplayName("le module coupé ferme tout")
    void moduleCoupe() throws Exception {
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.get(P + "/moi", JULIE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs_presence' non activé pour cet organisme"));
    }

    private void activites() throws Exception {
        ref("activites_presence", "{\"code\":\"INTERVENTION\",\"libelleFr\":\"Intervention\",\"comportement\":\"INTERVENTION\"}");
        ref("activites_presence", "{\"code\":\"SOUTIEN\",\"libelleFr\":\"Soutien\",\"comportement\":\"SOUTIEN\"}");
    }

    @Test
    @DisplayName("les profils de pointage décident du mode, de la fenêtre et des natures admises ; retirés, ils rendent le défaut (E-GP-1, E-GP-2, P-GP-1)")
    void profils() throws Exception {
        activites();
        http.get(P + "/referentiels-profils", JULIE).andExpect(status().isOk())
                .andExpect(jsonPath("$.modes", Matchers.contains("TEMPS_REEL", "A_POSTERIORI", "MIXTE")))
                .andExpect(jsonPath("$.naturesNonPayees", Matchers.contains("PAUSE", "REPAS", "REPOS")));
        http.get(P + "/mon-profil", JULIE).andExpect(jsonPath("$.profil.code").value("__defaut__")).andExpect(jsonPath("$.ressourceId").value(ids.get("P1")));
        http.get(P + "/mon-profil", LEA).andExpect(jsonPath("$.profil.mode").value("MIXTE")).andExpect(jsonPath("$.ressourceId").value(Matchers.nullValue()));
        http.get(P + "/profils", JULIE).andExpect(status().isForbidden());
        http.put(P + "/profils", ADMIN, "{\"code\":\"APOST\",\"porteeType\":\"UNITE\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une portée « UNITE » exige son code : sans lui, le profil ne viserait personne tout en paraissant configuré"));
        String profil = "{\"code\":\"APOST\",\"libelle\":\"Après coup\",\"mode\":\"A_POSTERIORI\",\"porteeType\":\"UNITE\",\"porteeCode\":\"EST\","
                + "\"naturesNonPayees\":[\"REPAS\"],\"fenetreAPosterioriJours\":2}";
        http.put(P + "/profils", ADMIN, profil).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("APOST"))
                .andExpect(jsonPath("$.naturesNonPayees", Matchers.contains("REPAS"))).andExpect(jsonPath("$.fenetreAPosterioriJours").value(2));
        http.put(P + "/profils", ADMIN, profil.replace("Après coup", "Après coup seulement")).andExpect(status().isOk())
                .andExpect(jsonPath("$.libelle").value("Après coup seulement"));
        http.put(P + "/profils", ADMIN, profil).andExpect(status().isOk());
        http.get(P + "/profils", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(1))).andExpect(jsonPath("$[0].porteeCode").value("EST"));
        http.get(P + "/mon-profil", JULIE).andExpect(jsonPath("$.profil.code").value("APOST"));

        pointer(JULIE, "2031-01-15T11:00:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + "}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("le profil « Après coup » est à saisie après coup : le pointage en temps réel n'y est pas admis"));
        horloge.a("2031-01-16T00:00:00.000Z");
        String pause = "[{\"debut\":\"2031-01-15T11:00:00.000Z\",\"fin\":\"2031-01-15T15:00:00.000Z\",\"type\":\"TRAVAIL\",\"activiteCode\":\"%s\"},"
                + "{\"debut\":\"2031-01-15T15:00:00.000Z\",\"fin\":\"2031-01-15T15:30:00.000Z\",\"type\":\"PAUSE\",\"nature\":\"%s\"},"
                + "{\"debut\":\"2031-01-15T15:30:00.000Z\",\"fin\":\"2031-01-15T19:00:00.000Z\",\"type\":\"TRAVAIL\"}]";
        http.post(P + "/saisies", JULIE, "{\"segments\":" + pause.formatted("INTERVENTION", "PAUSE") + ",\"justification\":\"Téléphone déchargé\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("le profil « Après coup » n'admet pas la pause (admises : pause-repas)"));
        http.post(P + "/saisies", JULIE, "{\"segments\":" + pause.formatted("VOL", "REPAS") + ",\"justification\":\"Téléphone déchargé\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("Activités de présence : « VOL » n'est pas en vigueur le 2031-01-15"));
        horloge.a("2031-01-20T00:00:00.000Z");
        http.post(P + "/saisies", JULIE, "{\"segments\":" + pause.formatted("INTERVENTION", "REPAS") + ",\"justification\":\"Téléphone déchargé\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la fenêtre de saisie du profil « Après coup » est de 2 jour(s) : cette déclaration en a 4"));
        horloge.a("2031-01-16T00:00:00.000Z");
        http.post(P + "/saisies", JULIE, "{\"segments\":" + pause.formatted("INTERVENTION", "REPAS") + ",\"justification\":\"Téléphone déchargé\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$[*].sens", Matchers.contains("ENTREE", "DEBUT_PAUSE", "FIN_PAUSE", "SORTIE")))
                .andExpect(jsonPath("$[0].activiteCode").value("INTERVENTION")).andExpect(jsonPath("$[1].naturePause").value("REPAS"))
                .andExpect(jsonPath("$[2].activiteCode").value(Matchers.nullValue()));

        http.delete(P + "/profils/APOST", JULIE).andExpect(status().isForbidden());
        http.delete(P + "/profils/APOST", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.retire").value("APOST"));
        http.delete(P + "/profils/APOST", ADMIN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("profil de pointage « APOST » introuvable"));
        http.get(P + "/mon-profil", JULIE).andExpect(jsonPath("$.profil.code").value("__defaut__"));
        assertThat(jdbc.sql("SELECT action FROM eff_journal WHERE objetType = 'profil_pointage' ORDER BY rowid").query(String.class).list())
                .containsExactly("presence.profil.pose", "presence.profil.pose", "presence.profil.pose", "presence.profil.retire");
    }

    @Test
    @DisplayName("le site déclaré au pointage, l'activité commencée, leur répartition dans « ma présence », et l'audit des localisations sans position (E-GP-7, E-GP-14, E-GP-15)")
    void multiSiteEtAudit() throws Exception {
        activites();
        perimetreDuChef();
        pointer(JULIE, "2031-01-15T10:50:00.000Z", "{\"sens\":\"ENTREE\"," + A_LA_CASERNE + ",\"activiteCode\":\"INTERVENTION\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.activiteCode").value("INTERVENTION")).andExpect(jsonPath("$.milieuId").value(ids.get("c12")));
        pointer(JULIE, "2031-01-15T13:00:00.000Z", "{\"sens\":\"DEBUT_PAUSE\"," + A_LA_CASERNE + ",\"activiteCode\":\"SOUTIEN\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une activité se déclare en commençant à travailler (entrée ou reprise après pause), pas en s'arrêtant"));
        pointer(JULIE, "2031-01-15T13:00:00.000Z", "{\"sens\":\"DEBUT_PAUSE\"," + A_LA_CASERNE + "}").andExpect(status().isCreated());
        pointer(JULIE, "2031-01-15T13:30:00.000Z", "{\"sens\":\"FIN_PAUSE\",\"activiteCode\":\"INCONNUE\",\"milieuId\":\"" + ids.get("c7") + "\"}")
                .andExpect(status().is(422));
        String reprise = JsonPath.read(corps(pointer(JULIE, "2031-01-15T13:30:00.000Z",
                "{\"sens\":\"FIN_PAUSE\",\"activiteCode\":\" SOUTIEN \",\"milieuId\":\" " + ids.get("c7") + " \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.milieuId").value(ids.get("c7"))).andExpect(jsonPath("$.activiteCode").value("SOUTIEN"))), "$.id");
        horloge.a("2031-01-15T14:30:00.000Z");
        http.get(P + "/moi", JULIE).andExpect(status().isOk())
                .andExpect(jsonPath("$.quart.parMilieu[*].milieu", Matchers.contains("Caserne 12", "Caserne 7")))
                .andExpect(jsonPath("$.quart.parMilieu[*].minutes", Matchers.contains(130, 60)))
                .andExpect(jsonPath("$.quart.parActivite[*].activiteCode", Matchers.contains("INTERVENTION", "SOUTIEN")))
                .andExpect(jsonPath("$.quart.parActivite[*].minutes", Matchers.contains(130, 60)))
                .andExpect(jsonPath("$.quart.deplacements[0].quand").value("2031-01-15T13:30:00.000Z"))
                .andExpect(jsonPath("$.quart.deplacements[0].deLibelle").value("Caserne 12")).andExpect(jsonPath("$.quart.deplacements[0].versLibelle").value("Caserne 7"));
        http.get(P + "/pointages/" + reprise, CHEF).andExpect(jsonPath("$.activiteCode").value("SOUTIEN")).andExpect(jsonPath("$.naturePause").value(Matchers.nullValue()));
        pointageAnaAuMauvaisMilieu();
        horloge.a("2031-01-16T00:00:00.000Z");

        http.get(P + "/pointages?milieuId=" + ids.get("c7"), CHEF).andExpect(jsonPath("$", Matchers.hasSize(1))).andExpect(jsonPath("$[0].id").value(reprise));
        http.get(P + "/audit-localisations", JULIE).andExpect(status().isForbidden());
        http.get(P + "/audit-localisations", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.lignes", Matchers.hasSize(4)))
                .andExpect(jsonPath("$.motifs[0]").value("4 déclaration(s) de présence auditées")).andExpect(jsonPath("$.motifs[1]").value("1 écart(s) de lieu"))
                .andExpect(jsonPath("$.motifs[2]").value(Matchers.containsString("Loi 25")));
        http.get(P + "/audit-localisations?ecartSeul=1", CHEF).andExpect(jsonPath("$.lignes[*].nom", Matchers.contains("Ana")))
                .andExpect(jsonPath("$.lignes[0].ecart").value("MAUVAIS_MILIEU")).andExpect(jsonPath("$.lignes[0].mecanismes[0].mecanisme").exists());
        http.get(P + "/audit-localisations?milieuId=" + ids.get("c7") + "&du=2031-01-15T00:00:00.000Z&au=2031-01-16T00:00:00.000Z", CHEF)
                .andExpect(jsonPath("$.lignes[*].milieu", Matchers.contains("Caserne 7")))
                .andExpect(jsonPath("$.motifs[0]").value("1 déclaration(s) de présence auditées au milieu « Caserne 7 »"))
                .andExpect(jsonPath("$.motifs[1]").value("Aucun écart de lieu"));
        String csv = http.get(P + "/audit-localisations.csv?milieuId=" + ids.get("c7"), CHEF).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.startsWith("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"audit-localisations.csv\""))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv.split("\n")).hasSize(2);
        assertThat(csv).startsWith("\"horodatage\";\"matriculeOuNom\";\"sens\";\"milieu\";\"unite\";\"resultat\";\"ecart\";\"distanceMetres\";\"mecanismes\";\"origine\";\"statut\"\n")
                .contains("\"2031-01-15T13:30:00.000Z\";\"Julie\";\"FIN_PAUSE\";\"Caserne 7\";\"EST\";").contains("CODE_QR_DE_LIEU:absente");
        String tout = http.get(P + "/audit-localisations.csv", CHEF).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(tout).contains("GEOREPERAGE:ok").contains("CODE_QR_DE_LIEU:hors");
        http.get(P + "/audit-localisations.csv", JULIE).andExpect(status().isForbidden());
    }
}
