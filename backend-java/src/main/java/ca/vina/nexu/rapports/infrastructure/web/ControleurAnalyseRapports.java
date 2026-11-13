package ca.vina.nexu.rapports.infrastructure.web;

import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import ca.vina.nexu.rapports.domaine.Indicateurs;
import ca.vina.nexu.rapports.domaine.Periodicites;
import ca.vina.nexu.rapports.domaine.Periodicites.Bornes;
import ca.vina.nexu.rapports.domaine.Periodicites.Periodicite;
import ca.vina.nexu.rapports.domaine.Utilisation;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotIndicateurs;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotUtilisation;
import ca.vina.nexu.rapports.infrastructure.persistance.MoteurRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * {@code /api/rapports} — l'analyse au-delà du constructeur : listes d'utilisation (BF-130), comparaison
 * de périodes (BF-132) et indicateurs de l'établissement (BF-046). Jumeau des routes de {@code rapports/routes.ts}.
 *
 * <p>Tout refus de la demande est un 400 nommé ; une capacité manquante, le 403 de la garde.
 */
@RestController
@RequestMapping("/api/rapports")
public class ControleurAnalyseRapports {

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final String[] ENCADREMENT = {Role.ADMIN.code(), Role.SUPERADMIN.code(), Role.MANAGER.code(), Role.RH.code(), Role.DIRECTOR.code()};
    private static final int CREE = 201;
    /** La valeur de {@code ?retires=} qui inclut les indicateurs retirés. */
    private static final String INCLURE = "1";

    /** Les dépôts et l'horloge : ce que le contrôleur reçoit au-delà du moteur et de la garde. */
    public record Ports(DepotQuotas quotas, DepotIndicateurs indicateurs, DepotUtilisation utilisation, Clock horloge, Sequence sequence) {
    }

    private final MoteurRapports moteur;
    private final GardeAcces garde;
    private final Ports ports;
    private final SourcesOuvertes sources;

    public ControleurAnalyseRapports(MoteurRapports moteur, GardeAcces garde, Ports ports) {
        this.moteur = moteur;
        this.garde = garde;
        this.ports = ports;
        this.sources = new SourcesOuvertes(ports.quotas(), garde);
    }

    /** Un refus déjà formé (403 de capacité) se rend tel quel ; toute autre faute de la demande est un 400 nommé. */
    private static final class Refus extends RuntimeException {
        private final transient ResponseEntity<Object> reponse;

        Refus(ResponseEntity<Object> reponse) {
            super(null, null, false, false);
            this.reponse = reponse;
        }
    }

    private ResponseEntity<Object> repondre(GardeAcces.Verdict v, int statut, Supplier<Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(statut).body(geste.get());
        } catch (Refus r) {
            return r.reponse;
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("errors", List.of(String.valueOf(e.getMessage()))));
        }
    }

    private ResponseEntity<Object> lecture(HttpServletRequest r, Supplier<Object> geste) {
        return repondre(garde.exigerTenant(r), 200, geste);
    }

    private static String tenant(HttpServletRequest r) {
        return GardeAcces.contexte(r).tenantId();
    }

    private String maintenant() {
        return Iso.de(ports.horloge().instant());
    }

    private static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    /** La source ouverte à l'organisme, puis permise au compte ; un refus de capacité interrompt le geste. */
    private void exigerAcces(HttpServletRequest r, Object source) {
        sources.exigerDisponible(tenant(r), source);
        ResponseEntity<Object> refus = sources.refusCapacite(r, String.valueOf(source));
        if (refus != null) {
            throw new Refus(refus);
        }
    }

    // ── Les listes d'utilisation (BF-130) ─────────────────────────────────────────────────────

    private static String exigerDate(String v, String role) {
        String t = v == null ? "" : v.trim();
        if (!DATE.matcher(t).matches()) {
            throw new CatalogueRapports.RapportInvalideException(role + " attend une date AAAA-MM-JJ");
        }
        return t;
    }

    /** Réservées à l'encadrement : la réponse nomme les comptes. La période est obligatoire. */
    @GetMapping("/utilisation")
    public ResponseEntity<Object> utilisation(HttpServletRequest r, @RequestParam(required = false) String du, @RequestParam(required = false) String au,
                                              @RequestParam(required = false) String seuilJours) {
        return repondre(garde.exigerRole(r, ENCADREMENT), 200, () -> {
            String debut = exigerDate(du, "le début de la période");
            String fin = exigerDate(au, "la fin de la période");
            if (fin.compareTo(debut) < 0) {
                throw new CatalogueRapports.RapportInvalideException("la fin de la période précède son début");
            }
            int seuil = Utilisation.validerSeuil(seuilJours);
            DepotUtilisation.Listes l = ports.utilisation().lire(tenant(r), debut, fin, maintenant());
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("periode", Map.of("du", debut, "au", fin));
            vue.put("seuilInactiviteJours", seuil);
            vue.put("quarts", l.quarts());
            vue.put("comptes", Utilisation.repartir(l.comptes(), maintenant(), seuil));
            vue.put("comptesObserves", l.comptes().size());
            return vue;
        });
    }

    // ── Périodicités et comparaison historique (BF-132) ───────────────────────────────────────

    @GetMapping("/periodicites")
    public ResponseEntity<Object> periodicites(HttpServletRequest r) {
        return lecture(r, () -> Arrays.stream(Periodicite.values()).map(p -> Map.of("code", p.code(), "libelle", p.libelle())).toList());
    }

    /**
     * <b>Comparer une période à une autre.</b> Les bornes de l'appelant sont <b>remplacées</b>, jamais
     * fusionnées : elles sont posées ici, deux fois, à partir de la périodicité — sans quoi l'on
     * comparerait mars à une semaine de février.
     */
    @PostMapping("/comparer")
    public ResponseEntity<Object> comparer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return lecture(r, () -> {
            Map<String, Object> b = corps(c);
            CatalogueRapports.Definition d = ControleurRapports.definition(b.get("definition") instanceof Map<?, ?> m ? carte(m) : Map.of());
            Periodicite p = Periodicite.de(b.get("periodicite"));
            Bornes courantes = p == Periodicite.PERSONNALISE ? Periodicites.bornesPersonnalisees(b.get("du"), b.get("au")) : Periodicites.bornesDe(p, b.get("reference"));
            Periodicites.Contre contre = Periodicites.Contre.de(b.get("contre"));
            Bornes anterieures = Periodicites.anterieures(p, courantes, contre);
            CatalogueRapports.Faits faits = CatalogueRapports.sourceDe(d.source()).faits();
            List<String> mesures = d.colonnes().stream().filter(col -> col.agregat() != null).map(CatalogueRapports.Colonne::champ).toList();
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("periodicite", p.code());
            vue.put("libelle", p.libelle());
            vue.put("contre", contre.code());
            vue.put("courante", courantes);
            vue.put("anterieure", anterieures);
            vue.put("cles", d.groupes());
            vue.put("mesures", mesures);
            vue.put("lignes", Periodicites.comparer(produire(r, borner(d, faits.periode(), courantes)), produire(r, borner(d, faits.periode(), anterieures)),
                    d.groupes(), mesures));
            return vue;
        });
    }

    private static CatalogueRapports.Definition borner(CatalogueRapports.Definition d, String champ, Bornes b) {
        List<CatalogueRapports.Filtre> filtres = new ArrayList<>(d.filtres().stream().filter(f -> !f.champ().equals(champ)).toList());
        filtres.add(new CatalogueRapports.Filtre(champ, CatalogueRapports.Operateur.ENTRE, List.of(b.du(), b.au())));
        return new CatalogueRapports.Definition(d.source(), d.colonnes(), d.groupes(), filtres, d.triChamp(), d.triDescendant(), d.limite());
    }

    private FournisseurFaits.Demandeur demandeur(HttpServletRequest r) {
        ContexteRequete c = GardeAcces.contexte(r);
        return new FournisseurFaits.Demandeur(c.tenantId(), c.userId(), c.role(), maintenant());
    }

    private List<Map<String, Object>> produire(HttpServletRequest r, CatalogueRapports.Definition d) {
        exigerAcces(r, d.source());
        return moteur.executer(d, demandeur(r)).lignes();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    // ── Les indicateurs définis par l'établissement (BF-046) ─────────────────────────────────

    @GetMapping("/indicateurs/formats")
    public ResponseEntity<Object> formatsIndicateur(HttpServletRequest r) {
        return lecture(r, () -> Map.of("formats", Indicateurs.Format.codes(), "directions", Indicateurs.Direction.codes(),
                "etats", Indicateurs.Etat.codes()));
    }

    /** Ce qu'une source offre à un indicateur : ses mesures et ses axes, pour que l'écran n'en invente pas. */
    @GetMapping("/indicateurs/mesures")
    public ResponseEntity<Object> mesures(HttpServletRequest r, @RequestParam(required = false) String source) {
        return lecture(r, () -> {
            String s = source == null ? "" : source;
            sources.exigerDisponible(tenant(r), s);
            return Map.of("source", s, "mesures", Indicateurs.mesuresDe(s), "axes", Indicateurs.axesDe(s));
        });
    }

    @GetMapping("/indicateurs")
    public ResponseEntity<Object> indicateurs(HttpServletRequest r, @RequestParam(required = false) String retires) {
        return lecture(r, () -> ports.indicateurs().lister(tenant(r), INCLURE.equals(retires)).stream().map(DepotIndicateurs.Indicateur::vue).toList());
    }

    /** Définir un indicateur engage tout l'établissement : l'encadrement le pose, un employé le lit. */
    @PostMapping("/indicateurs")
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerRole(r, ENCADREMENT), CREE, () -> {
            exigerAcces(r, corps(c).get("source"));
            DepotIndicateurs.Creation creation = new DepotIndicateurs.Creation(tenant(r), GardeAcces.contexte(r).userId(), maintenant(), ports.sequence().uuid());
            return ports.indicateurs().creer(creation, corps(c)).vue();
        });
    }

    @PatchMapping("/indicateurs/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerRole(r, ENCADREMENT), 200, () -> ports.indicateurs().modifier(tenant(r), id, corps(c)).vue());
    }

    @DeleteMapping("/indicateurs/{id}")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id) {
        return repondre(garde.exigerRole(r, ENCADREMENT), 200, () -> ports.indicateurs().retirer(tenant(r), id).vue());
    }

    /** <b>Mesurer</b> sur une période obligatoire : un indicateur « depuis toujours » relirait l'histoire entière. */
    @PostMapping("/indicateurs/mesurer")
    public ResponseEntity<Object> mesurer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return lecture(r, () -> {
            Object brutDu = corps(c).get("du");
            Object brutAu = corps(c).get("au");
            String du = brutDu == null ? "" : String.valueOf(brutDu);
            String au = brutAu == null ? "" : String.valueOf(brutAu);
            if (du.isEmpty() || au.isEmpty()) {
                throw new CatalogueRapports.RapportInvalideException("la mesure d'un indicateur exige une période (du, au)");
            }
            Object seul = corps(c).get("indicateurId");
            List<DepotIndicateurs.Indicateur> retenus = seul == null ? ports.indicateurs().lister(tenant(r), false)
                    : List.of(ports.indicateurs().lire(tenant(r), String.valueOf(seul)));
            retenus.forEach(i -> exigerAcces(r, i.definition().source()));
            Map<String, List<Map<String, Object>>> parSource = new HashMap<>();
            CatalogueRapports.Periode periode = new CatalogueRapports.Periode(du, au);
            List<Map<String, Object>> mesures = retenus.stream().map(i -> Map.<String, Object>of("indicateur", i.vue(), "valeurs",
                    Indicateurs.evaluer(i.definition(), parSource.computeIfAbsent(i.definition().source(), s -> faitsDe(r, s, periode))))).toList();
            return Map.of("du", du, "au", au, "mesures", mesures);
        });
    }

    /** Un fournisseur par source, appelé une seule fois : dix indicateurs sur les quarts ne relisent pas dix fois la période. */
    private List<Map<String, Object>> faitsDe(HttpServletRequest r, String source, CatalogueRapports.Periode periode) {
        return moteur.faits(source, demandeur(r), periode);
    }
}
