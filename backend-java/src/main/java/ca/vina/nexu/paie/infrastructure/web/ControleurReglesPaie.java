package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.commandes.GestionnaireReglesPaie;
import ca.vina.nexu.paie.application.requetes.LectureVariablesPaie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static ca.vina.nexu.paie.infrastructure.web.SaisiesPaie.corps;

/**
 * {@code /api/paie/regles} — les règles de paie variable de l'organisme : échelle salariale, primes et forfaits
 * (ADR 0084). Les tenir exige {@code paie.configurer} : le comptable paie selon des règles qu'il ne modifie pas.
 */
@RestController
@RequestMapping("/api/paie/regles")
public class ControleurReglesPaie {

    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardePaie garde;
    private final LectureVariablesPaie lecture;
    private final GestionnaireReglesPaie regles;

    public ControleurReglesPaie(GardePaie garde, LectureVariablesPaie lecture, GestionnaireReglesPaie regles) {
        this.garde = garde;
        this.lecture = lecture;
        this.regles = regles;
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r) {
        return garde.repondre(r, garde.configuration(r), OK, t -> lecture.regles(t.tenantId()).stream().map(VuesPaie::regle).toList());
    }

    @PostMapping
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.configuration(r), CREE, t -> VuesPaie.regle(regles.creer(t, SaisiesPaie.regle(corps(c)))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.configuration(r), OK, t -> VuesPaie.regle(regles.modifier(t, id, SaisiesPaie.regle(corps(c)))));
    }
}
