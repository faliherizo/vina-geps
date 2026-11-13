package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.SourceHorodatagesIntegrationPort;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** La source RAO en HTTP : {@code GET {url}/horodatages?depuis=…}, une liste JSON. */
public class HorodatagesHttpIntegration implements SourceHorodatagesIntegrationPort {

    private final ClientHttpIntegration.ConfigHttp config;
    private final ClientHttpIntegration.Appel appel;

    public HorodatagesHttpIntegration(ClientHttpIntegration.ConfigHttp config, ClientHttpIntegration.Appel appel) {
        this.config = config;
        this.appel = appel;
    }

    @Override
    public List<IntegrationsEffectifs.HorodatageExterne> lire(String depuis) {
        // encodeURIComponent : une espace s'écrit %20, pas « + » (qu'un serveur lirait comme un signe).
        String q = URLEncoder.encode(depuis, StandardCharsets.UTF_8).replace("+", "%20");
        return ClientHttpIntegration.lireListe(appel, ClientHttpIntegration.requete(config, "/horodatages?depuis=" + q).GET().build()).stream()
                .map(IntegrationsEffectifs::horodatageDe).toList();
    }
}
