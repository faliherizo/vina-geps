package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.application.commandes.GestionnaireEquipe;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code /api/personnel/equipe} — « Mon équipe » : chacun voit qui relève de lui ; qui peut corriger une fiche
 * ({@code employee.update}) réorganise sa propre équipe, et elle seule. Le cloisonnement est dans le domaine
 * ({@code Hierarchie.verifierDansEquipe}), pas dans l'écran.
 */
@RestController
@RequestMapping("/api/personnel/equipe")
public class ControleurEquipe {

    private final GardeAcces garde;
    private final GestionnaireEquipe equipe;

    public ControleurEquipe(GardeAcces garde, GestionnaireEquipe equipe) {
        this.garde = garde;
        this.equipe = equipe;
    }

    @GetMapping
    public ResponseEntity<Object> monEquipe(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(equipe.equipe(v.tenantId(), GardeAcces.contexte(requete).userId()));
    }

    @PutMapping("/{id}/superieur")
    public ResponseEntity<Object> designer(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerCapacite(requete, ControleurPersonnel.MODIFIER);
        if (v.refuse()) {
            return v.refus();
        }
        Object brut = corps == null ? null : corps.get("superieurId");
        if (brut != null && !(brut instanceof String)) {
            return refus(400, "« superieurId » : l'identifiant d'une fiche, ou null pour détacher");
        }
        ContexteRequete ctx = GardeAcces.contexte(requete);
        return tenter(() -> ResponseEntity.ok(equipe.designer(v.tenantId(), ctx.userId(), id, (String) brut)));
    }

    private static ResponseEntity<Object> tenter(Supplier<ResponseEntity<Object>> geste) {
        try {
            return geste.get();
        } catch (FichesPersonnes.FicheInvalideException e) {
            return refus(400, e.getMessage());
        } catch (HorsPerimetreException e) {
            return refus(403, e.getMessage());
        } catch (IntrouvableException e) {
            return refus(404, e.getMessage());
        }
    }

    private static ResponseEntity<Object> refus(int code, String motif) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(motif)));
    }
}
