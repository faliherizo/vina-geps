package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireBrouillonsMessages;
import ca.vina.nexu.communication.application.requetes.LectureBrouillonsMessages;
import ca.vina.nexu.communication.domaine.BrouillonsMessages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.node.NullNode;

import java.util.Map;
import java.util.Optional;

/**
 * Brouillons de message (BF-038). Aucune route ne prend d'identité : un brouillon est celui de
 * l'appelant, et le seul moyen de n'en jamais servir un autre est de ne pas offrir de le demander.
 * L'absence se sert comme le Node : le JSON {@code null}, pas un corps vide.
 */
@RestController
@RequestMapping("/api/communication")
public class ControleurBrouillonsMessages {

    private static final int OK = 200;

    private final GardeCommunication garde;
    private final GestionnaireBrouillonsMessages gestionnaire;
    private final LectureBrouillonsMessages lecture;

    public ControleurBrouillonsMessages(GardeCommunication garde, GestionnaireBrouillonsMessages gestionnaire, LectureBrouillonsMessages lecture) {
        this.garde = garde;
        this.gestionnaire = gestionnaire;
        this.lecture = lecture;
    }

    static Object servi(Optional<BrouillonsMessages.Brouillon> b) {
        return b.<Object>map(BrouillonsMessages.Brouillon::enCarte).orElse(NullNode.getInstance());
    }

    @GetMapping("/brouillons")
    public ResponseEntity<Object> miens(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK,
                a -> lecture.miens(a).stream().map(BrouillonsMessages.Brouillon::enCarte).toList());
    }

    @GetMapping("/conversations/{id}/brouillon")
    public ResponseEntity<Object> lire(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> servi(lecture.lire(a, id)));
    }

    @PutMapping("/conversations/{id}/brouillon")
    public ResponseEntity<Object> enregistrer(HttpServletRequest requete, @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> corps) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> servi(gestionnaire.enregistrer(a, id, CorpsJson.chaine(corps, "corps"))));
    }

    @DeleteMapping("/conversations/{id}/brouillon")
    public ResponseEntity<Object> supprimer(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> Map.of("supprime", gestionnaire.supprimer(a, id)));
    }
}
