package ca.vina.nexu.soutien.infrastructure.web;

import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import ca.vina.nexu.soutien.domaine.Soutien;
import ca.vina.nexu.soutien.infrastructure.persistance.AvisSoutienJournal;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.soutien.infrastructure.configuration.ConfigurationSoutien;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.soutien.domaine.AvisSoutien;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `soutien.integration.test.ts` : billets par auteur, soutien superadmin, délais, maintenances à 72 h, disponibilité. */
@DisplayName("API soutien — billets, maintenances, disponibilité")
class ControleurSoutienHttpTest {

    /** Une horloge que l'essai avance : les délais et le préavis se jugent à la minute. */
    static final class HorlogeEssai extends Clock {
        Instant instant = Instant.parse("2026-09-12T10:00:00Z");

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
            return instant;
        }
    }

    private static final ContexteRequete ALICE = new ContexteRequete("usr-alice", "alice", "employee", "emp-alice", "t-1");
    private static final ContexteRequete BOB = new ContexteRequete("usr-bob", "bob", "employee", "emp-bob", "t-1");
    private static final ContexteRequete SA = ClientHttpEssai.superadmin();
    private final HorlogeEssai horloge = new HorlogeEssai();
    private ClientHttpEssai http;
    private DepotSoutien depot;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        avis = new AvisSoutienJournal();
        depot = new DepotSoutien(jdbc, horloge, avis, Map.of("SOUTIEN_COURRIEL", " Soutien@Partage.qc.ca "));
        ConfigurationSoutien config = new ConfigurationSoutien();
        http = new ClientHttpEssai(new ControleurSoutien(config.lectureSoutien(depot, () -> Iso.de(horloge.instant()), t -> EngagementsDeService.Politique.STANDARD), config.gestionnaireSoutien(depot), new GardeAcces(new RegistrePolitiqueAcces())));
    }

    private AvisSoutienJournal avis;

    @Test
    void chaqueBilletEnvoyeAviseLeSoutienEtSeLitParEtablissement() throws Exception {
        jdbc.sql("INSERT INTO tenants (id, name) VALUES ('t-1', 'CSS des Chênes')").update();
        jdbc.sql("INSERT INTO users (id, username, passwordHash, role, createdAt, active, email) VALUES ('sa-1', 'sa', 'x', 'superadmin', 'd', 1, 'Soutien@NexuVina.qc.ca'), "
                + "('sa-2', 'sa2', 'x', 'superadmin', 'd', 1, NULL), ('sa-3', 'sa3', 'x', 'superadmin', 'd', 0, 'inactif@x.qc'), ('a-1', 'admin', 'x', 'admin', 'd', 1, 'admin@x.qc')").update();
        assertThat(depot.destinatairesSoutien()).containsExactly("soutien@nexuvina.qc.ca", "soutien@partage.qc.ca");
        assertThat(new DepotSoutien(jdbc, horloge).destinatairesSoutien()).containsExactly("soutien@nexuvina.qc.ca");

        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Connexion impossible\",\"description\":\"Toute l'école est bloquée depuis 8 h.\",\"niveau\":\"N1\"}").andExpect(status().isCreated());
        assertThat(avis.envoyes()).hasSize(2);
        AvisSoutien.Courriel c = avis.envoyes().get(0);
        assertThat(c.to()).isEqualTo("soutien@nexuvina.qc.ca");
        assertThat(c.subject()).isEqualTo("[Soutien] Nouveau billet N1 — CSS des Chênes : Connexion impossible");
        assertThat(c.text()).contains("Établissement : CSS des Chênes").contains("Auteur : alice").contains("N1 — Service interrompu").contains("Toute l'école est bloquée depuis 8 h.")
                .contains("(échéance 2026-09-12T11:00:00.000Z)");
        String id = depot.listerTickets(new Soutien.Acteur("usr-sa", "sa", null, true), null).get(0).id();
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\"Toujours bloqués à 9 h.\"}").andExpect(status().isCreated());
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"On regarde.\"}").andExpect(status().isCreated());
        assertThat(avis.envoyes()).hasSize(4);
        assertThat(avis.envoyes().get(2).subject()).isEqualTo("[Soutien] Réponse de l'auteur — CSS des Chênes : Connexion impossible");
        assertThat(avis.envoyes().get(2).text()).contains("Toujours bloqués à 9 h.");
        // Sans destinataire, rien ne part ; un compte système est nommé ainsi.
        jdbc.sql("DELETE FROM users").update();
        new DepotSoutien(jdbc, horloge, avis, Map.of()).ouvrirTicket(new Soutien.Acteur("sys", "sys", null, false), Map.of("sujet", "Question", "description", "Une question de plus de dix caractères.", "niveau", "N4"));
        assertThat(avis.envoyes()).hasSize(4);
        assertThat(AvisSoutien.composer("x", new AvisSoutien.Billet("b", "S", "D", "N9", "a", "e"), "compte système", false, null).text()).contains("Niveau : N9 — N9").contains("\n\n\n");

        http.get("/api/soutien/tickets/par-etablissement", ALICE).andExpect(status().isForbidden());
        // À charge égale, l'ordre est alphabétique en français : « compte système » précède « CSS des Chênes ».
        http.get("/api/soutien/tickets/par-etablissement", SA).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nom").value("compte système")).andExpect(jsonPath("$[0].tenantId").isEmpty())
                .andExpect(jsonPath("$[1].nom").value("CSS des Chênes")).andExpect(jsonPath("$[1].total").value(1)).andExpect(jsonPath("$[1].ouverts").value(1))
                .andExpect(jsonPath("$[1].enRetard").value(0)).andExpect(jsonPath("$[1].tickets[0].id").value(id));
        // Un retard fait passer l'établissement devant ; un billet fermé ne compte plus comme ouvert.
        horloge.instant = Instant.parse("2026-09-12T13:00:00Z");
        jdbc.sql("INSERT INTO tenants (id, name) VALUES ('t-2', 'Autre CSS')").update();
        depot.ouvrirTicket(new Soutien.Acteur("bob", "bob", "t-2", false), Map.of("sujet", "Nouveau", "description", "Une description assez longue.", "niveau", "N4"));
        http.get("/api/soutien/tickets/par-etablissement", SA).andExpect(jsonPath("$[0].nom").value("CSS des Chênes")).andExpect(jsonPath("$[0].enRetard").value(1))
                .andExpect(jsonPath("$[1].nom").value("Autre CSS"));
        assertThatThrownBy(() -> depot.listerParEtablissement(new Soutien.Acteur("x", "x", null, false))).hasMessage("réservé au soutien");
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    void billetsDelaisEtMessages() throws Exception {
        http.get("/api/soutien/politique", null).andExpect(status().isOk()).andExpect(jsonPath("$.langue").value("fr")).andExpect(jsonPath("$.niveaux[*].delaiResolutionMinutes", contains(60, 120, 240, 1440)));
        http.get("/api/soutien/tickets", null).andExpect(status().isUnauthorized());
        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"abc\",\"description\":\"court\",\"niveau\":\"P1\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("sujet requis (5 caractères au moins); description requise")));
        http.post("/api/soutien/tickets", ALICE, null).andExpect(status().isBadRequest());
        // Un billet N2 a son échéance à deux heures.
        MvcResult t = http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\" Export Excel vide \",\"description\":\"Le classeur des statistiques n'a aucune ligne.\",\"niveau\":\"N2\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.statut").value("nouveau")).andExpect(jsonPath("$.ouvertLe").value("2026-09-12T10:00:00.000Z"))
                .andExpect(jsonPath("$.echeanceLe").value("2026-09-12T12:00:00.000Z")).andExpect(jsonPath("$.sla.etat").value("dans_les_delais")).andExpect(jsonPath("$.sla.ecartMinutes").value(120))
                .andExpect(jsonPath("$.auteurNom").value("alice")).andExpect(jsonPath("$.sujet").value("Export Excel vide")).andExpect(jsonPath("$.tenantId").value("t-1")).andReturn();
        String id = champ(t, "id");
        // L'auteur voit son billet ; un autre employé ne le voit pas ; le soutien voit tout.
        http.get("/api/soutien/tickets/" + id, ALICE).andExpect(status().isOk());
        http.get("/api/soutien/tickets/" + id, BOB).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("billet introuvable"));
        http.get("/api/soutien/tickets", BOB).andExpect(jsonPath("$", hasSize(0)));
        http.get("/api/soutien/tickets", SA).andExpect(jsonPath("$[*].id", contains(id)));
        // Le soutien répond : le billet passe en cours ; une note interne n'atteint pas l'auteur ; l'auteur ne change pas le statut.
        horloge.instant = Instant.parse("2026-09-12T10:30:00Z");
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"Nous reproduisons le problème.\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.statut").value("en_cours")).andExpect(jsonPath("$.prisEnChargeLe").value("2026-09-12T10:30:00.000Z")).andExpect(jsonPath("$.traitePar").value("usr-sa"));
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"Cause probable : index absent.\",\"interne\":true}").andExpect(status().isCreated()).andExpect(jsonPath("$.messages", hasSize(2)));
        http.get("/api/soutien/tickets/" + id, ALICE).andExpect(jsonPath("$.messages[*].texte", contains("Nous reproduisons le problème.")));
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\"Merci !\",\"interne\":true}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("une note interne est réservée au soutien"));
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("message requis (5000 caractères au plus)"));
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, null).andExpect(status().isBadRequest());
        http.post("/api/soutien/tickets/" + id + "/messages", BOB, "{\"texte\":\"je m'incruste\"}").andExpect(status().isNotFound());
        http.post("/api/soutien/tickets/" + id + "/statut", ALICE, "{\"statut\":\"resolu\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]", containsString("réservé au soutien")));
        // Résolu à temps, puis fermé par l'auteur ; un billet fermé ne reçoit plus de message.
        horloge.instant = Instant.parse("2026-09-12T11:00:00Z");
        http.post("/api/soutien/tickets/" + id + "/statut", SA, "{\"statut\":\"resolu\"}").andExpect(status().isOk()).andExpect(jsonPath("$.sla.etat").value("resolu_a_temps")).andExpect(jsonPath("$.sla.ecartMinutes").value(60));
        http.post("/api/soutien/tickets/" + id + "/statut", SA, "{\"statut\":\"nouveau\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("transition impossible : resolu → nouveau"));
        http.post("/api/soutien/tickets/" + id + "/statut", ALICE, "{\"statut\":\"ferme\"}").andExpect(status().isOk()).andExpect(jsonPath("$.fermeLe").value("2026-09-12T11:00:00.000Z")).andExpect(jsonPath("$.traitePar").value("usr-sa"));
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\"encore ?\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("le billet est fermé : ouvrez-en un nouveau"));
        http.post("/api/soutien/tickets/" + id + "/statut", SA, null).andExpect(status().isConflict());
        http.get("/api/soutien/tickets?statut=ferme", ALICE).andExpect(jsonPath("$", hasSize(1)));
        http.get("/api/soutien/tickets?statut=nouveau", SA).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unBilletResoluTropTardResteEnRetardEtLeRouvrirEffaceLaResolution() {
        Soutien.Acteur bob = new Soutien.Acteur("usr-bob", "bob", "t-1", false);
        Soutien.Acteur sa = new Soutien.Acteur("usr-sa", "sa", null, true);
        Soutien.Ticket t = depot.ouvrirTicket(bob, Map.of("sujet", "Connexion impossible", "description", "Le bouton Connexion ne répond plus depuis 9 h.", "niveau", "N1"));
        horloge.instant = Instant.parse("2026-09-12T11:30:00Z");
        Soutien.Ticket resolu = depot.changerStatut(sa, t.id(), "resolu");
        assertThat(resolu.sla()).isEqualTo(new ca.vina.nexu.soutien.domaine.Soutien.Sla("resolu_en_retard", -30));
        Soutien.Ticket rouvert = depot.changerStatut(sa, t.id(), "en_cours");
        assertThat(rouvert.resoluLe()).isNull();
        assertThat(rouvert.sla().etat()).isEqualTo("en_retard");
        assertThatThrownBy(() -> depot.changerStatut(bob, t.id(), "ferme")).isInstanceOf(ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException.class).hasMessageContaining("réservé au soutien");
        assertThatThrownBy(() -> depot.changerStatut(sa, "inexistant", "ferme")).isInstanceOf(ca.vina.nexu.partage.erreurs.domaine.IntrouvableException.class);
        assertThat(depot.listerTickets(sa, "en_cours").stream().map(Soutien.Ticket::id)).containsExactly(t.id());
        assertThat(depot.listerTickets(bob, "ferme")).isEmpty();
        assertThat(depot.listerTickets(sa, null).stream().map(Soutien.Ticket::id)).contains(t.id());
        assertThatThrownBy(() -> depot.repondre(bob, "inexistant", "x", false)).isInstanceOf(ca.vina.nexu.partage.erreurs.domaine.IntrouvableException.class);
        assertThatThrownBy(() -> depot.repondre(bob, t.id(), "", false)).hasMessageContaining("message requis");
        // Une réponse de l'auteur ne change pas le statut ; une réponse interne du soutien à un billet neuf non plus.
        Soutien.Ticket neuf = depot.ouvrirTicket(bob, Map.of("sujet", "Question sur l'export", "description", "Le CSV est-il en UTF-8 avec BOM ?", "niveau", "N4"));
        assertThat(depot.repondre(bob, neuf.id(), "Précision : sous Excel.", false).statut()).isEqualTo("nouveau");
        assertThat(depot.repondre(sa, neuf.id(), "note interne", true).statut()).isEqualTo("nouveau");
        assertThat(depot.lireTicket(bob, neuf.id()).orElseThrow().messages()).hasSize(1);
    }

    @Test
    void maintenancesA72hEtDisponibiliteMesuree() throws Exception {
        http.post("/api/soutien/maintenances", SA, "{\"debut\":\"2026-09-13T02:00:00.000Z\",\"fin\":\"2026-09-13T04:00:00.000Z\",\"description\":\"Migration de la base de données\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("préavis insuffisant : 72 h au moins avant le début (il en reste 16)"));
        http.post("/api/soutien/maintenances", ALICE, "{}").andExpect(status().isForbidden());
        http.post("/api/soutien/maintenances", null, "{}").andExpect(status().isUnauthorized());
        http.post("/api/soutien/maintenances", SA, null).andExpect(status().isBadRequest());
        MvcResult ok = http.post("/api/soutien/maintenances", SA, "{\"debut\":\"2026-09-16T02:00\",\"fin\":\"2026-09-16T04:00:00.000Z\",\"description\":\" Migration de la base de données \"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.debut").value("2026-09-16T02:00:00.000Z")).andExpect(jsonPath("$.statut").value("planifiee"))
                .andExpect(jsonPath("$.description").value("Migration de la base de données")).andExpect(jsonPath("$.annonceePar").value("usr-sa")).andReturn();
        String id = champ(ok, "id");
        http.get("/api/soutien/maintenances", ALICE).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
        assertThat(depot.prochaineMaintenance().map(Soutien.Maintenance::id)).contains(id);
        assertThat(depot.maintenanceEnCours()).isEmpty();
        horloge.instant = Instant.parse("2026-09-16T03:00:00Z");
        assertThat(depot.maintenanceEnCours().map(Soutien.Maintenance::id)).contains(id);
        assertThat(depot.prochaineMaintenance()).isEmpty();
        horloge.instant = Instant.parse("2026-09-12T10:00:00Z");
        http.post("/api/soutien/maintenances/" + id + "/annuler", SA, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("annulee"));
        http.post("/api/soutien/maintenances/" + id + "/annuler", SA, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("maintenance introuvable ou déjà annulée"));
        http.post("/api/soutien/maintenances/" + id + "/annuler", ALICE, null).andExpect(status().isForbidden());
        assertThat(depot.prochaineMaintenance()).isEmpty();
        // Disponibilité : un battement par minute, la même minute battue deux fois ne compte qu'une ; maintenance exclue.
        for (String minute : new String[] {"10:00", "10:00", "10:01", "10:02", "10:04"}) {
            horloge.instant = Instant.parse("2026-09-12T" + minute + ":20Z");
            depot.battre(1.4);
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM soutien_battements").query(Integer.class).single()).isEqualTo(4);
        assertThat(jdbc.sql("SELECT latenceMs FROM soutien_battements WHERE minute = '2026-09-12T10:00'").query(Integer.class).single()).isEqualTo(1);
        horloge.instant = Instant.parse("2026-09-12T10:06:00Z");
        http.get("/api/soutien/disponibilite", ALICE).andExpect(status().isForbidden());
        http.get("/api/soutien/disponibilite?mois=2026-09", SA).andExpect(status().isOk()).andExpect(jsonPath("$.minutesObservees").value(6)).andExpect(jsonPath("$.minutesIndisponibles").value(2))
                .andExpect(jsonPath("$.pourcentage").value(66.67)).andExpect(jsonPath("$.objectifAtteint").value(false));
        http.get("/api/soutien/disponibilite", SA).andExpect(jsonPath("$.mois").value("2026-09"));   // le mois courant par défaut
        http.get("/api/soutien/disponibilite?mois=", SA).andExpect(jsonPath("$.mois").value("2026-09"));
        http.get("/api/soutien/disponibilite?mois=2026-08", SA).andExpect(jsonPath("$.pourcentage").isEmpty());
        // Une maintenance planifiée qui couvre 10:03–10:05 retire ces minutes de l'observation.
        jdbc.sql("INSERT INTO soutien_maintenances (id, debut, fin, description, statut, annonceeLe, annonceePar) VALUES ('m','2026-09-12T10:03:00.000Z','2026-09-12T10:05:00.000Z','fenêtre','planifiee','x','sa')").update();
        http.get("/api/soutien/disponibilite?mois=2026-09", SA).andExpect(jsonPath("$.minutesObservees").value(4)).andExpect(jsonPath("$.minutesEnMaintenance").value(2)).andExpect(jsonPath("$.minutesIndisponibles").value(1));
    }
}
