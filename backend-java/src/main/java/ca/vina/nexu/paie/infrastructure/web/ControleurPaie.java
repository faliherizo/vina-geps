package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireDossiersPaie;
import ca.vina.nexu.paie.application.requetes.DocumentsPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.domaine.CyclePaie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static ca.vina.nexu.paie.infrastructure.web.SaisiesPaie.corps;

/**
 * {@code /api/paie} — la paie (ADR 0076, 0084). Le contrôleur traduit HTTP en commande ou requête : les règles sont dans le
 * domaine, la garde dans {@link GardePaie}.
 */
@RestController
@RequestMapping("/api/paie")
public class ControleurPaie {

    private static final int OK = 200;
    private static final int CREE = 201;
    /** L'année civile de la paie se lit à Montréal : à 20 h le 31 décembre, c'est encore l'année qui finit. */
    private static final ZoneId FUSEAU = ZoneId.of("America/Montreal");

    public record Cas(GestionnaireDossiersPaie dossiers, GestionnaireCyclesPaie cycles, LecturePaie lecture, DocumentsPaie documents) { }

    private final GardePaie garde;
    private final Cas cas;
    private final Clock horloge;

    public ControleurPaie(GardePaie garde, Cas cas, Clock horloge) {
        this.garde = garde;
        this.cas = cas;
        this.horloge = horloge;
    }

    // ── Dossiers et réglages ────────────────────────────────────────────────

    @GetMapping("/dossiers")
    public ResponseEntity<Object> dossiers(HttpServletRequest r) {
        return garde.repondre(r, garde.configuration(r), OK, t -> cas.lecture().dossiers(t.tenantId()).stream().map(VuesPaie::dossier).toList());
    }

    @PutMapping("/dossiers/{ressourceId}")
    public ResponseEntity<Object> enregistrerDossier(HttpServletRequest r, @PathVariable String ressourceId, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.configuration(r), OK, t -> VuesPaie.dossier(cas.dossiers().enregistrer(t,
                SaisiesPaie.dossier(ressourceId, corps(c), cas.lecture().bareme(anneeCourante())))));
    }

    @GetMapping("/reglages")
    public ResponseEntity<Object> reglages(HttpServletRequest r) {
        int annee = anneeCourante();
        return garde.repondre(r, garde.configuration(r), OK, t -> VuesPaie.reglages(cas.lecture().reglages(t.tenantId(), annee), annee));
    }

    @PutMapping("/reglages")
    public ResponseEntity<Object> regler(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.configuration(r), OK, t -> VuesPaie.reglages(cas.dossiers().regler(t, SaisiesPaie.reglages(corps(c))), anneeCourante()));
    }

    /** Le barème de l'année : ce que la paie applique, lisible par qui la gère. */
    @GetMapping("/bareme")
    public ResponseEntity<Object> bareme(HttpServletRequest r, @RequestParam(required = false) String annee) {
        return garde.repondre(r, garde.configuration(r), OK, t -> cas.lecture().bareme(SaisiesPaie.annee(annee, anneeCourante())));
    }

    // ── Cycles ──────────────────────────────────────────────────────────────

    @GetMapping("/cycles")
    public ResponseEntity<Object> cycles(HttpServletRequest r) {
        return garde.repondre(r, garde.gestion(r), OK, t -> cas.lecture().cycles(t.tenantId()).stream().map(VuesPaie::cycle).toList());
    }

    @PostMapping("/cycles")
    public ResponseEntity<Object> preparer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.gestion(r), CREE, t -> VuesPaie.preparation(cas.cycles().preparer(t, SaisiesPaie.periode(corps(c)))));
    }

    @GetMapping("/cycles/{id}")
    public ResponseEntity<Object> cycle(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.gestion(r), OK, t -> {
            CyclePaie cycle = cas.lecture().exigerCycle(t.tenantId(), id);
            return VuesPaie.detail(cycle, cas.lecture().bulletins(t.tenantId(), id));
        });
    }

    @PostMapping("/cycles/{id}/recalculer")
    public ResponseEntity<Object> recalculer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.preparation(cas.cycles().recalculer(t, id)));
    }

    @PostMapping("/cycles/{id}/traiter")
    public ResponseEntity<Object> traiter(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.cycle(cas.cycles().traiter(t, id)));
    }

    @PostMapping("/cycles/{id}/annuler")
    public ResponseEntity<Object> annuler(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, garde.gestion(r), OK, t -> VuesPaie.cycle(cas.cycles().annuler(t, id)));
    }

    /** Le registre du cycle : une ligne par personne, et les totaux — ce que le comptable relit avant de traiter. */
    @GetMapping("/cycles/{id}/registre.pdf")
    public ResponseEntity<Object> registre(HttpServletRequest r, @PathVariable String id) {
        return garde.fichier(r, garde.gestion(r), t -> cas.documents().registre(t.tenantId(), id));
    }

    @GetMapping("/cycles/{id}/bulletins/{bulletinId}/pdf")
    public ResponseEntity<Object> fiche(HttpServletRequest r, @PathVariable String id, @PathVariable String bulletinId) {
        return garde.fichier(r, garde.gestion(r), t -> cas.documents().ficheDuCycle(t.tenantId(), id, bulletinId));
    }

    // ── Déclarations ────────────────────────────────────────────────────────

    @GetMapping("/remises")
    public ResponseEntity<Object> remises(HttpServletRequest r, @RequestParam(required = false) String annee) {
        return garde.repondre(r, garde.gestion(r), OK, t -> cas.lecture().remises(t.tenantId(), SaisiesPaie.annee(annee, anneeCourante())));
    }

    @GetMapping("/releves")
    public ResponseEntity<Object> releves(HttpServletRequest r, @RequestParam(required = false) String annee) {
        return garde.repondre(r, garde.gestion(r), OK, t -> cas.lecture().releves(t.tenantId(), SaisiesPaie.annee(annee, anneeCourante())));
    }

    // ── Chacun ──────────────────────────────────────────────────────────────

    @GetMapping("/mes-bulletins")
    public ResponseEntity<Object> mesBulletins(HttpServletRequest r) {
        return garde.repondre(r, garde.socle(r), OK, t -> cas.lecture().mesBulletins(t.tenantId(), GardePaie.utilisateur(r))
                .stream().map(VuesPaie::bulletin).toList());
    }

    /** Sa propre fiche, d'un cycle traité : celle d'un autre est introuvable. */
    @GetMapping("/mes-bulletins/{bulletinId}/pdf")
    public ResponseEntity<Object> maFiche(HttpServletRequest r, @PathVariable String bulletinId) {
        return garde.fichier(r, garde.socle(r), t -> cas.documents().maFiche(t.tenantId(), GardePaie.utilisateur(r), bulletinId));
    }

    private int anneeCourante() {
        return LocalDate.now(horloge.withZone(FUSEAU)).getYear();
    }
}
