package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.journal.infrastructure.DepotJournalActions;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Le journal des actions des utilisateurs (ADR 0041), à contrat figé sur le Node : l'administrateur
 * lit son organisme, un compte global tout et peut cibler un organisme ; les noms de connexion
 * accompagnent les identifiants — jamais le corps des requêtes, qui n'a pas été gardé.
 */
@RestController
@RequestMapping("/api/auth/journal-actions")
public class ControleurJournalActions {

    private final DepotJournalActions actions;
    private final DepotComptes comptes;
    private final GardeAcces garde;

    public ControleurJournalActions(DepotJournalActions actions, DepotComptes comptes, GardeAcces garde) {
        this.actions = actions;
        this.comptes = comptes;
        this.garde = garde;
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest requete,
                                         @RequestParam(name = "utilisateur", required = false) String utilisateur,
                                         @RequestParam(name = "depuis", required = false) String depuis,
                                         @RequestParam(name = "jusqua", required = false) String jusqua,
                                         @RequestParam(name = "q", required = false) String q,
                                         @RequestParam(name = "organisme", required = false) String organisme,
                                         @RequestParam(name = "page", defaultValue = "0") int page,
                                         @RequestParam(name = "pageSize", defaultValue = "50") int pageSize) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        if (!c.estSuperadmin() && !Role.ADMIN.code().equals(c.role())) {
            return ResponseEntity.status(403).body(Map.of("errors", List.of("forbidden")));
        }
        try {
            String perimetre = c.estSuperadmin() ? null : c.tenantId();
            DepotJournalActions.Page p = actions.lister(new DepotJournalActions.Filtre(perimetre, vide(organisme), vide(utilisateur), vide(depuis), vide(jusqua), q, page, pageSize));
            Map<String, String> noms = comptes.lister().stream().collect(Collectors.toMap(Compte::id, Compte::nomDeConnexion, (a, b) -> a));
            Function<String, String> nomDe = id -> id == null ? null : noms.getOrDefault(id, id);
            return ResponseEntity.ok(Map.of("items", p.items().stream().map(a -> {
                Map<String, Object> vue = new LinkedHashMap<>(a.vue());
                vue.put("utilisateur", nomDe.apply(a.userId()));
                vue.put("acteurReelNom", nomDe.apply(a.acteurReel()));
                return vue;
            }).toList(), "total", p.total()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", List.of(e.getMessage())));
        }
    }

    private static String vide(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
