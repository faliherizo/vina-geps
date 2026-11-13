package ca.vina.nexu.rapports.infrastructure.web;

import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import ca.vina.nexu.rapports.domaine.RapportsEnregistres;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotRapportsEnregistres;
import ca.vina.nexu.rapports.infrastructure.persistance.MoteurRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code /api/rapports} — le constructeur de rapports (FIN191).
 *
 * <p><b>Tout refus est un 400 nommé</b>, et jamais un 500. Il n'y a pas de 404 ici : un rapport ne
 * désigne aucune entité, seulement une définition. Définition mal formée, source coupée, champ
 * inconnu — ce sont tous des défauts de la demande, et le message dit lequel.
 */
@RestController
@RequestMapping("/api/rapports")
public class ControleurRapports {

    private static final Logger JOURNAL = LoggerFactory.getLogger(ControleurRapports.class);

    /** Les rapports enregistrés, l'horloge et les identifiants : ce que le contrôleur reçoit au-delà du moteur et de la garde. */
    public record Ports(DepotQuotas quotas, DepotRapportsEnregistres enregistres, Clock horloge, Sequence sequence) {
    }

    private final MoteurRapports moteur;
    private final GardeAcces garde;
    private final Ports ports;
    private final SourcesOuvertes sources;

    public ControleurRapports(MoteurRapports moteur, GardeAcces garde, Ports ports) {
        this.moteur = moteur;
        this.garde = garde;
        this.ports = ports;
        this.sources = new SourcesOuvertes(ports.quotas(), garde);
    }

    /** 404 pour un rapport enregistré introuvable ; tout autre refus est un défaut de la demande (400). */
    private static ResponseEntity<Object> erreur(RuntimeException e) {
        int statut = e instanceof RapportsEnregistres.RapportIntrouvableException ? 404 : 400;
        return ResponseEntity.status(statut).body(Map.of("errors", List.of(String.valueOf(e.getMessage()))));
    }

    private static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    private String tenant(HttpServletRequest requete) {
        return garde.exigerTenant(requete).tenantId();
    }

    private List<String> actifs(String tenantId) {
        return sources.actifs(tenantId);
    }

    /**
     * Les rapports enregistrés appartiennent à l'organisme : les définir, les changer ou les retirer revient à
     * l'encadrement — un employé les produit, il ne les réécrit pas pour tous (jumeau du {@code requireRole} du Node).
     */
    private ResponseEntity<Object> parEncadrement(HttpServletRequest requete, Supplier<Object> geste) {
        GardeAcces.Verdict v = garde.exigerRole(requete, Role.ADMIN.code(), Role.SUPERADMIN.code(), Role.MANAGER.code(), Role.RH.code(), Role.DIRECTOR.code());
        return v.refuse() ? v.refus() : repondre(requete, geste);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Object> repondre(HttpServletRequest requete, Supplier<Object> geste) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Object r = geste.get();
            return r instanceof ResponseEntity<?> pret ? (ResponseEntity<Object>) pret
                    : ResponseEntity.ok(r);
        } catch (RuntimeException e) {
            return erreur(e);
        }
    }

    /** Qui demande : l'horloge et l'identité entrent ici, à la frontière, et nulle part ailleurs. */
    private FournisseurFaits.Demandeur demandeur(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new FournisseurFaits.Demandeur(tenant(requete), c.userId(), c.role(), Iso.de(ports.horloge().instant()));
    }

    /**
     * Produire une définition : la source doit être ouverte à l'organisme, puis la capacité qu'elle
     * déclare (une source qui expose des personnes) vérifiée comme {@code requireCapability} — le refus
     * est la réponse 403 de la garde, rendue telle quelle.
     */
    private Object produire(HttpServletRequest requete, Map<String, Object> brute) {
        CatalogueRapports.Definition d = definition(brute);
        exigerSourceDisponible(tenant(requete), d.source());
        String capacite = CatalogueRapports.sourceDe(d.source()).capacite();
        if (capacite != null) {
            GardeAcces.Verdict v = garde.exigerCapacite(requete, capacite);
            if (v.refuse()) {
                return v.refus();
            }
        }
        return moteur.executer(d, demandeur(requete));
    }

    /** L'en-tête d'un export : ce que le relevé compte, et sur quelle période. */
    private MoteurRapports.Entete entete(HttpServletRequest requete, CatalogueRapports.Definition d, String nom) {
        CatalogueRapports.Source s = CatalogueRapports.sourceDe(d.source());
        List<String> morceaux = new ArrayList<>(List.of(s.libelle()));
        d.filtres().stream().filter(f -> f.champ().equals(s.faits().periode()) && f.operateur() == CatalogueRapports.Operateur.ENTRE)
                .findFirst().ifPresent(f -> morceaux.add("du " + f.valeurs().get(0) + " au " + f.valeurs().get(1)));
        morceaux.add("produit le " + Iso.de(ports.horloge().instant()).substring(0, 10));
        return new MoteurRapports.Entete(nom == null ? s.libelle() : nom, String.join(" — ", morceaux));
    }

    /** Un fichier produit, textuel ou binaire, servi sous le nom {@code rapport-<source>.<extension>}. */
    private Object telecharger(HttpServletRequest requete, Map<String, Object> brute, Object format, String nom) {
        Object r = produire(requete, brute);
        if (r instanceof ResponseEntity<?> refus) {
            return refus;
        }
        CatalogueRapports.Definition d = definition(brute);
        MoteurRapports.Octets fichier = MoteurRapports.exporterFichier((MoteurRapports.Resultat) r,
                format == null ? CatalogueRapports.FormatExport.CSV.code() : String.valueOf(format), entete(requete, d, nom));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, fichier.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-" + d.source() + "." + fichier.extension() + "\"")
                .contentType(MediaType.parseMediaType(fichier.type()))
                .body((Object) fichier.contenu());
    }

    // ── Les catalogues ───────────────────────────────────────────────────────────────────────

    /**
     * <b>Le catalogue, filtré par les modules de l'organisme.</b>
     *
     * <p>C'est la seule liste que l'écran connaît : il ne compose aucun nom de table, et ne peut
     * donc pas en demander une qui ne soit pas déclarée.
     */
    @GetMapping("/sources")
    public ResponseEntity<Object> sources(HttpServletRequest requete) {
        return repondre(requete, () -> CatalogueRapports.sourcesDisponibles(actifs(tenant(requete))).stream()
                .map(ControleurRapports::vueSource).toList());
    }

    /** Chaque source est une source de faits : sa période et la capacité qu'exige sa lecture sont annoncées. */
    private static Map<String, Object> vueSource(CatalogueRapports.Source s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", s.code());
        m.put("libelle", s.libelle());
        m.put("module", s.module());
        m.put("champs", s.champs().stream().map(c -> Map.of("code", c.code(), "libelle", c.libelle(),
                "type", c.type().code(), "groupable", c.groupable())).toList());
        m.put("faits", Map.of("periode", s.faits().periode()));
        m.put("capacite", s.capacite());
        return m;
    }

    @GetMapping("/agregats")
    public ResponseEntity<Object> agregats(HttpServletRequest requete) {
        return repondre(requete, CatalogueRapports.Agregat::codes);
    }

    @GetMapping("/operateurs")
    public ResponseEntity<Object> operateurs(HttpServletRequest requete) {
        return repondre(requete, CatalogueRapports.Operateur::codes);
    }

    /** Les formats <b>réellement branchés</b>, et non ceux que l'énumération déclare. */
    @GetMapping("/formats")
    public ResponseEntity<Object> formats(HttpServletRequest requete) {
        return repondre(requete, () -> MoteurRapports.FORMATS_DISPONIBLES.stream()
                .map(CatalogueRapports.FormatExport::code).toList());
    }

    // ── La définition ────────────────────────────────────────────────────────────────────────

    /** Valider une définition <b>sans l'exécuter</b> : l'écran montre les refus pendant la saisie. */
    @PostMapping("/valider")
    public ResponseEntity<Object> valider(HttpServletRequest requete,
                                          @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, () -> {
            CatalogueRapports.PlanRapport plan = CatalogueRapports.planifier(definition(corps(c)));
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("source", plan.source().code());
            vue.put("colonnes", plan.colonnes());
            vue.put("groupes", plan.groupes());
            vue.put("filtres", plan.filtres());
            vue.put("limite", plan.limite());
            return vue;
        });
    }

    @PostMapping("/executer")
    public ResponseEntity<Object> executer(HttpServletRequest requete,
                                           @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, () -> produire(requete, corps(c)));
    }

    @PostMapping("/exporter")
    public ResponseEntity<Object> exporter(HttpServletRequest requete,
                                           @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, () -> {
            Object brut = corps(c).get("definition");
            return telecharger(requete, brut instanceof Map<?, ?> m ? carte(m) : Map.of(), corps(c).get("format"), null);
        });
    }

    // ── Les rapports enregistrés (P-RF-1 : les paramètres « définis par l'organisation ») ─────

    private DepotRapportsEnregistres.Auteur auteur(HttpServletRequest requete) {
        return new DepotRapportsEnregistres.Auteur(tenant(requete), GardeAcces.contexte(requete).userId(), Iso.de(ports.horloge().instant()));
    }

    /** Une définition à enregistrer se valide d'abord, puis sa source doit être ouverte à l'organisme. */
    private RapportsEnregistres.Saisie saisie(HttpServletRequest requete, Map<String, Object> c) {
        RapportsEnregistres.Saisie s = RapportsEnregistres.saisie(corps(c));
        CatalogueRapports.planifier(definition(s.definition()));
        exigerSourceDisponible(tenant(requete), String.valueOf(s.definition().get("source")));
        return s;
    }

    @GetMapping("/enregistres")
    public ResponseEntity<Object> enregistres(HttpServletRequest requete) {
        return repondre(requete, () -> ports.enregistres().lister(tenant(requete)));
    }

    @GetMapping("/enregistres/{id}")
    public ResponseEntity<Object> enregistre(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, () -> ports.enregistres().lire(tenant(requete), id));
    }

    @PostMapping("/enregistres")
    public ResponseEntity<Object> enregistrer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return parEncadrement(requete, () -> {
            Map<String, Object> r = ports.enregistres().creer(auteur(requete), "rap_" + ports.sequence().uuid(), saisie(requete, c));
            JOURNAL.atInfo().addKeyValue("evenement", "rapports.enregistre").addKeyValue("rapportId", r.get("id")).log("rapport enregistré");
            return ResponseEntity.status(201).body((Object) r);
        });
    }

    @PutMapping("/enregistres/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return parEncadrement(requete, () -> {
            Map<String, Object> r = ports.enregistres().modifier(auteur(requete), id, saisie(requete, c));
            JOURNAL.atInfo().addKeyValue("evenement", "rapports.modifie").addKeyValue("rapportId", id).log("rapport enregistré modifié");
            return r;
        });
    }

    @DeleteMapping("/enregistres/{id}")
    public ResponseEntity<Object> retirer(HttpServletRequest requete, @PathVariable String id) {
        return parEncadrement(requete, () -> {
            Map<String, Object> r = ports.enregistres().retirer(tenant(requete), id);
            JOURNAL.atInfo().addKeyValue("evenement", "rapports.retire").addKeyValue("rapportId", id).log("rapport enregistré retiré");
            return r;
        });
    }

    /** Produire un rapport enregistré : sa définition, recalculée maintenant — jamais un résultat stocké. */
    @PostMapping("/enregistres/{id}/executer")
    public ResponseEntity<Object> produireEnregistre(HttpServletRequest requete, @PathVariable String id) {
        return repondre(requete, () -> produire(requete, definitionEnregistree(requete, id)));
    }

    @PostMapping("/enregistres/{id}/exporter")
    public ResponseEntity<Object> exporterEnregistre(HttpServletRequest requete, @PathVariable String id,
                                                     @RequestBody(required = false) Map<String, Object> c) {
        return repondre(requete, () -> {
            Map<String, Object> r = ports.enregistres().lire(tenant(requete), id);
            return telecharger(requete, definitionEnregistree(requete, id), corps(c).get("format"), String.valueOf(r.get("nom")));
        });
    }

    private Map<String, Object> definitionEnregistree(HttpServletRequest requete, String id) {
        Object d = ports.enregistres().lire(tenant(requete), id).get("definition");
        return d instanceof Map<?, ?> m ? carte(m) : Map.of();
    }

    private void exigerSourceDisponible(String tenantId, String source) {
        sources.exigerDisponible(tenantId, source);
    }

    // ── La traduction du corps JSON ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    /**
     * Le corps HTTP devient une {@link CatalogueRapports.Definition}.
     *
     * <p>Toute conversion d'ensemble fermé se fait <b>ici</b>, une seule fois, à la frontière : un
     * agrégat ou un opérateur inconnu est refusé par un 400 nommé avant que le plan n'existe.
     */
    static CatalogueRapports.Definition definition(Map<String, Object> c) {
        List<CatalogueRapports.Colonne> colonnes = new ArrayList<>();
        for (Object o : liste(c.get("colonnes"))) {
            Map<String, Object> col = o instanceof Map<?, ?> m ? carte(m) : Map.of();
            Object agregat = col.get("agregat");
            Object denominateur = col.get("denominateur");
            colonnes.add(new CatalogueRapports.Colonne(texte(col.get("champ")),
                    agregat == null ? null : CatalogueRapports.Agregat.de(String.valueOf(agregat)),
                    denominateur == null ? null : String.valueOf(denominateur)));
        }

        List<String> groupes = liste(c.get("groupes")).stream().map(ControleurRapports::texte).toList();

        List<CatalogueRapports.Filtre> filtres = new ArrayList<>();
        for (Object o : liste(c.get("filtres"))) {
            Map<String, Object> f = o instanceof Map<?, ?> m ? carte(m) : Map.of();
            filtres.add(new CatalogueRapports.Filtre(texte(f.get("champ")),
                    CatalogueRapports.Operateur.de(texte(f.get("operateur"))), liste(f.get("valeurs"))));
        }

        Map<String, Object> tri = c.get("tri") instanceof Map<?, ?> m ? carte(m) : Map.of();
        Object limite = c.get("limite");

        return new CatalogueRapports.Definition(texte(c.get("source")), colonnes, groupes, filtres,
                tri.get("champ") == null ? null : texte(tri.get("champ")),
                Boolean.TRUE.equals(tri.get("descendant")),
                limite instanceof Number n ? n.intValue() : null);
    }

    private static List<Object> liste(Object v) {
        return v instanceof List<?> l ? List.copyOf(l) : List.of();
    }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v);
    }
}
