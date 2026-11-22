package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.AvisDeComblement;
import ca.vina.nexu.effectifs.application.commandes.CascadeOffresQuart;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs.Declaration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireComblementAbsences;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresOffres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.commandes.ReponsesOffresQuart;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LectureOffresQuart;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotSoldesBanques;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurOrdonnanceurOffres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotComblementsAbsences;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotDisponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEquipages;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotOffresQuart;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotParametresOffres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPeriodes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPlanningAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPostes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRegles;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaAbsencesEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaDisponibilites;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaOffresQuart;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaReglesEffectifs;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>L'offre d'un quart par ancienneté et à tour de rôle, par HTTP</strong> (AD-07 ; convention SP-CSN, art. 15.10,
 * 16.02, 16.03), sur une vraie base SQLite et l'ordonnanceur réel.
 * <p>
 * Julie tient le quart du mardi 5 mars 2030 (6 h à 18 h, attribution manuelle) ; Marc (10 ans), Théo (6 ans) et Léa
 * (15 ans) sont disponibles. Léa a refusé 11,25 h le mois dernier : malgré son ancienneté, elle passe après eux. Julie
 * s'absente ; l'organisme offre en cascade, dix minutes pour répondre. L'horloge avance à la main.
 */
@DisplayName("/api/effectifs/offres — offre en cascade : ordre, délai, refus compté, passage au suivant, urgence, trace")
class ControleurOffresQuartHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final String API = E + "/offres";
    private static final String PLANIF = E + "/planification";
    private static final String LUNDI = "2030-03-04";
    private static final String MARDI = "2030-03-05";
    private static final String MAINTENANT = "2030-02-20T15:00:00Z";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_absences\",\"effectifs_planification\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final ContexteRequete MARC = new ContexteRequete("usr-marc", "marc", "employee", null, T);
    private static final ContexteRequete THEO = new ContexteRequete("usr-theo", "theo", "employee", null, T);
    private static final ContexteRequete LEA = new ContexteRequete("usr-lea", "lea", "employee", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);

    /** Une horloge qu'on avance : l'échéance de dix minutes se joue sans attendre. */
    private static final class Horloge extends Clock {
        private Instant t = Instant.parse(MAINTENANT);

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
            return t;
        }
    }

    private final Horloge horloge = new Horloge();
    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireAbsencesEffectifs absences;
    private DepotDisponibilites depotDispo;
    private DepotTaches taches;
    private final List<PublicationAvis.Avis> avisPublies = new ArrayList<>();
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

    private Contexte ctx(ContexteRequete qui) {
        return new Contexte(T, qui.userId(), qui.role(), horloge.instant().toString());
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
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc), horloge);
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
        GestionnairePlaces places = new GestionnairePlaces(depotEqp, lectEqp, lectRes, perimetres, equipages, evaluateur, journal);
        ControleurPlanificationEffectifs planification = new ControleurPlanificationEffectifs(garde,
                new ControleurPlanificationEffectifs.Lectures(lectRes, lectPrd, lectPos, lectEqp, lectQuo),
                new ControleurPlanificationEffectifs.Gestes(new GestionnaireRessources((t, inscription) -> { }, depotRes, lectRes, referentiels, perimetres, journal, seq),
                        new GestionnairePeriodes(depotPrd, lectPrd, referentiels, perimetres, journal, seq),
                        new GestionnairePostes(depotPos, new GestionnairePostes.Lectures(lectPos, referentiels, perimetres, milieux, lectRes), journal, seq),
                        equipages, places, new GestionnaireQuotas(depotQuo, lectQuo, referentiels, perimetres, journal, seq)));

        SchemaAbsencesEffectifs schemaAbs = new SchemaAbsencesEffectifs(jdbc);
        DepotAbsencesEffectifs depotAbs = new DepotAbsencesEffectifs(jdbc, schemaPlanif, schemaAbs);
        DepotPlanningAbsencesEffectifs planning = new DepotPlanningAbsencesEffectifs(jdbc, schemaPlanif);
        LectureAbsencesEffectifs lecture = new LectureAbsencesEffectifs(depotAbs, planning, lectRes, perimetres);
        LectureDisponibilites.Lectures lectPlanif = new LectureDisponibilites.Lectures(lectRes, lectPrd, lectEqp, perimetres);
        depotDispo = new DepotDisponibilites(jdbc, new SchemaDisponibilites(jdbc));
        LectureDisponibilites dispo = new LectureDisponibilites(depotDispo, lectPlanif, evaluateur);
        PublicationAvis avis = a -> {
            avisPublies.add(a);
            return a.destinataires().stream().map(d -> new PublicationAvis.Envoi(d, "courriel", "sent", null)).toList();
        };
        DepotComblementsAbsences depotCbl = new DepotComblementsAbsences(jdbc, schemaAbs);
        SchemaOffresQuart schemaOfr = new SchemaOffresQuart(jdbc);
        DepotOffresQuart depotOfr = new DepotOffresQuart(jdbc, schemaOfr);
        DepotParametresOffres depotPar = new DepotParametresOffres(jdbc, schemaOfr);
        RegistreExecuteurs registre = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, registre);
        AdaptateurOrdonnanceurOffres echeances = new AdaptateurOrdonnanceurOffres(taches, registre);
        CascadeOffresQuart cascade = new CascadeOffresQuart(new CascadeOffresQuart.Ports(depotOfr, depotPar, echeances),
                new CascadeOffresQuart.Collaborateurs(new AvisDeComblement(lectRes, depotCbl, avis), lectEqp, journal, seq));
        echeances.enregistrerExecuteur(cascade::executer);
        GestionnaireComblementAbsences comblement = new GestionnaireComblementAbsences(new GestionnaireComblementAbsences.Lectures(lectEqp, dispo, lectRes, referentiels),
                new GestionnaireComblementAbsences.Ecritures(depotCbl, new GestionnaireAttributionFinDeCollecte(lectPlanif, dispo, places, journal), avis, journal, seq,
                        cascade));
        absences = new GestionnaireAbsencesEffectifs(new GestionnaireAbsencesEffectifs.Ports(depotAbs, planning, depotEqp, comblement),
                new GestionnaireAbsencesEffectifs.Lectures(lecture, lectRes, referentiels, perimetres,
                        new DepotSoldesBanques(jdbc, new SchemaEffectifs(jdbc))), evaluateur, journal, seq);
        ControleurOffresQuart offres = new ControleurOffresQuart(garde, new ControleurOffresQuart.Gestes(cascade,
                new ReponsesOffresQuart(new ReponsesOffresQuart.Collaborateurs(cascade, depotOfr, places, journal), lectRes),
                new GestionnaireParametresOffres(depotPar, journal), new LectureOffresQuart(depotOfr, lectRes, lecture, perimetres)));
        http = new ClientHttpEssai(socle, planification, offres);
        decor();
    }

    private String creer(String chemin, String json) throws Exception {
        return JsonPath.read(http.post(chemin, ADMIN, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json.replaceFirst("\\{", "{\"effectifDepuis\":\"2026-01-01\",")).andExpect(status().isCreated());
    }

    private String ressource(String matricule, String nom, int anciennete, String compte) throws Exception {
        return creer(PLANIF + "/ressources", "{\"matricule\":\"" + matricule + "\",\"nom\":\"" + nom + "\",\"corpsEmploiCode\":\"PARA\",\"uniteCode\":\"EST\","
                + "\"anciennete\":" + anciennete + ",\"utilisateurId\":\"" + compte + "\"}");
    }

    /** Une unité, quatre paramédics, le quart manuel du mardi tenu par Julie, trois disponibles, et le compte d'heures de Léa. */
    private void decor() throws Exception {
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\"}");
        ref("corps_emploi", "{\"code\":\"PARA\",\"libelleFr\":\"Paramédics\"}");
        ref("natures_quart", "{\"code\":\"REG\",\"libelleFr\":\"Régulier\",\"comportement\":\"REGULIER\"}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\"}");
        ref("codes_absence", "{\"code\":\"MAL\",\"libelleFr\":\"Maladie\",\"comportement\":\"PAYE\",\"attributs\":{\"duree\":\"PONCTUELLE\"}}");
        ids.put("c12", creer(E + "/milieux", "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}"));
        ids.put("P1", ressource("P1", "Julie", 12, "usr-julie"));
        ids.put("P2", ressource("P2", "Marc", 10, "usr-marc"));
        ids.put("P3", ressource("P3", "Léa", 15, "usr-lea"));
        ids.put("P4", ressource("P4", "Théo", 6, "usr-theo"));
        ids.put("periode", creer(PLANIF + "/periodes", "{\"nom\":\"Semaine 10\",\"uniteCode\":\"EST\",\"debut\":\"" + LUNDI + "\",\"fin\":\"2030-03-10\"}"));
        http.post(PLANIF + "/periodes/" + ids.get("periode") + "/etat", ADMIN, "{\"etat\":\"CONFECTION\"}").andExpect(status().isOk());
        String e = http.post(PLANIF + "/equipages", ADMIN, "{\"periodeId\":\"" + ids.get("periode") + "\",\"date\":\"" + MARDI + "\",\"debut\":360,\"fin\":1080,"
                + "\"natureCode\":\"REG\",\"milieuId\":\"" + ids.get("c12") + "\",\"modeAttribution\":\"MANUEL\",\"composition\":[{\"corpsEmploiCode\":\"PARA\",\"nombre\":1}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        ids.put("place", JsonPath.read(e, "$.places[0].id"));
        http.post(PLANIF + "/places/" + ids.get("place") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P1") + "\"}").andExpect(status().isOk());
        for (String r : new String[]{"P2", "P3", "P4"}) {
            depotDispo.remplacer(T, new DepotDisponibilitesPort.NouveauDepot(ids.get("periode"), ids.get(r), Disponibilites.Conformite.CONFORME, List.of(), MAINTENANT,
                    "usr-admin", List.of(new DepotDisponibilitesPort.PlageIdentifiee("pl-" + r, new Disponibilites.PlageDisponible(MARDI, 0, 1440,
                    Disponibilites.TypeDisponibilite.DISPONIBLE)))));
        }
        // Le compte d'heures (16.03) : Léa a refusé 11,25 h il y a dix jours ; le refus de Théo, il y a 200 jours, sort de la
        // fenêtre de 91 jours ; Marc a été écarté par une règle, ce qui ne compte pas.
        compte("ancienne-1", "P3", "REFUSE", "2030-02-10T12:00:00.000Z", 11.25);
        compte("ancienne-2", "P4", "REFUSE", "2029-08-04T12:00:00.000Z", 12);
        compte("ancienne-3", "P2", "ECARTE", "2030-02-10T12:00:00.000Z", 12);
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"etendue\":\"UNITE\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated());
    }

    private void compte(String offreId, String ressource, String etat, String le, double heures) {
        jdbc.sql("INSERT INTO eff_offre_rangs (tenantId, offreId, rang, ressourceId, anciennete, heuresComptees, heuresOffertes, etat, reponduLe) "
                + "VALUES (?, ?, 1, ?, 0, 0, ?, ?, ?)").params(T, offreId, ids.get(ressource), heures, etat, le).update();
    }

    private void avancerDe(int minutes) {
        horloge.t = horloge.t.plusSeconds(minutes * 60L);
    }

    private void cascade() throws Exception {
        http.put(API + "/parametres", CHEF, "{\"mode\":\"CASCADE\"}").andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("CASCADE"))
                .andExpect(jsonPath("$.delaiReponseMinutes").value(10));
    }

    /** Julie s'absente le mardi : la place se libère et le comblement ouvre l'offre. */
    private String absenceDeJulie() {
        GestionnaireAbsencesEffectifs.Resultat r = absences.declarer(ctx(ADMIN), new Declaration(ids.get("P1"), "MAL", MARDI, null, null));
        assertThat(r.placesLiberees()).isEqualTo(1);
        return r.absence().id();
    }

    /** L'ordre complet de l'offre, tel que l'encadrement le lit. */
    private org.springframework.test.web.servlet.ResultActions ordre(String offreId) throws Exception {
        String absence = jdbc.sql("SELECT absenceId FROM eff_offres WHERE id = ?").param(offreId).query(String.class).single();
        return http.get(API + "?absenceId=" + absence, CHEF).andExpect(status().isOk());
    }

    private String offreDe(String absenceId) throws Exception {
        return JsonPath.read(http.get(API + "?absenceId=" + absenceId, CHEF).andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    @Test
    @DisplayName("réglage : simultané par défaut (aucun changement d'avant) ; mode et bornes refusés en les nommant ; gardé par la gestion des absences")
    void reglage() throws Exception {
        http.get(API + "/parametres", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("SIMULTANEE"))
                .andExpect(jsonPath("$.delaiReponseMinutes").value(10)).andExpect(jsonPath("$.seuilUrgenceMinutes").value(120))
                .andExpect(jsonPath("$.fenetreRepartitionJours").value(91));
        http.put(API + "/parametres", CHEF, "{\"mode\":\"cascade\"}").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« mode » : SIMULTANEE, CASCADE"));
        http.put(API + "/parametres", CHEF, "{\"delaiReponseMinutes\":0}").andExpect(status().isUnprocessableEntity());
        http.put(API + "/parametres", CHEF, null).andExpect(status().isOk()).andExpect(jsonPath("$.mode").value("SIMULTANEE"));
        http.put(API + "/parametres", MARC, "{\"mode\":\"CASCADE\"}").andExpect(status().isForbidden());
        cascade();
        http.get(E + "/journal?action=offres.parametres", ADMIN).andExpect(jsonPath("$[0].apres.mode").value("CASCADE"))
                .andExpect(jsonPath("$[0].avant.mode").value("SIMULTANEE"));
        String absence = absenceDeJulie();
        assertThat(avisPublies).hasSize(1);
        assertThat(avisPublies.get(0).destinataires()).containsExactly("usr-marc");
        http.get(API + "?absenceId=" + absence, MARC).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("en simultané, l'offre d'avant : tous les admissibles avisés, aucune offre en cascade")
    void simultaneInchange() throws Exception {
        String absence = absenceDeJulie();
        assertThat(avisPublies).extracting(a -> a.destinataires().get(0)).containsExactlyInAnyOrder("usr-marc", "usr-lea", "usr-theo");
        http.get(API + "?absenceId=" + absence, CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("cascade : ordre par heures comptées puis ancienneté ; pas encore son tour (409) ; refus compté ; silence échu ; acceptation sous les règles ; trace")
    void cascadeComplete() throws Exception {
        cascade();
        String absence = absenceDeJulie();
        String offre = offreDe(absence);
        http.get(API + "?absenceId=" + absence, CHEF).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rangs[*].nom", Matchers.contains("Marc", "Théo", "Léa")))
                .andExpect(jsonPath("$[0].rangs[*].heuresComptees", Matchers.contains(0.0, 0.0, 11.25)))
                .andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("OFFERT", "EN_ATTENTE", "EN_ATTENTE")))
                .andExpect(jsonPath("$[0].rangs[0].echeance").value("2030-02-20T15:10:00.000Z")).andExpect(jsonPath("$[0].urgente").value(false))
                .andExpect(jsonPath("$[0].quart.date").value(MARDI));
        assertThat(avisPublies).hasSize(1);
        assertThat(avisPublies.get(0).corps()).contains("à votre tour selon l'ancienneté et le tour de rôle").contains("2030-02-20 à 10:10");
        assertThat(taches.lister(T, "en_attente", OffresQuart.TACHE_ECHEANCE)).extracting(t -> t.cibleId() + "@" + t.echeanceLe())
                .containsExactly(offre + "@2030-02-20T15:10:00.000Z");

        http.get(API + "/miennes", MARC).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(offre));
        http.get(API + "/miennes", THEO).andExpect(jsonPath("$.length()").value(0));
        http.get(API + "/miennes", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.post(API + "/" + offre + "/accepter", THEO, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("ce n'est pas encore votre tour"));
        http.post(API + "/" + offre + "/accepter", JULIE, null).andExpect(status().isNotFound());
        http.post(API + "/inconnue/refuser", MARC, null).andExpect(status().isNotFound());

        avancerDe(3);
        http.post(API + "/" + offre + "/refuser", MARC, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.rangs[*].etat", Matchers.contains("REFUSE"))).andExpect(jsonPath("$.rangs[0].nom").value("Marc"));
        ordre(offre).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("REFUSE", "OFFERT", "EN_ATTENTE")))
                .andExpect(jsonPath("$[0].rangs[1].echeance").value("2030-02-20T15:13:00.000Z"));
        http.post(API + "/" + offre + "/refuser", MARC, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("vous avez déjà refusé ce quart"));

        avancerDe(10);
        http.get(API + "/miennes", THEO).andExpect(jsonPath("$.length()").value(0));
        http.post(API + "/" + offre + "/accepter", THEO, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("le délai de réponse est échu")));
        // L'employé ne voit que son rang : ni le nom, ni les heures, ni l'ancienneté des collègues.
        http.get(API + "/miennes", LEA).andExpect(jsonPath("$[0].rangs.length()").value(1)).andExpect(jsonPath("$[0].rangs[0].nom").value("Léa"))
                .andExpect(jsonPath("$[0].rangs[0].etat").value("OFFERT"));

        http.post(API + "/" + offre + "/accepter", LEA, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("POURVUE"))
                .andExpect(jsonPath("$.attribueeA").value(ids.get("P3")))
                .andExpect(jsonPath("$.rangs[*].etat", Matchers.contains("ACCEPTE")));
        ordre(offre).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("REFUSE", "EXPIRE", "ACCEPTE")));
        assertThat(jdbc.sql("SELECT ressourceId FROM eff_places WHERE id = ?").param(ids.get("place")).query(String.class).single()).isEqualTo(ids.get("P3"));
        assertThat(taches.lister(T, "en_attente", OffresQuart.TACHE_ECHEANCE)).isEmpty();
        assertThat(avisPublies).extracting(a -> a.destinataires().get(0)).containsExactly("usr-marc", "usr-theo", "usr-lea");
        http.get(E + "/journal?objetType=offre", ADMIN).andExpect(jsonPath("$[*].action", Matchers.hasItems("offre.ouverte", "offre.proposee", "offre.refusee",
                "offre.expiree", "offre.acceptee")));
        // La prochaine offre tient compte de ces réponses : Marc et Théo comptent 12 h, Léa 23,25 h.
        assertThat(jdbc.sql("SELECT ressourceId || ':' || SUM(heuresOffertes) FROM eff_offre_rangs WHERE offreId = ? AND etat IN ('ACCEPTE','REFUSE','EXPIRE') "
                + "GROUP BY ressourceId ORDER BY ressourceId").param(offre).query(String.class).list())
                .containsExactlyInAnyOrder(ids.get("P2") + ":12.0", ids.get("P3") + ":12.0", ids.get("P4") + ":12.0");
    }

    @Test
    @DisplayName("l'ordonnanceur fait expirer le silence et avise le suivant ; une règle qui refuse écarte sans compter ; personne : l'offre s'épuise")
    void ordonnanceurEcartEtEpuisement() throws Exception {
        cascade();
        String offre = offreDe(absenceDeJulie());
        avancerDe(10);
        taches.tic(horloge.instant().toString(), PolitiqueRetentative.PAR_DEFAUT, "essai");
        http.get(API + "/miennes", THEO).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("OFFERT")));
        ordre(offre).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("EXPIRE", "OFFERT", "EN_ATTENTE")));
        // Théo prend entre-temps un quart qui chevauche : la règle d'attribution l'écarte, et l'offre passe à Léa.
        jdbc.sql("INSERT INTO eff_equipages (id, tenantId, periodeId, uniteCode, date, debut, fin, natureCode, milieuId, modeAttribution, minimumOperationnel, creeLe) "
                + "SELECT 'eqp-x', tenantId, periodeId, uniteCode, date, debut, fin, natureCode, milieuId, modeAttribution, minimumOperationnel, creeLe "
                + "FROM eff_equipages WHERE tenantId = ?").param(T).update();
        jdbc.sql("INSERT INTO eff_places (id, tenantId, equipageId, rang, corpsEmploiCode, ressourceId) VALUES ('pl-x', ?, 'eqp-x', 1, 'PARA', ?)")
                .params(T, ids.get("P4")).update();
        http.post(API + "/" + offre + "/accepter", THEO, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("OUVERTE"))
                .andExpect(jsonPath("$.rangs[0].etat").value("ECARTE")).andExpect(jsonPath("$.rangs[0].motif").value(Matchers.containsString("chevauche")));
        ordre(offre).andExpect(jsonPath("$[0].rangs[2].etat").value("OFFERT"));
        http.post(API + "/" + offre + "/refuser", LEA, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("EPUISEE"));
        assertThat(jdbc.sql("SELECT ressourceId FROM eff_places WHERE id = ?").param(ids.get("place")).query(String.class).optional()).isEmpty();
        http.get(E + "/journal?action=offre.epuisee", ADMIN).andExpect(jsonPath("$[0].acteurId").value(OffresQuart.MECANISME));
        http.get(E + "/journal?action=offre.ecartee", ADMIN).andExpect(jsonPath("$[0].acteurId").value("usr-theo"));
    }

    @Test
    @DisplayName("place pourvue par le planificateur pendant l'offre : l'offre est retirée et la réponse le dit")
    void retiree() throws Exception {
        cascade();
        String offre = offreDe(absenceDeJulie());
        http.post(PLANIF + "/places/" + ids.get("place") + "/attribuer", ADMIN, "{\"ressourceId\":\"" + ids.get("P3") + "\"}").andExpect(status().isOk());
        http.post(API + "/" + offre + "/accepter", MARC, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("l'offre de ce quart est close"));
        http.get(API + "/miennes", MARC).andExpect(jsonPath("$.length()").value(0));
        http.get(API + "?absenceId=" + jdbc.sql("SELECT absenceId FROM eff_offres").query(String.class).single(), CHEF)
                .andExpect(jsonPath("$[0].etat").value("RETIREE"));
    }

    @Test
    @DisplayName("urgence (16.02) : le quart commence dans deux heures, tous sont avisés jusqu'à son début ; le silence n'est pas compté")
    void urgence() throws Exception {
        cascade();
        horloge.t = Instant.parse("2030-03-05T10:00:00Z");
        String offre = offreDe(absenceDeJulie());
        http.get(API + "/miennes", LEA).andExpect(jsonPath("$[0].urgente").value(true))
                .andExpect(jsonPath("$[0].rangs[0].echeance").value("2030-03-05T11:00:00.000Z"));
        ordre(offre).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("OFFERT", "OFFERT", "OFFERT")));
        http.post(API + "/" + offre + "/refuser", MARC, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("OUVERTE"));
        horloge.t = Instant.parse("2030-03-05T11:00:00Z");
        http.get(API + "?absenceId=" + jdbc.sql("SELECT absenceId FROM eff_offres").query(String.class).single(), CHEF)
                .andExpect(jsonPath("$[0].etat").value("EPUISEE")).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("REFUSE", "CLOS", "CLOS")));
    }

    @Test
    @DisplayName("urgence : le premier qui accepte prend le quart, les autres propositions sont closes")
    void urgencePremierQuiAccepte() throws Exception {
        cascade();
        horloge.t = Instant.parse("2030-03-05T10:30:00Z");
        String offre = offreDe(absenceDeJulie());
        http.post(API + "/" + offre + "/accepter", LEA, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("POURVUE"))
                .andExpect(jsonPath("$.rangs[*].etat", Matchers.contains("ACCEPTE")));
        ordre(offre).andExpect(jsonPath("$[0].rangs[*].etat", Matchers.contains("CLOS", "CLOS", "ACCEPTE")));
        http.post(API + "/" + offre + "/accepter", MARC, null).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("l'offre de ce quart est close"));
    }
}
