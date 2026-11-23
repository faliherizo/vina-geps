package ca.vina.nexu.demonstration.infrastructure.persistance;


import ca.vina.nexu.demonstration.domaine.Demontrable;
import ca.vina.nexu.demonstration.domaine.Demontrables;
import ca.vina.nexu.demonstration.domaine.DonneesDemo;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.RecepteurWebhooksDemo;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.integrations.domaine.ClientApi;
import ca.vina.nexu.integrations.domaine.Webhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.persistance.ClientFournisseur;
import ca.vina.nexu.notifications.infrastructure.persistance.Twilio;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.demonstration.Commutateur;
import ca.vina.nexu.partage.demonstration.RegistreDemonstrations;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.securite.infrastructure.persistance.AnalyseurClamd;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <strong>Le registre des démonstrations</strong> — quelle intégration est branchée sur son tiers
 * embarqué, et comment on l'y branche ou l'en débranche. L'état est celui du processus (les ports
 * SMS, appel, antivirus, SSO sont des singletons de configuration, pas des données
 * d'établissement) ; les webhooks et les clients d'API, eux, sont des données du locataire, et
 * leur démonstration se lit dans la base.
 */
public class Demonstrations implements RegistreDemonstrations {

    /** Les ports du processus que la démonstration permute, et les tiers embarqués qu'elle branche. */
    public record Ports(Commutateur<Textos.PorteurSms> sms, Commutateur<Appels.PorteurAppelSortant> appels, Commutateur<Antivirus.AnalyseurFichier> antivirus,
                        ClientFournisseur client, EntraDemo entra, SamlIdpDemo samlIdp, RecepteurWebhooksDemo recepteur) {
    }

    /** Les données du locataire que la démonstration crée ou désactive. */
    public record Donnees(DepotClientsApi clients, JetonClient jetons, DepotWebhooks webhooks) {
    }

    public record Essai(boolean ok, String detail) {
    }

    private final Ports ports;
    private final Donnees donnees;
    private final Map<String, String> env;
    private final String urlServeur;
    private final Clock horloge;
    private final Set<String> branchees = ConcurrentHashMap.newKeySet();
    private final Map<String, ClientApi.AvecSecret> clientsDemo = new ConcurrentHashMap<>();
    private volatile String urlApplication;
    private volatile ServeurClamdDemo clamd;

    public Demonstrations(Ports ports, Donnees donnees, Map<String, String> env, String urlServeur, Clock horloge) {
        this.ports = ports;
        this.donnees = donnees;
        this.env = env;
        this.urlServeur = urlServeur;
        this.horloge = horloge;
    }

    @Override
    public boolean estBranchee(String code) {
        return branchees.contains(code);
    }

    @Override
    public Optional<String> urlApplication() {
        return Optional.ofNullable(urlApplication);
    }

    @Override
    public String urlServeur() {
        return urlServeur;
    }

    @Override
    public Map<String, String> reglages(String code) {
        Map<String, String> r = new LinkedHashMap<>();
        Demontrable d = Demontrable.parCode(code).orElse(null);
        if (d == Demontrable.ENTRA) {
            r.put("tenantId", DonneesDemo.ENTRA_TENANT);
            r.put("clientId", DonneesDemo.ENTRA_CLIENT_ID);
            r.put("clientSecret", DonneesDemo.ENTRA_CLIENT_SECRET);
        }
        if (d == Demontrable.SAML) {
            r.put("issuer", DonneesDemo.SAML_ISSUER);
            r.put("clePublique", Base64.getEncoder().encodeToString(ports.samlIdp().clePublique().getEncoded()));
        }
        return r;
    }

    public Optional<ClientApi.AvecSecret> clientDemo(String tenantId) {
        return Optional.ofNullable(clientsDemo.get(tenantId));
    }

    public Optional<Integer> portClamdDemo() {
        return Optional.ofNullable(clamd).map(ServeurClamdDemo::port);
    }

    private String urlRecepteur() {
        return urlServeur + "/mock/webhooks/recepteur";
    }

    private Optional<DepotWebhooks.WebhookVue> webhookDemo(String tenantId) {
        return donnees.webhooks().lister(tenantId).stream().filter(w -> w.url().equals(urlRecepteur())).findFirst();
    }

    /** Branchée ? Pour les ports du processus, le registre ; pour les données du locataire, la base. */
    public boolean estDemontree(String code, String tenantId) {
        Demontrable d = Demontrable.parCode(code).orElse(null);
        if (d == Demontrable.WEBHOOKS) {
            return webhookDemo(tenantId).map(w -> w.actif() && !w.suspendu()).orElse(false);
        }
        if (d == Demontrable.API) {
            return donnees.clients().lister(tenantId).stream().anyMatch(c -> c.active() && Demontrables.NOM_CLIENT_DEMO.equals(c.name()));
        }
        return estBranchee(code);
    }

    public Demontrables.Possibilite peutBrancher(String code) {
        return Demontrables.peutBrancher(code, env);
    }

    public void brancher(String code, String tenantId, String acteur, String urlApplicationBrute) {
        Demontrable d = Demontrable.de(code);
        Demontrables.Possibilite p = peutBrancher(code);
        if (!p.possible()) {
            throw new IllegalStateException("démonstration impossible pour " + code + " : " + p.motif());
        }
        if (urlApplicationBrute != null && !urlApplicationBrute.isEmpty()) {
            urlApplication = Demontrables.origineApplication(urlApplicationBrute);
        }
        switch (d) {
            case SMS -> {
                Twilio.Config twilio = new Twilio.Config(urlServeur + "/mock/twilio", DonneesDemo.TWILIO_ACCOUNT_SID, DonneesDemo.TWILIO_AUTH_TOKEN, DonneesDemo.TWILIO_FROM);
                ports.sms().brancher(new Twilio.Sms(ports.client(), twilio, () -> Iso.de(horloge.instant())));
                ports.appels().brancher(new Twilio.Appel(ports.client(), twilio, () -> Iso.de(horloge.instant())));
            }
            case ANTIVIRUS -> {
                if (clamd == null) {
                    clamd = demarrerClamd();
                }
                ports.antivirus().brancher(new AnalyseurClamd("127.0.0.1", clamd.port(), 5_000, 64 * 1024));
            }
            case API -> {
                if (!estDemontree(code, tenantId)) {
                    clientsDemo.put(tenantId, donnees.clients().creer(tenantId, Demontrables.NOM_CLIENT_DEMO, List.of("read")));
                }
                return;
            }
            case WEBHOOKS -> {
                Optional<DepotWebhooks.WebhookVue> existant = webhookDemo(tenantId);
                if (existant.isPresent()) {
                    donnees.webhooks().reglerActivite(tenantId, existant.get().id(), true);
                    return;
                }
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("url", urlRecepteur());
                w.put("evenements", Webhooks.EVENEMENTS);
                w.put("description", Demontrables.DESCRIPTION_WEBHOOK_DEMO);
                Map<String, Object> cree = donnees.webhooks().creer(tenantId, acteur, w, Iso.de(horloge.instant()));
                ports.recepteur().regler(Map.of("secret", cree.get("secret"), "codeReponse", 200));
                return;
            }
            case ENTRA, SAML -> {
                // le registre suffit, les adaptateurs le lisent
            }
        }
        branchees.add(code);
    }

    private ServeurClamdDemo demarrerClamd() {
        String port = env.getOrDefault("MOCK_CLAMAV_PORT", "");
        return Primitives.surement("clamd de démonstration", () -> new ServeurClamdDemo(port.isEmpty() ? 0 : Integer.parseInt(port)));
    }

    public void debrancher(String code, String tenantId) {
        switch (Demontrable.de(code)) {
            case SMS -> {
                ports.sms().debrancher();
                ports.appels().debrancher();
            }
            case ANTIVIRUS -> {
                ports.antivirus().debrancher();
                fermerClamd();
            }
            case API -> {
                donnees.clients().lister(tenantId).stream().filter(c -> c.active() && Demontrables.NOM_CLIENT_DEMO.equals(c.name())).forEach(c -> donnees.clients().revoquer(tenantId, c.id()));
                clientsDemo.remove(tenantId);
                return;
            }
            case WEBHOOKS -> {
                // Un webhook qui a livré ne se supprime pas (règle WH-6) : on le désactive, c'est réversible.
                webhookDemo(tenantId).ifPresent(w -> donnees.webhooks().reglerActivite(tenantId, w.id(), false));
                return;
            }
            case ENTRA, SAML -> {
                // le registre suffit
            }
        }
        branchees.remove(code);
    }

    private void fermerClamd() {
        ServeurClamdDemo s = clamd;
        if (s != null) {
            clamd = null;
            try {
                s.close();
            } catch (IOException e) {
                // la prise était déjà fermée : le démon est arrêté de toute façon
            }
        }
    }

    /**
     * Un essai qui <strong>fait passer un message réel par le tiers embarqué</strong> — pas une
     * lecture d'état. C'est la preuve que l'écran affiche : le texto a un identifiant Twilio,
     * l'antivirus nomme EICAR, le récepteur a bien vu la signature.
     */
    public Essai essayer(String code, String tenantId) {
        return switch (Demontrable.de(code)) {
            case SMS -> essaiSms();
            case ANTIVIRUS -> essaiAntivirus();
            case API -> essaiApi(tenantId);
            case WEBHOOKS -> essaiWebhooks(tenantId);
            case ENTRA -> essaiEntra();
            case SAML -> essaiSaml();
        };
    }

    private Essai essaiSms() {
        Textos.Envoi ok = ports.sms().courant().envoyer("+15550100150", "Essai de la démonstration Nexu VINA.", null);
        Textos.Envoi ko = ports.sms().courant().envoyer(DonneesDemo.NUMERO_INJOIGNABLE, "Essai vers un injoignable.", null);
        Appels.AppelSortant a = ports.appels().courant().appeler(DonneesDemo.TWILIO_FROM, "+15550100151", "+15550100152");
        boolean reussi = !Textos.ECHOUE.equals(ok.statut()) && Textos.ECHOUE.equals(ko.statut()) && !Textos.ECHOUE.equals(a.statut());
        return new Essai(reussi, "texto " + ok.id() + " : " + ok.statut() + " ; vers l'injoignable " + DonneesDemo.NUMERO_INJOIGNABLE + " : " + ko.statut()
                + " ; appel " + a.id() + " : " + a.statut() + " (l'agent est sonné d'abord, le numéro affiché est celui du client)");
    }

    private Essai essaiAntivirus() {
        Antivirus.Verdict sain = ports.antivirus().courant().analyser("Contrat de travail — exemplaire employé.".getBytes(StandardCharsets.UTF_8));
        Antivirus.Verdict eicar = ports.antivirus().courant().analyser(Antivirus.EICAR.getBytes(StandardCharsets.US_ASCII));
        return new Essai(sain.sain() && !eicar.sain(), "fichier ordinaire : " + (sain.sain() ? "sain" : "refusé") + " ; chaîne EICAR : "
                + (eicar.sain() ? "acceptée (défaut !)" : "refusée (" + eicar.signature() + ")"));
    }

    private Essai essaiApi(String tenantId) {
        ClientApi.AvecSecret c = clientsDemo.get(tenantId);
        if (c == null) {
            return new Essai(false, "le secret du client de démonstration n'est connu que du processus qui l'a créé : débranchez puis rebranchez");
        }
        ClientApi verifie = donnees.clients().verifier(c.client().clientId(), c.clientSecret()).orElseThrow();
        String jeton = donnees.jetons().emettre(verifie.clientId(), verifie.tenantId(), verifie.scopes());
        JetonClient.Revendications revendications = donnees.jetons().lireRevendications(jeton);
        boolean ok = revendications != null && tenantId.equals(revendications.tenantId());
        return new Essai(ok, "jeton client_credentials obtenu pour " + verifie.clientId() + " (portées " + String.join(" ", verifie.scopes()) + "), vérifié : locataire " + (revendications == null ? null : revendications.tenantId()));
    }

    private Essai essaiWebhooks(String tenantId) {
        int avant = ports.recepteur().journal().size();
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("essai", true);
        charge.put("message", "Essai de la démonstration");
        int n = donnees.webhooks().emettre(tenantId, "employe.cree", charge, null, Iso.de(horloge.instant()));
        return new Essai(n > 0, n > 0 ? "événement employe.cree mis en file (" + n + " envoi) ; le récepteur en a " + avant + " au journal : " + urlServeur + "/mock/webhooks/recepteur/journal.html"
                : "aucun webhook de démonstration actif");
    }

    private Essai essaiEntra() {
        Map<String, Object> d = EntraDemo.documentDecouverte(urlServeur, DonneesDemo.ENTRA_TENANT);
        int cles = ((List<?>) ports.entra().jwks().get("keys")).size();
        return new Essai(d.get("issuer") != null && cles > 0, "émetteur " + d.get("issuer") + " ; " + cles + " clé(s) de signature publiée(s) ; connexion : bouton « Microsoft » de l'écran de connexion");
    }

    private Essai essaiSaml() {
        String xml = ports.samlIdp().metadonnees(urlServeur);
        return new Essai(xml.contains("IDPSSODescriptor"), "métadonnées de l'IdP lues (" + xml.length() + " caractères) ; connexion : bouton « SAML » de l'écran de connexion");
    }

    /** Au démarrage : {@code DEMONSTRATION_INTEGRATIONS=entra,sms,…} branche les ports du processus. */
    public List<String> demarrerDepuisEnv() {
        List<String> codes = Demontrables.lireDemonstrationsEnv(env);
        codes.forEach(code -> brancher(code, "", "demarrage", null));
        return codes;
    }
}
