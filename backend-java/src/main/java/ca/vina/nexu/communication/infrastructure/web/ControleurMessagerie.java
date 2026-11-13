package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.Messagerie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Messagerie sécurisée (L6) sur {@code /api/communication} : ouverte à tout compte de
 * l'établissement dont le module {@code effectifs_communication} est actif — c'est un outil de pair à
 * pair, et le contrôle se joue sur la portée de lecture, dans l'application. Une garde de rôle ici
 * interdirait à un clinicien d'écrire à son collègue ; un module coupé, lui, coupe aussi l'API, pas
 * seulement le menu (refus par défaut, ADR 0063). Le contrôleur ne fait que traduire HTTP.
 */
@RestController
@RequestMapping("/api/communication")
public class ControleurMessagerie {

    private static final int OK = 200;
    private static final int CREE = 201;

    private final GardeCommunication garde;
    private final LectureMessagerie lecture;
    private final GestionnaireMessagerie gestionnaire;

    public ControleurMessagerie(GardeCommunication garde, LectureMessagerie lecture, GestionnaireMessagerie gestionnaire) {
        this.garde = garde;
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
    }

    private ResponseEntity<Object> repondre(HttpServletRequest requete, int code, Function<Appelant, Object> geste) {
        return GardeCommunication.repondre(requete, garde.socle(requete), code, geste);
    }

    private static List<String> textes(Map<String, Object> corps, String cle) {
        return CorpsJson.valeur(corps, cle) instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }

    private static String texte(Map<String, Object> corps, String cle) {
        Object v = CorpsJson.valeur(corps, cle);
        return v == null ? null : String.valueOf(v);
    }

    static List<GestionnaireMessagerie.PieceDemandee> pieces(Map<String, Object> corps) {
        if (!(CorpsJson.valeur(corps, "piecesJointes") instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().map(o -> o instanceof Map<?, ?> m ? m : Map.of())
                .map(p -> new GestionnaireMessagerie.PieceDemandee(p.get("nom") == null ? null : String.valueOf(p.get("nom")),
                        p.get("typeMime") == null ? null : String.valueOf(p.get("typeMime")), p.get("taille")))
                .toList();
    }

    @GetMapping("/conversations")
    public ResponseEntity<Object> conversations(HttpServletRequest requete) {
        return repondre(requete, OK, a -> lecture.conversations(a).stream().map(Messagerie.Conversation::enCarte).toList());
    }

    /**
     * **Ce qui m'attend** — le compte que la barre du haut demande, sans charger les fils. Le chemin
     * littéral l'emporte sur le gabarit {@code /conversations/{id}} en Spring.
     */
    @GetMapping("/conversations/non-lus")
    public ResponseEntity<Object> conversationsNonLues(HttpServletRequest requete) {
        return repondre(requete, OK, lecture::nonLus);
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<Object> conversation(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> lecture.conversation(a.tenantId(), id).enCarte());
    }

    @PostMapping("/conversations")
    public ResponseEntity<Object> creer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        var demande = new GestionnaireMessagerie.DemandeConversation(texte(corps, "sujet"), texte(corps, "type"), texte(corps, "groupeId"), textes(corps, "participants"));
        return repondre(requete, CREE, a -> gestionnaire.creer(a, demande).enCarte());
    }

    @PostMapping("/conversations/{id}/participants")
    public ResponseEntity<Object> ajouterParticipant(HttpServletRequest requete, @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> corps) {
        String personId = texte(corps, "personId") == null ? "" : texte(corps, "personId");
        return repondre(requete, CREE, a -> gestionnaire.ajouterParticipant(a, id, personId, texte(corps, "role")).enCarte());
    }

    @DeleteMapping("/conversations/{id}/participants/{personId}")
    public ResponseEntity<Object> retirerParticipant(HttpServletRequest requete, @PathVariable("id") String id, @PathVariable("personId") String personId) {
        return repondre(requete, OK, a -> gestionnaire.retirerParticipant(a, id, personId).enCarte());
    }

    @PostMapping("/conversations/{id}/archiver")
    public ResponseEntity<Object> archiver(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> gestionnaire.archiver(a, id).enCarte());
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<Object> messages(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> lecture.messages(a, id).stream().map(Messagerie.Message::enCarte).toList());
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<Object> envoyer(HttpServletRequest requete, @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> corps) {
        return repondre(requete, CREE, a -> gestionnaire.envoyer(a, id, texte(corps, "corps"), pieces(corps)).enCarte());
    }

    @PostMapping("/conversations/{id}/lu")
    public ResponseEntity<Object> toutLu(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> Map.of("marques", gestionnaire.marquerToutLu(a, id)));
    }

    @PostMapping("/messages/{id}/lu")
    public ResponseEntity<Object> lu(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> {
            gestionnaire.marquerLu(a, id);
            return Map.of("lu", true);
        });
    }

    @GetMapping("/messages/{id}/lectures")
    public ResponseEntity<Object> lectures(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> lecture.lecteurs(a, id));
    }

    @GetMapping("/messages/recherche")
    public ResponseEntity<Object> recherche(HttpServletRequest requete, @RequestParam(name = "q", required = false) String q) {
        return repondre(requete, OK, a -> lecture.chercher(a, q));
    }

    @GetMapping("/pieces-jointes/{id}")
    public ResponseEntity<Object> pieceJointe(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> lecture.pieceJointe(a, id));
    }

    @GetMapping("/conversations/{id}/acheminement")
    public ResponseEntity<Object> acheminement(HttpServletRequest requete, @PathVariable("id") String id) {
        return repondre(requete, OK, a -> lecture.acheminement(a, id));
    }
}
