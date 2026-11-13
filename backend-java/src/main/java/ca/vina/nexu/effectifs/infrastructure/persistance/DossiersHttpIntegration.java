package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.SourceDossiersIntegrationPort;

import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

/** La source BD Corpo en HTTP : {@code POST {url}/dossiers/recherche} avec les matricules, une liste de {@code { matricule, anciennete }}. */
public class DossiersHttpIntegration implements SourceDossiersIntegrationPort {

    private final ClientHttpIntegration.ConfigHttp config;
    private final ClientHttpIntegration.Appel appel;

    public DossiersHttpIntegration(ClientHttpIntegration.ConfigHttp config, ClientHttpIntegration.Appel appel) {
        this.config = config;
        this.appel = appel;
    }

    @Override
    public List<IntegrationsEffectifs.DossierEmploye> lire(List<String> matricules) {
        HttpRequest r = ClientHttpIntegration.requete(config, "/dossiers/recherche")
                .POST(HttpRequest.BodyPublishers.ofString(ClientHttpIntegration.json(Map.of("matricules", matricules)))).build();
        return ClientHttpIntegration.lireListe(appel, r).stream().map(IntegrationsEffectifs::dossierDe).toList();
    }
}
