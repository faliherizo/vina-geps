package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.commandes.GestionnaireValidationTemps;
import ca.vina.nexu.paie.application.requetes.LectureVariablesPaie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static ca.vina.nexu.paie.infrastructure.web.SaisiesPaie.corps;

/**
 * {@code /api/paie/temps} — la validation des temps par la paie (ADR 0084) : lire les feuilles d'une période et leurs
 * anomalies, valider, valider en lot ce qui n'a aucune anomalie, retourner avec un motif. Exige {@code paie.gerer}.
 */
@RestController
@RequestMapping("/api/paie/temps")
public class ControleurValidationTemps {

    private static final int OK = 200;

    private final GardePaie garde;
    private final LectureVariablesPaie lecture;
    private final GestionnaireValidationTemps validation;

    public ControleurValidationTemps(GardePaie garde, LectureVariablesPaie lecture, GestionnaireValidationTemps validation) {
        this.garde = garde;
        this.lecture = lecture;
        this.validation = validation;
    }

    @GetMapping
    public ResponseEntity<Object> controle(HttpServletRequest r, @RequestParam(required = false) String debut, @RequestParam(required = false) String fin) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.controle(lecture.controle(t.tenantId(), SaisiesPaie.periodeTemps(debut, fin))));
    }

    @PostMapping("/{feuilleId}/valider")
    public ResponseEntity<Object> valider(HttpServletRequest r, @PathVariable String feuilleId) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.validation(validation.valider(t, feuilleId)));
    }

    @PostMapping("/valider-sans-anomalie")
    public ResponseEntity<Object> validerSansAnomalie(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.gestion(r), OK, t -> {
            Map<String, Object> corps = corps(c);
            var periode = SaisiesPaie.periodeTemps(SaisiesPaie.texte(corps, "debut"), SaisiesPaie.texte(corps, "fin"));
            return Map.of("validees", validation.validerSansAnomalie(t, periode).stream().map(VuesPaie::validation).toList());
        });
    }

    @PostMapping("/{feuilleId}/retourner")
    public ResponseEntity<Object> retourner(HttpServletRequest r, @PathVariable String feuilleId, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.validation(validation.retourner(t, feuilleId, SaisiesPaie.texte(corps(c), "motif"))));
    }
}
