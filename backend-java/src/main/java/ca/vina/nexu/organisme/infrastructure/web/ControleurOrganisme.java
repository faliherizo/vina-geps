package ca.vina.nexu.organisme.infrastructure.web;

import ca.vina.nexu.organisme.application.commandes.GestionnaireCharteOrganisme;
import ca.vina.nexu.organisme.application.commandes.GestionnairePaysAutorises;
import ca.vina.nexu.organisme.application.commandes.GestionnaireReglesAlerte;
import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/organisme} — les <strong>options de l'organisme</strong> (AO 26-015-DO, lot R11), jumeau de
 * {@code backend/src/organisme/routes.ts} : mêmes chemins, mêmes codes, mêmes messages. La charte se lit par
 * tout membre de l'organisme (l'interface l'applique à tous) ; elle se pose, comme les pays autorisés et les
 * règles d'alerte, par l'administration de l'organisme.
 */
@RestController
@RequestMapping("/api/organisme")
@Transactional
public class ControleurOrganisme {

    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardeOrganisme garde;
    private final LectureOptionsOrganisme lecture;
    private final GestionnaireCharteOrganisme charte;
    private final GestionnairePaysAutorises pays;
    private final GestionnaireReglesAlerte regles;

    public ControleurOrganisme(GardeOrganisme garde, LectureOptionsOrganisme lecture, GestionnaireCharteOrganisme charte,
                               GestionnairePaysAutorises pays, GestionnaireReglesAlerte regles) {
        this.garde = garde;
        this.lecture = lecture;
        this.charte = charte;
        this.pays = pays;
        this.regles = regles;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    @GetMapping("/charte")
    public ResponseEntity<Object> lireCharte(HttpServletRequest r) {
        return garde.repondre(garde.membre(r), OK, t -> lecture.charte(t).orElse(null));
    }

    @PutMapping("/charte")
    public ResponseEntity<Object> poserCharte(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(garde.administration(r), OK, t -> charte.enregistrer(t, corps(c), garde.modification(r)));
    }

    @DeleteMapping("/charte")
    public ResponseEntity<Object> retirerCharte(HttpServletRequest r) {
        return garde.repondre(garde.administration(r), OK, charte::retirer);
    }

    @GetMapping("/pays")
    public ResponseEntity<Object> lirePays(HttpServletRequest r) {
        return garde.repondre(garde.administration(r), OK, lecture::pays);
    }

    @PutMapping("/pays")
    public ResponseEntity<Object> poserPays(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(garde.administration(r), OK, t -> pays.enregistrer(t, corps(c).get("pays"), garde.modification(r)));
    }

    @GetMapping("/alertes")
    public ResponseEntity<Object> regles(HttpServletRequest r) {
        return garde.repondre(garde.administration(r), OK, lecture::regles);
    }

    @PostMapping("/alertes")
    public ResponseEntity<Object> creerRegle(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(garde.administration(r), CREE, t -> regles.creer(t, corps(c), garde.modification(r)));
    }

    @DeleteMapping("/alertes/{id}")
    public ResponseEntity<Object> retirerRegle(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(garde.administration(r), OK, t -> regles.retirer(t, id));
    }

    @GetMapping("/alertes/journal")
    public ResponseEntity<Object> journal(HttpServletRequest r) {
        return garde.repondre(garde.administration(r), OK, lecture::journal);
    }
}
