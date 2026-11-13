package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotPreferences;
import ca.vina.nexu.notifications.infrastructure.persistance.Twilio;

import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code /api/notifications} — le journal des textos ({@code sms_message}), l'état du fournisseur,
 * le rappel de statut du fournisseur et les canaux que chaque compte a choisis, à contrat figé sur
 * {@code routesTextos.ts}. Le journal se lit par l'administration de l'établissement (admin, RH,
 * gestionnaire), numéros masqués. Le rappel Twilio est <strong>public</strong> mais signé : sans
 * signature valide, rien n'est écrit.
 */
@RestController
@RequestMapping("/api/notifications")
public class ControleurTextos {

    private static final String[] ADMINISTRATION = {"admin", "manager", "rh"};

    private final DepotJournalTextos journal;
    private final DepotPreferences preferences;
    private final JournalTextos.Reglages reglages;
    private final GardeAcces garde;

    public ControleurTextos(DepotJournalTextos journal, DepotPreferences preferences, JournalTextos.Reglages reglages, GardeAcces garde) {
        this.journal = journal;
        this.preferences = preferences;
        this.reglages = reglages;
        this.garde = garde;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private ResponseEntity<Object> administration(HttpServletRequest requete, Function<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerRole(requete, ADMINISTRATION);
        return v.refuse() ? v.refus() : ResponseEntity.ok(corps.apply(v.tenantId()));
    }

    // ── Rappel du fournisseur : formulaire encodé, signé, avant toute session ──

    @PostMapping("/sms/statut/twilio")
    public ResponseEntity<Object> rappelTwilio(HttpServletRequest requete, @RequestParam Map<String, String> params, @RequestHeader(name = "X-Twilio-Signature", required = false) String signature) {
        String url = reglages.urlRappel() != null ? reglages.urlRappel() : requete.getRequestURL().toString();
        if (!JournalTextos.verifierSignatureTwilio(url, params, signature, reglages.jetonTwilio())) {
            return erreur(403, "signature Twilio absente ou invalide");
        }
        String sid = params.get("MessageSid");
        String statut = params.get("MessageStatus");
        if (sid == null || sid.isEmpty() || statut == null || statut.isEmpty()) {
            return erreur(400, "MessageSid et MessageStatus requis");
        }
        return ResponseEntity.ok(Map.of("misesAJour", journal.appliquerRappel(sid, statut, JournalTextos.erreurDuRappel(params.get("ErrorCode"), params.get("ErrorMessage")))));
    }

    // ── Les canaux du compte : lisibles et réglables par lui seul ──

    @GetMapping("/preferences")
    public ResponseEntity<Object> mesCanaux(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        return v.refuse() ? v.refus() : ResponseEntity.ok(preferences.preferencesDe(GardeAcces.contexte(requete).userId()).vue());
    }

    @PutMapping("/preferences")
    public ResponseEntity<Object> reglerMesCanaux(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        try {
            return ResponseEntity.ok(preferences.regler(c.userId(), c.tenantId(), corps).vue());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    // ── Le journal de l'établissement ──

    @GetMapping("/sms/etat")
    public ResponseEntity<Object> etat(HttpServletRequest requete) {
        return administration(requete, t -> journal.etat().vue());
    }

    @GetMapping("/sms/resume")
    public ResponseEntity<Object> resume(HttpServletRequest requete) {
        return administration(requete, journal::resume);
    }

    @GetMapping("/sms")
    public ResponseEntity<Object> lister(HttpServletRequest requete, @RequestParam(name = "statut", required = false) String statut, @RequestParam(name = "contexte", required = false) String contexte,
                                         @RequestParam(name = "limite", required = false) Integer limite) {
        return administration(requete, t -> journal.lister(t, statut, contexte, limite).stream().map(JournalTextos.Texto::vue).toList());
    }

    /**
     * Un texto composé à l'écran, par l'administration : numéro E.164, texte borné, quota vérifié,
     * journalisé quoi qu'il arrive. Le fournisseur qui refuse répond 502 avec son erreur nommée ;
     * le quota atteint répond 429. C'est le seul point d'envoi libre — tout le reste est contextuel.
     */
    @PostMapping("/sms")
    public ResponseEntity<Object> envoyer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerRole(requete, ADMINISTRATION);
        if (v.refuse()) {
            return v.refus();
        }
        Map<String, Object> c = corps == null ? Map.of() : corps;
        String numero = Textos.normaliserNumero(c.get("numero") == null ? "" : String.valueOf(c.get("numero")));
        String texte = c.get("texte") == null ? "" : String.valueOf(c.get("texte"));
        List<String> erreurs = JournalTextos.refusDeComposition(numero, texte);
        if (!erreurs.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("errors", erreurs));
        }
        JournalTextos.Texto t = journal.envoyer(new JournalTextos.Demande(v.tenantId(), numero, texte, null, "manuel:" + GardeAcces.contexte(requete).userId()));
        if (t.echoue()) {
            Map<String, Object> refus = new LinkedHashMap<>();
            refus.put("errors", List.of(t.erreur()));
            refus.put("texto", t.vue());
            return ResponseEntity.status(t.refusePourQuota() ? 429 : 502).body(refus);
        }
        return ResponseEntity.status(201).body(t.vue());
    }

    @GetMapping("/sms/quota")
    public ResponseEntity<Object> quota(HttpServletRequest requete) {
        return administration(requete, t -> {
            Map<String, Object> m = journal.quotaDe(t).vue();
            m.put("longueurMax", Textos.LONGUEUR_MAX);
            return m;
        });
    }

    /** La limite d'un établissement se règle par l'administration du système, jamais par l'établissement lui-même. */
    @PutMapping("/sms/quota")
    public ResponseEntity<Object> reglerQuota(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict systeme = garde.exigerAdministrationSysteme(requete);
        if (systeme.refuse()) {
            return systeme.refus();
        }
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.ok(journal.reglerQuota(v.tenantId(), corps == null ? null : corps.get("limite")).vue());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    @PostMapping("/sms/{id}/rafraichir")
    public ResponseEntity<Object> rafraichir(HttpServletRequest requete, @PathVariable("id") long id) {
        GardeAcces.Verdict v = garde.exigerRole(requete, ADMINISTRATION);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.ok(journal.rafraichir(v.tenantId(), id).vue());
        } catch (DepotJournalTextos.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }
}
