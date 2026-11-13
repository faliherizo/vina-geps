package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireReglagesAcheminement;
import ca.vina.nexu.communication.application.requetes.LectureReglagesAcheminement;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * L'acheminement des avis réglé par unité : la lecture est ouverte à l'établissement (chacun peut
 * savoir par où on le joindra), le réglage demande la gestion des effectifs.
 */
@RestController
@RequestMapping("/api/communication/acheminement/unites")
public class ControleurAcheminementUnites {

    private static final int OK = 200;

    private final GardeCommunication garde;
    private final LectureReglagesAcheminement lecture;
    private final GestionnaireReglagesAcheminement gestionnaire;

    public ControleurAcheminementUnites(GardeCommunication garde, LectureReglagesAcheminement lecture, GestionnaireReglagesAcheminement gestionnaire) {
        this.garde = garde;
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
    }

    /** Les étapes du corps : une étape mal formée garde ses trous, la conversion la refuse nommément. */
    static List<GestionnaireReglagesAcheminement.EtapeDemandee> etapes(Map<String, Object> corps) {
        List<?> liste = CorpsJson.liste(corps, "etapes");
        return liste == null ? List.of() : liste.stream().map(o -> o instanceof Map<?, ?> m ? m : Map.of())
                .map(m -> new GestionnaireReglagesAcheminement.EtapeDemandee(m.get("canal") instanceof String c ? c : null, m.get("mode") instanceof String s ? s : null))
                .toList();
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, lecture::lister);
    }

    @PutMapping("/{uniteId}")
    public ResponseEntity<Object> regler(HttpServletRequest requete, @PathVariable("uniteId") String uniteId, @RequestBody(required = false) Map<String, Object> corps) {
        return GardeCommunication.repondre(requete, garde.gestion(requete), OK, a -> gestionnaire.regler(a, uniteId, etapes(corps)).enCarte());
    }

    @DeleteMapping("/{uniteId}")
    public ResponseEntity<Object> retablir(HttpServletRequest requete, @PathVariable("uniteId") String uniteId) {
        return GardeCommunication.repondre(requete, garde.gestion(requete), OK, a -> {
            gestionnaire.retablirDefaut(a, uniteId);
            return Map.of("parDefaut", true);
        });
    }
}
