package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.domaine.Avis;
import ca.vina.nexu.notifications.domaine.Evenements;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotAnnonces;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotEquipe;
import ca.vina.nexu.notifications.infrastructure.persistance.NotifierEvenement;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.Map;

/**
 * {@code /api/notifications/evenements} — le catalogue des avis (ADR 0029), lisible par tout compte : c'est la
 * matrice de « Mon compte » ; {@code /api/notifications/envoyer} — l'encadrement avise des personnes désignées par
 * leur identifiant d'employé (choisi par nom à l'écran), par les canaux qu'elles ont laissés ouverts. Un
 * gestionnaire n'avise que son équipe : hors périmètre, 403 et rien ne part.
 */
@RestController
@RequestMapping("/api/notifications")
public class ControleurAvis {

    private final GardeAcces garde;
    private final NotifierEvenement notifier;
    private final DepotEquipe equipe;
    private final DepotAnnonces annonces;

    public ControleurAvis(GardeAcces garde, NotifierEvenement notifier, DepotEquipe equipe, DepotAnnonces annonces) {
        this.annonces = annonces;
        this.garde = garde;
        this.notifier = notifier;
        this.equipe = equipe;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    // ── Annonces (CSSMB 21, ADR 0039) : publiées par l'administration, lues à l'accueil ──

    private static ResponseEntity<Object> repondreAnnonce(GardeAcces.Verdict v, int code, Supplier<Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Object r = geste.get();
            return r == null ? ResponseEntity.noContent().build() : ResponseEntity.status(code).body(r);
        } catch (IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @GetMapping("/annonces")
    public ResponseEntity<Object> annoncesDuCompte(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return repondreAnnonce(v, 200, () -> annonces.pourCompte(v.tenantId(), GardeAcces.contexte(requete).userId(), GardeAcces.contexte(requete).role()));
    }

    @GetMapping("/annonces/toutes")
    public ResponseEntity<Object> toutesLesAnnonces(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerRole(requete, Role.ADMIN.code());
        return repondreAnnonce(v, 200, () -> annonces.toutes(v.tenantId()));
    }

    @PostMapping("/annonces")
    public ResponseEntity<Object> publierAnnonce(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerRole(requete, Role.ADMIN.code());
        return repondreAnnonce(v, 201, () -> annonces.publier(v.tenantId(), GardeAcces.contexte(requete).userId(), corps).vue());
    }

    @PostMapping("/annonces/{id}/lue")
    public ResponseEntity<Object> annonceLue(HttpServletRequest requete, @PathVariable("id") String id) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        return repondreAnnonce(v, 200, () -> annonces.marquerLue(v.tenantId(), id, GardeAcces.contexte(requete).userId()));
    }

    @DeleteMapping("/annonces/{id}")
    public ResponseEntity<Object> retirerAnnonce(HttpServletRequest requete, @PathVariable("id") String id) {
        GardeAcces.Verdict v = garde.exigerRole(requete, Role.ADMIN.code());
        return repondreAnnonce(v, 204, () -> {
            annonces.retirer(v.tenantId(), id);
            return null;
        });
    }

    @GetMapping("/evenements")
    public ResponseEntity<Object> catalogue(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(Evenements.TOUS.stream().map(Evenements.Evenement::vue).toList());
    }

    @PostMapping("/envoyer")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> envoyer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerRole(requete, Role.ADMIN.code(), Role.MANAGER.code(), Role.RH.code());
        if (v.refuse()) {
            return v.refus();
        }
        List<String> refus = Avis.refusEnvoiDirect(corps);
        if (!refus.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("errors", refus));
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        List<String> ids = List.copyOf(new LinkedHashSet<>((List<String>) corps.get("employeeIds")));
        List<String> inconnus = ids.stream().filter(id -> equipe.lire(v.tenantId(), id).isEmpty()).toList();
        if (!inconnus.isEmpty()) {
            return erreur(404, "employé inconnu : " + String.join(", ", inconnus));
        }
        // RH, admin, superadmin : l'établissement entier ; gestionnaire : sa branche hiérarchique.
        boolean entier = c.roleIntegre().map(Role::etablissementEntier).orElse(false);
        List<String> horsPerimetre = entier ? List.of() : ids.stream().filter(id -> !equipe.dansEquipe(c.employeeId(), id)).toList();
        if (!horsPerimetre.isEmpty()) {
            return erreur(403, "hors de votre équipe : " + String.join(", ", horsPerimetre));
        }
        List<String> canaux = corps.get("canaux") instanceof List<?> l ? (List<String>) l : null;
        List<Map<String, Object>> envois = ids.stream().flatMap(id -> {
            DepotEquipe.Personne p = equipe.lire(v.tenantId(), id).orElseThrow();
            Map<String, Object> variables = Map.of("nom", p.nomComplet(), "prenom", p.prenom());
            return notifier.publier(new PublicationAvis.Avis(v.tenantId(), "message.direct", List.of(id), ((String) corps.get("sujet")).trim(), ((String) corps.get("corps")).trim(), "/admin/notifications", variables, canaux)).stream();
        }).map(ControleurAvis::vue).toList();
        return ResponseEntity.status(201).body(Map.of("envois", envois));
    }

    private static Map<String, Object> vue(PublicationAvis.Envoi e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("employeeId", e.employeeId());
        m.put("canal", e.canal());
        m.put("statut", e.statut());
        m.put("erreur", e.erreur());
        return m;
    }
}
