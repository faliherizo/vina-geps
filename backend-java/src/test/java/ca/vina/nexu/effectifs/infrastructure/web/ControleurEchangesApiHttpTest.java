package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesApi;
import ca.vina.nexu.effectifs.application.commandes.GestesEchangesApi;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPresenceEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotRessources;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotSoldesBanques;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPresenceEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotEchangesApi;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaTempsEffectifs;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.web.GardeMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Path;
import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/echanges/v1} contre une vraie base : 26-015-DO, addenda 5 (a, b, e) — quarts planifiés et correctifs,
 * relevés de présence validés, lus par une application de l'organisme. Une session n'y entre pas ; un organisme ne lit
 * jamais l'autre ; une période en confection ne sort pas.
 */
@DisplayName("API d'échange de l'organisme (AD-06) : lectures machine")
class ControleurEchangesApiHttpTest {

    private static final String SECRET = "secret-d-essai-de-l-api-d-echange-assez-long";
    private static final String T = "t-1";
    private static final String AUTRE = "t-2";
    private static final JetonClient JETONS = new JetonClient(SECRET);
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-21T15:00:00Z"), ZoneOffset.UTC);

    private JdbcClient jdbc;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        new SchemaEffectifs(jdbc);
        new SchemaPlanification(jdbc);
        new SchemaTempsEffectifs(jdbc);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, '[\"effectifs\"]'), (?, -1, -1, '[]')")
                .params(T, AUTRE).update();
        semer();
        http = new ClientHttpEssai(new ControleurEchangesApi(new GardeMachine(JETONS), new AdaptateurModulesPourEffectifs(jdbc),
                new ControleurEchangesApi.Services(new LectureEchangesApi(new DepotEchangesApi(jdbc, null, null)), gestes(), HORLOGE)));
    }

    /** Les écritures passent par le vrai gestionnaire des ressources : mêmes référentiels et même journal que l'écran. */
    private GestesEchangesApi gestes() {
        AtomicInteger n = new AtomicInteger();
        Sequence seq = new Sequence() {
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
                return "jeton";
            }
        };
        SchemaEffectifs socle = new SchemaEffectifs(jdbc);
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, socle);
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(new DepotPerimetres(jdbc, socle), referentiels);
        DepotRessources depotRes = new DepotRessources(jdbc, new SchemaPlanification(jdbc));
        LectureRessources lecture = new LectureRessources(depotRes, perimetres);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, socle);
        GestionnaireRessources ressources = new GestionnaireRessources((t, inscription) -> { }, depotRes, lecture, referentiels, perimetres, journal, seq);
        return new GestesEchangesApi(new GestesEchangesApi.Ports(ressources, lecture,
                new DepotPresenceEffectifs(jdbc, new SchemaPresenceEffectifs(jdbc), new SchemaPlanification(jdbc), socle), new DepotSoldesBanques(jdbc, socle)),
                new GestesEchangesApi.Absences(null, new DepotEchangesApi(jdbc, null, null)), journal);
    }

    /** Deux quarts publiés et un en confection pour t-1, un publié pour t-2 ; journal et feuilles à l'avenant. */
    private void semer() {
        jdbc.sql("INSERT INTO eff_periodes (id, tenantId, uniteCode, nom, debut, fin, etat, creeLe) VALUES "
                + "('per-pub', ?, 'U1', 'Déc.', '2026-12-01', '2026-12-31', 'PUBLIEE', 'x'), ('per-conf', ?, 'U1', 'Janv.', '2027-01-01', '2027-01-31', 'CONFECTION', 'x'), "
                + "('per-autre', ?, 'U9', 'Déc.', '2026-12-01', '2026-12-31', 'PUBLIEE', 'x')").params(T, T, AUTRE).update();
        String equipage = "INSERT INTO eff_equipages (id, tenantId, periodeId, uniteCode, date, debut, fin, natureCode, milieuId, modeAttribution, minimumOperationnel, creeLe) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'JOUR', 'm-1', 'MANUEL', 1, 'x')";
        jdbc.sql(equipage).params("eq-2", T, "per-pub", "U1", "2026-12-02", 1140, 420).update();
        jdbc.sql(equipage).params("eq-1", T, "per-pub", "U1", "2026-12-01", 420, 1140).update();
        jdbc.sql(equipage).params("eq-conf", T, "per-conf", "U1", "2027-01-05", 420, 1140).update();
        jdbc.sql(equipage).params("eq-autre", AUTRE, "per-autre", "U9", "2026-12-01", 420, 1140).update();
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, uniteCode, corpsEmploiCode, creeLe) VALUES ('r-1', ?, 'M-100', 'Léa', 'U1', 'PARA', 'x')")
                .params(T).update();
        String referentiel = "INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, effectifDepuis, creeLe, creePar) VALUES (?, ?, ?, ?, ?, '2020-01-01', 'x', 'u')";
        jdbc.sql(referentiel).params("ref-u1", T, "unites", "U1", "Unité 1").update();
        jdbc.sql(referentiel).params("ref-para", T, "corps_emploi", "PARA", "Paramédic").update();
        String place = "INSERT INTO eff_places (id, tenantId, equipageId, rang, corpsEmploiCode, ressourceId) VALUES (?, ?, ?, ?, 'PARA', ?)";
        jdbc.sql(place).params("pl-2", T, "eq-1", 2, null).update();
        jdbc.sql(place).params("pl-1", T, "eq-1", 1, "r-1").update();
        jdbc.sql(place).params("pl-conf", T, "eq-conf", 1, "r-1").update();
        String journal = "INSERT INTO eff_journal (id, tenantId, horodatage, acteurId, origine, action, objetType, objetId) VALUES (?, ?, ?, 'u', 'UI', ?, ?, ?)";
        jdbc.sql(journal).params("j-1", T, "2026-11-20T10:00:00.000Z", "equipage.cree", "equipage", "eq-1").update();
        jdbc.sql(journal).params("j-2", T, "2026-11-20T11:00:00.000Z", "absence.declaree", "absence", "a-1").update();
        jdbc.sql(journal).params("j-3", T, "2026-11-20T12:00:00.000Z", "place.attribuee", "place", "pl-conf").update();
        jdbc.sql(journal).params("j-4", T, "2026-11-20T13:00:00.000Z", "place.attribuee", "place", "pl-1").update();
        jdbc.sql(journal).params("j-5", AUTRE, "2026-11-20T14:00:00.000Z", "equipage.cree", "equipage", "eq-autre").update();
        String feuille = "INSERT INTO eff_feuilles (id, tenantId, ressourceId, uniteCode, debut, fin, statut, paliersRequis, approuveeLe, creeLe, creePar) "
                + "VALUES (?, ?, 'r-1', 'U1', ?, '2026-11-14', ?, 1, ?, 'x', 'u')";
        jdbc.sql(feuille).params("f-2", T, "2026-10-18", "PAYEE", "2026-11-18T09:00:00.000Z").update();
        jdbc.sql(feuille).params("f-1", T, "2026-11-01", "APPROUVEE", "2026-11-17T09:00:00.000Z").update();
        jdbc.sql(feuille).params("f-sou", T, "2026-11-15", "SOUMISE", null).update();
        jdbc.sql(feuille).params("f-autre", AUTRE, "2026-11-01", "APPROUVEE", "2026-11-17T09:00:00.000Z").update();
        String segment = "INSERT INTO eff_segments_temps (id, tenantId, feuilleId, placeId, date, debut, fin, typeHeureCode, source, version, actif, creeLe, creePar) "
                + "VALUES (?, ?, ?, 'pl-1', '2026-11-03', ?, ?, 'REG', 'POINTAGE', 1, ?, 'x', 'u')";
        jdbc.sql(segment).params("s-1", T, "f-1", "2026-11-03T12:00:00.000Z", "2026-11-03T20:00:00.000Z", 1).update();
        jdbc.sql(segment).params("s-0", T, "f-1", "2026-11-03T11:00:00.000Z", "2026-11-03T11:30:00.000Z", 0).update();
    }

    private ResultActions lire(String chemin, String jeton) throws Exception {
        return http.envoyer(jeton == null ? get(chemin) : get(chemin).header("Authorization", "Bearer " + jeton), null);
    }

    private static String machine(String tenant, String... portees) {
        return JETONS.emettre("cli-rao", tenant, List.of(portees));
    }

    @Test
    @DisplayName("quarts : publiés seulement, triés, avec leurs places et le matricule de la personne")
    void quarts() throws Exception {
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2027-01-31", machine(T, "read")).andExpect(status().isOk())
                .andExpect(jsonPath("$.quarts[*].id", contains("eq-1", "eq-2")))
                .andExpect(jsonPath("$.quarts[0].debutMinutes").value(420))
                .andExpect(jsonPath("$.quarts[0].places[*].id", contains("pl-1", "pl-2")))
                .andExpect(jsonPath("$.quarts[0].places[0].matricule").value("M-100"))
                .andExpect(jsonPath("$.quarts[0].places[1].ressourceId").value(nullValue()))
                .andExpect(jsonPath("$.quarts[1].finMinutes").value(420))
                .andExpect(jsonPath("$.quarts[1].places", hasSize(0)));
    }

    @Test
    @DisplayName("correctifs : seuls les quarts exposés, l'état présent joint, le curseur avance même sur un quart non exposé")
    void modifications() throws Exception {
        lire("/api/echanges/v1/quarts/modifications", machine(T, "read")).andExpect(status().isOk())
                .andExpect(jsonPath("$.modifications[*].objetId", contains("eq-1", "pl-1")))
                .andExpect(jsonPath("$.modifications[1].quart.id").value("eq-1"))
                .andExpect(jsonPath("$.modifications[1].curseur").value("2026-11-20T13:00:00.000Zj-4"))
                .andExpect(jsonPath("$.curseur").value("2026-11-20T13:00:00.000Zj-4"));
        lire("/api/echanges/v1/quarts/modifications?limite=2", machine(T, "read"))
                .andExpect(jsonPath("$.modifications[*].objetId", contains("eq-1")))
                .andExpect(jsonPath("$.curseur").value("2026-11-20T12:00:00.000Zj-3"));
        lire("/api/echanges/v1/quarts/modifications?depuis=2026-11-20T13:00:00.000Zj-4", machine(T, "read"))
                .andExpect(jsonPath("$.modifications", hasSize(0)))
                .andExpect(jsonPath("$.curseur").value("2026-11-20T13:00:00.000Zj-4"));
    }

    @Test
    @DisplayName("relevés : feuilles approuvées ou payées, dans l'ordre de validation, segments actifs seulement")
    void releves() throws Exception {
        lire("/api/echanges/v1/releves-presence", machine(T, "read")).andExpect(status().isOk())
                .andExpect(jsonPath("$.releves[*].feuilleId", contains("f-1", "f-2")))
                .andExpect(jsonPath("$.releves[0].matricule").value("M-100"))
                .andExpect(jsonPath("$.releves[0].segments[*].debut", contains("2026-11-03T12:00:00.000Z")))
                .andExpect(jsonPath("$.releves[1].segments", hasSize(0)))
                .andExpect(jsonPath("$.curseur").value("2026-11-18T09:00:00.000Zf-2"));
        lire("/api/echanges/v1/releves-presence?depuis=2026-11-17T09:00:00.000Zf-1&limite=1", machine(T, "read"))
                .andExpect(jsonPath("$.releves[*].feuilleId", contains("f-2")));
        lire("/api/echanges/v1/releves-presence?depuis=2026-11-18T09:00:00.000Zf-2", machine(T, "read"))
                .andExpect(jsonPath("$.releves", hasSize(0))).andExpect(jsonPath("$.curseur").value("2026-11-18T09:00:00.000Zf-2"));
    }

    @Test
    @DisplayName("isolation : l'organisme est celui du jeton ; t-2, module coupé, est refusé")
    void isolation() throws Exception {
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2026-12-31", machine(AUTRE, "read")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs' non activé pour cet organisme"));
        jdbc.sql("UPDATE tenant_quotas SET modules = '[\"effectifs\"]' WHERE tenantId = ?").params(AUTRE).update();
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2026-12-31", machine(AUTRE, "read"))
                .andExpect(jsonPath("$.quarts[*].id", contains("eq-autre")));
        lire("/api/echanges/v1/releves-presence", machine(AUTRE, "read")).andExpect(jsonPath("$.releves[*].feuilleId", contains("f-autre")));
        // Rien à lire et aucun curseur reçu : la page n'en invente pas.
        jdbc.sql("DELETE FROM eff_feuilles WHERE tenantId = ?").params(AUTRE).update();
        lire("/api/echanges/v1/releves-presence", machine(AUTRE, "read")).andExpect(jsonPath("$.releves", hasSize(0)))
                .andExpect(jsonPath("$.curseur").doesNotExist());
    }

    @Test
    @DisplayName("refus : sans jeton ou jeton de session 401 ; portée write seule 403 ; paramètres invalides 422 nommés")
    void refus() throws Exception {
        String session = new JetonDeSession(SECRET).emettre(new JetonDeSession.Revendications("u", "julie", "admin", null, T));
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2026-12-31", null).andExpect(status().isUnauthorized());
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2026-12-31", session).andExpect(status().isUnauthorized());
        lire("/api/echanges/v1/quarts?du=2026-12-01&au=2026-12-31", machine(T, "write")).andExpect(status().isForbidden());
        lire("/api/echanges/v1/quarts?du=2026-12-01", machine(T, "read")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("« au » : date AAAA-MM-JJ exigée"));
        lire("/api/echanges/v1/releves-presence?limite=0", machine(T, "read")).andExpect(status().isUnprocessableEntity());
    }

    private ResultActions ecrire(String chemin, String jeton, String corps) throws Exception {
        return http.envoyer(put(chemin).header("Authorization", "Bearer " + jeton).contentType("application/json").content(corps), null);
    }

    @Test
    @DisplayName("employés : créé (201) puis mis à jour (200) par son matricule, au journal sous le nom de l'application")
    void employes() throws Exception {
        String jeton = machine(T, "read", "write");
        String corps = "{\"nom\":\"Marc Roy\",\"uniteCode\":\"U1\",\"corpsEmploiCode\":\"PARA\",\"anciennete\":4.5}";
        ecrire("/api/echanges/v1/employes/M-200", jeton, corps).andExpect(status().isCreated())
                .andExpect(jsonPath("$.matricule").value("M-200")).andExpect(jsonPath("$.nom").value("Marc Roy"));
        ecrire("/api/echanges/v1/employes/M-200", jeton, corps.replace("Marc Roy", "Marc Roy-Tremblay")).andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Marc Roy-Tremblay"));
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT DISTINCT acteurId FROM eff_journal WHERE objetType = 'ressource'").query(String.class).list())
                .containsExactly("application:cli-rao");
        ecrire("/api/echanges/v1/employes/M-201", jeton, "{\"nom\":\"X\",\"uniteCode\":\"U9\",\"corpsEmploiCode\":\"PARA\"}")
                .andExpect(status().is4xxClientError());
        ecrire("/api/echanges/v1/employes/M-200", machine(T, "read"), corps).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("portée « write » exigée"));
    }

    @Test
    @DisplayName("soldes : posés banque par banque, relus ; employé inconnu 404 ; envoi invalide 422 qui nomme chaque écart")
    void soldes() throws Exception {
        String jeton = machine(T, "read", "write");
        ecrire("/api/echanges/v1/employes/M-100/soldes", jeton, "{\"soldes\":[{\"banque\":\"vac\",\"heures\":70.5,\"au\":\"2026-11-20\"},"
                + "{\"banque\":\"MAL\",\"heures\":-3,\"au\":\"2026-11-20\"}]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.soldes[*].banqueCode", contains("MAL", "VAC")));
        ecrire("/api/echanges/v1/employes/M-100/soldes", jeton, "{\"soldes\":[{\"banque\":\"VAC\",\"heures\":56,\"au\":\"2026-11-21\"}]}")
                .andExpect(jsonPath("$.soldes[*].heures", contains(-3.00, 56.00)));
        lire("/api/echanges/v1/employes/M-100/soldes", machine(T, "read")).andExpect(status().isOk())
                .andExpect(jsonPath("$.soldes[1].auJour").value("2026-11-21"))
                .andExpect(jsonPath("$.soldes[1].recuLe").value("2026-11-21T15:00:00.000Z"));
        ecrire("/api/echanges/v1/employes/INCONNU/soldes", jeton, "{\"soldes\":[{\"banque\":\"VAC\",\"heures\":1,\"au\":\"2026-11-20\"}]}")
                .andExpect(status().isNotFound());
        ecrire("/api/echanges/v1/employes/M-100/soldes", jeton, "{\"soldes\":[{\"banque\":\"\",\"heures\":\"x\",\"au\":\"demain\"}]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0]").value("soldes[0].banque : code de 1 à 40 lettres, chiffres, tirets ou soulignés ; "
                        + "soldes[0].heures : nombre de -99999 à 99999 ; soldes[0].au : date AAAA-MM-JJ du solde"));
        ecrire("/api/echanges/v1/employes/M-100/soldes", jeton, "{}").andExpect(status().isUnprocessableEntity());
    }
}
