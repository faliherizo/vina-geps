package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.application.ServiceAnnuaires;
import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.EcritureAnnuaires;
import ca.vina.nexu.auth.domaine.RegistreAnnuaires;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.persistance.DepotAnnuaires;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.infrastructure.persistance.EchangeurOidcHttp;
import ca.vina.nexu.auth.infrastructure.persistance.LiaisonLdaps;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.PareFeu;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.net.ssl.SSLSocketFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * <strong>Plusieurs annuaires par organisme</strong> (AO 26-015-DO P-TN-2) en HTTP.
 *
 * <ul>
 *   <li>Connexion, sans session : la liste des annuaires actifs de l'organisme (de l'hôte, ADR 0033,
 *       sinon du paramètre {@code tenantId} de la page de connexion), le départ vers l'annuaire choisi,
 *       et le retour, qui pose la session comme la connexion par mot de passe. Un annuaire LDAPS reçoit
 *       l'identifiant et le mot de passe en JSON (ADR 0083) et pose la même session.</li>
 *   <li>Administration, par l'administrateur de l'organisme : lire, déclarer, modifier, retirer.
 *       Le secret n'est jamais lu ni rendu : seul le nom de sa variable, et s'il est déployé.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurAnnuaires {

    private static final SecureRandom ALEA = new SecureRandom();
    private static final Duration DELAI_ANNUAIRE = Duration.ofSeconds(10);
    private static final int OCTETS_CSRF = 24;
    private static final String RETOUR_PAR_DEFAUT = "/admin/dashboard";

    /** Ce que le contrôleur emploie, regroupé : la construction en essai les fournit en une fois. */
    public record Dependances(ServiceAnnuaires service, RegistreAnnuaires registre, EcritureAnnuaires ecriture, DepotRolesEtablissement roles,
                              GardeAcces garde, HoteEtablissement hote, AdressePublique adresse, JournalAudit audit, Sequence sequence,
                              Map<String, String> env, boolean cookieSecurise, PareFeu pareFeu) {
    }

    private final Dependances d;

    @Autowired
    public ControleurAnnuaires(DepotAnnuaires annuaires, DepotComptes comptes, DepotQuestionsSecretes questions, DepotRolesEtablissement roles,
                               GardeAcces garde, HoteEtablissement hote, AdressePublique adresse, JournalAudit audit, Sequence sequence, Clock horloge,
                               PareFeu pareFeu, @Value("${nexu.jwt.secret}") String secret, @Value("${nexu.cookie.secure:false}") boolean cookieSecurise) {
        this(new Dependances(new ServiceAnnuaires(new ServiceAnnuaires.Ports(annuaires, new AdaptateurComptesAnnuaire(comptes, ALEA),
                new ServiceConnexion(comptes, questions, new JetonDeSession(secret), horloge)::emettre,
                new EchangeurOidcHttp(HttpClient.newHttpClient(), DELAI_ANNUAIRE, horloge)::echanger,
                new EchangeurOidcHttp(HttpClient.newHttpClient(), DELAI_ANNUAIRE, horloge),
                new LiaisonLdaps((SSLSocketFactory) SSLSocketFactory.getDefault(), DELAI_ANNUAIRE)), System.getenv(), horloge, ALEA),
                annuaires, annuaires, roles, garde, hote, adresse, audit, sequence, System.getenv(), cookieSecurise, pareFeu));
    }

    public ControleurAnnuaires(Dependances d) {
        this.d = d;
    }

    private static ResponseEntity<Object> erreur(int code, List<String> messages) {
        return ResponseEntity.status(code).body(Map.of("errors", messages));
    }

    /** Les refus du domaine en statuts HTTP : chaque exception a le sien. */
    private static ResponseEntity<Object> tenter(Supplier<ResponseEntity<Object>> geste) {
        try {
            return geste.get();
        } catch (Annuaires.AnnuaireInvalideException e) {
            return erreur(400, e.motifs());
        } catch (Annuaires.ConnexionRefuseeException e) {
            return erreur(403, List.of(e.getMessage()));
        } catch (ServiceAnnuaires.IntrouvableException e) {
            return erreur(404, List.of(e.getMessage()));
        } catch (ServiceAnnuaires.ConfigurationIncompleteException e) {
            return erreur(503, List.of(e.getMessage()));
        }
    }

    // ── Connexion ───────────────────────────────────────────────────────────

    /** L'hôte désigne l'organisme quand il le peut ; le paramètre ne vaut que sur l'hôte commun. */
    private String organismeDeConnexion(HttpServletRequest requete, String tenantId) {
        String hote = d.hote().tenantDeLHote(requete);
        return hote != null ? hote : tenantId;
    }

    @GetMapping("/sso/annuaires")
    public ResponseEntity<Object> annuairesDeConnexion(HttpServletRequest requete, @RequestParam(name = "tenantId", required = false) String tenantId) {
        String organisme = organismeDeConnexion(requete, tenantId);
        List<Map<String, Object>> liste = organisme == null || organisme.isBlank() ? List.of()
                : d.service().actifs(organisme).stream().map(a -> Map.<String, Object>of("id", a.id(), "nom", a.nom(), "fournisseur", a.fournisseur().code()))
                .toList();
        return ResponseEntity.ok(Map.of("annuaires", liste));
    }

    private String origine(String tenantId) {
        String url = d.adresse().pour(tenantId);
        if (url.isEmpty()) {
            throw new ServiceAnnuaires.ConfigurationIncompleteException("l'adresse publique de l'application n'est pas déclarée (PUBLIC_APP_URL)");
        }
        return url;
    }

    @GetMapping("/sso/annuaires/{id}/login")
    public ResponseEntity<Object> connexion(@PathVariable String id) {
        return tenter(() -> {
            String tenantId = d.registre().parId(id).map(Annuaires.Annuaire::tenantId)
                    .orElseThrow(() -> new ServiceAnnuaires.IntrouvableException("annuaire introuvable"));
            return ResponseEntity.status(302).location(URI.create(d.service().debuter(id, origine(tenantId)))).build();
        });
    }

    /** Le retour de l'annuaire. Tout refus est consigné : une rafale de refus est un signal de sécurité. */
    @GetMapping("/sso/annuaires/callback")
    public ResponseEntity<Object> rappel(@RequestParam(name = "code", required = false) String code, @RequestParam(name = "state", required = false) String state) {
        ResponseEntity<Object> r;
        try {
            r = tenter(() -> ouvrir(d.service().completer(code == null ? "" : code, state == null ? "" : state)));
        } catch (IllegalStateException | IllegalArgumentException e) {
            // Échange du code, signature, émetteur, nonce ou courriel absent : l'annuaire n'a rien prouvé.
            r = erreur(401, List.of("l'annuaire n'a pas authentifié la connexion : " + e.getMessage()));
        }
        if (r.getStatusCode().isError()) {
            d.audit().consigner("anonymous", "annuaire", "auth.sso.annuaire.refus", null, Map.of("motif", String.valueOf(r.getBody())));
        }
        return r;
    }

    /**
     * La connexion par un annuaire LDAPS : identifiant et mot de passe, vérifiés par liaison. La session se pose comme
     * au retour d'un annuaire OIDC ; la réponse dit où aller. Tout refus est consigné, sans le mot de passe ; le verrou
     * du pare-feu s'applique comme à la connexion par mot de passe, pour qu'une rafale ne verrouille pas le compte AD.
     */
    @PostMapping("/sso/annuaires/{id}/connexion")
    public ResponseEntity<Object> connexionParLiaison(HttpServletRequest requete, @PathVariable String id,
                                                      @RequestBody(required = false) Map<String, Object> corps) {
        Object identifiant = corps == null ? null : corps.get("identifiant");
        Object motDePasse = corps == null ? null : corps.get("motDePasse");
        if (!(identifiant instanceof String i) || i.isBlank() || !(motDePasse instanceof String m) || m.isEmpty()) {
            return erreur(400, List.of("« identifiant » et « motDePasse » sont requis"));
        }
        String ip = requete.getRemoteAddr();
        Optional<PareFeu.Refus> verrou = d.pareFeu().gardeConnexion(ip, i.trim());
        if (verrou.isPresent()) {
            return erreur(verrou.get().code(), List.of(verrou.get().message()));
        }
        ResponseEntity<Object> r = tenter(() -> ouvrirEnJson(d.service().ouvrirParLiaison(id, i.trim(), m)));
        if (r.getStatusCode().isError()) {
            d.pareFeu().echecConnexion(ip, i.trim());
            d.audit().consigner("anonymous", i.trim(), "auth.sso.annuaire.refus", null, Map.of("motif", String.valueOf(r.getBody())));
        } else {
            d.pareFeu().connexionReussie(ip, i.trim());
        }
        return r;
    }

    private ResponseEntity<Object> ouvrirEnJson(ServiceAnnuaires.Ouverture o) {
        String retour = d.adresse().pour(o.compte().tenantId());
        return ResponseEntity.ok().headers(h -> h.put(HttpHeaders.SET_COOKIE, session(o)))
                .body(Map.of("retour", retour.isEmpty() ? RETOUR_PAR_DEFAUT : retour, "role", o.compte().role()));
    }

    private ResponseEntity<Object> ouvrir(ServiceAnnuaires.Ouverture o) {
        List<String> cookies = session(o);
        String retour = d.adresse().pour(o.compte().tenantId());
        return ResponseEntity.status(302).location(URI.create(retour.isEmpty() ? RETOUR_PAR_DEFAUT : retour))
                .headers(h -> h.put(HttpHeaders.SET_COOKIE, cookies)).build();
    }

    /** Les cookies de session et de CSRF, comme la connexion par mot de passe les pose ; la connexion est consignée. */
    private List<String> session(ServiceAnnuaires.Ouverture o) {
        byte[] b = new byte[OCTETS_CSRF];
        ALEA.nextBytes(b);
        String csrf = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("role", o.compte().role());
        details.put("provisioned", o.cree());
        details.put("protocole", "annuaire");
        d.audit().consigner(o.compte().id(), o.courriel(), "auth.sso.login.ok", null, details);
        return List.of(
                ControleurAuth.cookie(ControleurAuth.COOKIE_SESSION, o.jeton(), true, ControleurAuth.DUREE_SESSION, d.cookieSecurise()).toString(),
                ControleurAuth.cookie(ControleurAuth.COOKIE_CSRF, csrf, false, ControleurAuth.DUREE_SESSION, d.cookieSecurise()).toString());
    }

    // ── Administration ──────────────────────────────────────────────────────

    private Map<String, Object> vue(Annuaires.Annuaire a) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.id());
        v.put("nom", a.nom());
        v.put("fournisseur", a.fournisseur().code());
        v.put("locataireEntra", a.locataireEntra());
        v.put("emetteur", a.emetteur());
        v.put("clientId", a.clientId());
        v.put("variableSecret", a.variableSecret());
        v.put("secretDeploye", !d.env().getOrDefault(a.variableSecret(), "").isEmpty());
        v.put("domaines", a.domaines());
        v.put("revendication", a.revendication().code());
        v.put("correspondances", a.correspondances().stream().map(c -> Map.of("groupe", c.groupe(), "role", c.role())).toList());
        v.put("roleParDefaut", a.roleParDefaut().orElse(null));
        v.put("actif", a.actif());
        return v;
    }

    private ResponseEntity<Object> administrer(HttpServletRequest requete, Function<String, ResponseEntity<Object>> geste) {
        GardeAcces.Verdict v = d.garde().exigerRole(requete, "admin");
        return v.refuse() ? v.refus() : tenter(() -> geste.apply(v.tenantId()));
    }

    @GetMapping("/annuaires")
    public ResponseEntity<Object> lister(HttpServletRequest requete) {
        // L'adresse de retour est celle à inscrire dans l'inscription d'application de l'annuaire.
        return administrer(requete, t -> ResponseEntity.ok(Map.of("annuaires", d.registre().de(t).stream().map(this::vue).toList(),
                "rappel", d.adresse().pour(t) + ServiceAnnuaires.CHEMIN_RAPPEL)));
    }

    @PostMapping("/annuaires")
    public ResponseEntity<Object> declarer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return administrer(requete, t -> {
            Annuaires.Annuaire a = Annuaires.valider(d.sequence().suivant("ann"), t, SaisieAnnuaire.de(corps), new HashSet<>(d.roles().codesAttribuables(t)));
            d.ecriture().enregistrer(a, GardeAcces.contexte(requete).userId());
            return ResponseEntity.status(201).body(vue(a));
        });
    }

    private Annuaires.Annuaire duTenant(String tenantId, String id) {
        return d.registre().parId(id).filter(a -> a.tenantId().equals(tenantId))
                .orElseThrow(() -> new ServiceAnnuaires.IntrouvableException("annuaire introuvable"));
    }

    @PutMapping("/annuaires/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> corps) {
        return administrer(requete, t -> {
            duTenant(t, id);
            Annuaires.Annuaire a = Annuaires.valider(id, t, SaisieAnnuaire.de(corps), new HashSet<>(d.roles().codesAttribuables(t)));
            d.ecriture().enregistrer(a, GardeAcces.contexte(requete).userId());
            return ResponseEntity.ok(vue(a));
        });
    }

    @DeleteMapping("/annuaires/{id}")
    public ResponseEntity<Object> retirer(HttpServletRequest requete, @PathVariable String id) {
        return administrer(requete, t -> {
            duTenant(t, id);
            d.ecriture().retirer(t, id);
            return ResponseEntity.noContent().build();
        });
    }
}
