package ca.vina.nexu.demonstration.infrastructure.web;

import ca.vina.nexu.demonstration.infrastructure.persistance.Demonstrations;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.ModulesActives;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Brancher, débrancher, essayer un tiers de démonstration — par l'administrateur de
 * l'établissement, module {@code integrations} activé. Les routes répondent sous
 * {@code /api/integrations/demonstration}, à côté des cartes d'état qui les décrivent.
 */
@RestController
@RequestMapping("/api/integrations")
public class ControleurDemonstrations {

    private final Demonstrations demonstrations;
    private final ModulesActives modules;
    private final GardeAcces garde;

    public ControleurDemonstrations(Demonstrations demonstrations, ModulesActives modules, GardeAcces garde) {
        this.demonstrations = demonstrations;
        this.modules = modules;
        this.garde = garde;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    /** Session + établissement + module {@code integrations} + rôle d'administration, dans l'ordre du Node. */
    private GardeAcces.Verdict exigerAdministration(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        if (!modules.estActive(v.tenantId(), "integrations")) {
            return new GardeAcces.Verdict(null, erreur(403, "module « integrations » non activé pour cet organisme"));
        }
        return garde.exigerRole(requete, "admin", "superadmin");
    }

    private ResponseEntity<Object> repondre(HttpServletRequest requete, Function<String, Object> geste) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.ok(geste.apply(v.tenantId()));
        } catch (RuntimeException e) {
            return erreur(e.getMessage() != null && e.getMessage().toLowerCase().contains("introuvable") ? 404 : 400, e.getMessage());
        }
    }

    private static String acteur(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return c == null ? "inconnu" : c.userId();
    }

    @PostMapping("/demonstration/{code}/brancher")
    public ResponseEntity<Object> brancher(HttpServletRequest requete, @PathVariable("code") String code, @RequestBody(required = false) Map<String, Object> corps) {
        String urlApplication = corps != null && corps.get("urlApplication") instanceof String u ? u : null;
        return repondre(requete, tenantId -> {
            demonstrations.brancher(code, tenantId, acteur(requete), urlApplication);
            return Map.of("code", code, "branchee", true);
        });
    }

    @PostMapping("/demonstration/{code}/debrancher")
    public ResponseEntity<Object> debrancher(HttpServletRequest requete, @PathVariable("code") String code) {
        return repondre(requete, tenantId -> {
            demonstrations.debrancher(code, tenantId);
            return Map.of("code", code, "branchee", false);
        });
    }

    @PostMapping("/demonstration/{code}/essai")
    public ResponseEntity<Object> essai(HttpServletRequest requete, @PathVariable("code") String code) {
        return repondre(requete, tenantId -> demonstrations.essayer(code, tenantId));
    }
}
