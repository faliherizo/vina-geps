package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.Annonces;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAbonnementsPoussee;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAppareils;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotEquipe;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAnnonces;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotNotifications;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotPreferences;
import ca.vina.nexu.notifications.infrastructure.persistance.NotifierEvenement;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le catalogue et l'envoi manuel par identifiant (ADR 0029), puis le dispatcher lui-même : canaux effectifs d'un
 * avis d'événement, canaux demandés d'un envoi manuel, poussée une fois par avis, textos réels, journal.
 */
@DisplayName("API notifications — catalogue des avis, envoi par identifiant, dispatcher")
class ControleurAvisHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete RH = new ContexteRequete("usr-rh", "rh@t", "rh", null, "t-1");
    private static final ContexteRequete GESTIONNAIRE = new ContexteRequete("usr-chef", "chef", "manager", "e-chef", "t-1");
    private static final ContexteRequete EMPLOYE = new ContexteRequete("u-lea", "lea", "employee", "e-lea", "t-1");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private CourrierJournal courrier;
    private Textos.SmsSimule sms;
    private PousseeWeb.PousseeSimulee poussee;
    private DepotPreferences preferences;
    private NotifierEvenement notifier;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        poussee = new PousseeWeb.PousseeSimulee(Set.of(), Set.of());
        DepotAbonnementsPoussee abonnements = new DepotAbonnementsPoussee(jdbc, HORLOGE, new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:x"), true), poussee, new DepotAppareils(jdbc, HORLOGE, new PousseeNative.Simulee()));
        courrier = new CourrierJournal();
        preferences = new DepotPreferences(jdbc, HORLOGE);
        sms = new Textos.SmsSimule(List.of("+14185550199"), () -> "t", "NexuVINA");
        DepotJournalTextos textos = new DepotJournalTextos(jdbc, HORLOGE, sms, JournalTextos.Reglages.de(Map.of()));
        DepotNotifications notifications = new DepotNotifications(jdbc, HORLOGE, abonnements, courrier, preferences, textos);
        notifier = new NotifierEvenement(jdbc, notifications, preferences, abonnements, AdressePublique.fixe("https://nexu.exemple"));
        http = new ClientHttpEssai(new ControleurAvis(new GardeAcces(new RegistrePolitiqueAcces()), notifier, new DepotEquipe(jdbc), new DepotAnnonces(jdbc, HORLOGE, abonnements)),
                new ControleurTextos(textos, preferences, JournalTextos.Reglages.de(Map.of()), new GardeAcces(new RegistrePolitiqueAcces())));
        jdbc.sql("INSERT INTO tenants (id, name) VALUES ('t-1', 'CSS des Rives')").update();
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, email, phone, managerId) VALUES "
                + "('e-chef', 'Marc', 'Trépanier', 't-1', 'marc@css.qc', NULL, NULL), ('e-lea', 'Léa', 'Tremblay', 't-1', 'lea@css.qc', '418 555 0100', 'e-chef'), "
                + "('e-sous', 'Paul', 'Gagnon', 't-1', 'paul@css.qc', NULL, 'e-lea'), ('e-ailleurs', 'Sara', 'Nadeau', 't-1', 'sara@css.qc', '418 555 0199', NULL), ('e-t2', 'Autre', 'Tenant', 't-2', 'x@y', NULL, NULL)").update();
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, employeeId, createdAt) VALUES ('u-lea', 'lea', 't-1', 'x', 'employee', 'e-lea', 'x'), ('u-sara', 'sara', 't-1', 'x', 'employee', 'e-ailleurs', 'x')").update();
        jdbc.sql("INSERT INTO push_abonnements (id, tenantId, userId, endpoint, p256dh, auth, navigateur, creeLe) VALUES ('ab-1', 't-1', 'u-lea', 'https://push/lea', 'k', 'a', 'Firefox', 'x')").update();
    }

    @Test
    @DisplayName("le catalogue se lit par tout compte ; l'envoi manuel : refus nommés, employé inconnu, hors équipe, puis envoi par canal avec le sort de chacun")
    void catalogueEtEnvoi() throws Exception {
        http.get("/api/notifications/evenements", null).andExpect(status().isUnauthorized());
        http.get("/api/notifications/evenements", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$[0].code").value("remplacement.attribue"))
                .andExpect(jsonPath("$[?(@.code=='message.direct')].essentiel").value(true))
                .andExpect(jsonPath("$[?(@.code=='message.direct')].moduleLibelle").value("Encadrement"))
                .andExpect(jsonPath("$[?(@.code=='quart.offert')].moduleLibelle").value("Urgences-santé"));

        http.post("/api/notifications/envoyer", EMPLOYE, "{\"employeeIds\":[\"e-lea\"],\"sujet\":\"s\",\"corps\":\"c\"}").andExpect(status().isForbidden());
        http.post("/api/notifications/envoyer", ADMIN, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.length()").value(3));
        http.post("/api/notifications/envoyer", ADMIN, "{\"employeeIds\":[\"e-t2\"],\"sujet\":\"s\",\"corps\":\"c\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("employé inconnu : e-t2"));
        http.post("/api/notifications/envoyer", GESTIONNAIRE, "{\"employeeIds\":[\"e-ailleurs\"],\"sujet\":\"s\",\"corps\":\"c\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("hors de votre équipe : e-ailleurs"));

        // Le gestionnaire avise sa branche (Paul relève de Léa qui relève de lui) ; Paul n'a pas de numéro et a fermé le texto, Léa a fermé la poussée.
        preferences.regler("u-lea", "t-1", Map.of("poussee", false));
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, employeeId, createdAt) VALUES ('u-paul', 'paul', 't-1', 'x', 'employee', 'e-sous', 'x')").update();
        preferences.regler("u-paul", "t-1", Map.of("texto", false));
        http.post("/api/notifications/envoyer", GESTIONNAIRE, "{\"employeeIds\":[\"e-lea\",\"e-sous\",\"e-lea\"],\"sujet\":\"Réunion jeudi\",\"corps\":\"Bonjour {{prenom}}, réunion jeudi 9 h.\",\"canaux\":[\"courriel\",\"texto\",\"poussee\"]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.envois.length()").value(6))
                .andExpect(jsonPath("$.envois[0].employeeId").value("e-lea")).andExpect(jsonPath("$.envois[0].canal").value("courriel")).andExpect(jsonPath("$.envois[0].statut").value("sent"))
                .andExpect(jsonPath("$.envois[1].statut").value("sent"))
                .andExpect(jsonPath("$.envois[2].statut").value("desactive")).andExpect(jsonPath("$.envois[2].erreur").value("poussée fermée par le destinataire"))
                .andExpect(jsonPath("$.envois[4].statut").value("desactive")).andExpect(jsonPath("$.envois[4].erreur").value("texto fermé par le destinataire"))
                .andExpect(jsonPath("$.envois[5].canal").value("poussee")).andExpect(jsonPath("$.envois[5].statut").value("sent"));
        assertThat(courrier.envoyes()).extracting(m -> m.to()).containsExactly("lea@css.qc", "paul@css.qc");
        assertThat(courrier.envoyes().get(0).subject()).isEqualTo("[CSS des Rives] Réunion jeudi");
        assertThat(courrier.envoyes().get(0).text()).startsWith("Bonjour Léa, réunion jeudi 9 h.").contains("Ouvrir : https://nexu.exemple/admin/notifications").contains("« Message de l'encadrement »");
        assertThat(sms.envois()).hasSize(1);
        assertThat(sms.envois().get(0).texte()).isEqualTo("CSS des Rives : Bonjour Léa, réunion jeudi 9 h. Réglages : Mon compte › Mes notifications. Répondre STOP/ARRET pour ne plus recevoir de textos.");
        assertThat(poussee.envois()).as("poussée fermée : rien").isEmpty();

        // Deux courriels (CSSMB 21, ADR 0039) : l'adresse personnelle que Paul a donnée reçoit aussi l'avis — une ligne au journal, deux envois ; une institutionnelle en double n'est pas répétée ; une adresse fautive est refusée nommément.
        http.put("/api/notifications/preferences", new ContexteRequete("u-paul", "paul", "employee", "e-sous", "t-1"), "{\"courrielPersonnel\":\"pas une adresse\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("courrielPersonnel : une adresse de courriel valide, ou vide"));
        http.put("/api/notifications/preferences", new ContexteRequete("u-paul", "paul", "employee", "e-sous", "t-1"), "{\"courrielPersonnel\":\" Paul.Perso@Gmail.com \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.courrielPersonnel").value("paul.perso@gmail.com"));
        http.put("/api/notifications/preferences", new ContexteRequete("u-paul", "paul", "employee", "e-sous", "t-1"), "{\"texto\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.courrielPersonnel").value("paul.perso@gmail.com"));
        http.post("/api/notifications/envoyer", GESTIONNAIRE, "{\"employeeIds\":[\"e-sous\"],\"sujet\":\"Rappel\",\"corps\":\"Réunion demain.\",\"canaux\":[\"courriel\"]}").andExpect(status().isCreated()).andExpect(jsonPath("$.envois.length()").value(1));
        assertThat(courrier.envoyes().stream().filter(m -> m.subject().endsWith("] Rappel")).map(m -> m.to())).containsExactly("paul@css.qc", "paul.perso@gmail.com");
        http.put("/api/notifications/preferences", new ContexteRequete("u-paul", "paul", "employee", "e-sous", "t-1"), "{\"courrielPersonnel\":\"PAUL@css.qc\"}").andExpect(status().isOk());
        http.post("/api/notifications/envoyer", GESTIONNAIRE, "{\"employeeIds\":[\"e-sous\"],\"sujet\":\"Rappel 2\",\"corps\":\"Réunion demain.\",\"canaux\":[\"courriel\"]}").andExpect(status().isCreated());
        assertThat(courrier.envoyes().stream().filter(m -> m.subject().endsWith("] Rappel 2")).map(m -> m.to())).containsExactly("paul@css.qc");
        http.put("/api/notifications/preferences", new ContexteRequete("u-paul", "paul", "employee", "e-sous", "t-1"), "{\"courrielPersonnel\":\"\"}").andExpect(status().isOk()).andExpect(jsonPath("$.courrielPersonnel").isEmpty());

        // Sans numéro connu, le texto échoue nommément (Léa en a un, Paul non — et Paul l'avait fermé : on l'ouvre).
        preferences.regler("u-paul", "t-1", Map.of("texto", true));
        http.post("/api/notifications/envoyer", GESTIONNAIRE, "{\"employeeIds\":[\"e-sous\"],\"sujet\":\"s\",\"corps\":\"c\",\"canaux\":[\"texto\"]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.envois[0].statut").value("failed")).andExpect(jsonPath("$.envois[0].erreur").value("aucun numéro de téléphone connu"));
        // La RH avise tout l'établissement, un canal seulement ; le journal porte le type « employee ».
        http.post("/api/notifications/envoyer", RH, "{\"employeeIds\":[\"e-ailleurs\"],\"sujet\":\"Rappel\",\"corps\":\"Formation lundi.\",\"canaux\":[\"texto\"]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.envois[0].statut").value("failed"));   // +14185550199 est injoignable
        assertThat(jdbc.sql("SELECT status, recipientType, error FROM varpay_notifications WHERE recipientId = 'e-ailleurs'").query().listOfRows()).hasSize(1)
                .first().satisfies(l -> assertThat(l).containsEntry("recipientType", "employee").containsEntry("status", "failed"));
    }

    @Test
    @DisplayName("le dispatcher : un avis d'événement n'emprunte que les canaux effectifs (défauts, réglages), la poussée part une fois — seule s'il le faut ; un événement inconnu est refusé ; rien ne lève")
    void dispatcher() {
        // « quart.offert » : courriel, texto et poussée par défaut ; Léa règle texto ouvert et courriel fermé pour cet événement.
        preferences.regler("u-lea", "t-1", Map.of("evenements", Map.of("quart.offert", Map.of("courriel", false, "texto", true))));
        List<PublicationAvis.Envoi> envois = notifier.publier(PublicationAvis.Avis.de("t-1", "quart.offert", List.of("e-lea"), "Feuille approuvée", "Votre feuille est approuvée.", "/admin/timesheets"));
        assertThat(envois).extracting(PublicationAvis.Envoi::canal, PublicationAvis.Envoi::statut).containsExactly(org.assertj.core.groups.Tuple.tuple("texto", "sent"), org.assertj.core.groups.Tuple.tuple("poussee", "sent"));
        assertThat(courrier.envoyes()).isEmpty();
        assertThat(poussee.envois()).hasSize(1);
        assertThat(poussee.envois().get(0).charge().url()).isEqualTo("/admin/timesheets");

        // Sans canal écrit effectif, la poussée part seule par le raccord — une fois.
        preferences.regler("u-lea", "t-1", Map.of("evenements", Map.of("quart.attribue", Map.of("courriel", false, "texto", false))));
        assertThat(notifier.publier(PublicationAvis.Avis.de("t-1", "quart.attribue", List.of("e-lea"), "s", "c", null))).extracting(PublicationAvis.Envoi::canal).containsExactly("poussee");
        assertThat(poussee.envois()).hasSize(2);
        assertThat(poussee.envois().get(1).charge().url()).isEqualTo("/admin/dashboard");

        // Sans compte, tout est ouvert : Sara reçoit les défauts de « remplacement.attribue » (courriel, texto, poussée) — le texto échoue chez le porteur.
        List<PublicationAvis.Envoi> sara = notifier.publier(new PublicationAvis.Avis("t-1", "remplacement.attribue", List.of("e-ailleurs"), "Absence {{verdict}}", "c", null, Map.of("verdict", "approuvée"), null));
        assertThat(sara).extracting(PublicationAvis.Envoi::canal, PublicationAvis.Envoi::statut).containsExactly(org.assertj.core.groups.Tuple.tuple("courriel", "sent"), org.assertj.core.groups.Tuple.tuple("texto", "failed"), org.assertj.core.groups.Tuple.tuple("poussee", "sent"));
        assertThat(courrier.envoyes().get(0).subject()).isEqualTo("[CSS des Rives] Absence approuvée");

        assertThatThrownBy(() -> notifier.publier(PublicationAvis.Avis.de("t-1", "pas.un", List.of("e-lea"), "s", "c", null))).hasMessage("événement inconnu : pas.un");
        notifier.publierSansLever(PublicationAvis.Avis.de("t-1", "pas.un", List.of("e-lea"), "s", "c", null));
        notifier.publierSansLever(PublicationAvis.Avis.de("t-1", "quart.attribue", List.of("e-lea"), "s", "c", null));
        assertThat(poussee.envois()).hasSize(3);
        assertThat(PublicationAvis.MUET.publier(PublicationAvis.Avis.de("t-1", "quart.offert", List.of("e-lea"), "s", "c", null))).isEmpty();
        // Un établissement inconnu signe « Nexu VINA ».
        assertThat(notifier.publier(new PublicationAvis.Avis("t-inconnu", "message.direct", List.of("e-ailleurs"), "s", "c", null, Map.of(), List.of("courriel")))).extracting(PublicationAvis.Envoi::statut).containsExactly("sent");
        assertThat(courrier.envoyes().get(courrier.envoyes().size() - 1).text()).contains("Nexu VINA pour Nexu VINA");
        // Une base sans la table des établissements (base d'essai réduite) signe aussi « Nexu VINA », sans panne.
        jdbc.sql("DROP TABLE tenants").update();
        assertThat(notifier.publier(new PublicationAvis.Avis("t-1", "message.direct", List.of("e-ailleurs"), "s", "c", null, Map.of(), List.of("courriel")))).extracting(PublicationAvis.Envoi::statut).containsExactly("sent");
        assertThat(courrier.envoyes().get(courrier.envoyes().size() - 1).text()).contains("Nexu VINA pour Nexu VINA");
    }

    @Test
    @DisplayName("l'équipe d'un gestionnaire : lui-même, sa branche, jamais sans identifiant ; des réglages corrompus ou d'un code disparu rendent les défauts")
    void equipeEtReglagesCorrompus() {
        DepotEquipe equipe = new DepotEquipe(jdbc);
        assertThat(equipe.dansEquipe(null, "e-lea")).isFalse();
        assertThat(equipe.dansEquipe("e-chef", "e-chef")).isTrue();
        assertThat(equipe.dansEquipe("e-chef", "e-sous")).isTrue();
        assertThat(equipe.dansEquipe("e-chef", "e-ailleurs")).isFalse();
        assertThat(equipe.lire("t-1", "e-t2")).isEmpty();
        assertThat(equipe.lire("t-1", "e-lea").orElseThrow().nomComplet()).isEqualTo("Léa Tremblay");
        jdbc.sql("INSERT INTO notification_preferences (userId, tenantId, courriel, texto, poussee, evenements, updatedAt) VALUES ('u-corrompu', 't-1', 1, 1, 1, '{pas du json', 'x'), ('u-vide', 't-1', 1, 1, 1, '', 'x'), ('u-ancien', 't-1', 1, 1, 1, '{\"evenement.retire\":{\"courriel\":false},\"quart.offert\":{\"texto\":true}}', 'x')").update();
        assertThat(preferences.preferencesDe("u-corrompu").evenements()).isEmpty();
        assertThat(preferences.preferencesDe("u-vide").evenements()).isEmpty();
        assertThat(preferences.preferencesDe("u-ancien").evenements()).containsOnlyKeys("quart.offert");
        assertThat(preferences.preferencesDe("u-ancien").ouvert("texto", "quart.offert")).isTrue();
    }

    private static String champ(MvcResult r, String nom) throws Exception {
        return r.getResponse().getContentAsString().replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    @DisplayName("annonces (CSSMB 21, ADR 0039) : chaque manque est nommé ; publiée, l'annonce vise ses rôles pendant sa période, se marque lue par compte, se pousse à la publication ; l'administration la retire")
    void annonces() throws Exception {
        ContexteRequete direction = new ContexteRequete("u-dir", "dir", "director", null, "t-1");
        http.get("/api/notifications/annonces/toutes", EMPLOYE).andExpect(status().isForbidden());
        http.post("/api/notifications/annonces", EMPLOYE, "{}").andExpect(status().isForbidden());
        http.get("/api/notifications/annonces", null).andExpect(status().isUnauthorized());
        http.post("/api/notifications/annonces", ADMIN, "{\"roles\":[\"roi\"],\"du\":\"2026-10-10\",\"au\":\"2026-10-01\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("titre requis; corps requis; roles : parmi admin, director, manager, rh, accountant, employee; au doit suivre du"));
        http.post("/api/notifications/annonces", ADMIN, "{\"titre\":\"" + "x".repeat(121) + "\",\"corps\":\"" + "c".repeat(2001) + "\",\"roles\":\"tous\",\"du\":\"hier\",\"au\":\"demain\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("titre : 120 caractères au plus; corps : 2000 caractères au plus; roles : une liste de rôles, vide pour tous; du : date AAAA-MM-JJ requise; au : date AAAA-MM-JJ requise"));
        http.post("/api/notifications/annonces", ADMIN, null).andExpect(status().isBadRequest());
        // Publiée pour les employés, en cours : Léa (abonnée) reçoit la poussée ; une annonce future n'est ni vue ni poussée.
        MvcResult pub = http.post("/api/notifications/annonces", ADMIN, "{\"titre\":\" Rentrée \",\"corps\":\"Les horaires changent lundi.\",\"roles\":[\"employee\",\"employee\"],\"du\":\"2026-09-14\",\"au\":\"2099-12-31\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.titre").value("Rentrée")).andExpect(jsonPath("$.roles.length()").value(1)).andExpect(jsonPath("$.auteur").value("usr-admin")).andReturn();
        String id = champ(pub, "id");
        assertThat(poussee.envois()).hasSize(1);
        assertThat(poussee.envois().get(0).charge().titre()).isEqualTo("Rentrée");
        http.post("/api/notifications/annonces", ADMIN, "{\"titre\":\"Plus tard\",\"corps\":\"c\",\"du\":\"2099-01-01\",\"au\":\"2099-12-31\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.roles.length()").value(0));
        assertThat(poussee.envois()).hasSize(1);
        http.get("/api/notifications/annonces", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].titre").value("Rentrée")).andExpect(jsonPath("$[0].lue").value(false));
        http.get("/api/notifications/annonces", direction).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.post("/api/notifications/annonces/" + id + "/lue", EMPLOYE, null).andExpect(status().isOk()).andExpect(jsonPath("$.lue").value(true));
        http.post("/api/notifications/annonces/" + id + "/lue", EMPLOYE, null).andExpect(status().isOk());
        http.post("/api/notifications/annonces/fantome/lue", EMPLOYE, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("annonce introuvable"));
        http.get("/api/notifications/annonces", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$[0].lue").value(true));
        http.get("/api/notifications/annonces/toutes", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].titre").value("Plus tard")).andExpect(jsonPath("$[0].lectures").value(0)).andExpect(jsonPath("$[1].lectures").value(1));
        http.delete("/api/notifications/annonces/" + id, ADMIN).andExpect(status().isNoContent());
        http.delete("/api/notifications/annonces/" + id, ADMIN).andExpect(status().isNotFound());
        http.get("/api/notifications/annonces", EMPLOYE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // Les règles pures : une annonce sans rôle vise tout le monde ; en cours au jour dit.
        Annonces.Annonce a = new Annonces.Annonce("a", "t", "T", "C", null, "2026-09-01", "2026-09-30", "u", "x");
        assertThat(a.vise("employee")).isTrue();
        assertThat(a.enCours("2026-09-15")).isTrue();
        assertThat(a.enCours("2026-10-01")).isFalse();
        assertThat(new Annonces.Annonce("a", "t", "T", "C", List.of("rh"), "2026-09-01", "2026-09-30", "u", "x").vise("employee")).isFalse();
    }
}
