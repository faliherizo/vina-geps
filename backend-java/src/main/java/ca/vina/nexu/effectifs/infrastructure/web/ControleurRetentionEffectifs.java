package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireRetentionEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureRetentionEffectifs;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/retention} — la politique de rétention des effectifs (AO 26-015-DO, lot R11), jumeau de
 * {@code backend/src/effectifs/retention/routes.ts} : gardée par le module {@code effectifs} et réservée à
 * l'administration ({@code effectifs.configure}). La purge se fait dans la transaction de l'appel : une erreur à
 * mi-chemin n'en laisse rien.
 */
@RestController
@RequestMapping("/api/effectifs/retention")
@Transactional
public class ControleurRetentionEffectifs {

    private static final String MODULE = "effectifs";
    private static final String POLITIQUE = "politique";
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final LectureRetentionEffectifs lire;
    private final GestionnaireRetentionEffectifs agir;

    public ControleurRetentionEffectifs(GardeEffectifs garde, LectureRetentionEffectifs lire, GestionnaireRetentionEffectifs agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private GardeAcces.Verdict configurer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER);
    }

    @GetMapping
    public ResponseEntity<Object> apercu(HttpServletRequest r) {
        return garde.repondre(r, configurer(r), OK, lire::apercu);
    }

    @PutMapping
    public ResponseEntity<Object> definir(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, configurer(r), OK, ctx -> agir.definir(ctx, c == null ? null : c.get(POLITIQUE)));
    }

    @PostMapping("/appliquer")
    public ResponseEntity<Object> appliquer(HttpServletRequest r) {
        return garde.repondre(r, configurer(r), OK, agir::appliquer);
    }
}
