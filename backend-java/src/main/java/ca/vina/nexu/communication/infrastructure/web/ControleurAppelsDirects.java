package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsDirects;
import ca.vina.nexu.communication.application.requetes.LectureAppelsDirects;
import ca.vina.nexu.communication.domaine.AppelsDirects;
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

/**
 * Appels entre collègues (sonnerie, décrochage, raccrochage) sur {@code /api/horaires} : ouverts à tout
 * compte de l'établissement, comme la messagerie. La session seulement ; le flux audio se branche dessus.
 */
@RestController
@RequestMapping("/api/communication")
public class ControleurAppelsDirects {

    private static final int CREE = 201;
    private static final int OK = 200;

    private final GardeCommunication garde;
    private final GestionnaireAppelsDirects gestionnaire;
    private final LectureAppelsDirects lecture;

    public ControleurAppelsDirects(GardeCommunication garde, GestionnaireAppelsDirects gestionnaire, LectureAppelsDirects lecture) {
        this.garde = garde;
        this.gestionnaire = gestionnaire;
        this.lecture = lecture;
    }

    @PostMapping("/appels")
    public ResponseEntity<Object> lancer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        // `conversationId ? String(conversationId) : null` : une valeur fausse au sens JavaScript ne rattache à aucun fil.
        String conversationId = Boolean.TRUE.equals(CorpsJson.booleen(corps, "conversationId")) ? CorpsJson.texte(corps, "conversationId") : null;
        String appele = CorpsJson.texte(corps, "appeleId");
        return GardeCommunication.repondre(requete, garde.socle(requete), CREE, a -> gestionnaire.lancer(a, appele, conversationId));
    }

    /** Ce qui sonne ou se parle pour moi : l'écran le demande en boucle. */
    @GetMapping("/appels/en-cours")
    public ResponseEntity<Object> enCours(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, lecture::enCours);
    }

    @GetMapping("/appels")
    public ResponseEntity<Object> historique(HttpServletRequest requete, @RequestParam(name = "limite", required = false) String limite) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> lecture.historique(a, limite(limite)));
    }

    /** {@code Number(limite ?? 50)} : une chaîne vide vaut zéro ; une valeur illisible retombe sur le défaut. */
    static int limite(String brut) {
        if (brut == null) {
            return LectureAppelsDirects.LIMITE_PAR_DEFAUT;
        }
        String t = brut.trim();
        try {
            return t.isEmpty() ? 0 : (int) Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return LectureAppelsDirects.LIMITE_PAR_DEFAUT;
        }
    }

    private ResponseEntity<Object> geste(HttpServletRequest requete, String id, AppelsDirects.Geste geste) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> gestionnaire.poser(a, id, geste));
    }

    @PostMapping("/appels/{id}/decrocher")
    public ResponseEntity<Object> decrocher(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsDirects.Geste.DECROCHER);
    }

    @PostMapping("/appels/{id}/refuser")
    public ResponseEntity<Object> refuser(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsDirects.Geste.REFUSER);
    }

    @PostMapping("/appels/{id}/raccrocher")
    public ResponseEntity<Object> raccrocher(HttpServletRequest requete, @PathVariable("id") String id) {
        return geste(requete, id, AppelsDirects.Geste.RACCROCHER);
    }
}
