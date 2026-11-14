package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsGroupe;
import ca.vina.nexu.communication.application.requetes.LectureAppelsGroupe;
import ca.vina.nexu.communication.domaine.AppelsGroupe;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'appel de groupe sur {@code /api/communication} : on l'appelle depuis un fil, on le rejoint, on le quitte, on
 * l'ignore. Même garde que la messagerie ; la portée se juge dans l'application.
 */
@RestController
@RequestMapping("/api/communication")
public class ControleurAppelsGroupe {

    private static final int CREE = 201;
    private static final int OK = 200;

    private final GardeCommunication garde;
    private final GestionnaireAppelsGroupe gestionnaire;
    private final LectureAppelsGroupe lecture;

    public ControleurAppelsGroupe(GardeCommunication garde, GestionnaireAppelsGroupe gestionnaire, LectureAppelsGroupe lecture) {
        this.garde = garde;
        this.gestionnaire = gestionnaire;
        this.lecture = lecture;
    }

    @PostMapping("/conversations/{id}/appel")
    public ResponseEntity<Object> lancer(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), CREE, a -> gestionnaire.lancer(a, id));
    }

    @GetMapping("/appels-groupe/en-cours")
    public ResponseEntity<Object> enCours(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, lecture::enCours);
    }

    private ResponseEntity<Object> geste(HttpServletRequest requete, String id, AppelsGroupe.Geste geste) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> gestionnaire.poser(a, id, geste));
    }

    @PostMapping("/appels-groupe/{id}/rejoindre")
    public ResponseEntity<Object> rejoindre(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsGroupe.Geste.REJOINDRE);
    }

    @PostMapping("/appels-groupe/{id}/quitter")
    public ResponseEntity<Object> quitter(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsGroupe.Geste.QUITTER);
    }

    @PostMapping("/appels-groupe/{id}/ignorer")
    public ResponseEntity<Object> ignorer(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsGroupe.Geste.IGNORER);
    }
}
