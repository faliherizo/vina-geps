package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.application.ServiceApprobationsLiaison;
import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DemandesLiaison;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.PareFeu;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <strong>« Relier mon compte »</strong> (ADR 0093) en HTTP.
 *
 * <ul>
 *   <li>Sans session : l'état de la liaison en cours (un indice masqué, les preuves possibles), puis l'une des trois
 *       preuves — mot de passe du compte existant, invitation, ou demande à l'administration. La liaison en cours
 *       est désignée par un témoin {@code HttpOnly}, {@code SameSite=Strict}, limité à ces routes : le jeton ne
 *       paraît ni dans une adresse ni dans un journal, et un site tiers ne peut pas le présenter.</li>
 *   <li>Administration de l'organisme : les demandes en attente, approuver, refuser.</li>
 * </ul>
 * Chaque issue est consignée ({@code auth.sso.lie}, {@code auth.sso.refus}, {@code auth.sso.liaison.*}), sans mot
 * de passe ni jeton.
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurLiaisonIdentite {

    /** Le témoin de la liaison en cours, et le chemin qui seul le reçoit. */
    public static final String COOKIE_LIAISON = "liaison_sso";
    static final String CHEMIN_LIAISON = "/api/auth/sso/liaison";
    /** La page de l'interface où la personne prouve la possession de son compte. */
    public static final String PAGE_RELIER = "/connexion/relier";
    private static final Duration DUREE_LIAISON = Duration.ofMinutes(10);
    private static final String RETOUR_PAR_DEFAUT = "/admin/dashboard";
    private static final int OCTETS_CSRF = 24;
    private static final String ROLE_ADMIN = "admin";
    private static final String ANONYME = "anonymous";
    private static final SecureRandom ALEA = new SecureRandom();

    /** Ce que le contrôleur emploie, regroupé : la construction en essai les fournit en une fois. */
    public record Dependances(ServiceResolutionIdentite resolution, ServiceApprobationsLiaison approbations, ServiceConnexion connexion,
                              JournalAudit audit, PareFeu pareFeu, GardeAcces garde, AdressePublique adresse, boolean cookieSecurise) {
    }

    private final Dependances d;

    @Autowired
    public ControleurLiaisonIdentite(ServiceResolutionIdentite resolution, ServiceApprobationsLiaison approbations, DepotComptes comptes,
                                     DepotQuestionsSecretes questions, JournalAudit audit, PareFeu pareFeu, GardeAcces garde, AdressePublique adresse,
                                     Clock horloge, @Value("${nexu.jwt.secret}") String secret, @Value("${nexu.cookie.secure:false}") boolean cookieSecurise) {
        this(new Dependances(resolution, approbations, new ServiceConnexion(comptes, questions, new JetonDeSession(secret), horloge), audit, pareFeu,
                garde, adresse, cookieSecurise));
    }

    public ControleurLiaisonIdentite(Dependances d) {
        this.d = d;
    }

    // ── Pour les retours des fournisseurs ───────────────────────────────────

    /** Le témoin qui désigne la liaison en cours ; vide et échu pour l'effacer. */
    static String cookieLiaison(String jeton, Duration duree, boolean securise) {
        return ResponseCookie.from(COOKIE_LIAISON, jeton).httpOnly(true).secure(securise).sameSite("Strict").path(CHEMIN_LIAISON).maxAge(duree)
                .build().toString();
    }

    /** Le retour d'un fournisseur sans identité reliée : vers la page « Relier mon compte », la liaison dans le témoin. */
    static ResponseEntity<Object> versRelier(String jeton, boolean securise) {
        return ResponseEntity.status(302).location(URI.create(PAGE_RELIER)).header(HttpHeaders.SET_COOKIE, cookieLiaison(jeton, DUREE_LIAISON, securise))
                .build();
    }

    /** La variante JSON (connexion LDAPS, SSO simulé) : l'interface navigue elle-même vers la page. */
    static ResponseEntity<Object> versRelierJson(String jeton, boolean securise) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookieLiaison(jeton, DUREE_LIAISON, securise))
                .body(Map.of("liaisonRequise", true, "retour", PAGE_RELIER));
    }

    /** La liaison exigée est consignée : une rafale d'identités inconnues est un signal à surveiller. */
    static void consignerLiaisonRequise(JournalAudit audit, String courriel, String protocole) {
        audit.consigner(ANONYME, IdentitesExternes.masquer(courriel), "auth.sso.liaison.requise", null, Map.of("protocole", protocole));
    }

    // ── Liaison en cours ────────────────────────────────────────────────────

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static int statut(IdentitesExternes.Motif motif) {
        return switch (motif) {
            case LIAISON_EXPIREE, DEMANDE_INTROUVABLE, COMPTE_INTROUVABLE -> 404;
            case TENTATIVES_EPUISEES -> 429;
            case COMPTE_INACTIF, ORGANISME_DIFFERENT, PREUVE_REFUSEE, AUCUN_CANDIDAT, DEJA_LIEE -> 403;
        };
    }

    private ResponseEntity<Object> tenter(String qui, Supplier<ResponseEntity<Object>> geste) {
        try {
            return geste.get();
        } catch (IdentitesExternes.LiaisonRefuseeException e) {
            d.audit().consigner(ANONYME, qui, "auth.sso.refus", null, Map.of("motif", e.motif().name()));
            return erreur(statut(e.motif()), e.getMessage());
        }
    }

    private static String jeton(HttpServletRequest requete) {
        return Optional.ofNullable(requete.getCookies()).stream().flatMap(Arrays::stream).filter(c -> COOKIE_LIAISON.equals(c.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    @GetMapping("/sso/liaison")
    public ResponseEntity<Object> etat(HttpServletRequest requete) {
        return tenter("liaison", () -> {
            ServiceResolutionIdentite.EtatLiaison e = d.resolution().etat(jeton(requete));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("indice", e.indice());
            r.put("candidat", e.candidat());
            r.put("demandeEnAttente", e.demandeEnAttente());
            r.put("essaisRestants", e.essaisRestants());
            return ResponseEntity.ok(r);
        });
    }

    private static Optional<String> texte(Map<String, Object> corps, String cle) {
        return Optional.ofNullable(corps).map(c -> c.get(cle)).filter(String.class::isInstance).map(String.class::cast);
    }

    /** Preuve (b) : le mot de passe du compte existant. Le verrou du pare-feu s'applique comme à la connexion. */
    @PostMapping("/sso/liaison/mot-de-passe")
    public ResponseEntity<Object> parMotDePasse(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        Optional<String> motDePasse = texte(corps, "motDePasse").filter(m -> !m.isEmpty());
        if (motDePasse.isEmpty()) {
            return erreur(400, "« motDePasse » est requis");
        }
        String identifiant = texte(corps, "identifiant").map(String::trim).orElse("");
        String cle = identifiant.isEmpty() ? COOKIE_LIAISON : identifiant;
        String ip = requete.getRemoteAddr();
        Optional<PareFeu.Refus> verrou = d.pareFeu().gardeConnexion(ip, cle);
        if (verrou.isPresent()) {
            return erreur(verrou.get().code(), verrou.get().message());
        }
        ResponseEntity<Object> r = tenter(cle, () -> ouvrir(d.resolution().prouverParMotDePasse(jeton(requete), identifiant, motDePasse.get())));
        if (r.getStatusCode().isError()) {
            d.pareFeu().echecConnexion(ip, cle);
        } else {
            d.pareFeu().connexionReussie(ip, cle);
        }
        return r;
    }

    /** Preuve (a) : le lien (ou le jeton) d'une invitation adressée au courriel vérifié. */
    @PostMapping("/sso/liaison/invitation")
    public ResponseEntity<Object> parInvitation(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        Optional<String> invitation = texte(corps, "invitation").filter(i -> !i.isBlank());
        if (invitation.isEmpty()) {
            return erreur(400, "« invitation » est requise : le lien reçu par courriel");
        }
        return tenter("invitation", () -> ouvrir(d.resolution().prouverParInvitation(jeton(requete), invitation.get())));
    }

    /** Preuve (c) : la demande à l'administration de l'organisme. Rien n'est relié avant sa décision. */
    @PostMapping("/sso/liaison/demande")
    public ResponseEntity<Object> demander(HttpServletRequest requete) {
        return tenter("demande", () -> {
            DemandesLiaison.Demande dem = d.resolution().demanderApprobation(jeton(requete));
            d.audit().consigner(ANONYME, IdentitesExternes.masquer(dem.courriel()), "auth.sso.liaison.demandee", dem.id(),
                    Map.of("tenantId", dem.tenantId()));
            return ResponseEntity.status(202).body(Map.of("demandeId", dem.id(),
                    "message", "Votre demande est transmise à l'administration de votre organisme. Reconnectez-vous une fois qu'elle l'aura approuvée."));
        });
    }

    /** La preuve acceptée : l'identité est reliée, la session s'ouvre comme à la connexion, le témoin de liaison s'efface. */
    private ResponseEntity<Object> ouvrir(ServiceResolutionIdentite.Liaison l) {
        Compte c = l.compte();
        Map<String, Object> lie = new LinkedHashMap<>();
        lie.put("preuve", l.preuve().code());
        lie.put("emetteur", l.identite().cle().emetteur().valeur());
        l.identite().provenance().annuaireId().ifPresent(a -> lie.put("annuaireId", a));
        d.audit().consigner(c.id(), c.nomDeConnexion(), "auth.sso.lie", null, lie);
        d.audit().consigner(c.id(), c.nomDeConnexion(), "auth.sso.login.ok", null, Map.of("role", c.role(), "protocole", "liaison"));
        byte[] b = new byte[OCTETS_CSRF];
        ALEA.nextBytes(b);
        String csrf = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        List<String> cookies = List.of(
                ControleurAuth.cookie(ControleurAuth.COOKIE_SESSION, d.connexion().emettre(c), true, ControleurAuth.DUREE_SESSION, d.cookieSecurise()).toString(),
                ControleurAuth.cookie(ControleurAuth.COOKIE_CSRF, csrf, false, ControleurAuth.DUREE_SESSION, d.cookieSecurise()).toString(),
                cookieLiaison("", Duration.ZERO, d.cookieSecurise()));
        String retour = d.adresse().pour(c.tenantId());
        return ResponseEntity.ok().headers(h -> h.put(HttpHeaders.SET_COOKIE, cookies))
                .body(Map.of("retour", retour.isEmpty() ? RETOUR_PAR_DEFAUT : retour, "role", c.role(), "preuve", l.preuve().code()));
    }

    // ── Administration ──────────────────────────────────────────────────────

    private ResponseEntity<Object> administrer(HttpServletRequest requete, Function<String, ResponseEntity<Object>> geste) {
        GardeAcces.Verdict v = d.garde().exigerRole(requete, ROLE_ADMIN);
        return v.refuse() ? v.refus() : tenter(GardeAcces.contexte(requete).userId(), () -> geste.apply(v.tenantId()));
    }

    private static Map<String, Object> vue(DemandesLiaison.Demande dem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", dem.id());
        m.put("courriel", dem.courriel());
        m.put("emetteur", dem.cle().emetteur().valeur());
        m.put("annuaireId", dem.annuaireId().orElse(null));
        m.put("compteId", dem.compteId());
        m.put("statut", dem.statut().code());
        m.put("creeLe", dem.creeLe().toString());
        return m;
    }

    @GetMapping("/liaisons")
    public ResponseEntity<Object> enAttente(HttpServletRequest requete) {
        return administrer(requete, t -> ResponseEntity.ok(Map.of("demandes", d.approbations().enAttente(t).stream().map(ControleurLiaisonIdentite::vue).toList())));
    }

    @PostMapping("/liaisons/{id}/approuver")
    public ResponseEntity<Object> approuver(HttpServletRequest requete, @PathVariable String id) {
        return administrer(requete, t -> decide(d.approbations().approuver(t, id, GardeAcces.contexte(requete).userId()), "auth.sso.liaison.approuvee", requete));
    }

    @PostMapping("/liaisons/{id}/refuser")
    public ResponseEntity<Object> refuser(HttpServletRequest requete, @PathVariable String id) {
        return administrer(requete, t -> decide(d.approbations().refuser(t, id, GardeAcces.contexte(requete).userId()), "auth.sso.liaison.refusee", requete));
    }

    private ResponseEntity<Object> decide(DemandesLiaison.Demande dem, String action, HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        d.audit().consigner(c.userId(), c.username(), action, dem.id(), Map.of("compteId", dem.compteId()));
        return ResponseEntity.ok(vue(dem));
    }
}
