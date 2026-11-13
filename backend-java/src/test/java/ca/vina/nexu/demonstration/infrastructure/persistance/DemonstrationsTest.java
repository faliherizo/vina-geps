package ca.vina.nexu.demonstration.infrastructure.persistance;

import ca.vina.nexu.demonstration.infrastructure.configuration.ConfigurationDemonstration;
import ca.vina.nexu.demonstration.infrastructure.web.ControleurDemonstrations;
import ca.vina.nexu.demonstration.infrastructure.web.UrlServeur;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.RecepteurWebhooksDemo;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.demonstration.domaine.TwilioDemo;
import ca.vina.nexu.integrations.domaine.CartesIntegrations;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.EtatIntegrations;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.persistance.ClientFournisseur;
import ca.vina.nexu.notifications.infrastructure.FournisseurFactice;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.demonstration.Commutateur;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.securite.infrastructure.persistance.AnalyseurClamd;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.json.JsonMapper;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le registre des démonstrations, avec de vrais tiers embarqués derrière : le Twilio de
 * démonstration servi par HTTP, le clamd de démonstration sur une vraie prise TCP, les données
 * du locataire en base. Chaque essai fait passer un message réel — c'est la preuve que l'écran affiche.
 */
@DisplayName("Démonstrations — brancher, débrancher, essayer chaque tiers embarqué")
class DemonstrationsTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ADMIN = new ContexteRequete("a1", "a1", "admin", null, "t-1");
    private static final ContexteRequete EMP = new ContexteRequete("e1", "e1", "employee", "e1", "t-1");
    private static final ContexteRequete SANS_MODULE = new ContexteRequete("a3", "a3", "admin", null, "t-3");

    private JdbcClient jdbc;
    private FournisseurFactice tiers;
    private Demonstrations demonstrations;
    private Commutateur<Textos.PorteurSms> sms;
    private Commutateur<Appels.PorteurAppelSortant> appels;
    private Commutateur<Antivirus.AnalyseurFichier> antivirus;
    private RecepteurWebhooksDemo recepteur;
    private DepotWebhooks webhooks;
    private DepotClientsApi clients;
    private ClientHttpEssai http;
    private Map<String, String> env;

    private static Map<String, String> formulaire(String corps) {
        Map<String, String> m = new HashMap<>();
        for (String p : corps.split("&")) {
            int i = p.indexOf('=');
            if (i > 0) {
                m.put(URLDecoder.decode(p.substring(0, i), StandardCharsets.UTF_8), URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8));
            }
        }
        return m;
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) throws Exception {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas VALUES ('t-1', 10, 10, '[\"integrations\"]'), ('t-3', 10, 10, '[]')").update();
        tiers = new FournisseurFactice();
        TwilioDemo twilio = new TwilioDemo(HORLOGE, new SecureRandom());
        JsonMapper json = JsonMapper.builder().build();
        tiers.repondre = r -> {
            if (r.chemin().endsWith("/Messages.json")) {
                TwilioDemo.Reponse rep = twilio.creerMessage(formulaire(r.texte()), DonneesDemo.TWILIO_ACCOUNT_SID);
                return new FournisseurFactice.Reponse(rep.statut(), json.writeValueAsString(rep.corps()));
            }
            TwilioDemo.Reponse rep = twilio.creerAppel(formulaire(r.texte()), DonneesDemo.TWILIO_ACCOUNT_SID);
            return new FournisseurFactice.Reponse(rep.statut(), json.writeValueAsString(rep.corps()));
        };
        sms = new Commutateur<>(new Textos.SmsSimule(List.of(), () -> "2026-09-14T12:00:00.000Z", "NexuVINA"));
        appels = new Commutateur<>(new Appels.AppelSimule(List.of(), () -> "2026-09-14T12:00:00.000Z"));
        antivirus = new Commutateur<>(new Antivirus.AnalyseurSimule());
        recepteur = new RecepteurWebhooksDemo(HORLOGE);
        RegistreExecuteurs registre = new RegistreExecuteurs();
        DepotModulesActives modules = new DepotModulesActives(jdbc);
        webhooks = new DepotWebhooks(jdbc, new DepotTaches(jdbc, registre), modules, (url, corps, enTetes) -> 200, registre);
        clients = new DepotClientsApi(jdbc);
        env = new HashMap<>();
        demonstrations = new Demonstrations(
                new Demonstrations.Ports(sms, appels, antivirus, new ClientFournisseur(HttpClient.newHttpClient(), 5_000), new EntraDemo(HORLOGE, new SecureRandom()), new SamlIdpDemo(HORLOGE, new SecureRandom()), recepteur),
                new Demonstrations.Donnees(clients, new JetonClient("secret-d-essai-de-trente-deux-octets-au-moins"), webhooks), env, tiers.url(), HORLOGE);
        http = new ClientHttpEssai(new ControleurDemonstrations(demonstrations, modules, new GardeAcces(new RegistrePolitiqueAcces())));
    }

    @AfterEach
    void fermer() {
        demonstrations.debrancher("antivirus", "t-1");
        tiers.close();
    }

    @Test
    @DisplayName("textos et appels : le Twilio embarqué répond, l'injoignable échoue, le débranchement rend le simulateur")
    void sms() {
        assertThat(demonstrations.estBranchee("sms")).isFalse();
        demonstrations.brancher("sms", "t-1", "a1", "http://localhost:5173/admin/");
        assertThat(demonstrations.estBranchee("sms")).isTrue();
        assertThat(demonstrations.urlApplication()).contains("http://localhost:5173/admin");
        assertThat(sms.estBranche()).isTrue();
        Demonstrations.Essai e = demonstrations.essayer("sms", "t-1");
        assertThat(e.ok()).isTrue();
        assertThat(e.detail()).startsWith("texto SM").contains(": envoye ; vers l'injoignable +15550100199 : echoue ; appel CA").contains("(l'agent est sonné d'abord, le numéro affiché est celui du client)");
        assertThat(tiers.requetes).hasSize(3);
        assertThat(tiers.requetes.get(0).chemin()).isEqualTo("/mock/twilio/2010-04-01/Accounts/" + DonneesDemo.TWILIO_ACCOUNT_SID + "/Messages.json");
        demonstrations.debrancher("sms", "t-1");
        assertThat(sms.estBranche()).isFalse();
        assertThat(appels.estBranche()).isFalse();
        assertThat(demonstrations.estBranchee("sms")).isFalse();
        // Le simulateur ne connaît pas l'injoignable : l'essai le dit.
        assertThat(demonstrations.essayer("sms", "t-1").ok()).isFalse();
    }

    @Test
    @DisplayName("antivirus : un clamd de démonstration démarre sur une prise locale, l'adaptateur réel lui parle, EICAR est nommée")
    void antivirus() {
        assertThat(demonstrations.portClamdDemo()).isEmpty();
        demonstrations.brancher("antivirus", "t-1", "a1", null);
        assertThat(demonstrations.portClamdDemo()).isPresent();
        assertThat(antivirus.courant()).isInstanceOf(AnalyseurClamd.class);
        Demonstrations.Essai e = demonstrations.essayer("antivirus", "t-1");
        assertThat(e).isEqualTo(new Demonstrations.Essai(true, "fichier ordinaire : sain ; chaîne EICAR : refusée (Eicar-Test-Signature)"));
        int port = demonstrations.portClamdDemo().orElseThrow();
        // Rebrancher ne redémarre pas le démon.
        demonstrations.brancher("antivirus", "t-1", "a1", null);
        assertThat(demonstrations.portClamdDemo()).contains(port);
        demonstrations.debrancher("antivirus", "t-1");
        assertThat(demonstrations.portClamdDemo()).isEmpty();
        assertThat(antivirus.estBranche()).isFalse();
        assertThat(demonstrations.essayer("antivirus", "t-1").ok()).isTrue();   // le simulateur EICAR aussi
        demonstrations.debrancher("antivirus", "t-1");   // déjà arrêté : sans effet
        // Un analyseur qui accepte tout : le défaut est nommé.
        antivirus.brancher(c -> new Antivirus.Verdict(true, null));
        assertThat(demonstrations.essayer("antivirus", "t-1").detail()).isEqualTo("fichier ordinaire : sain ; chaîne EICAR : acceptée (défaut !)");
        antivirus.brancher(c -> new Antivirus.Verdict(false, "Tout"));
        assertThat(demonstrations.essayer("antivirus", "t-1").detail()).startsWith("fichier ordinaire : refusé");
    }

    @Test
    @DisplayName("clients d'API : un client de démonstration est créé, son jeton vérifié, puis révoqué")
    void api() {
        assertThat(demonstrations.estDemontree("api", "t-1")).isFalse();
        assertThat(demonstrations.essayer("api", "t-1")).isEqualTo(new Demonstrations.Essai(false, "le secret du client de démonstration n'est connu que du processus qui l'a créé : débranchez puis rebranchez"));
        demonstrations.brancher("api", "t-1", "a1", null);
        assertThat(demonstrations.estDemontree("api", "t-1")).isTrue();
        assertThat(demonstrations.estBranchee("api")).isFalse();   // donnée du locataire, pas port du processus
        assertThat(demonstrations.clientDemo("t-1")).isPresent();
        Demonstrations.Essai e = demonstrations.essayer("api", "t-1");
        assertThat(e.ok()).isTrue();
        assertThat(e.detail()).startsWith("jeton client_credentials obtenu pour ").contains("(portées read), vérifié : locataire t-1");
        demonstrations.brancher("api", "t-1", "a1", null);   // déjà démontré : rien de plus
        assertThat(clients.lister("t-1")).hasSize(1);
        demonstrations.debrancher("api", "t-1");
        assertThat(demonstrations.estDemontree("api", "t-1")).isFalse();
        assertThat(demonstrations.clientDemo("t-1")).isEmpty();
    }

    @Test
    @DisplayName("webhooks : le récepteur embarqué est abonné à tout, l'essai met un événement en file, le débranchement désactive sans supprimer")
    void webhooks() {
        assertThat(demonstrations.essayer("webhooks", "t-1")).isEqualTo(new Demonstrations.Essai(false, "aucun webhook de démonstration actif"));
        demonstrations.brancher("webhooks", "t-1", "a1", null);
        assertThat(demonstrations.estDemontree("webhooks", "t-1")).isTrue();
        assertThat(recepteur.reglages()).containsEntry("secretConfigure", true);
        Demonstrations.Essai e = demonstrations.essayer("webhooks", "t-1");
        assertThat(e.ok()).isTrue();
        assertThat(e.detail()).isEqualTo("événement employe.cree mis en file (1 envoi) ; le récepteur en a 0 au journal : " + tiers.url() + "/mock/webhooks/recepteur/journal.html");
        demonstrations.debrancher("webhooks", "t-1");
        assertThat(demonstrations.estDemontree("webhooks", "t-1")).isFalse();
        assertThat(webhooks.lister("t-1")).hasSize(1);
        demonstrations.brancher("webhooks", "t-1", "a1", null);   // réactive l'existant
        assertThat(webhooks.lister("t-1")).hasSize(1);
        assertThat(demonstrations.estDemontree("webhooks", "t-1")).isTrue();
        demonstrations.debrancher("webhooks", "t-2");   // aucun webhook : sans effet
    }

    @Test
    @DisplayName("Entra et SAML : le registre suffit, l'essai lit la découverte et les métadonnées, les réglages livrent ce que les adaptateurs demandent")
    void sso() {
        demonstrations.brancher("entra", "t-1", "a1", null);
        demonstrations.brancher("saml", "t-1", "a1", null);
        assertThat(demonstrations.estBranchee("entra")).isTrue();
        assertThat(demonstrations.estDemontree("saml", "t-1")).isTrue();
        Demonstrations.Essai entra = demonstrations.essayer("entra", "t-1");
        assertThat(entra.ok()).isTrue();
        assertThat(entra.detail()).isEqualTo("émetteur " + tiers.url() + "/mock/entra/" + DonneesDemo.ENTRA_TENANT + "/v2.0 ; 1 clé(s) de signature publiée(s) ; connexion : bouton « Microsoft » de l'écran de connexion");
        Demonstrations.Essai saml = demonstrations.essayer("saml", "t-1");
        assertThat(saml.ok()).isTrue();
        assertThat(saml.detail()).startsWith("métadonnées de l'IdP lues (").endsWith(" caractères) ; connexion : bouton « SAML » de l'écran de connexion");
        assertThat(demonstrations.reglages("entra")).containsEntry("tenantId", DonneesDemo.ENTRA_TENANT).containsEntry("clientId", DonneesDemo.ENTRA_CLIENT_ID).containsEntry("clientSecret", DonneesDemo.ENTRA_CLIENT_SECRET);
        assertThat(demonstrations.reglages("saml")).containsEntry("issuer", DonneesDemo.SAML_ISSUER).containsKey("clePublique");
        assertThat(demonstrations.reglages("sms")).isEmpty();
        demonstrations.debrancher("entra", "t-1");
        demonstrations.debrancher("saml", "t-1");
        assertThat(demonstrations.estBranchee("entra")).isFalse();
        assertThat(demonstrations.estBranchee("saml")).isFalse();
    }

    @Test
    @DisplayName("une démonstration n'est possible que si rien de réel n'est configuré ; un code inconnu est introuvable ; le démarrage branche ce que l'environnement demande")
    void regles() {
        env.put("AZURE_CLIENT_ID", "reel");
        assertThatThrownBy(() -> demonstrations.brancher("entra", "t-1", "a1", null)).hasMessage("démonstration impossible pour entra : configuration réelle présente (AZURE_CLIENT_ID)");
        assertThat(demonstrations.peutBrancher("entra").possible()).isFalse();
        assertThatThrownBy(() -> demonstrations.brancher("fax", "t-1", "a1", null)).hasMessage("intégration introuvable : fax");
        assertThatThrownBy(() -> demonstrations.debrancher("fax", "t-1")).hasMessage("intégration introuvable : fax");
        assertThatThrownBy(() -> demonstrations.essayer("fax", "t-1")).hasMessage("intégration introuvable : fax");
        assertThatThrownBy(() -> demonstrations.brancher("sms", "t-1", "a1", "ftp://x")).hasMessage("urlApplication : http ou https seulement");
        env.remove("AZURE_CLIENT_ID");
        env.put("DEMONSTRATION_INTEGRATIONS", "entra,saml");
        assertThat(demonstrations.demarrerDepuisEnv()).containsExactly("entra", "saml");
        assertThat(demonstrations.estBranchee("saml")).isTrue();
        assertThat(demonstrations.urlServeur()).isEqualTo(tiers.url());
    }

    @Test
    @DisplayName("HTTP : session, établissement, module et rôle d'administration ; puis brancher, essayer, débrancher")
    void http() throws Exception {
        http.post("/api/integrations/demonstration/sms/brancher", null, null).andExpect(status().isUnauthorized());
        http.post("/api/integrations/demonstration/sms/brancher", SANS_MODULE, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module « integrations » non activé pour cet organisme"));
        http.post("/api/integrations/demonstration/sms/brancher", EMP, null).andExpect(status().isForbidden());
        http.post("/api/integrations/demonstration/fax/brancher", ADMIN, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("intégration introuvable : fax"));
        http.post("/api/integrations/demonstration/sms/brancher", ADMIN, "{\"urlApplication\":\"ftp://x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("urlApplication : http ou https seulement"));
        http.post("/api/integrations/demonstration/sms/brancher", ADMIN, "{\"urlApplication\":\"http://localhost:5173\"}").andExpect(status().isOk()).andExpect(jsonPath("$.code").value("sms")).andExpect(jsonPath("$.branchee").value(true));
        assertThat(demonstrations.urlApplication()).contains("http://localhost:5173");
        http.post("/api/integrations/demonstration/sms/essai", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("texto SM")));
        http.post("/api/integrations/demonstration/sms/debrancher", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.branchee").value(false));
        http.post("/api/integrations/demonstration/api/brancher", ADMIN, null).andExpect(status().isOk());
        http.post("/api/integrations/demonstration/api/essai", ADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    @DisplayName("la configuration Spring : environnement filtré, lecteur des cartes, registre partagé")
    void configuration() {
        ConfigurationDemonstration c = new ConfigurationDemonstration();
        MockEnvironment env = new MockEnvironment().withProperty("AZURE_CLIENT_ID", "x").withProperty("SMS_URL", "").withProperty("AUTRE", "y");
        assertThat(ConfigurationDemonstration.environnement(env)).containsExactly(Map.entry("AZURE_CLIENT_ID", "x"));
        demonstrations.brancher("antivirus", "t-1", "a1", null);
        demonstrations.brancher("api", "t-1", "a1", null);
        EtatIntegrations.LecteurDemonstrations lecteur = c.lecteurDemonstrations(demonstrations);
        CartesIntegrations.Demonstrations d = lecteur.pour("t-1", Map.of("AZURE_CLIENT_ID", "x"));
        assertThat(d.parCode().get("entra")).isEqualTo(new CartesIntegrations.Demonstration(false, "configuration réelle présente (AZURE_CLIENT_ID)", false));
        assertThat(d.parCode().get("antivirus").branchee()).isTrue();
        assertThat(d.parCode().get("api").branchee()).isTrue();
        assertThat(d.clientApiDemo()).isNotNull();
        assertThat(d.portClamdDemo()).isEqualTo(demonstrations.portClamdDemo().orElseThrow());
        assertThat(d.urlServeur()).isEqualTo(tiers.url());
        assertThat(c.registreDemonstrations(demonstrations)).isSameAs(demonstrations);
        assertThat(c.portsDemonstration(sms, appels, antivirus, new ClientFournisseur(HttpClient.newHttpClient(), 1), null, null, recepteur).recepteur()).isSameAs(recepteur);
        assertThat(c.donneesDemonstration(clients, null, webhooks).webhooks()).isSameAs(webhooks);
        Demonstrations d2 = c.demonstrations(new Demonstrations.Ports(sms, appels, antivirus, null, null, null, recepteur), new Demonstrations.Donnees(clients, null, webhooks), new MockEnvironment().withProperty("DEMONSTRATION_INTEGRATIONS", "saml"), new UrlServeur("http://localhost:4000"), HORLOGE);
        assertThat(d2.estBranchee("saml")).isTrue();
        // Les cartes d'état lisent les démonstrations branchées.
        EtatIntegrations etat = new EtatIntegrations(jdbc, clients, webhooks, HORLOGE, lecteur);
        Map<String, CartesIntegrations.Carte> cartes = new LinkedHashMap<>();
        etat.cartes("t-1", Map.of()).forEach(x -> cartes.put(x.code(), x));
        assertThat(cartes.get("antivirus").etat()).isEqualTo("demonstration");
        assertThat(cartes.get("api").detail()).startsWith("client de démonstration ");
        assertThat(cartes.get("webhooks").demonstration().branchee()).isFalse();
    }
}
