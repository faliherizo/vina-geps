package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.infrastructure.persistance.DepotAbonnementsPoussee;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAppareils;

import ca.vina.nexu.partage.web.GardeAcces;
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

/**
 * {@code /api/notifications/push} — l'abonnement d'un navigateur aux notifications poussées
 * (devis 2.6), à contrat figé sur le Node. La **poussée** elle-même (chiffrement RFC 8291, envoi
 * au service du navigateur) part du Node avec les notifications qu'il journalise ; ici, le compte
 * abonne ses navigateurs et lit la clé publique de l'instance.
 */
@RestController
@RequestMapping("/api/notifications")
public class ControleurPoussee {

    private final DepotAbonnementsPoussee depot;
    private final DepotAppareils appareils;
    private final GardeAcces garde;

    public ControleurPoussee(DepotAbonnementsPoussee depot, DepotAppareils appareils, GardeAcces garde) {
        this.depot = depot;
        this.appareils = appareils;
        this.garde = garde;
    }

    // ── Appareils mobiles (notifications natives, refonte E ★) : le compte enregistre, liste, retire les siens. ──

    @GetMapping("/appareils")
    public ResponseEntity<Object> appareils(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(appareils.lister(v.tenantId(), compte(requete)));
    }

    @PostMapping("/appareils")
    public ResponseEntity<Object> enregistrerAppareil(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(201).body(appareils.enregistrer(v.tenantId(), compte(requete), corps));
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @DeleteMapping("/appareils/{id}")
    public ResponseEntity<Object> retirerAppareil(HttpServletRequest requete, @PathVariable("id") String id) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return appareils.retirer(v.tenantId(), compte(requete), id) ? ResponseEntity.noContent().build() : erreur(404, "appareil inconnu");
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static String compte(HttpServletRequest requete) {
        return GardeAcces.contexte(requete).userId();
    }

    @GetMapping("/push/cle-publique")
    public ResponseEntity<Object> clePublique(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(depot.etat());
    }

    @GetMapping("/push/abonnements")
    public ResponseEntity<Object> abonnements(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(depot.lister(v.tenantId(), compte(requete)));
    }

    @PostMapping("/push/abonnements")
    public ResponseEntity<Object> abonner(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Object navigateur = corps == null ? null : corps.get("navigateur");
            return ResponseEntity.status(201).body(depot.abonner(v.tenantId(), compte(requete), corps == null ? null : corps.get("abonnement"), navigateur == null ? null : String.valueOf(navigateur)));
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @DeleteMapping("/push/abonnements")
    public ResponseEntity<Object> desabonner(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps,
                                             @RequestParam(name = "endpoint", required = false) String endpointParam) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Object e = corps == null ? null : corps.get("endpoint");
        String endpoint = e != null ? String.valueOf(e) : endpointParam == null ? "" : endpointParam;
        if (endpoint.isEmpty()) {
            return erreur(400, "endpoint requis");
        }
        return ResponseEntity.ok(Map.of("retire", depot.desabonner(v.tenantId(), compte(requete), endpoint)));
    }
}
