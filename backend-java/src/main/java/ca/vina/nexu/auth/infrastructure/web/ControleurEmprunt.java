package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotEmprunts;

import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * L'emprunt d'identité (CSSMB 25 ; ADR 0038), à contrat figé sur le Node : le réglage de l'organisme
 * (administration), le début — la session devient celle de la cible pour une heure, cookies renouvelés —,
 * la fin — la session de l'administrateur revient —, le journal. Le filtre de session tient les interdits.
 */
@RestController
@RequestMapping("/api/auth/emprunt")
public class ControleurEmprunt {

    private static final int LIMITE_JOURNAL = 50;
    private static final int OCTETS_CSRF = 24;
    private static final SecureRandom ALEA = new SecureRandom();

    private final DepotEmprunts emprunts;
    private final GardeAcces garde;
    private final boolean cookieSecurise;

    public ControleurEmprunt(DepotEmprunts emprunts, GardeAcces garde, @Value("${nexu.cookie.secure:false}") boolean cookieSecurise) {
        this.emprunts = emprunts;
        this.garde = garde;
        this.cookieSecurise = cookieSecurise;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static ResponseEntity<Object> repondre(GardeAcces.Verdict v, Supplier<ResponseEntity<Object>> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return geste.get();
        } catch (DepotEmprunts.RefusException e) {
            return erreur(e.getMessage().contains("introuvable") ? 404 : 403, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    /** {@code requireRole("admin")} du Node : l'administration d'un organisme, ou le superadmin — sans exiger d'organisme (un compte global règle le sien, « global »). */
    private GardeAcces.Verdict administration(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v;
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        return c.estSuperadmin() || Role.ADMIN.code().equals(c.role()) ? v : new GardeAcces.Verdict(null, erreur(403, "forbidden"));
    }

    private static String tenantDe(HttpServletRequest requete) {
        return GardeAcces.contexte(requete).tenantId();
    }

    @GetMapping("/reglage")
    public ResponseEntity<Object> reglage(HttpServletRequest requete) {
        return repondre(administration(requete), () -> ResponseEntity.ok(emprunts.lireReglage(tenantDe(requete)).vue()));
    }

    @PutMapping("/reglage")
    public ResponseEntity<Object> regler(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return repondre(administration(requete), () -> ResponseEntity.ok(emprunts.regler(tenantDe(requete), GardeAcces.contexte(requete), corps == null ? null : corps.get("actif")).vue()));
    }

    @GetMapping("/journal")
    public ResponseEntity<Object> journal(HttpServletRequest requete) {
        return repondre(administration(requete), () -> ResponseEntity.ok(emprunts.journal(tenantDe(requete), LIMITE_JOURNAL)));
    }

    /** La session devient celle de la cible pour une heure ; le cookie CSRF est renouvelé avec elle. */
    @PostMapping
    public ResponseEntity<Object> demarrer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return repondre(administration(requete), () -> {
            Map<String, Object> c = corps == null ? Map.of() : corps;
            DepotEmprunts.SessionEmpruntee s = emprunts.demarrer(GardeAcces.contexte(requete), c.get("cibleId"), c.get("motif"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("token", s.token());
            r.put("emprunt", s.emprunt().vue());
            r.put("user", ca.vina.nexu.auth.domaine.Emprunt.sansPortesDePremiereConnexion(s.cible().vue()));
            return avecCookies(r, s.token(), Duration.ofMinutes(ca.vina.nexu.auth.domaine.Emprunt.DUREE_MINUTES));
        });
    }

    @PostMapping("/terminer")
    public ResponseEntity<Object> terminer(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            ContexteRequete session = GardeAcces.contexte(requete);
            DepotEmprunts.SessionRendue s = emprunts.terminer(session);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("token", s.token());
            r.put("user", s.emprunteur().vue());
            return avecCookies(r, s.token(), ControleurAuth.DUREE_SESSION);
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    private ResponseEntity<Object> avecCookies(Map<String, Object> corps, String jeton, Duration duree) {
        byte[] octets = new byte[OCTETS_CSRF];
        ALEA.nextBytes(octets);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
        corps.put("csrf", csrf);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, ControleurAuth.cookie(ControleurAuth.COOKIE_SESSION, jeton, true, duree, cookieSecurise).toString())
                .header(HttpHeaders.SET_COOKIE, ControleurAuth.cookie(ControleurAuth.COOKIE_CSRF, csrf, false, duree, cookieSecurise).toString())
                .body(corps);
    }
}
