package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireNotes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_ENVOI;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.repondre;
import static ca.vina.nexu.alertes.infrastructure.web.SaisiesAlertes.corps;

/**
 * {@code /api/alertes/notes} (ST-G12) — la note sur la fiche d'un destinataire, lue et tenue par les expéditeurs
 * autorisés ({@code alertes.envoyer}). Rien de son texte n'est journalisé.
 */
@RestController
@RequestMapping("/api/alertes/notes")
public class ControleurNotes {

    private static final int OK = 200;
    private static final int PERSONNES_MAX = 200;

    private final GardeAlertes garde;
    private final GestionnaireNotes notes;

    public ControleurNotes(GardeAlertes garde, GestionnaireNotes notes) {
        this.garde = garde;
        this.notes = notes;
    }

    /** {@code personnes} : les noms de connexion, séparés par des virgules (au plus {@value #PERSONNES_MAX}). */
    @GetMapping
    public ResponseEntity<Object> lire(HttpServletRequest r, @RequestParam(required = false) String personnes) {
        List<String> ids = personnes == null ? List.of() : Arrays.stream(personnes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct()
                .limit(PERSONNES_MAX).toList();
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> notes.lire(a, ids));
    }

    @PutMapping("/{personId}")
    public ResponseEntity<Object> ecrire(HttpServletRequest r, @PathVariable String personId, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> notes.ecrire(a, personId, SaisiesAlertes.texte(corps(c), "texte")));
    }

    @DeleteMapping("/{personId}")
    public ResponseEntity<Object> effacer(HttpServletRequest r, @PathVariable String personId) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> {
            notes.effacer(a, personId);
            return Map.of("effacee", true);
        });
    }
}
