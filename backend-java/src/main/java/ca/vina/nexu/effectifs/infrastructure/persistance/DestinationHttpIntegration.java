package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DestinataireIndisponibleException;
import ca.vina.nexu.effectifs.domaine.DestinationIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageALivrer;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Le destinataire HTTP d'un connecteur sortant : {@code POST {url}/{type}} avec la clé d'idempotence. 2xx accepté ; 400, 409,
 * 422 rejeté ; le reste — et une panne de transport — lève : c'est passager, l'ordonnanceur retentera.
 */
public class DestinationHttpIntegration implements DestinationIntegrationPort {

    private final ClientHttpIntegration.ConfigHttp config;
    private final ClientHttpIntegration.Appel appel;

    public DestinationHttpIntegration(ClientHttpIntegration.ConfigHttp config, ClientHttpIntegration.Appel appel) {
        this.config = config;
        this.appel = appel;
    }

    @Override
    public Issue livrer(MessageALivrer m) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("id", m.id());
        corps.put("type", m.type());
        corps.put("cle", m.cle());
        corps.put("charge", m.charge());
        HttpRequest r = ClientHttpIntegration.requete(config, m.type().chemin()).header("Idempotency-Key", m.cle())
                .POST(HttpRequest.BodyPublishers.ofString(ClientHttpIntegration.json(corps))).build();
        ClientHttpIntegration.Reponse x;
        try {
            x = ClientHttpIntegration.appeler(appel, r);
        } catch (IOException e) {
            throw new DestinataireIndisponibleException(m.connecteur() + " injoignable : " + ClientHttpIntegration.motif(e));
        }
        return IntegrationsEffectifs.issueHttp(x.statut(), ClientHttpIntegration.carte(x.corps()))
                .orElseThrow(() -> new DestinataireIndisponibleException(m.connecteur() + " en erreur (HTTP " + x.statut() + ") : on retentera"));
    }
}
