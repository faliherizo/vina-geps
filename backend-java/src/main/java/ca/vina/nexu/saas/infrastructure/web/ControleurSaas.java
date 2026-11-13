package ca.vina.nexu.saas.infrastructure.web;

import ca.vina.nexu.saas.infrastructure.persistance.DepotDestruction;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotMaintenance;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.saas.infrastructure.persistance.DepotVuesMasquees;

import ca.vina.nexu.notifications.domaine.Consommation;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotConsommation;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.domaine.CatalogueModules;
import ca.vina.nexu.saas.domaine.CatalogueVues;
import ca.vina.nexu.saas.domaine.Destruction;
import ca.vina.nexu.saas.domaine.Locataires;
import ca.vina.nexu.saas.domaine.Plans;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * API {@code /api/saas} — la bannière de maintenance, les forfaits, le catalogue des modules, les modules de l'utilisateur courant (ce qui pilote les
 * menus) et leur réglage par établissement depuis l'espace superadmin. Les locataires (création,
 * cycle de vie, usage) demeurent au Node.
 */
@RestController
@RequestMapping("/api/saas")
public class ControleurSaas {

    private final DepotQuotas quotas;
    private final DepotMaintenance maintenance;
    private final DepotLocataires locataires;
    private final GardeAcces garde;
    private final DepotDestruction destruction;
    private final JournalAudit audit;
    private final DepotConsommation consommation;
    private final DepotVuesMasquees vues;
    private final DepotJournalTextos textos;
    private final java.time.Clock horloge;

    public ControleurSaas(DepotQuotas quotas, DepotMaintenance maintenance, DepotLocataires locataires, GardeAcces garde,
                          DepotDestruction destruction, JournalAudit audit, DepotConsommation consommation, DepotJournalTextos textos, java.time.Clock horloge,
                          DepotVuesMasquees vues) {
        this.destruction = destruction;
        this.audit = audit;
        this.consommation = consommation;
        this.vues = vues;
        this.textos = textos;
        this.horloge = horloge;
        this.quotas = quotas;
        this.maintenance = maintenance;
        this.locataires = locataires;
        this.garde = garde;
    }

    // ── Locataires : création, cycle de vie, pays, usage (administration du système) ────────

    private static String texte(Map<String, Object> c, String cle) {
        Object v = c.get(cle);
        return v == null ? null : String.valueOf(v);
    }

    private ResponseEntity<Object> locataire(HttpServletRequest requete, Supplier<Object> geste, int code) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.get());
        } catch (ca.vina.nexu.partage.erreurs.domaine.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (Locataires.ValidationException e) {
            return ResponseEntity.status(400).body(Map.of("errors", e.motifs()));
        } catch (Locataires.ConflitException e) {
            return erreur(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @PostMapping("/tenants")
    public ResponseEntity<Object> creerLocataire(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> corps = c == null ? Map.of() : c;
        return locataire(requete, () -> locataires.creer(new Locataires.Demande(texte(corps, "name"), texte(corps, "type"), texte(corps, "country"),
                texte(corps, "region"), texte(corps, "planCode"), corps.get("trialDays") instanceof Number n ? n.intValue() : null,
                texte(corps, "dataResidency"), texte(corps, "address"), texte(corps, "city"), texte(corps, "postalCode"),
                texte(corps, "phone"), texte(corps, "email"), texte(corps, "slug"))), 201);
    }

    @GetMapping("/tenants")
    public ResponseEntity<Object> listerLocataires(HttpServletRequest requete) {
        return locataire(requete, locataires::lister, 200);
    }

    /** Le pays porte la devise : réservé au superadmin, et seulement tant qu'aucune transaction n'existe. */
    @PatchMapping("/tenants/{id}/country")
    public ResponseEntity<Object> changerPays(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        if (!GardeAcces.contexte(requete).estSuperadmin()) {
            return erreur(403, "réservé au superadmin");
        }
        return locataire(requete, () -> locataires.changerPays(id, texte(c == null ? Map.of() : c, "country")), 200);
    }

    @GetMapping("/tenants/{id}/country-lock")
    public ResponseEntity<Object> verrouPays(HttpServletRequest requete, @PathVariable String id) {
        return locataire(requete, () -> {
            Map<String, Object> t = locataires.lire(id).orElseThrow(() -> new ca.vina.nexu.partage.erreurs.domaine.IntrouvableException("tenant not found"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("country", t.get("country"));
            r.put("locked", locataires.aDesTransactions(id));
            return r;
        }, 200);
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<Object> lireLocataire(HttpServletRequest requete, @PathVariable String id) {
        return locataire(requete, () -> {
            Map<String, Object> t = new LinkedHashMap<>(locataires.lire(id).orElseThrow(() -> new ca.vina.nexu.partage.erreurs.domaine.IntrouvableException("tenant not found")));
            t.put("quota", quotas.quota(id).map(q -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tenantId", q.tenantId());
                m.put("maxTeachers", q.maxTeachers());
                m.put("maxContractsPerYear", q.maxContractsPerYear());
                m.put("modules", q.modules());
                return (Object) m;
            }).orElse(null));
            return t;
        }, 200);
    }

    @GetMapping("/tenants/{id}/usage")
    public ResponseEntity<Object> usage(HttpServletRequest requete, @PathVariable String id, @RequestParam(required = false) String month) {
        return locataire(requete, () -> {
            locataires.lire(id).orElseThrow(() -> new ca.vina.nexu.partage.erreurs.domaine.IntrouvableException("tenant not found"));
            return locataires.usage(id, month);
        }, 200);
    }

    @PostMapping("/tenants/{id}/activate")
    public ResponseEntity<Object> activer(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        Object mois = c == null ? null : c.get("subscriptionMonths");
        return locataire(requete, () -> locataires.activer(id, mois instanceof Number n ? n.intValue() : 12), 200);
    }

    @PostMapping("/tenants/{id}/suspend")
    public ResponseEntity<Object> suspendre(HttpServletRequest requete, @PathVariable String id) {
        return locataire(requete, () -> locataires.changerStatut(id, "suspended"), 200);
    }

    @PostMapping("/tenants/{id}/terminate")
    public ResponseEntity<Object> resilier(HttpServletRequest requete, @PathVariable String id) {
        return locataire(requete, () -> locataires.changerStatut(id, "terminated"), 200);
    }

    /**
     * Destruction sécuritaire et irréversible au terme du contrat (contrat 9.25.06, EFVP 7) :
     * superadmin seulement, établissement résilié, nom retapé ; l'attestation se relit ensuite.
     */
    @PostMapping("/tenants/{id}/detruire-donnees")
    public ResponseEntity<Object> detruireDonnees(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = superadmin(requete);
        if (v.refuse()) {
            return v.refus();
        }
        var contexte = GardeAcces.contexte(requete);
        try {
            Destruction.Attestation a = destruction.detruire(id, contexte.username(), c == null ? "" : texte(c, "confirmation"), c == null ? null : texte(c, "motif"));
            audit.consigner(contexte.userId(), contexte.username(), "saas.tenant.destruction", id, Map.of("totalLignes", a.totalLignes(), "empreinte", a.empreinte()));
            return ResponseEntity.status(201).body(Destruction.enCarte(a));
        } catch (ca.vina.nexu.partage.erreurs.domaine.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (IllegalStateException e) {
            return erreur(409, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @GetMapping("/tenants/{id}/attestation-destruction")
    public ResponseEntity<Object> attestation(HttpServletRequest requete, @PathVariable String id, @RequestParam(name = "format", required = false) String format) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return destruction.lireAttestation(id)
                .<ResponseEntity<Object>>map(a -> "texte".equals(format)
                        ? ResponseEntity.ok().contentType(MediaType.parseMediaType("text/plain;charset=UTF-8")).body(Destruction.texte(a))
                        : ResponseEntity.ok(Destruction.enCarte(a)))
                .orElseGet(() -> erreur(404, "aucune attestation : les données n'ont pas été détruites"));
    }

    private GardeAcces.Verdict superadmin(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v;
        }
        return GardeAcces.contexte(requete).estSuperadmin() ? v : new GardeAcces.Verdict(null, erreur(403, "forbidden"));
    }

    @GetMapping("/plans")
    public ResponseEntity<Object> plans() {
        return ResponseEntity.ok(Map.of("plans", Plans.CODES.stream().map(code -> {
            Plans.Quota q = Plans.defauts(code);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("code", code); d.put("maxTeachers", q.maxTeachers()); d.put("maxContractsPerYear", q.maxContractsPerYear()); d.put("modules", q.modules());
            return d;
        }).toList()));
    }

    /** Message de maintenance (Global-19) : lecture publique, écriture superadmin. */
    @GetMapping("/maintenance")
    public ResponseEntity<Object> maintenance() {
        return ResponseEntity.ok(maintenance.lire());
    }

    @PutMapping("/maintenance")
    public ResponseEntity<Object> reglerMaintenance(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = superadmin(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.ok(maintenance.regler(c == null ? Map.of() : c));
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    /** Chaque entrée porte sa famille, son prérequis et ses dépendants : l'écran n'a pas à redéclarer les dépendances. */
    @GetMapping("/modules")
    public ResponseEntity<Object> modules(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        List<Map<String, Object>> modules = CatalogueModules.MODULES.stream().map(m -> {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("code", m.code()); d.put("label", m.label()); d.put("famille", m.famille()); d.put("requiert", m.requiert());
            d.put("dependants", CatalogueModules.dependantsDe(m.code())); d.put("debloque", m.debloque());
            return d;
        }).toList();
        return ResponseEntity.ok(Map.of("familles", CatalogueModules.FAMILLES, "modules", modules));
    }

    /** Jamais mis en cache : la désactivation d'un module par le superadmin doit se voir au prochain chargement. */
    @GetMapping("/my-modules")
    public ResponseEntity<Object> mesModules(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        if (v.tenantId() == null) {
            // Sans établissement, aucun module : un compte global qui n'a pas choisi d'organisme ne
            // voit que les menus du cœur — les écrans d'un module n'ont rien à lire sans organisme.
            corps.put("modules", List.of());
            corps.put("planCode", null);
        } else {
            corps.put("modules", quotas.quota(v.tenantId()).map(DepotQuotas.Quota::modules).orElse(List.of()));
            corps.put("planCode", quotas.planCode(v.tenantId()).orElse(null));
            // Les vues masquées voyagent avec les modules : l'application lit les deux au même
            // moment, et un second aller-retour ne ferait que retarder le menu d'autant.
            corps.put("vuesMasquees", vues.masquees(v.tenantId()));
        }
        return ResponseEntity.ok().header("Cache-Control", "no-store, must-revalidate").body(corps);
    }

    /** Un code inconnu est refusé ; une sélection incohérente est corrigée, et la réponse dit ce qui a changé. */
    @PutMapping("/tenants/{id}/modules")
    public ResponseEntity<Object> reglerModules(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        if (c == null || !(c.get("modules") instanceof List<?> modules)) {
            return erreur(400, "modules must be an array");
        }
        List<String> codes = modules.stream().map(String::valueOf).toList();
        List<String> inconnus = CatalogueModules.codesInconnus(codes);
        if (!inconnus.isEmpty()) {
            return erreur(400, "invalid modules: " + String.join(", ", inconnus));
        }
        try {
            return ResponseEntity.ok(quotas.reglerModules(id, codes));
        } catch (DepotQuotas.IntrouvableException e) {
            return erreur(404, e.getMessage());
        }
    }

    // ── Vues de menu masquées par organisme ─────────────────────────────────────────────────────
    //
    // ⚠️ **Ce n'est pas un contrôle d'accès** : les routes d'API d'une page restent ouvertes quand
    // un de ses onglets est masqué. La frontière de sécurité reste le module.

    /** Le catalogue des vues réglables, avec leurs sections — ce que l'écran superadmin affiche. */
    @GetMapping("/vues")
    public ResponseEntity<Object> catalogueDesVues(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("sections", CatalogueVues.SECTIONS.stream().map(CatalogueVues.Section::vue).toList());
        corps.put("vues", CatalogueVues.VUES.stream().map(CatalogueVues.Vue::vue).toList());
        return ResponseEntity.ok(corps);
    }

    /** Les vues masquées d'un organisme. */
    @GetMapping("/tenants/{id}/vues")
    public ResponseEntity<Object> vuesMasquees(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        if (locataires.lire(id).isEmpty()) {
            return erreur(404, "tenant not found");
        }
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("tenantId", id);
        corps.put("masquees", vues.masquees(id));
        return ResponseEntity.ok(corps);
    }

    /**
     * Remplace la liste masquée d'un organisme.
     *
     * <p>Une clé inconnue est <b>refusée</b> plutôt qu'ignorée : enregistrée sans bruit, elle
     * serait un menu qu'on croit masqué et qui reste visible.
     */
    @PutMapping("/tenants/{id}/vues")
    public ResponseEntity<Object> reglerVuesMasquees(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        if (locataires.lire(id).isEmpty()) {
            return erreur(404, "tenant not found");
        }
        if (c == null || !(c.get("masquees") instanceof List<?> demandees)) {
            return erreur(400, "masquees doit être un tableau");
        }
        try {
            List<String> posees = vues.regler(id, demandees.stream().map(String::valueOf).toList());
            var contexte = GardeAcces.contexte(requete);
            audit.consigner(contexte.userId(), contexte.username(), "saas.tenant.vues", id, Map.of("masquees", posees.size()));
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("tenantId", id);
            corps.put("masquees", posees);
            return ResponseEntity.ok(corps);
        } catch (DepotVuesMasquees.ClesInconnues e) {
            return erreur(400, e.getMessage());
        }
    }

    // ── Consommation d'avis par organisme (textos et courriels) ─────────────────────────────────
    //
    // L'écran d'où l'exploitant plafonne et facture. Réservé à l'administration du système : c'est
    // une vue **inter-organismes**, et un administrateur d'établissement n'a rien à voir de ce que
    // consomment les autres.

    /**
     * La consommation du mois, tous organismes. Les textos sont comptés en <b>segments</b> —
     * l'unité que le fournisseur facture — et le nombre de messages est donné à côté : l'écart
     * entre les deux est ce qu'une enveloppe « en nombre de textos » ne montre pas.
     */
    @GetMapping("/consommation")
    public ResponseEntity<Object> consommation(HttpServletRequest requete, @RequestParam(required = false) String mois) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.ok(consommation.duMois(moisDemande(mois)).vue());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    /** Le détail d'un organisme, par contexte d'envoi — de quoi expliquer une ligne de facture. */
    @GetMapping("/consommation/{id}")
    public ResponseEntity<Object> consommationDetail(HttpServletRequest requete, @PathVariable String id, @RequestParam(required = false) String mois) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            String m = moisDemande(mois);
            if (!Consommation.NON_ATTRIBUE.equals(id) && locataires.lire(id).isEmpty()) {
                return erreur(404, "tenant not found");
            }
            Map<String, Object> corps = new LinkedHashMap<>();
            corps.put("mois", m);
            corps.put("tenantId", id);
            corps.put("contextes", consommation.detailParContexte(id, m));
            return ResponseEntity.ok(corps);
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    /**
     * Les plafonds mensuels d'un organisme. Zéro vaut <b>illimité</b> sur les deux canaux ; c'est
     * le défaut pour le courriel, qui ne se facture pas au message, et non pour les textos.
     *
     * <p>Les deux plafonds se règlent d'un seul appel : les régler séparément laisserait une
     * fenêtre où l'un est posé et l'autre non, et c'est l'écran qui s'en apercevrait.
     */
    @PutMapping("/consommation/{id}/quotas")
    public ResponseEntity<Object> reglerPlafonds(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = garde.exigerAdministrationSysteme(requete);
        if (v.refuse()) {
            return v.refus();
        }
        if (locataires.lire(id).isEmpty()) {
            return erreur(404, "tenant not found");
        }
        Object limiteTextos = c == null ? null : c.get("textos");
        Object limiteCourriels = c == null ? null : c.get("courriels");
        if (limiteTextos == null && limiteCourriels == null) {
            return erreur(400, "textos ou courriels attendu");
        }
        try {
            Map<String, Object> reponse = new LinkedHashMap<>();
            if (limiteTextos != null) {
                reponse.put("textos", textos.reglerQuota(id, limiteTextos).vue());
            }
            if (limiteCourriels != null) {
                reponse.put("courriels", Map.of("limite", consommation.reglerQuotaCourriel(id, limiteCourriels)));
            }
            var contexte = GardeAcces.contexte(requete);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("textos", limiteTextos);
            details.put("courriels", limiteCourriels);
            audit.consigner(contexte.userId(), contexte.username(), "saas.consommation.quotas", id, details);
            return ResponseEntity.ok(reponse);
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    private String moisDemande(String mois) {
        return Consommation.moisDemande(mois, ca.vina.nexu.partage.temps.Iso.de(horloge.instant()));
    }
}
