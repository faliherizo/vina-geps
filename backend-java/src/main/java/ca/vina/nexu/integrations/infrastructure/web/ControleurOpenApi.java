package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La documentation de l'API : la page Swagger UI, servie sans session (une navigation n'envoie pas
 * d'en-tête de jeton) avec sa propre politique de sécurité ; la spécification OpenAPI, sous session.
 */
@RestController
@RequestMapping("/api/integrations")
public class ControleurOpenApi {

    static final String CHEMIN_SPECIFICATION = "/api/integrations/openapi.json";
    private static final int OCTETS_NONCE = 16;

    private final GardeAcces garde;
    private final SpecificationOpenApi specification;
    private final Sequence sequence;

    public ControleurOpenApi(GardeAcces garde, SpecificationOpenApi specification, Sequence sequence) {
        this.garde = garde;
        this.specification = specification;
        this.sequence = sequence;
    }

    /** Un nonce par réponse : la page remplace la politique {@code default-src 'none'} du pare-feu par la sienne. */
    @GetMapping("/swagger")
    public ResponseEntity<String> swagger() {
        String nonce = sequence.jetonBase64Url(OCTETS_NONCE);
        return ResponseEntity.ok().header("Content-Type", "text/html; charset=utf-8").header("Content-Security-Policy", PageSwagger.csp(nonce))
                .body(PageSwagger.html(CHEMIN_SPECIFICATION, nonce));
    }

    @GetMapping("/openapi.json")
    public ResponseEntity<Object> openapi(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return ResponseEntity.ok().header("Content-Type", "application/json; charset=utf-8").body(Json.textePresente(specification.construire()));
    }
}
