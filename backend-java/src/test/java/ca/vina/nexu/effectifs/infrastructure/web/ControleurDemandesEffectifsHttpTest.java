package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDemandes;
import ca.vina.nexu.effectifs.application.requetes.LectureDemandes;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotDemandes;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Les demandes opérationnelles par HTTP</strong> (addenda 8, M-08) : Julie s'inscrit à la liste de temps
 * supplémentaire et demande une formation ; le chef d'EST accepte l'une et refuse l'autre en motivant ; la liste du jour
 * les classe par ancienneté ; un chef sans périmètre ne voit ni ne décide rien ; une employée ne décide pas.
 */
@DisplayName("/api/effectifs/planification/demandes — soumettre, annuler, décider, liste de temps supplémentaire")
class ControleurDemandesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String API = "/api/effectifs/planification/demandes";
    private static final String CREE = "2026-01-01T00:00:00Z";
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final ContexteRequete MARC = new ContexteRequete("usr-marc", "marc", "employee", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);
    private static final ContexteRequete AUTRE_CHEF = new ContexteRequete("usr-autre", "autre", "manager", null, T);
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;

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

    private void ref(String type, String code, String comportement) {
        jdbc.sql("INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, comportement, attributs, effectifDepuis, creeLe, creePar) "
                + "VALUES (?, ?, ?, ?, ?, ?, '{}', '2026-01-01', ?, 'adm')").params("r-" + type + "-" + code, T, type, code, code, comportement, CREE).update();
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)")
                .params(T, "[\"effectifs\",\"effectifs_planification\"]").update();
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification planif = new SchemaPlanification(jdbc);
        Sequence seq = sequence();
        LectureReferentiels referentiels = new LectureReferentiels(new DepotReferentiels(jdbc, schema));
        LecturePerimetres perimetres = new LecturePerimetres(new DepotPerimetres(jdbc, schema), referentiels);
        LectureRessources ressources = new LectureRessources(new DepotRessources(jdbc, planif), perimetres);
        DepotDemandes depot = new DepotDemandes(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse("2027-01-15T10:00:00Z"), ZoneOffset.UTC));
        http = new ClientHttpEssai(new ControleurDemandesEffectifs(garde, new LectureDemandes(depot, referentiels, ressources, perimetres),
                new GestionnaireDemandes(new GestionnaireDemandes.Lectures(ressources, referentiels, perimetres), new GestionnaireDemandes.Ecritures(depot, journal, seq))));
        ref("unites", "EST", null);
        ref("unites", "OUEST", null);
        ref("types_demande", "LISTE-TS", "LISTE_TEMPS_SUPPLEMENTAIRE");
        ref("types_demande", "FORMATION", "FORMATION");
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, anciennete, creeLe) VALUES "
                + "('rs-julie', ?, 'P1', 'Julie Roy', 'usr-julie', 'EST', 'PARA', 4, ?), ('rs-marc', ?, 'P2', 'Marc Dubé', 'usr-marc', 'EST', 'PARA', 9, ?)")
                .params(T, CREE, T, CREE).update();
        jdbc.sql("INSERT INTO eff_perimetres (id, tenantId, utilisateurId, uniteCode, etendue, niveau, depuis, creeLe) VALUES ('per-1', ?, 'usr-chef', 'EST', 'UNITE', 'GESTION', '2026-01-01', ?)")
                .params(T, CREE).update();
    }

    private String soumettre(ContexteRequete qui, String json) throws Exception {
        return JsonPath.read(http.post(API, qui, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
    }

    @Test
    @DisplayName("la personne soumet et suit ; l'encadrement de l'unité accepte, ou refuse en motivant ; la liste du jour suit l'ancienneté")
    void parcours() throws Exception {
        http.get(API + "/types", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[*].code", Matchers.containsInAnyOrder("LISTE-TS", "FORMATION")));
        String ts = soumettre(JULIE, "{\"typeCode\":\"LISTE-TS\",\"du\":\"2027-02-01\",\"au\":\"2027-02-28\",\"commentaire\":\" Soirs de semaine \"}");
        String tsMarc = soumettre(MARC, "{\"typeCode\":\"LISTE-TS\",\"du\":\"2027-02-10\",\"au\":\"2027-02-20\"}");
        String formation = soumettre(JULIE, "{\"typeCode\":\"FORMATION\",\"du\":\"2027-03-01\",\"au\":\"2027-03-02\"}");
        http.get(API + "/miennes", JULIE).andExpect(jsonPath("$", Matchers.hasSize(2))).andExpect(jsonPath("$[1].commentaire").value("Soirs de semaine"))
                .andExpect(jsonPath("$[0].statut").value("EN_ATTENTE")).andExpect(jsonPath("$[0].ressourceNom").value("Julie Roy"));

        http.get(API + "?statut=EN_ATTENTE", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(3)));
        http.get(API, AUTRE_CHEF).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.post(API + "/" + ts + "/decision", AUTRE_CHEF, "{\"decision\":\"ACCEPTER\"}").andExpect(status().isForbidden());
        http.post(API + "/" + ts + "/decision", JULIE, "{\"decision\":\"ACCEPTER\"}").andExpect(status().isForbidden());
        http.post(API + "/" + ts + "/decision", CHEF, "{\"decision\":\"ACCEPTER\"}").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ACCEPTEE"));
        http.post(API + "/" + tsMarc + "/decision", CHEF, "{\"decision\":\"ACCEPTER\"}").andExpect(status().isOk());
        http.post(API + "/" + formation + "/decision", CHEF, "{\"decision\":\"REFUSER\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« motif » : un refus se motive"));
        http.post(API + "/" + formation + "/decision", CHEF, "{\"decision\":\"REFUSER\",\"motif\":\"Quota de formation atteint\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE")).andExpect(jsonPath("$.motifDecision").value("Quota de formation atteint"));
        http.post(API + "/" + ts + "/decision", CHEF, "{\"decision\":\"REFUSER\",\"motif\":\"x\"}").andExpect(status().isConflict());
        http.post(API + "/" + ts + "/decision", CHEF, "{\"decision\":\"PEUT-ETRE\"}").andExpect(status().is(422));

        // Le 15 février, Marc (9 ans) passe avant Julie (4 ans) ; le 5, Julie seule ; la formation refusée n'y est jamais.
        http.get(API + "/liste-ts?date=2027-02-15", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$[*].ressourceNom", Matchers.contains("Marc Dubé", "Julie Roy")));
        http.get(API + "/liste-ts?date=2027-02-05", ADMIN).andExpect(jsonPath("$[*].ressourceNom", Matchers.contains("Julie Roy")));
        http.get(API + "/liste-ts?date=2027-03-01", CHEF).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(API + "/liste-ts?date=demain", CHEF).andExpect(status().is(422));
        http.get(API + "/liste-ts", JULIE).andExpect(status().isForbidden());
        http.get(API + "?statut=INCONNU", CHEF).andExpect(status().is(422));
        http.get(API + "?statut=ACCEPTEE", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)));
    }

    @Test
    @DisplayName("annuler : la sienne, tant qu'elle attend ; une saisie fautive est refusée en nommant le champ")
    void annulationEtSaisie() throws Exception {
        String id = soumettre(JULIE, "{\"typeCode\":\"FORMATION\",\"du\":\"2027-03-01\",\"au\":\"2027-03-01\"}");
        http.post(API + "/" + id + "/annuler", MARC, "{}").andExpect(status().isNotFound());
        http.post(API + "/" + id + "/annuler", JULIE, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("ANNULEE"));
        http.post(API + "/" + id + "/annuler", JULIE, "{}").andExpect(status().isConflict());
        http.post(API + "/inconnue/annuler", JULIE, "{}").andExpect(status().isNotFound());
        http.post(API + "/inconnue/decision", CHEF, "{\"decision\":\"ACCEPTER\"}").andExpect(status().isNotFound());
        http.post(API, JULIE, "{\"du\":\"2027-03-01\",\"au\":\"2027-03-01\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« typeCode » requis"));
        http.post(API, JULIE, "{\"typeCode\":\"FORMATION\",\"du\":\"2027-03-02\",\"au\":\"2027-03-01\"}").andExpect(status().is(422));
        http.post(API, JULIE, "{\"typeCode\":\"INCONNU\",\"du\":\"2027-03-01\",\"au\":\"2027-03-01\"}").andExpect(status().is4xxClientError());
        http.post(API, JULIE, "{\"typeCode\":\"FORMATION\",\"du\":\"2027-03-01\",\"au\":\"2027-03-01\",\"commentaire\":\"" + "x".repeat(501) + "\"}").andExpect(status().is(422));
        http.post(API, null, "{}").andExpect(status().isUnauthorized());
        jdbc.sql("UPDATE tenant_quotas SET modules = '[\"effectifs\"]'").update();
        http.get(API + "/miennes", JULIE).andExpect(status().isForbidden());
    }
}
