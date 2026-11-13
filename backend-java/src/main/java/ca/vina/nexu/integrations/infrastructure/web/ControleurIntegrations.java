package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.integrations.infrastructure.persistance.DepotClientsApi;
import ca.vina.nexu.integrations.infrastructure.persistance.DepotWebhooks;
import ca.vina.nexu.integrations.infrastructure.persistance.EtatIntegrations;

import ca.vina.nexu.integrations.domaine.ClientApi;
import ca.vina.nexu.integrations.domaine.MappageRole;
import ca.vina.nexu.integrations.domaine.RoutageCourriel;
import ca.vina.nexu.integrations.domaine.SerialisationXml;
import ca.vina.nexu.integrations.domaine.Webhooks;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.modules.ModulesActives;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * API {@code /api/integrations} (AO-5338 #NF-06/08/19-22).
 *
 * <p>Toutes les routes exigent une session et un établissement ; la gestion des clients d'API est
 * en outre réservée à l'administration — un client d'API porte les droits de l'établissement sans
 * intervention humaine, le créer n'est pas un acte de gestion courante.
 */
@RestController
@RequestMapping("/api/integrations")
public class ControleurIntegrations {

    private final DepotClientsApi depot;
    private final DepotWebhooks webhooks;
    private final EtatIntegrations etat;
    private final ModulesActives modules;
    private final GardeAcces garde;
    private final Map<String, String> env;

    @org.springframework.beans.factory.annotation.Autowired
    public ControleurIntegrations(DepotClientsApi depot, DepotWebhooks webhooks, EtatIntegrations etat, ModulesActives modules, GardeAcces garde) {
        this(depot, webhooks, etat, modules, garde, System.getenv());
    }

    /** L'environnement est injecté : les essais décrivent une plateforme configurée sans toucher au processus. */
    public ControleurIntegrations(DepotClientsApi depot, DepotWebhooks webhooks, EtatIntegrations etat, ModulesActives modules, GardeAcces garde, Map<String, String> env) {
        this.depot = depot;
        this.webhooks = webhooks;
        this.etat = etat;
        this.modules = modules;
        this.garde = garde;
        this.env = env;
    }

    private static ResponseEntity<Object> invalide(String message) {
        return ResponseEntity.badRequest().body(Map.of("errors", List.of(message)));
    }

    // ── Intégrations en cartes (ÉC-5, INT-1) et webhooks sortants (WH-1…WH-6) ──────────────

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

    private static ResponseEntity<Object> repondre(int code, Supplier<Object> geste) {
        try {
            return ResponseEntity.status(code).body(geste.get());
        } catch (DepotWebhooks.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (DepotWebhooks.DejaLivreException e) {
            return erreur(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    private static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    @GetMapping("/etat")
    public ResponseEntity<Object> etat(HttpServletRequest requete) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(etat.cartes(v.tenantId(), env));
    }

    @GetMapping("/webhooks/evenements")
    public ResponseEntity<Object> evenements(HttpServletRequest requete) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(Webhooks.EVENEMENTS.stream().map(e -> Map.of("code", e, "libelle", Webhooks.LIBELLE_EVENEMENT.get(e))).toList());
    }

    @GetMapping("/webhooks")
    public ResponseEntity<Object> webhooks(HttpServletRequest requete) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(webhooks.lister(v.tenantId()));
    }

    @PostMapping("/webhooks")
    public ResponseEntity<Object> creerWebhook(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : repondre(201, () -> webhooks.creer(v.tenantId(), GardeAcces.contexte(requete).userId(), corps(c)));
    }

    @PostMapping("/webhooks/{id}/activite")
    public ResponseEntity<Object> activite(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : repondre(200, () -> webhooks.reglerActivite(v.tenantId(), id, !Boolean.FALSE.equals(corps(c).get("actif"))));
    }

    @DeleteMapping("/webhooks/{id}")
    public ResponseEntity<Object> supprimerWebhook(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : repondre(200, () -> {
            webhooks.supprimer(v.tenantId(), id);
            return Map.of("supprime", true);
        });
    }

    @GetMapping("/webhooks/{id}/envois")
    public ResponseEntity<Object> envois(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(webhooks.listerEnvois(v.tenantId(), id));
    }

    @PostMapping("/webhooks/envois/{id}/rejouer")
    public ResponseEntity<Object> rejouer(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = exigerAdministration(requete);
        return v.refuse() ? v.refus() : repondre(201, () -> webhooks.rejouer(v.tenantId(), id));
    }

    // ── Clients d'API ───────────────────────────────────────────────────────

    @GetMapping("/api-clients")
    public ResponseEntity<Object> lister(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerRole(requete, "admin", "superadmin");
        return v.refuse() ? v.refus() : ResponseEntity.ok(depot.lister(v.tenantId()));
    }

    @PostMapping("/api-clients")
    public ResponseEntity<Object> creer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerRole(requete, "admin", "superadmin");
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;
        if (!(c.get("name") instanceof String nom) || nom.trim().isEmpty()) {
            return invalide("name required");
        }
        try {
            ClientApi.AvecSecret cree = depot.creer(v.tenantId(), nom, portees(c.get("scopes")));
            // Le secret figure dans cette réponse **et nulle part ailleurs** : aucune relecture
            // ultérieure ne le rendra. C'est le contrat du Node, et c'est ce qui rend une fuite
            // de la table des clients moins grave qu'une fuite de cette réponse.
            Map<String, Object> reponse = new LinkedHashMap<>();
            reponse.put("id", cree.client().id());
            reponse.put("tenantId", cree.client().tenantId());
            reponse.put("name", cree.client().name());
            reponse.put("clientId", cree.client().clientId());
            reponse.put("scopes", cree.client().scopes());
            reponse.put("active", cree.client().active());
            reponse.put("createdAt", cree.client().createdAt());
            reponse.put("lastUsedAt", cree.client().lastUsedAt());
            reponse.put("clientSecret", cree.clientSecret());
            return ResponseEntity.status(201).body(reponse);
        } catch (ClientApi.PorteeInvalideException e) {
            return invalide(e.getMessage());
        }
    }

    @DeleteMapping("/api-clients/{id}")
    public ResponseEntity<Object> revoquer(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = garde.exigerRole(requete, "admin", "superadmin");
        if (v.refuse()) {
            return v.refus();
        }
        return ResponseEntity.status(depot.revoquer(v.tenantId(), id) ? 204 : 404).build();
    }

    // ── Export XML ──────────────────────────────────────────────────────────

    @PostMapping("/export.xml")
    public ResponseEntity<Object> exporterXml(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;

        List<Map<String, Object>> enregistrements = new ArrayList<>();
        if (c.get("records") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> m) {
                    Map<String, Object> ligne = new LinkedHashMap<>();
                    m.forEach((k, val) -> ligne.put(String.valueOf(k), val));
                    enregistrements.add(ligne);
                }
            }
        }

        String xml = SerialisationXml.versXml(enregistrements, texteOuDefaut(c.get("root"), "records"),
                texteOuDefaut(c.get("item"), "record"));

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/xml; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"export.xml\"")
                .body(xml);
    }

    /**
     * Portées demandées dans le corps de la requête.
     *
     * <p>Les valeurs non textuelles sont <strong>conservées</strong> sous leur forme chaîne, et
     * non filtrées ici. La nuance décide du code de réponse : {@code scopes: [1]} doit devenir
     * une liste non vide sans portée valide — donc un 400 — alors qu'un filtrage prématuré la
     * rendrait vide, et une liste vide vaut « read » par défaut. Le Node refuse ; nous refusons.
     */
    private static List<String> portees(Object brut) {
        if (!(brut instanceof List<?> l)) {
            return List.of();
        }
        List<String> demandees = new ArrayList<>();
        for (Object o : l) {
            demandees.add(String.valueOf(o));
        }
        return demandees;
    }

    /** `req.body?.root || "records"` : une chaîne vide est falsy en JavaScript, donc un défaut. */
    private static String texteOuDefaut(Object valeur, String defaut) {
        return valeur instanceof String s && !s.isEmpty() ? s : defaut;
    }

    // ── SSO → rôle ──────────────────────────────────────────────────────────

    @PostMapping("/sso/map-role")
    public ResponseEntity<Object> mapperRole(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;
        if (!(c.get("mapping") instanceof Map<?, ?> m) || !(m.get("claim") instanceof String claim) || claim.isEmpty()) {
            return invalide("mapping.claim required");
        }

        Map<String, String> table = new LinkedHashMap<>();
        if (m.get("map") instanceof Map<?, ?> brute) {
            brute.forEach((k, val) -> table.put(String.valueOf(k), val == null ? null : String.valueOf(val)));
        }
        String repli = m.get("fallback") instanceof String f ? f : null;

        Map<String, Object> revendications = new LinkedHashMap<>();
        if (c.get("claims") instanceof Map<?, ?> cl) {
            cl.forEach((k, val) -> revendications.put(String.valueOf(k), val));
        }

        // `role` peut valoir null : c'est une réponse valide — aucun rôle ne correspond — et non
        // une erreur. Le front-end distingue « pas de rôle » de « échec de la résolution ».
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("role", MappageRole.resoudre(revendications, new MappageRole.Regle(claim, table, repli)));
        return ResponseEntity.ok(reponse);
    }

    // ── Courriel entrant ────────────────────────────────────────────────────

    @PostMapping("/inbound-email")
    public ResponseEntity<Object> courrielEntrant(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;
        if (!(c.get("email") instanceof Map<?, ?> e) || !(e.get("subject") instanceof String sujet) || sujet.isEmpty()) {
            return invalide("email.subject required");
        }

        List<RoutageCourriel.Route> routes = new ArrayList<>();
        if (c.get("routes") instanceof List<?> l) {
            for (Object o : l) {
                if (o instanceof Map<?, ?> r && r.get("subjectPattern") instanceof String motif) {
                    routes.add(new RoutageCourriel.Route(motif,
                            r.get("action") instanceof String a ? a : null));
                }
            }
        }

        RoutageCourriel.Courriel courriel = new RoutageCourriel.Courriel(
                e.get("from") instanceof String f ? f : null, sujet,
                e.get("body") instanceof String b ? b : null);

        RoutageCourriel.Resultat r = RoutageCourriel.router(courriel, routes);
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("action", r.action());
        reponse.put("matchedPattern", r.matchedPattern());
        return ResponseEntity.ok(reponse);
    }
}
