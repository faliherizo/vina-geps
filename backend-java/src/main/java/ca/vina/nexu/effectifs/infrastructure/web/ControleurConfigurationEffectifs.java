package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.PromotionConfiguration;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * La promotion de la configuration des effectifs d'un environnement à l'autre (P-TN-1) : le paquet
 * s'exporte en pièce jointe, puis s'importe ailleurs — simulé par défaut, appliqué sur demande.
 * Transactionnel : un import qui échoue en cours de route n'en laisse rien.
 */
@RestController
@RequestMapping("/api/effectifs/configuration")
@Transactional
public class ControleurConfigurationEffectifs {

    private static final String MODULE = "effectifs";

    private final GardeEffectifs garde;
    private final PromotionConfiguration promotion;

    public ControleurConfigurationEffectifs(GardeEffectifs garde, PromotionConfiguration promotion) {
        this.garde = garde;
        this.promotion = promotion;
    }

    private GardeAcces.Verdict configurer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER);
    }

    @GetMapping("/paquet")
    public ResponseEntity<Object> exporter(HttpServletRequest r) {
        GardeAcces.Verdict v = configurer(r);
        ResponseEntity<Object> reponse = garde.repondre(r, v, 200, promotion::exporter);
        if (v.refuse()) {
            return reponse;
        }
        // Une pièce jointe : le paquet se range comme un fichier, et l'alerte « export téléchargé » s'applique.
        String nom = "configuration-effectifs-" + garde.contexte(r, v).aujourdhui() + ".json";
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nom).build().toString())
                .body(reponse.getBody());
    }

    @PostMapping("/paquet")
    public ResponseEntity<Object> importer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> paquet,
                                           @RequestParam(defaultValue = "false") boolean appliquer, @RequestParam(required = false) String depuis) {
        return garde.repondre(r, configurer(r), 200, ctx -> promotion.importer(ctx, paquet, appliquer, depuis));
    }
}
