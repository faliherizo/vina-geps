package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireListesDistribution;
import ca.vina.nexu.communication.application.requetes.LectureListesDistribution;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Listes de distribution (BF-033) : nommées, partagées ou privées, recalculées à l'envoi. Ouvertes à tout compte de l'établissement. */
@RestController
@RequestMapping("/api/communication")
public class ControleurListesDistribution {

    private static final int CREE = 201;
    private static final int OK = 200;

    private final GardeCommunication garde;
    private final GestionnaireListesDistribution gestionnaire;
    private final LectureListesDistribution lecture;

    public ControleurListesDistribution(GardeCommunication garde, GestionnaireListesDistribution gestionnaire, LectureListesDistribution lecture) {
        this.garde = garde;
        this.gestionnaire = gestionnaire;
        this.lecture = lecture;
    }

    /** Le corps traduit une fois, ici : un nom qui n'est pas une chaîne vaut « vide », un membre mal formé garde ses trous pour la validation. */
    static GestionnaireListesDistribution.Demande demande(Map<String, Object> corps) {
        Object nom = CorpsJson.valeur(corps, "nom");
        List<?> membres = CorpsJson.liste(corps, "membres");
        return new GestionnaireListesDistribution.Demande(nom == null ? null : CorpsJson.chaine(corps, "nom") == null ? "" : (String) nom,
                CorpsJson.booleen(corps, "partagee"), membres == null ? null : membres.stream().map(ControleurListesDistribution::membre).toList());
    }

    private static ListesDistribution.MembreDemande membre(Object o) {
        @SuppressWarnings("unchecked")
        Map<String, Object> m = o instanceof Map<?, ?> carte ? (Map<String, Object>) carte : Map.of();
        return new ListesDistribution.MembreDemande(CorpsJson.chaine(m, "nature"), CorpsJson.chaine(m, "cible"));
    }

    @GetMapping("/listes-distribution")
    public ResponseEntity<Object> lister(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, lecture::lister);
    }

    @PostMapping("/listes-distribution")
    public ResponseEntity<Object> creer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return GardeCommunication.repondre(requete, garde.socle(requete), CREE, a -> gestionnaire.creer(a, demande(corps)).enCarte());
    }

    @GetMapping("/listes-distribution/{id}")
    public ResponseEntity<Object> lire(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> lecture.lire(a, id).enCarte());
    }

    @PutMapping("/listes-distribution/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest requete, @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> corps) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> gestionnaire.modifier(a, id, demande(corps)).enCarte());
    }

    @DeleteMapping("/listes-distribution/{id}")
    public ResponseEntity<Object> supprimer(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> gestionnaire.supprimer(a, id));
    }

    /** Les destinataires résolus maintenant : ce que l'écran montre avant de diffuser. */
    @GetMapping("/listes-distribution/{id}/destinataires")
    public ResponseEntity<Object> destinataires(HttpServletRequest requete, @PathVariable("id") String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> lecture.destinataires(a, id));
    }

    @PostMapping("/listes-distribution/{id}/conversation")
    public ResponseEntity<Object> conversation(HttpServletRequest requete, @PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> corps) {
        Object sujet = CorpsJson.valeur(corps, "sujet");
        return GardeCommunication.repondre(requete, garde.socle(requete), CREE,
                a -> gestionnaire.ouvrirConversation(a, id, sujet == null ? null : String.valueOf(sujet)).enCarte());
    }
}
