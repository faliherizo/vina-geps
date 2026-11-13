package ca.vina.nexu.ordonnanceur.infrastructure.web;

import ca.vina.nexu.ordonnanceur.application.commandes.DeclencherTic;
import ca.vina.nexu.ordonnanceur.application.requetes.LectureTaches;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/ordonnanceur} — la file des tâches différées (ADR 0014). Une file qu'on ne peut
 * lire qu'une fois n'a de sens que si quelqu'un peut la voir : lecture réservée à l'administration,
 * et un tic à la demande, à l'instant présent.
 */
@RestController
@RequestMapping("/api/ordonnanceur")
public class ControleurOrdonnanceur {

    private final LectureTaches lecture;
    private final DeclencherTic tic;
    private final GardeAcces garde;

    public ControleurOrdonnanceur(LectureTaches lecture, DeclencherTic tic, GardeAcces garde) {
        this.lecture = lecture;
        this.tic = tic;
        this.garde = garde;
    }

    @GetMapping("/taches")
    public ResponseEntity<Object> taches(HttpServletRequest requete, @RequestParam(required = false) String statut, @RequestParam(required = false) String type) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "superadmin"), t -> lecture.lister(t, statut, type));
    }

    @PostMapping("/tic")
    public ResponseEntity<Object> tic(HttpServletRequest requete) {
        return Bord.repondre(garde.exigerRole(requete, "admin", "superadmin"), t -> tic.executer());
    }
}
