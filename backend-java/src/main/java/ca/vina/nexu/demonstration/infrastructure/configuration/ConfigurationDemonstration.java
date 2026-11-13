package ca.vina.nexu.demonstration.infrastructure.configuration;

import ca.vina.nexu.demonstration.infrastructure.persistance.Demonstrations;
import ca.vina.nexu.demonstration.infrastructure.web.UrlServeur;

import ca.vina.nexu.demonstration.domaine.Demontrables;
import ca.vina.nexu.demonstration.domaine.EntraDemo;
import ca.vina.nexu.demonstration.domaine.RecepteurWebhooksDemo;
import ca.vina.nexu.demonstration.domaine.SamlIdpDemo;
import ca.vina.nexu.demonstration.domaine.TelnyxDemo;
import ca.vina.nexu.demonstration.domaine.TwilioDemo;
import ca.vina.nexu.integrations.domaine.CartesIntegrations;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.EtatIntegrations;
import ca.vina.nexu.integrations.infrastructure.persistance.JetonClient;
import ca.vina.nexu.notifications.domaine.Appels;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.persistance.ClientFournisseur;
import ca.vina.nexu.partage.demonstration.Commutateur;
import ca.vina.nexu.partage.demonstration.RegistreDemonstrations;
import ca.vina.nexu.securite.domaine.Antivirus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Les tiers de démonstration, un par processus : annuaire Entra, fournisseur SAML, OAuth, récepteur
 * de webhooks, Twilio, Telnyx et clamd. Leur état (codes en attente, messages, journal) est celui du
 * serveur — jamais une donnée d'établissement. L'aléa et l'horloge leur sont injectés : le domaine ne
 * les appelle pas. GRICS et l'ATS du produit d'origine n'ont pas suivi la séparation : ils servent
 * le scolaire, pas les effectifs.
 */
@Configuration
public class ConfigurationDemonstration {

    /** L'adresse à laquelle ce serveur se joint lui-même, et à laquelle un navigateur atteint ses mocks. */
    @Bean
    public UrlServeur urlServeur(@Value("${nexu.url.serveur}") String base) {
        return new UrlServeur(base);
    }

    @Bean
    public TwilioDemo twilioDemo(Clock horloge) {
        return new TwilioDemo(horloge, new SecureRandom());
    }

    @Bean
    public TelnyxDemo telnyxDemo(Clock horloge) {
        return new TelnyxDemo(horloge, UUID::randomUUID);
    }

    @Bean
    public RecepteurWebhooksDemo recepteurWebhooksDemo(Clock horloge) {
        return new RecepteurWebhooksDemo(horloge);
    }

    @Bean
    public EntraDemo entraDemo(Clock horloge) {
        return new EntraDemo(horloge, new SecureRandom());
    }

    @Bean
    public SamlIdpDemo samlIdpDemo(Clock horloge) {
        return new SamlIdpDemo(horloge, new SecureRandom());
    }

    /** Les variables que le registre consulte, lues une fois : les essais lui donnent leur propre carte. */
    public static Map<String, String> environnement(Environment env) {
        Map<String, String> m = new HashMap<>();
        for (String v : Demontrables.variables()) {
            String valeur = env.getProperty(v);
            if (valeur != null && !valeur.isEmpty()) {
                m.put(v, valeur);
            }
        }
        return m;
    }

    /**
     * Le registre des démonstrations, branché au démarrage selon {@code DEMONSTRATION_INTEGRATIONS}
     * — une valeur inconnue ou une configuration réelle présente arrêtent le serveur.
     */
    @Bean
    public Demonstrations demonstrations(Demonstrations.Ports ports, Demonstrations.Donnees donnees, Environment env, UrlServeur urlServeur, Clock horloge) {
        Demonstrations d = new Demonstrations(ports, donnees, environnement(env), urlServeur.base(), horloge);
        d.demarrerDepuisEnv();
        return d;
    }

    @Bean
    public Demonstrations.Ports portsDemonstration(Commutateur<Textos.PorteurSms> sms, Commutateur<Appels.PorteurAppelSortant> appels, Commutateur<Antivirus.AnalyseurFichier> antivirus,
                                                   ClientFournisseur client, EntraDemo entra, SamlIdpDemo samlIdp, RecepteurWebhooksDemo recepteur) {
        return new Demonstrations.Ports(sms, appels, antivirus, client, entra, samlIdp, recepteur);
    }

    @Bean
    public Demonstrations.Donnees donneesDemonstration(DepotClientsApi clients, JetonClient jetons, DepotWebhooks webhooks) {
        return new Demonstrations.Donnees(clients, jetons, webhooks);
    }

    @Bean
    public RegistreDemonstrations registreDemonstrations(Demonstrations demonstrations) {
        return demonstrations;
    }

    /** Ce que les cartes d'état lisent du registre : possibilité, état, client d'API et port clamd de démonstration. */
    @Bean
    public EtatIntegrations.LecteurDemonstrations lecteurDemonstrations(Demonstrations demonstrations) {
        return (tenantId, env) -> {
            Map<String, CartesIntegrations.Demonstration> parCode = new LinkedHashMap<>();
            for (String code : Demontrables.CODES) {
                Demontrables.Possibilite p = Demontrables.peutBrancher(code, env);
                parCode.put(code, new CartesIntegrations.Demonstration(p.possible(), p.motif(), demonstrations.estDemontree(code, tenantId)));
            }
            return new CartesIntegrations.Demonstrations(null, null, parCode, demonstrations.clientDemo(tenantId).map(c -> c.client().clientId()).orElse(null),
                    demonstrations.portClamdDemo().orElse(null), demonstrations.urlServeur());
        };
    }
}
