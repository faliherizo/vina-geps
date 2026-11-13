package ca.vina.nexu.notifications.infrastructure;

import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.notifications.infrastructure.configuration.ConfigurationFournisseurs;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAbonnementsPoussee;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAppareils;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotNotifications;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotPreferences;
import ca.vina.nexu.notifications.infrastructure.persistance.ExecuteurNotifier;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.notifications.domaine.Canal;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Notifications;
import ca.vina.nexu.notifications.domaine.Preferences;
import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Journal des notifications, poussée vers les navigateurs d'une personne, exécuteur « notifier »")
class NotificationsEtPousseeTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC);
    private JdbcClient jdbc;
    private PousseeWeb.PousseeSimulee poussee;
    private DepotAbonnementsPoussee abonnements;
    private DepotNotifications notifications;
    private Textos.SmsSimule sms;
    private CourrierJournal courrier;
    private DepotPreferences preferences;
    private DepotJournalTextos textos;
    private RegistreExecuteurs registre;
    private DepotTaches taches;
    private PousseeNative.Simulee natif;
    private ExecuteurNotifier notifier;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        poussee = new PousseeWeb.PousseeSimulee(new HashSet<>(Set.of("https://push/ko")), new HashSet<>(Set.of("https://push/expire")));
        natif = new PousseeNative.Simulee().perimer("ExponentPushToken[perime00000]");
        abonnements = new DepotAbonnementsPoussee(jdbc, HORLOGE, new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:x"), true), poussee, new DepotAppareils(jdbc, HORLOGE, natif));
        courrier = new CourrierJournal();
        preferences = new DepotPreferences(jdbc, HORLOGE);
        sms = new Textos.SmsSimule(List.of("+14185550199"), () -> "t", "NexuVINA");
        textos = new DepotJournalTextos(jdbc, HORLOGE, sms, JournalTextos.Reglages.de(Map.of("SMS_QUOTA_MENSUEL", "3")));
        notifications = new DepotNotifications(jdbc, HORLOGE, abonnements, courrier, preferences, textos);
        registre = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, registre);
        notifier = new ExecuteurNotifier(jdbc, taches, notifications, textos, preferences, registre);
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, employeeId, createdAt, email) VALUES ('u-1', 'lea', 't-1', 'x', 'employee', 'e1', 'x', 'lea@exemple.qc.ca'), ('e2', 'nadir', NULL, 'x', 'employee', NULL, 'x', NULL), ('u-3', 'autre', 't-2', 'x', 'employee', 'e1', 'x', NULL)").update();
        jdbc.sql("INSERT INTO push_abonnements (id, tenantId, userId, endpoint, p256dh, auth, creeLe) VALUES ('a1', 't-1', 'u-1', 'https://push/ok', 'p', 'a', 'x'), ('a2', 't-1', 'u-1', 'https://push/expire', 'p', 'a', 'x'), ('a3', 't-1', 'e2', 'https://push/ko', 'p', 'a', 'x'), ('a9', 't-2', 'u-3', 'https://push/autre', 'p', 'a', 'x')").update();
        // Les appareils mobiles de Léa (refonte E ★) : un qui reçoit, un que le service ne connaît plus.
        jdbc.sql("INSERT INTO notification_appareils (id, tenantId, userId, jeton, plateforme, enregistreLe) VALUES ('ap1', 't-1', 'u-1', 'ExponentPushToken[ok0000000000]', 'ios', 'x'), ('ap2', 't-1', 'u-1', 'ExponentPushToken[perime00000]', 'android', 'x')").update();
    }

    @Test
    @DisplayName("pousser vers une personne atteint ses comptes (identifiant ou dossier), retire l'expiré, note l'échec")
    void pousserVers() {
        List<Map<String, Object>> r = abonnements.pousserVers("t-1", "e1", new PousseeWeb.Charge("T", "C", null, null));
        assertThat(r).extracting(m -> m.get("statut")).containsExactly("envoye", "expire");
        assertThat(jdbc.sql("SELECT id FROM push_abonnements WHERE tenantId = 't-1' ORDER BY id").query(String.class).list()).containsExactly("a1", "a3");
        assertThat(jdbc.sql("SELECT dernierStatut, dernierEnvoiLe FROM push_abonnements WHERE id = 'a1'").query().singleRow()).containsEntry("dernierStatut", "envoye").containsEntry("dernierEnvoiLe", "2026-09-16T12:00:00.000Z");
        assertThat(abonnements.pousserVers("t-1", "e2", new PousseeWeb.Charge("T", "C", null, null))).extracting(m -> m.get("statut")).containsExactly("echoue");
        assertThat(jdbc.sql("SELECT dernierStatut FROM push_abonnements WHERE id = 'a3'").query(String.class).single()).isEqualTo("echoue : service de poussée indisponible");
        assertThat(abonnements.pousserVers("t-1", "inconnu", new PousseeWeb.Charge("T", "C", null, null))).isEmpty();
        assertThat(poussee.envois()).hasSize(3);
    }

    @Test
    @DisplayName("le journal rend une ligne par destinataire, substitue les variables, et pousse vers les personnes sans jamais lever")
    void journal() {
        List<Map<String, Object>> envoyees = notifications.envoyer("t-1", new Notifications.Demande("email", "teacher", List.of("e1", "e2"), " Remplacement — {{jours}} ", "Le {{jours}}, {{corps}}.", Map.of("jours", "2026-09-20", "corps", "ENS")));
        assertThat(envoyees).hasSize(2);
        assertThat(envoyees.get(0)).containsEntry("recipientId", "e1").containsEntry("subject", "Remplacement — 2026-09-20").containsEntry("body", "Le 2026-09-20, ENS.").containsEntry("status", "sent").containsEntry("channel", "email").containsEntry("sentAt", "2026-09-16T12:00:00.000Z").containsEntry("error", null);
        // Le courriel part vraiment, à l'adresse du compte rattaché au dossier ; sans adresse, l'échec est nommé.
        assertThat(courrier.envoyes()).singleElement().satisfies(m -> {
            assertThat(m.to()).isEqualTo("lea@exemple.qc.ca");
            assertThat(m.subject()).isEqualTo("Remplacement — 2026-09-20");
        });
        assertThat(envoyees.get(1)).containsEntry("recipientId", "e2").containsEntry("status", "failed").containsEntry("error", "aucune adresse de courriel connue").containsEntry("sentAt", null);
        assertThat(poussee.envois()).hasSize(3);
        assertThat(poussee.envois().get(0).charge()).isEqualTo(new PousseeWeb.Charge("Remplacement — 2026-09-20", "Le 2026-09-20, ENS.", "/admin/notifications", "notification"));
        // La même charge part vers les appareils mobiles de Léa ; l'appareil périmé est retiré, l'autre garde son statut.
        assertThat(natif.envois()).hasSize(2);
        assertThat(natif.envois().get(0).getValue()).isEqualTo(poussee.envois().get(0).charge());
        assertThat(jdbc.sql("SELECT id, dernierStatut FROM notification_appareils ORDER BY id").query().listOfRows()).singleElement().satisfies(m -> assertThat(m).containsEntry("id", "ap1").containsEntry("dernierStatut", "envoye"));
        notifications.envoyer("t-1", new Notifications.Demande("sms", "establishment", List.of("et-1"), "S", "C", null));
        assertThat(poussee.envois()).hasSize(3);
        assertThatThrownBy(() -> notifications.envoyer("t-1", new Notifications.Demande("sms", "teacher", List.of(), "S", "C", null))).isInstanceOf(IllegalArgumentException.class).hasMessage("recipients[] required");
        assertThat(notifications.lister("t-1", null, null, null)).hasSize(3).extracting(m -> m.get("recipientId")).contains("e1", "e2", "et-1");
        assertThat(notifications.lister("t-1", "teacher", "e2", "email")).singleElement().satisfies(m -> assertThat(m).containsEntry("id", envoyees.get(1).get("id")).containsEntry("tenantId", "t-1"));
        assertThat(notifications.lister("t-1", "establishment", null, null)).hasSize(1);
        assertThat(notifications.lister("t-2", null, null, null)).isEmpty();
        // Un service de poussée qui lève ne casse pas le journal.
        DepotNotifications sansPoussee = new DepotNotifications(jdbc, HORLOGE, new DepotAbonnementsPoussee(jdbc, HORLOGE, new ConfigurationFournisseurs.ClesInstance(new PousseeWeb.ClesVapid("pub", "priv", "mailto:x"), true), (a, c) -> {
            throw new IllegalStateException("panne");
        }, new DepotAppareils(jdbc, HORLOGE, new PousseeNative.Simulee())), courrier, preferences, textos);
        assertThat(sansPoussee.envoyer("t-1", new Notifications.Demande("email", "teacher", List.of("e1"), "S", "C", null))).hasSize(1);
    }

    @Test
    @DisplayName("l'adresse d'un professeur : la sienne, sinon l'employé lié, sinon le dossier, sinon le compte ; un transport qui refuse est un échec nommé")
    void adresses() {
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, email) VALUES ('emp-9', 'Nour', 'K', 't-1', ' nour@exemple.qc.ca '), ('emp-vide', 'Sans', 'Courriel', 't-1', '  ')").update();
        jdbc.sql("INSERT INTO varpay_teachers (id, tenantId, sagirEmployeeId, firstName, lastName, email, type, hireDate, createdAt, linkedEmployeeId) VALUES ('p-1', 't-1', 's1', 'A', 'B', 'prof@exemple.qc.ca', 'regular', 'x', 'x', NULL), ('p-2', 't-1', 's2', 'C', 'D', NULL, 'regular', 'x', 'x', 'emp-9'), ('p-3', 't-1', 's3', 'E', 'F', '', 'regular', 'x', 'x', 'emp-vide')").update();
        assertThat(notifications.adresseDe("t-1", "teacher", "p-1")).isEqualTo("prof@exemple.qc.ca");
        assertThat(notifications.adresseDe("t-1", "teacher", "p-2")).isEqualTo("nour@exemple.qc.ca");
        assertThat(notifications.adresseDe("t-1", "teacher", "p-3")).isNull();
        assertThat(notifications.adresseDe("t-1", "teacher", "emp-9")).isEqualTo("nour@exemple.qc.ca");
        assertThat(notifications.adresseDe("t-1", "teacher", "u-1")).isEqualTo("lea@exemple.qc.ca");
        assertThat(notifications.adresseDe("t-1", "establishment", "p-1")).isNull();
        // Le destinataire qui a fermé le courriel ne le reçoit pas, et le journal le dit.
        preferences.regler("u-1", "t-1", Map.of("courriel", false));
        assertThat(notifications.envoyer("t-1", new Notifications.Demande("email", "teacher", List.of("e1"), "S", "C", null)).get(0)).containsEntry("status", "desactive").containsEntry("error", "courriel fermé par le destinataire");
        assertThat(courrier.envoyes()).isEmpty();
        DepotNotifications transportEnPanne = new DepotNotifications(jdbc, HORLOGE, abonnements, m -> false, preferences, textos);
        assertThat(transportEnPanne.envoyer("t-1", new Notifications.Demande("email", "teacher", List.of("emp-9"), "S", "C", null)).get(0)).containsEntry("status", "failed")
                // Le refus nomme l'adresse (ADR 0039) : avec deux courriels par personne, « refusé » seul ne dirait pas lequel.
                .containsEntry("error", "nour@exemple.qc.ca : courriel refusé par le transport (journal)");
        assertThat(notifications.lister("t-1", null, "e1", null).get(0)).containsEntry("error", "courriel fermé par le destinataire");
    }

    @Test
    @DisplayName("les canaux d'un compte : tout ouvert à défaut, correctif fusionné, jamais les deux canaux écrits fermés ; une personne à deux comptes reçoit tant qu'un compte est ouvert")
    void canaux() {
        assertThat(preferences.preferencesDe("inconnu")).isEqualTo(Preferences.PAR_DEFAUT);
        assertThat(preferences.regler("u-1", "t-1", Map.of("texto", false)).vue()).containsEntry("courriel", true).containsEntry("texto", false).containsEntry("poussee", true).containsEntry("updatedAt", "2026-09-16T12:00:00.000Z");
        assertThat(preferences.regler("u-1", "t-1", Map.of("poussee", false)).poussee()).isFalse();
        assertThat(preferences.regler("u-1", "t-1", null).texto()).isFalse();
        assertThatThrownBy(() -> preferences.regler("u-1", "t-1", Map.of("courriel", false))).isInstanceOf(IllegalArgumentException.class).hasMessage("au moins un canal écrit (courriel ou texto) doit rester actif");
        assertThatThrownBy(() -> preferences.regler("u-1", "t-1", Map.of("courriel", "oui"))).hasMessage("courriel : vrai ou faux attendu");
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.ouvert("fax")).hasMessage("canal inconnu : fax");
        // e1 est porté par u-1 (t-1) et u-3 (t-2) : dans t-1, seul u-1 compte ; le texto y est fermé.
        assertThat(preferences.comptesDePersonne("t-1", "e1")).containsExactly("u-1");
        assertThat(preferences.canalOuvert("t-1", "e1", Canal.TEXTO)).isFalse();
        assertThat(preferences.canalOuvert("t-1", "e1", Canal.COURRIEL)).isTrue();
        assertThat(preferences.canalOuvert("t-1", "personne-sans-compte", Canal.TEXTO)).isTrue();
        // Un professeur passe par l'employé lié ; un second compte ouvert suffit.
        jdbc.sql("INSERT INTO varpay_teachers (id, tenantId, sagirEmployeeId, firstName, lastName, type, hireDate, createdAt, linkedEmployeeId) VALUES ('p-1', 't-1', 's1', 'A', 'B', 'regular', 'x', 'x', 'e1')").update();
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, employeeId, createdAt) VALUES ('u-bis', 'lea2', 't-1', 'x', 'employee', 'e1', 'x')").update();
        assertThat(preferences.comptesDePersonne("t-1", "p-1")).containsExactly("u-1", "u-bis");
        assertThat(preferences.canalOuvert("t-1", "p-1", Canal.TEXTO)).isTrue();
    }

    @Test
    @DisplayName("le journal des textos : en attente avant le fournisseur, envoyé ou échoué après, quota compté sur le journal, rappel signé, statut redemandé")
    void journalTextos() {
        JournalTextos.Texto t = textos.envoyer(new JournalTextos.Demande("t-1", "+14185550100", "Bonjour", null, "besoin:b1"));
        assertThat(t.statut()).isEqualTo("envoye");
        assertThat(t.identifiantFournisseur()).isEqualTo("sim-1");
        assertThat(t.vue()).containsEntry("numero", "••••••••0100").containsEntry("fournisseur", "simule").containsEntry("contexte", "besoin:b1").containsEntry("envoyeLe", "t").containsEntry("creeLe", "2026-09-16T12:00:00.000Z");
        JournalTextos.Texto refuse = textos.envoyer(new JournalTextos.Demande("t-1", "+14185550199", "Bonjour", "+15145550000", null));
        assertThat(refuse.statut()).isEqualTo("echoue");
        assertThat(refuse.erreur()).isEqualTo("refusé par le fournisseur");
        assertThat(refuse.refuseAvantLeFournisseur()).isFalse();
        assertThat(textos.quotaDe("t-1").vue()).containsEntry("mois", "2026-09").containsEntry("limite", 3).containsEntry("utilises", 2).containsEntry("restants", 1).containsEntry("illimite", false);
        // Un porteur qui lève : la ligne reste, échouée, sans identifiant — elle ne compte pas au quota.
        DepotJournalTextos enPanne = new DepotJournalTextos(jdbc, HORLOGE, new Textos.PorteurSms() {
            @Override
            public Textos.Envoi envoyer(String numero, String texte, String expediteur) {
                throw new IllegalStateException("Twilio : 503 (code 20429) trop de requêtes");
            }

            @Override
            public String statut(String envoiId) {
                return "livre";
            }
        }, JournalTextos.Reglages.de(Map.of("SMS_FOURNISSEUR", "twilio", "SMS_QUOTA_MENSUEL", "3")));
        JournalTextos.Texto panne = enPanne.envoyer(new JournalTextos.Demande("t-1", "+14185550100", "B", null, null));
        assertThat(panne.refuseAvantLeFournisseur()).isTrue();
        assertThat(panne.erreur()).isEqualTo("Twilio : 503 (code 20429) trop de requêtes");
        assertThat(panne.fournisseur()).isEqualTo("twilio");
        assertThat(textos.quotaDe("t-1").utilises()).isEqualTo(2);
        // Le troisième envoi réel épuise le quota ; le suivant est refusé sans appel au fournisseur.
        textos.envoyer(new JournalTextos.Demande("t-1", "+14185550100", "C", null, null));
        JournalTextos.Texto quota = textos.envoyer(new JournalTextos.Demande("t-1", "+14185550100", "D", null, null));
        assertThat(quota.refusePourQuota()).isTrue();
        // Le quota se compte en **segments**, pas en messages, et le motif dit ce que le message
        // demandé coûtait : sans ce chiffre, un administrateur ne sait pas quoi raccourcir.
        assertThat(quota.erreur()).isEqualTo("quota mensuel atteint (3/3 segments en 2026-09) ; ce message en coûte 1");
        assertThat(sms.envois()).hasSize(3);
        assertThat(textos.quotaDe("t-2").vue()).containsEntry("utilises", 0).containsEntry("restants", 3);
        assertThat(textos.reglerQuota("t-1", 0).vue()).containsEntry("illimite", true).containsEntry("restants", null);
        assertThat(textos.envoyer(new JournalTextos.Demande("t-1", "+14185550100", "E", null, null)).statut()).isEqualTo("envoye");
        assertThat(textos.reglerQuota("t-1", 5.0).limite()).isEqualTo(5);
        assertThatThrownBy(() -> textos.reglerQuota("t-1", -1)).hasMessage("limite mensuelle : entier de 0 (illimité) à 1 000 000 attendu");
        assertThatThrownBy(() -> textos.reglerQuota("t-1", "dix")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> textos.reglerQuota("t-1", 2.5)).isInstanceOf(IllegalArgumentException.class);
        // Le journal, le résumé, le rafraîchissement, le rappel.
        assertThat(textos.lister("t-1", null, null, null)).hasSize(6);
        assertThat(textos.lister("t-1", "echoue", null, 10)).hasSize(3);
        assertThat(textos.lister("t-1", null, "besoin:b1", 1)).singleElement().satisfies(x -> assertThat(x.id()).isEqualTo(t.id()));
        assertThat(textos.lister("t-2", null, null, null)).isEmpty();
        assertThat(textos.resume("t-1")).containsEntry("total", 6).containsEntry("dernierLe", "2026-09-16T12:00:00.000Z");
        assertThat(textos.resume("t-2")).containsEntry("total", 0).containsEntry("dernierLe", null);
        assertThat(enPanne.rafraichir("t-1", t.id()).statut()).isEqualTo("livre");
        assertThat(textos.rafraichir("t-1", refuse.id()).erreur()).isEqualTo("refusé par le fournisseur");
        assertThatThrownBy(() -> textos.rafraichir("t-2", t.id())).isInstanceOf(DepotJournalTextos.IntrouvableException.class);
        assertThatThrownBy(() -> textos.rafraichir("t-1", panne.id())).hasMessage("texto sans identifiant chez le fournisseur : rien à redemander");
        assertThat(textos.appliquerRappel("sim-2", "undelivered", "30003 Unreachable")).isEqualTo(1);
        assertThat(textos.lire(refuse.id()).orElseThrow().erreur()).isEqualTo("30003 Unreachable");
        assertThat(textos.appliquerRappel("sim-1", "failed", null)).isEqualTo(1);
        assertThat(textos.lire(t.id()).orElseThrow().erreur()).isEqualTo("refusé par le fournisseur (failed)");
        assertThat(textos.appliquerRappel("sim-1", "delivered", null)).isEqualTo(1);
        assertThat(textos.lire(t.id()).orElseThrow().statut()).isEqualTo("livre");
        assertThat(textos.appliquerRappel("inconnu", "delivered", null)).isZero();
        assertThat(textos.etat().vue()).containsEntry("fournisseur", "simule").containsEntry("simule", true).containsEntry("rappel", null);
    }

    @Test
    @DisplayName("l'exécuteur envoie le texto par le porteur puis journalise ; un numéro absent ou un texto non livré fait échouer la tâche")
    void executeur() {
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, phone) VALUES ('e1', 'Léa', 'B', 't-1', '418 555 0100'), ('e3', 'Sans', 'Numéro', 't-1', NULL), ('e4', 'Injoignable', 'X', 't-1', '418 555 0199')").update();
        jdbc.sql("INSERT INTO scol_suppleants (id, tenantId, personId, telephone, createdAt) VALUES ('s1', 't-1', 'e2', '514 555 0100', 'x')").update();
        assertThat(registre.pour("notifier")).isPresent();
        textos.reglerQuota("t-1", 0);
        TacheDifferee t = notifier.planifier("t-1", "annulation-b1", "2026-09-16T12:00:00.000Z", List.of("email", "sms"), "teacher", List.of("e1", "e2"), "Remplacement annulé — {{jours}}", "Le {{jours}} n'a plus lieu : {{motif}}.", Map.of("jours", "2026-09-20", "motif", "absence annulée"), null);
        assertThat(t.type()).isEqualTo("notifier");
        assertThat(t.charge()).containsEntry("destinataireType", "teacher");
        assertThat(notifier.executer(t, "2026-09-16T12:00:00.000Z")).isEmpty();
        assertThat(sms.envois()).extracting(Textos.SmsSimule.Trace::numero, Textos.SmsSimule.Trace::texte).containsExactly(
                org.assertj.core.groups.Tuple.tuple("+14185550100", "Le 2026-09-20 n'a plus lieu : absence annulée."), org.assertj.core.groups.Tuple.tuple("+15145550100", "Le 2026-09-20 n'a plus lieu : absence annulée."));
        assertThat(notifications.lister("t-1", null, null, null)).hasSize(4).extracting(m -> m.get("channel")).containsExactlyInAnyOrder("email", "email", "sms", "sms");
        assertThat(notifications.lister("t-1", null, "e1", "sms").get(0)).containsEntry("subject", "Remplacement annulé — 2026-09-20");

        TacheDifferee sansCanal = new TacheDifferee("x", "t-1", "notifier", "c", Map.of(), "now", "a_faire", 0, null, null, null);
        assertThatThrownBy(() -> notifier.executer(sansCanal, "now")).hasMessage("notification sans canal");
        TacheDifferee sansNumero = notifier.planifier("t-1", "n2", "2026-09-16T12:00:00.000Z", List.of("sms"), "teacher", List.of("e3"), "S", "C", null, "rh");
        assertThatThrownBy(() -> notifier.executer(sansNumero, "now")).hasMessage("aucun numéro de téléphone pour e3");
        TacheDifferee nonLivre = notifier.planifier("t-1", "n3", "2026-09-16T12:00:00.000Z", List.of("sms"), "teacher", List.of("e4"), "S", "C", Map.of(), "rh");
        assertThatThrownBy(() -> notifier.executer(nonLivre, "now")).hasMessage("texto non livré à e4 (texto 3 : refusé par le fournisseur)");
        // Le journal des textos garde la trace, avec le contexte de la tâche ; la poussée n'a sonné qu'une fois par personne pour la nouvelle bicanal.
        assertThat(textos.lister("t-1", null, "annulation-b1", null)).hasSize(2).allSatisfy(x -> assertThat(x.statut()).isEqualTo("envoye"));
        assertThat(poussee.envois()).hasSize(3);
        // Une personne qui a fermé le texto n'en reçoit pas, et la tâche réussit quand même.
        preferences.regler("u-1", "t-1", Map.of("texto", false));
        TacheDifferee ferme = notifier.planifier("t-1", "n4", "2026-09-16T12:00:00.000Z", List.of("sms"), "teacher", List.of("e1"), "S", "C", Map.of(), "rh");
        assertThat(notifier.executer(ferme, "now")).isEmpty();
        assertThat(sms.envois()).hasSize(3);
        // Par l'ordonnanceur : la tâche échouée est retentée, la réussie est faite.
        BilanTic bilan = taches.tic("2026-09-16T12:00:00.000Z", PolitiqueRetentative.PAR_DEFAUT, "essai");
        assertThat(bilan.faites()).isEqualTo(2);
        assertThat(bilan.retentees() + bilan.echouees()).isEqualTo(2);
    }
}
