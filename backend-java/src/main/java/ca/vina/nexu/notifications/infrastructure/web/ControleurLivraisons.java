package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.domaine.Livraisons;
import ca.vina.nexu.notifications.domaine.Regroupement;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotLivraisons;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotNotifications;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotRegroupement;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/notifications} — le journal des livraisons (maquette P69 : ce qui est parti, ce qui a
 * échoué, la relance d'un échec) et le regroupement des avis (BF-124 : le défaut de
 * l'établissement, le choix de chaque compte). Jumeau de {@code routesAvis.ts}.
 */
@RestController
@RequestMapping("/api/notifications")
@Transactional
public class ControleurLivraisons {

    /** Qui lit le journal des livraisons et relance un échec. */
    private static final String[] PILOTAGE = {"admin", "manager", "rh", "director", "superadmin"};
    private static final String[] ADMINISTRATION = {"admin", "superadmin"};

    private final GardeAcces garde;
    private final DepotLivraisons livraisons;
    private final DepotNotifications notifications;
    private final DepotRegroupement regroupement;

    public ControleurLivraisons(GardeAcces garde, DepotLivraisons livraisons, DepotNotifications notifications,
                                DepotRegroupement regroupement) {
        this.garde = garde;
        this.livraisons = livraisons;
        this.notifications = notifications;
        this.regroupement = regroupement;
    }

    @GetMapping("/livraisons")
    public ResponseEntity<Object> livraisons(HttpServletRequest r, @RequestParam(required = false) String evenement,
                                             @RequestParam(required = false) String canal, @RequestParam(required = false) String etat,
                                             @RequestParam(required = false) String destinataire, @RequestParam(required = false) String limite) {
        Livraisons.Filtre f = new Livraisons.Filtre(evenement, canal, etat, destinataire, entier(limite));
        return Bord.repondre(garde.exigerRole(r, PILOTAGE), t -> {
            List<Livraisons.Ligne> lignes = lignes(t, f, Livraisons.limite(f.limite()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("resume", Livraisons.resumer(lignes(t, f, Livraisons.LIMITE_MAX)));
            m.put("lignes", lignes);
            return m;
        });
    }

    @PostMapping("/livraisons/{id}/relancer")
    public ResponseEntity<Object> relancer(HttpServletRequest r, @PathVariable String id) {
        return Bord.repondre(garde.exigerRole(r, PILOTAGE), 201, t -> {
            Livraisons.Envoi e = livraisons.lire(t, id).orElseThrow(() -> new IntrouvableException("envoi introuvable"));
            Livraisons.verifierRelance(e.etat());
            return Livraisons.ligne(notifications.renvoyer(t, e));
        });
    }

    @GetMapping("/regroupement")
    public ResponseEntity<Object> regroupement(HttpServletRequest r) {
        return Bord.repondre(garde.exigerTenant(r), t -> {
            String userId = GardeAcces.contexte(r).userId();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("modes", Arrays.stream(Regroupement.Mode.values()).map(x -> Map.of("code", x.code(), "libelle", x.libelle())).toList());
            m.put("defautEtablissement", code(regroupement.defautEtablissement(t)));
            m.put("mien", code(regroupement.choixDuCompte(userId)));
            m.put("effectif", code(regroupement.effectifDuCompte(t, userId)));
            return m;
        });
    }

    @PutMapping("/regroupement/mien")
    public ResponseEntity<Object> mien(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(garde.exigerTenant(r), t -> {
            String userId = GardeAcces.contexte(r).userId();
            Regroupement.Mode m = regroupement.reglerChoix(userId, Bord.corps(corps).get("mode"));
            Map<String, Object> rep = new LinkedHashMap<>();
            rep.put("mien", code(m));
            rep.put("effectif", code(regroupement.effectifDuCompte(t, userId)));
            return rep;
        });
    }

    @PutMapping("/regroupement/defaut")
    public ResponseEntity<Object> defaut(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.repondre(garde.exigerRole(r, ADMINISTRATION), t -> Map.of("mode",
                regroupement.reglerDefaut(t, GardeAcces.contexte(r).userId(), Bord.corps(corps).get("mode")).code()));
    }

    private List<Livraisons.Ligne> lignes(String tenantId, Livraisons.Filtre f, int limite) {
        return livraisons.lister(tenantId, f, limite).stream().map(Livraisons::ligne).toList();
    }

    private static String code(Regroupement.Mode m) {
        return m == null ? null : m.code();
    }

    /** {@code Number(x)} ramené à l'entier ; {@code NaN} vaut « absent » ({@code || 200}). */
    private static Integer entier(String brut) {
        if (brut == null || brut.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(brut.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
