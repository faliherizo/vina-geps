package ca.vina.nexu.partage.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;
import java.util.Map;

/**
 * Table de routage effective — outil de suivi de la migration.
 *
 * <p>Répond à « quels endpoints ce backend sert-il réellement ? » par la vérité de Spring plutôt
 * que par une déduction. Sonder les chemins ne suffit pas : un {@code 404} peut aussi bien
 * signifier « aucun contrôleur » que « le contrôleur répond que la ressource n'existe pas » —
 * {@code /api/countries/XX} rend légitimement 404 avec {@code unknown country}. Confondre les
 * deux faisait mesurer 1 endpoint servi là où il y en avait 7.
 *
 * <p><strong>Désactivé par défaut.</strong> Exposer sa propre carte des routes est une aide à
 * la reconnaissance pour qui cherche une faille : cet endpoint n'existe que si
 * {@code nexu.migration.exposer-routes=true}, ce que seuls les profils de développement posent.
 */
@RestController
@ConditionalOnProperty(name = "nexu.migration.exposer-routes", havingValue = "true")
public class ControleurRoutes {

    private final RequestMappingHandlerMapping mappings;

    public ControleurRoutes(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @GetMapping("/api/_migration/routes")
    public Map<String, Object> routes() {
        List<Map<String, String>> liste = TableDeRoutage.routes(mappings).stream().map(r -> Map.of("methode", r.methode(), "chemin", r.chemin())).toList();
        return Map.of("total", liste.size(), "routes", liste);
    }
}
