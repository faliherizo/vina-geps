package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireCascades;
import ca.vina.nexu.alertes.application.commandes.GestionnairePolitiques;
import ca.vina.nexu.alertes.application.requetes.LectureCascades;
import ca.vina.nexu.alertes.domaine.Cascades;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_CONFIGURATION;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_ENVOI;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_SUIVI;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.repondre;
import static ca.vina.nexu.alertes.infrastructure.web.SaisiesAlertes.corps;

/**
 * {@code /api/alertes/politiques} et la cascade d'une alerte (ST-G04). L'administration tient les politiques ; un
 * expéditeur les choisit à l'envoi, lit la trace d'une cascade et la reprend à une étape.
 */
@RestController
@RequestMapping("/api/alertes")
public class ControleurCascades {

    private static final int OK = 200;
    private static final int CREE = 201;

    public record Cas(GestionnairePolitiques politiques, GestionnaireCascades cascades, LectureCascades lecture) { }

    private final GardeAlertes garde;
    private final Cas cas;

    public ControleurCascades(GardeAlertes garde, Cas cas) {
        this.garde = garde;
        this.cas = cas;
    }

    @GetMapping("/politiques")
    public ResponseEntity<Object> politiques(HttpServletRequest r) {
        boolean toutes = garde.peut(r, CAPACITE_CONFIGURATION);
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().politiques(a.tenantId(), toutes).stream().map(VuesCascades::politique).toList());
    }

    @PostMapping("/politiques")
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), CREE, a -> VuesCascades.politique(cas.politiques().creer(a, SaisiesCascades.contenu(corps(c)))));
    }

    @PutMapping("/politiques/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesCascades.politique(cas.politiques().modifier(a, id, SaisiesCascades.contenu(corps(c)))));
    }

    @PatchMapping("/politiques/{id}")
    public ResponseEntity<Object> activer(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesCascades.politique(cas.politiques().activer(a, id, SaisiesAlertes.booleen(corps(c), "actif"))));
    }

    @GetMapping("/envoyees/{id}/cascade")
    public ResponseEntity<Object> cascade(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.capacite(r, CAPACITE_SUIVI), OK, a -> VuesCascades.suivi(cas.lecture().suivi(a.tenantId(), id)));
    }

    /** La reprise manuelle (critère 17) : {@code etape} compte à partir de 1. */
    @PostMapping("/envoyees/{id}/cascade/reprendre")
    public ResponseEntity<Object> reprendre(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> {
            Cascades.Cascade reprise = cas.cascades().reprendre(a, id, SaisiesCascades.entier(corps(c), "etape"));
            return VuesCascades.suivi(new LectureCascades.Suivi(java.util.Optional.of(reprise), List.of(), List.of()));
        });
    }
}
