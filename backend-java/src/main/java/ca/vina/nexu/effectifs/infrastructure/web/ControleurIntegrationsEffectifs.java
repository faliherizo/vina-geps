package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.application.commandes.BoiteEnvoiIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestesEntrantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestesSortantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireConnecteursIntegration;
import ca.vina.nexu.effectifs.application.commandes.RelaisJournalIntegration;
import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
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

import java.util.Map;

/**
 * {@code /api/effectifs/integrations} — les intégrations des effectifs (AO 26-015-DO, lot R10), jumeau de
 * {@code backend/src/effectifs/integrations/routes.ts} : mêmes chemins, mêmes codes, mêmes messages. Configurer un connecteur
 * relève de l'administration ({@code effectifs.configure}) ; déclencher un envoi, tirer une source et suivre la boîte
 * d'envoi, de l'encadrement ({@code effectifs.manage}, puis le périmètre de l'unité visée).
 */
@RestController
@RequestMapping("/api/effectifs/integrations")
@Transactional
public class ControleurIntegrationsEffectifs {

    private static final String MODULE = "effectifs_integrations";

    /** Les écritures du lot. */
    public record Gestes(GestionnaireConnecteursIntegration connecteurs, BoiteEnvoiIntegration boite, GestesSortantsIntegration sortants,
                         RelaisJournalIntegration relais, GestesEntrantsIntegration entrants) { }

    private final GardeEffectifs garde;
    private final LectureIntegrations lire;
    private final Gestes agir;

    public ControleurIntegrationsEffectifs(GardeEffectifs garde, LectureIntegrations lire, Gestes agir) {
        this.garde = garde;
        this.lire = lire;
        this.agir = agir;
    }

    private static Map<String, Object> corps(Map<String, Object> recu) {
        return recu == null ? Map.of() : recu;
    }

    private static String requete(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private GardeAcces.Verdict gerer(HttpServletRequest r) {
        return garde.capacite(r, MODULE, GardeEffectifs.Capacite.GERER);
    }

    /**
     * Un geste qui tire une source externe. La garde commune traduit les erreurs du contexte par leur type ; une source
     * injoignable, propre à ce lot, est interceptée ici et rendue en 502. Elle survient avant toute écriture : il n'y a
     * rien à annuler.
     */
    @GetMapping("/connecteurs")
    public ResponseEntity<Object> connecteurs(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, lire::connecteurs);
    }

    @PutMapping("/connecteurs/{code}")
    public ResponseEntity<Object> configurer(HttpServletRequest r, @PathVariable String code, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER), 200,
                ctx -> agir.connecteurs().configurer(ctx, IntegrationsEffectifs.connecteurDe(code), corps(c)));
    }

    @GetMapping("/messages")
    public ResponseEntity<Object> messages(HttpServletRequest r, @RequestParam(required = false) String connecteur, @RequestParam(required = false) String etat) {
        return garde.repondre(r, gerer(r), 200, ctx -> lire.messages(ctx, requete(connecteur), requete(etat)));
    }

    @PostMapping("/messages/{id}/rejouer")
    public ResponseEntity<Object> rejouer(HttpServletRequest r, @PathVariable String id) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.boite().rejouer(ctx, id));
    }

    @PostMapping("/paie")
    public ResponseEntity<Object> paie(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.sortants().transmettrePaie(ctx, corps(c)));
    }

    @PostMapping("/equipages")
    public ResponseEntity<Object> equipages(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.sortants().publierEquipages(ctx, corps(c)));
    }

    @PostMapping("/calendriers/{ressourceId}")
    public ResponseEntity<Object> calendrier(HttpServletRequest r, @PathVariable String ressourceId) {
        return garde.repondre(r, gerer(r), 201, ctx -> agir.sortants().publierCalendrier(ctx, ressourceId));
    }

    @PostMapping("/relais/{code}")
    public ResponseEntity<Object> relais(HttpServletRequest r, @PathVariable String code) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.relais().relayer(ctx, IntegrationsEffectifs.relaisDe(code)));
    }

    @PostMapping("/horodatages/tirer")
    public ResponseEntity<Object> tirer(HttpServletRequest r) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.entrants().tirerHorodatages(ctx));
    }

    @PostMapping("/anciennetes/{uniteCode}")
    public ResponseEntity<Object> anciennetes(HttpServletRequest r, @PathVariable String uniteCode) {
        return garde.repondre(r, gerer(r), 200, ctx -> agir.entrants().synchroniserAnciennetes(ctx, uniteCode));
    }
}
