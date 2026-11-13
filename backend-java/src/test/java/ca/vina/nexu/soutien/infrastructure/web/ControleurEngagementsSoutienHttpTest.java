package ca.vina.nexu.soutien.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.soutien.infrastructure.configuration.ConfigurationSoutien;
import ca.vina.nexu.soutien.infrastructure.persistance.AvisSoutienJournal;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotEngagementsSoutien;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un organisme sous le contrat 26-015-DO : le soutien lui attribue la politique « service
 * essentiel » ; ses billets ont alors un délai de réponse (rappel) puis un délai de résolution,
 * et la disponibilité du mois est jugée contre 99,7 % et 99,0 %. Le 12 novembre à 10 h UTC, il
 * est 5 h à Montréal : les heures de soutien viennent d'ouvrir.
 */
@DisplayName("API soutien — engagements de service par organisme")
class ControleurEngagementsSoutienHttpTest {

    private static final ContexteRequete ALICE = new ContexteRequete("usr-alice", "alice", "employee", "emp-alice", "t-us");
    private static final ContexteRequete SA = ClientHttpEssai.superadmin();
    private final ControleurSoutienHttpTest.HorlogeEssai horloge = new ControleurSoutienHttpTest.HorlogeEssai();
    private ClientHttpEssai http;
    private DepotSoutien depot;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name) VALUES ('t-us', 'Urgences-santé'), ('t-css', 'CSS des Chênes')").update();
        horloge.instant = Instant.parse("2026-11-12T10:00:00Z");
        DepotEngagementsSoutien engagements = new DepotEngagementsSoutien(jdbc);
        depot = new DepotSoutien(jdbc, horloge, new AvisSoutienJournal(), Map.of(), new ca.vina.nexu.securite.domaine.Antivirus.AnalyseurSimule(), engagements);
        ConfigurationSoutien config = new ConfigurationSoutien();
        GardeAcces garde = new GardeAcces(new RegistrePolitiqueAcces());
        http = new ClientHttpEssai(
                new ControleurSoutien(config.lectureSoutien(depot, () -> Iso.de(horloge.instant()), engagements), config.gestionnaireSoutien(depot), garde),
                new ControleurEngagementsSoutien(config.lectureEngagements(engagements), config.fixerEngagement(engagements, horloge), garde));
    }

    private static String id(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"id\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    void leSoutienAttribueUnePolitiqueAUnOrganismeEtLuiSeul() throws Exception {
        http.get("/api/soutien/engagements", null).andExpect(status().isUnauthorized());
        http.get("/api/soutien/engagements", ALICE).andExpect(status().isForbidden());
        http.put("/api/soutien/engagements/t-us", ALICE, "{\"politique\":\"service-essentiel\"}").andExpect(status().isForbidden());
        http.get("/api/soutien/engagements", SA).andExpect(status().isOk())
                .andExpect(jsonPath("$.politiques[*].code", contains("standard", "service-essentiel")))
                .andExpect(jsonPath("$.organismes[*].nom", contains("CSS des Chênes", "Urgences-santé")))
                .andExpect(jsonPath("$.organismes[*].politique", contains("standard", "standard")))
                .andExpect(jsonPath("$.organismes[0].modifieLe").isEmpty());

        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":\" service-essentiel \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t-us")).andExpect(jsonPath("$.politique").value("service-essentiel"))
                .andExpect(jsonPath("$.modifieLe").value("2026-11-12T10:00:00.000Z")).andExpect(jsonPath("$.modifiePar").value(SA.userId()));
        http.get("/api/soutien/engagements", SA).andExpect(jsonPath("$.organismes[*].politique", contains("standard", "service-essentiel")));

        http.put("/api/soutien/engagements/t-inconnu", SA, "{\"politique\":\"standard\"}").andExpect(status().isNotFound());
        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":\"platine\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", containsString("politique de soutien inconnue : « platine »")));
        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":3}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("politique requise (texte)"));
        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":\"  \"}").andExpect(status().isBadRequest());
        http.put("/api/soutien/engagements/t-us", SA, null).andExpect(status().isBadRequest());

        // L'écran de l'organisme lit sa politique ; un visiteur lit la standard.
        http.get("/api/soutien/politique", ALICE).andExpect(jsonPath("$.code").value("service-essentiel"))
                .andExpect(jsonPath("$.objectifDisponibilitePourcent").value(99.7)).andExpect(jsonPath("$.heuresDeSoutien.debut").value("05:00"))
                .andExpect(jsonPath("$.niveaux[*].delaiReponseEnHeuresMinutes", contains(30, 30, 60, 60)));
        http.get("/api/soutien/politique", null).andExpect(jsonPath("$.code").value("standard")).andExpect(jsonPath("$.heuresDeSoutien").isEmpty());
        http.get("/api/soutien/politique", SA).andExpect(jsonPath("$.code").value("standard"));
    }

    @Test
    void unBilletDuServiceEssentielSuitLeRappelPuisLaResolution() throws Exception {
        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":\"service-essentiel\"}").andExpect(status().isOk());
        MvcResult cree = http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Horaire illisible\",\"description\":\"Aucune affectation ne s'affiche depuis 4 h 50.\",\"niveau\":\"N1\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.politique").value("service-essentiel"))
                .andExpect(jsonPath("$.reponse.echeanceLe").value("2026-11-12T10:30:00.000Z"))
                .andExpect(jsonPath("$.reponse.etat").value("dans_les_delais"))
                .andExpect(jsonPath("$.reponse.atteintLe").isEmpty())
                .andExpect(jsonPath("$.echeanceLe").value("2026-11-12T12:30:00.000Z")).andReturn();
        String billet = id(cree);

        // 40 minutes sans rappel : le billet est en retard, et le résumé du soutien le compte.
        horloge.instant = Instant.parse("2026-11-12T10:40:00Z");
        http.get("/api/soutien/tickets/" + billet, ALICE).andExpect(jsonPath("$.reponse.etat").value("en_retard")).andExpect(jsonPath("$.reponse.ecartMinutes").value(-10))
                .andExpect(jsonPath("$.sla.etat").value("dans_les_delais"));
        http.get("/api/soutien/tickets/resume", SA).andExpect(jsonPath("$.enRetard").value(1));
        http.get("/api/soutien/tickets/par-etablissement", SA).andExpect(jsonPath("$[0].nom").value("Urgences-santé")).andExpect(jsonPath("$[0].enRetard").value(1));

        // Le rappel arrive : le délai de réponse est manqué, la résolution part du rappel.
        http.post("/api/soutien/tickets/" + billet + "/messages", SA, "{\"texte\":\"Nous prenons l'appel.\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.reponse.etat").value("manque")).andExpect(jsonPath("$.reponse.atteintLe").value("2026-11-12T10:40:00.000Z"))
                .andExpect(jsonPath("$.echeanceLe").value("2026-11-12T12:40:00.000Z"));
        http.get("/api/soutien/tickets/resume", SA).andExpect(jsonPath("$.enRetard").value(1));

        // Un billet d'un organisme standard n'a pas de délai de réponse.
        assertThat(depot.ouvrirTicket(new ca.vina.nexu.soutien.domaine.Soutien.Acteur("bob", "bob", "t-css", false),
                Map.of("sujet", "Une question", "description", "Où se trouve le guide de l'usager ?", "niveau", "N4")).reponse()).isNull();
    }

    @Test
    void laDisponibiliteDuMoisEstJugeeContreChaquePolitiqueEnUsage() throws Exception {
        depot.battre(12);
        horloge.instant = Instant.parse("2026-11-12T10:02:00Z");
        http.get("/api/soutien/disponibilite?mois=2026-11", SA).andExpect(status().isOk())
                .andExpect(jsonPath("$.pourcentage").value(50.0))
                .andExpect(jsonPath("$.engagements[*].politique", contains("standard")));
        http.put("/api/soutien/engagements/t-us", SA, "{\"politique\":\"service-essentiel\"}").andExpect(status().isOk());
        http.get("/api/soutien/disponibilite?mois=2026-11", SA)
                .andExpect(jsonPath("$.engagements[*].politique", contains("standard", "service-essentiel")))
                .andExpect(jsonPath("$.engagements[*].manquement", contains("manquement", "manquement_majeur")))
                .andExpect(jsonPath("$.engagements[1].objectifPourcent").value(99.7))
                .andExpect(jsonPath("$.engagements[1].seuilManquementMajeurPourcent").value(99.0));
    }
}
