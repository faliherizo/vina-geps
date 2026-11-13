package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotAudit;
import ca.vina.nexu.auth.infrastructure.persistance.DepotCatalogueQuestions;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEmprunts;
import ca.vina.nexu.auth.infrastructure.persistance.DepotEtablissementsConnexion;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DroitDApprouver;
import ca.vina.nexu.auth.domaine.Emprunt;
import ca.vina.nexu.auth.domaine.PolitiqueMotDePasse;
import ca.vina.nexu.auth.domaine.RestrictionPaysConnexion;
import ca.vina.nexu.auth.domaine.QuestionsSecretes;
import ca.vina.nexu.auth.domaine.RolesEtablissement;
import ca.vina.nexu.auth.domaine.Siem;
import ca.vina.nexu.auth.domaine.EvenementAudit;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.PareFeu;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * {@code /api/auth} — sessions, comptes, politique de mot de passe et second facteur : la
 * première tranche du port de {@code backend/src/auth/routes.ts}. Mêmes chemins, mêmes codes,
 * mêmes messages ; le cookie {@code session} est celui que le Node pose, et réciproquement.
 *
 * <p>Ne sont <strong>pas</strong> dans cette tranche : le verrou de connexion du pare-feu
 * (le Node le tient tant que {@code /login} n'est pas basculé), les SSO Entra et SAML, les
 * passkeys, le profil (adossé au magasin de paie en mémoire), les rôles et le catalogue de
 * questions, l'audit et la rétention. Le README § Migration les liste.
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurAuth {

    static final String COOKIE_SESSION = "session";
    static final String COOKIE_CSRF = "csrf";
    static final Duration DUREE_SESSION = Duration.ofHours(8);
    private static final SecureRandom ALEA = new SecureRandom();

    private static final String ETABLISSEMENT_INCONNU = "organisme inconnu ou fermé";

    private final HoteEtablissement hote;
    private final PareFeu pareFeu;
    private final DepotComptes comptes;
    private final DepotQuestionsSecretes questions;
    private final DepotRolesEtablissement roles;
    private final DepotCatalogueQuestions catalogueQuestions;
    private final DepotAudit registre;
    private final DepotEtablissementsConnexion etablissements;
    private final DepotQuotas quotas;
    private final ServiceConnexion connexion;
    private final JournalAudit audit;
    private final GardeAcces garde;
    private final Clock horloge;
    private final boolean cookieSecurise;
    private final DepotEmprunts emprunts;
    private final RestrictionPaysConnexion restrictionPays;
    private final DroitDApprouver droitDApprouver;

    @Autowired
    public ControleurAuth(DepotComptes comptes, DepotQuestionsSecretes questions, DepotRolesEtablissement roles,
                          DepotCatalogueQuestions catalogueQuestions, DepotAudit registre, DepotEtablissementsConnexion etablissements,
                          DepotQuotas quotas, JournalAudit audit, GardeAcces garde, Clock horloge, PareFeu pareFeu, @Value("${nexu.jwt.secret}") String secret,
                          @Value("${nexu.cookie.secure:false}") boolean cookieSecurise, HoteEtablissement hote, DepotEmprunts emprunts,
                          RestrictionPaysConnexion restrictionPays, DroitDApprouver droitDApprouver) {
        this(comptes, questions, roles, catalogueQuestions, registre, etablissements, quotas, audit, garde, horloge, pareFeu,
                new ServiceConnexion(comptes, questions, new JetonDeSession(secret), horloge), cookieSecurise, hote, emprunts, restrictionPays,
                droitDApprouver);
    }

    /** Sans restriction de pays : les essais qui ne la visent pas. */
    ControleurAuth(DepotComptes comptes, DepotQuestionsSecretes questions, DepotRolesEtablissement roles,
                   DepotCatalogueQuestions catalogueQuestions, DepotAudit registre, DepotEtablissementsConnexion etablissements,
                   DepotQuotas quotas, JournalAudit audit, GardeAcces garde, Clock horloge, PareFeu pareFeu, ServiceConnexion connexion, boolean cookieSecurise,
                   HoteEtablissement hote, DepotEmprunts emprunts) {
        this(comptes, questions, roles, catalogueQuestions, registre, etablissements, quotas, audit, garde, horloge, pareFeu, connexion, cookieSecurise, hote, emprunts,
                RestrictionPaysConnexion.AUCUNE);
    }

    ControleurAuth(DepotComptes comptes, DepotQuestionsSecretes questions, DepotRolesEtablissement roles,
                   DepotCatalogueQuestions catalogueQuestions, DepotAudit registre, DepotEtablissementsConnexion etablissements,
                   DepotQuotas quotas, JournalAudit audit, GardeAcces garde, Clock horloge, PareFeu pareFeu, ServiceConnexion connexion, boolean cookieSecurise,
                   HoteEtablissement hote, DepotEmprunts emprunts, RestrictionPaysConnexion restrictionPays) {
        this(comptes, questions, roles, catalogueQuestions, registre, etablissements, quotas, audit, garde, horloge, pareFeu, connexion, cookieSecurise, hote,
                emprunts, restrictionPays, DroitDApprouver.PERSONNE);
    }

    ControleurAuth(DepotComptes comptes, DepotQuestionsSecretes questions, DepotRolesEtablissement roles,
                   DepotCatalogueQuestions catalogueQuestions, DepotAudit registre, DepotEtablissementsConnexion etablissements,
                   DepotQuotas quotas, JournalAudit audit, GardeAcces garde, Clock horloge, PareFeu pareFeu, ServiceConnexion connexion, boolean cookieSecurise,
                   HoteEtablissement hote, DepotEmprunts emprunts, RestrictionPaysConnexion restrictionPays, DroitDApprouver droitDApprouver) {
        this.droitDApprouver = droitDApprouver;
        this.restrictionPays = restrictionPays;
        this.emprunts = emprunts;
        this.hote = hote;
        this.pareFeu = pareFeu;
        this.comptes = comptes;
        this.questions = questions;
        this.roles = roles;
        this.catalogueQuestions = catalogueQuestions;
        this.registre = registre;
        this.etablissements = etablissements;
        this.quotas = quotas;
        this.audit = audit;
        this.garde = garde;
        this.horloge = horloge;
        this.connexion = connexion;
        this.cookieSecurise = cookieSecurise;
    }

    // ── Outils ────────────────────────────────────────────────────────────────────────────────

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    private static String texte(Map<String, Object> c, String cle) {
        Object v = c.get(cle);
        return v == null ? null : String.valueOf(v);
    }

    private static Map<String, Object> ok() {
        return Map.of("ok", true);
    }

    private static ResponseEntity<Object> repondre(GardeAcces.Verdict v, Supplier<ResponseEntity<Object>> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return geste.get();
        } catch (DepotComptes.IntrouvableException | DepotRolesEtablissement.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    /** {@code requireRole("admin")} : le superadmin passe, l'admin aussi, personne d'autre. */
    private GardeAcces.Verdict exigerAdmin(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return new GardeAcces.Verdict(null, erreur(401, "unauthenticated"));
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        return c.estSuperadmin() || "admin".equals(c.role()) ? v : new GardeAcces.Verdict(null, erreur(403, "insufficient role"));
    }

    /**
     * Tenant effectif du demandeur : le sien (jeton) d'abord ; l'en-tête {@code X-Tenant-Id} ne
     * vaut que pour un admin système sans établissement. Un admin rattaché ne cible jamais un autre.
     */
    static String tenantDuDemandeur(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        String t = c.tenantId() != null ? c.tenantId() : requete.getHeader("X-Tenant-Id");
        return t == null || t.isEmpty() ? null : t;
    }

    private static String enTeteTenant(HttpServletRequest requete) {
        String t = requete.getHeader("X-Tenant-Id");
        return t == null || t.trim().isEmpty() ? null : t.trim();
    }

    private void consigner(HttpServletRequest requete, String action, String cible, Map<String, Object> details) {
        ContexteRequete c = GardeAcces.contexte(requete);
        audit.consigner(c.userId(), c.username(), action, cible, details);
    }

    private ResponseCookie cookie(String nom, String valeur, boolean httpOnly, Duration duree) {
        return cookie(nom, valeur, httpOnly, duree, cookieSecurise);
    }

    /** Le cookie de session (ou de CSRF) tel que le Node le pose : {@code SameSite=Lax}, racine, huit heures. */
    static ResponseCookie cookie(String nom, String valeur, boolean httpOnly, Duration duree, boolean secure) {
        return ResponseCookie.from(nom, valeur).httpOnly(httpOnly).secure(secure).sameSite("Lax").path("/").maxAge(duree).build();
    }

    // ── Public : établissements et politique ──────────────────────────────────────────────────

    /**
     * Ce que le déploiement a résolu depuis l'hôte (ADR 0033) : le mode, la racine, et
     * l'établissement que l'hôte désigne. En sous-domaine, cette réponse fait foi pour l'écran de
     * connexion — jamais une lecture de {@code window.location} côté client.
     */
    @GetMapping("/deploiement")
    public ResponseEntity<Object> deploiement(HttpServletRequest requete) {
        HoteEtablissement.Resolution r = hote.de(requete);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("resolution", hote.configuration().mode().code());
        corps.put("domaineRacine", hote.configuration().domaineRacine());
        corps.put("hote", statutDe(r));
        corps.put("etablissement", r instanceof HoteEtablissement.Resolution.Etablissement e ? Map.of("id", e.id(), "nom", e.nom(), "slug", e.slug()) : null);
        if (r instanceof HoteEtablissement.Resolution.Inconnu || r instanceof HoteEtablissement.Resolution.HorsDomaine) {
            corps.put("errors", List.of(ETABLISSEMENT_INCONNU));
            return ResponseEntity.status(404).body(corps);
        }
        return ResponseEntity.ok(corps);
    }

    private static String statutDe(HoteEtablissement.Resolution r) {
        return switch (r) {
            case HoteEtablissement.Resolution.Selecteur s -> "selecteur";
            case HoteEtablissement.Resolution.Global g -> "global";
            case HoteEtablissement.Resolution.Inconnu i -> "inconnu";
            case HoteEtablissement.Resolution.HorsDomaine h -> "hors_domaine";
            case HoteEtablissement.Resolution.Etablissement e -> "etablissement";
        };
    }

    /**
     * La liste minimale des établissements. En sélecteur, publique : c'est le sélecteur de
     * connexion. En sous-domaine, la liste des clients ne se donne plus à un anonyme (404) ; un
     * compte rattaché ne voit que le sien, un compte global les voit tous.
     */
    @GetMapping("/tenants")
    public ResponseEntity<Object> tenants(HttpServletRequest requete) {
        boolean sousDomaine = hote.configuration().parSousDomaine();
        ContexteRequete c = GardeAcces.contexte(requete);
        if (sousDomaine && c == null) {
            return erreur(404, "aucun sélecteur d'organisme en déploiement par sous-domaine");
        }
        String seul = sousDomaine && c.tenantId() != null ? c.tenantId() : null;
        return ResponseEntity.ok(etablissements.selecteur().stream().filter(t -> seul == null || seul.equals(t.get("id"))).toList());
    }

    @GetMapping("/tenant-by-slug/{slug}")
    public ResponseEntity<Object> parSlug(@PathVariable String slug) {
        return etablissements.parSlug(slug).<ResponseEntity<Object>>map(ResponseEntity::ok).orElseGet(() -> erreur(404, "tenant not found"));
    }

    @GetMapping("/password-policy")
    public Map<String, Object> politique() {
        return Map.of("minLength", PolitiqueMotDePasse.LONGUEUR_MINIMALE);
    }

    @PostMapping("/password-policy/check")
    public Map<String, Object> verifierPolitique(@RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> corps = corps(c);
        return Map.of("issues", PolitiqueMotDePasse.manquements(texte(corps, "password"), texte(corps, "username")));
    }

    // ── Session ───────────────────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<Object> login(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> corps = corps(c);
        String username = texte(corps, "username");
        String password = texte(corps, "password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return erreur(400, "username and password required");
        }
        // Le verrou du pare-feu (exigence 32) : un compte verrouillé ne se tente même plus.
        var verrou = pareFeu.gardeConnexion(requete.getRemoteAddr(), username);
        if (verrou.isPresent()) {
            return erreur(verrou.get().code(), verrou.get().message());
        }
        String demande = enTeteTenant(requete) != null ? enTeteTenant(requete) : texte(corps, "tenantId");
        // En sous-domaine (ADR 0033), l'hôte est la seule autorité : un tenantId qui le contredit est
        // refusé, un slug inconnu ne connaît personne, et admin. n'ouvre qu'aux comptes globaux.
        HoteEtablissement.Resolution h = hote.de(requete);
        if (h instanceof HoteEtablissement.Resolution.Inconnu || h instanceof HoteEtablissement.Resolution.HorsDomaine) {
            return erreur(404, ETABLISSEMENT_INCONNU);
        }
        if (h instanceof HoteEtablissement.Resolution.Etablissement e && demande != null && !demande.isEmpty() && !demande.equals(e.id())) {
            return erreur(403, "tenant mismatch");
        }
        String tenantId = h instanceof HoteEtablissement.Resolution.Etablissement e ? e.id() : h instanceof HoteEtablissement.Resolution.Global ? null : demande;
        // P-TN-11 (lot R11) : un organisme peut restreindre les connexions à certains pays.
        Optional<String> refusPays = restrictionPays.refus(tenantId, requete::getHeader, username);
        if (refusPays.isPresent()) {
            audit.consigner(null, username, "auth.login.pays_refuse", username, null);
            return erreur(403, refusPays.get());
        }
        ServiceConnexion.Issue issue = connexion.connecter(username, password, tenantId, facteurs(corps), h instanceof HoteEtablissement.Resolution.Global);
        return switch (issue) {
            case ServiceConnexion.ChoixEtablissement choix -> choixEtablissement(username, choix);
            case ServiceConnexion.Orphelin o -> orphelin(username);
            case ServiceConnexion.Refus r -> refus(requete.getRemoteAddr(), username);
            case ServiceConnexion.SecondFacteur f -> secondFacteur(f);
            case ServiceConnexion.Succes s -> succes(requete.getRemoteAddr(), s);
        };
    }

    @SuppressWarnings("unchecked")
    private static ServiceConnexion.Facteurs facteurs(Map<String, Object> corps) {
        Object reponses = corps.get("securityAnswers");
        Object reponse = corps.get("securityAnswer");
        List<String> liste = reponses instanceof List<?> l ? (List<String>) l : reponse instanceof String s ? List.of(s) : List.of();
        Object code = corps.get("totpCode");
        return new ServiceConnexion.Facteurs(liste, code instanceof String s ? s : "");
    }

    private ResponseEntity<Object> choixEtablissement(String username, ServiceConnexion.ChoixEtablissement choix) {
        audit.consigner(null, username, "auth.login.tenant_choice", username, null);
        List<Map<String, Object>> tenants = choix.tenantIds().stream().map(id -> {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("id", id);
            t.put("name", etablissements.nom(id).orElse(id));
            return t;
        }).toList();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("tenantChoiceRequired", true);
        r.put("tenants", tenants);
        return ResponseEntity.ok(r);
    }

    private ResponseEntity<Object> orphelin(String username) {
        audit.consigner(null, username, "auth.login.orphan", username, null);
        return ResponseEntity.status(403).body(Map.of(
                "errors", List.of("Ce compte n'est rattaché à aucun établissement. Contactez un administrateur pour qu'il l'y rattache."),
                "orphanAccount", true));
    }

    private ResponseEntity<Object> refus(String ip, String username) {
        pareFeu.echecConnexion(ip, username);
        audit.consigner(null, username, "auth.login.fail", username, null);
        return erreur(401, "invalid credentials");
    }

    private ResponseEntity<Object> secondFacteur(ServiceConnexion.SecondFacteur f) {
        Compte u = f.compte();
        audit.consigner(u.id(), u.nomDeConnexion(), "auth.login.mfa.required", null, Map.of("method", f.methode()));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("mfaRequired", true);
        r.put("securityMethod", f.methode());
        if ("question".equals(f.methode())) {
            r.put("securityQuestions", f.questions());
        }
        r.put("mustChangePassword", u.mustChangePassword());
        r.put("securitySetupRequired", u.securitySetupRequired());
        return ResponseEntity.ok(r);
    }

    private ResponseEntity<Object> succes(String ip, ServiceConnexion.Succes s) {
        Compte u = s.compte();
        pareFeu.connexionReussie(ip, u.nomDeConnexion());
        audit.consigner(u.id(), u.nomDeConnexion(), "auth.login.ok", null, null);
        byte[] alea = new byte[24];
        ALEA.nextBytes(alea);
        String csrf = Base64.getUrlEncoder().withoutPadding().encodeToString(alea);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("token", s.jeton());
        r.put("user", identite(u, u.vue()));
        r.put("csrf", csrf);
        r.put("mustChangePassword", u.mustChangePassword());
        r.put("securitySetupRequired", u.securitySetupRequired());
        r.put("securityMethod", u.securityMethod());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(COOKIE_SESSION, s.jeton(), true, DUREE_SESSION).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(COOKIE_CSRF, csrf, false, DUREE_SESSION).toString())
                .body(r);
    }

    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c != null) {
            audit.consigner(c.userId(), c.username(), "auth.logout", null, null);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(COOKIE_SESSION, "", true, Duration.ZERO).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(COOKIE_CSRF, "", false, Duration.ZERO).toString())
                .body(ok());
    }

    @GetMapping("/whoami")
    public ResponseEntity<Object> whoami(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> {
            ContexteRequete c = GardeAcces.contexte(requete);
            Optional<Compte> u = comptes.parId(c.userId());
            String tenantId = u.map(Compte::tenantId).orElse(null) != null ? u.get().tenantId() : enTeteTenant(requete);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("authentifie", true);
            r.put("methode", aUnCookieDeSession(requete) ? "cookie" : "bearer");
            Map<String, Object> utilisateur = new LinkedHashMap<>();
            utilisateur.put("id", c.userId());
            utilisateur.put("username", c.username());
            utilisateur.put("role", c.role());
            utilisateur.put("tenantId", u.map(Compte::tenantId).orElse(null));
            utilisateur.put("employeeId", c.employeeId());
            // Aucune colonne `language` sur `users` : le Node renvoie toujours null ici.
            utilisateur.put("langue", null);
            r.put("utilisateur", utilisateur);
            r.put("locataire", locataire(tenantId));
            r.put("capacites", u.map(x -> roles.capacitesDe(x.tenantId(), x.role())).orElse(List.of()));
            r.put("instant", Iso.de(horloge.instant()));
            return ResponseEntity.ok(r);
        });
    }

    private static boolean aUnCookieDeSession(HttpServletRequest requete) {
        Cookie[] cookies = requete.getCookies();
        if (cookies == null) {
            return false;
        }
        for (Cookie c : cookies) {
            if (COOKIE_SESSION.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> locataire(String tenantId) {
        if (tenantId == null) {
            return null;
        }
        return etablissements.nom(tenantId).map(nom -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("id", tenantId);
            l.put("name", nom);
            l.put("modules", quotas.quota(tenantId).map(DepotQuotas.Quota::modules).orElse(List.of()));
            return l;
        }).orElse(null);
    }

    /**
     * Qui suis-je — la même forme à la connexion et à {@code /me} : les capacités et le droit
     * d'approuver accompagnent le profil. Sans eux à la connexion, la session se déroulait sur le
     * repli par rôle jusqu'au premier rechargement, et un encadrant au compte « employé » ne voyait
     * pas son menu d'approbation.
     */
    private Map<String, Object> identite(Compte u, Map<String, Object> vue) {
        Map<String, Object> r = new LinkedHashMap<>(vue);
        r.put("capabilities", roles.capacitesDe(u.tenantId(), u.role()));
        r.put("peutApprouver", droitDApprouver.peutApprouver(u.tenantId(), u.id(), u.role(), u.employeeId()));
        return r;
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> {
            ContexteRequete contexte = GardeAcces.contexte(requete);
            Compte u = comptes.parId(contexte.userId()).orElseThrow(DepotComptes.IntrouvableException::new);
            // Session empruntée (ADR 0038) : l'écran sait qui agit vraiment, et jusqu'à quand ; les portes de première connexion de la cible restent fermées.
            Map<String, Object> r = identite(u, contexte.estEmpruntee() ? Emprunt.sansPortesDePremiereConnexion(u.vue()) : u.vue());
            r.put("emprunt", emprunts.empruntDe(contexte));
            return ResponseEntity.ok(r);
        });
    }

    // ── Mot de passe ──────────────────────────────────────────────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<Object> changerMotDePasse(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerSession(requete), () -> {
            Map<String, Object> corps = corps(c);
            String ancien = texte(corps, "oldPassword");
            String nouveau = texte(corps, "newPassword");
            if (ancien == null || ancien.isEmpty() || nouveau == null || nouveau.isEmpty()) {
                return erreur(400, "oldPassword and newPassword required");
            }
            ContexteRequete ctx = GardeAcces.contexte(requete);
            if (!(connexion.connecter(ctx.username(), ancien, null, new ServiceConnexion.Facteurs(List.of(), "")) instanceof ServiceConnexion.Succes)) {
                return erreur(401, "wrong password");
            }
            PolitiqueMotDePasse.exiger(nouveau, ctx.username());
            comptes.modifier(ctx.userId(), new DepotComptes.Modification(null, null, nouveau, null, null, null, null));
            consigner(requete, "auth.password.change", null, null);
            return ResponseEntity.ok(ok());
        });
    }

    @PostMapping("/first-login/change-password")
    public ResponseEntity<Object> premierMotDePasse(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerSession(requete), () -> {
            ContexteRequete ctx = GardeAcces.contexte(requete);
            Compte u = comptes.parId(ctx.userId()).orElseThrow(DepotComptes.IntrouvableException::new);
            if (!u.mustChangePassword()) {
                return erreur(409, "password change not required");
            }
            List<String> manques = PolitiqueMotDePasse.manquements(texte(corps(c), "newPassword"), ctx.username());
            if (!manques.isEmpty()) {
                return ResponseEntity.status(400).body(Map.of("errors", List.of(PolitiqueMotDePasse.message(manques)), "passwordIssues", manques));
            }
            comptes.modifier(u.id(), new DepotComptes.Modification(null, null, texte(corps(c), "newPassword"), null, false, null, null));
            audit.consigner(u.id(), u.username(), "auth.first-login.password", null, null);
            return ResponseEntity.ok(ok());
        });
    }

    // ── Comptes (administration) ──────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public ResponseEntity<Object> utilisateurs(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            boolean superadmin = GardeAcces.contexte(requete).estSuperadmin();
            List<Compte> liste = superadmin ? comptes.lister() : comptes.listerParTenant(tenantDuDemandeur(requete));
            return ResponseEntity.ok(liste.stream().filter(u -> superadmin || !"superadmin".equals(u.role())).map(Compte::vue).toList());
        });
    }

    @PostMapping("/users")
    public ResponseEntity<Object> creerUtilisateur(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            Map<String, Object> corps = corps(c);
            String username = texte(corps, "username");
            String password = texte(corps, "password");
            String role = texte(corps, "role");
            if (username == null || password == null || role == null) {
                return erreur(400, "username, password, role required");
            }
            ContexteRequete ctx = GardeAcces.contexte(requete);
            List<String> attribuables = roles.codesAttribuables(ctx.tenantId());
            if (!attribuables.contains(role)) {
                return erreur(400, "invalid role (attendus : " + String.join(", ", attribuables) + ")");
            }
            boolean permanent = Boolean.FALSE.equals(corps.get("mustChangePassword"));
            if (!permanent && password.length() < 8) {
                return erreur(400, "temporary password must be at least 8 characters");
            }
            String tenantId = ctx.estSuperadmin() ? texte(corps, "tenantId") : tenantDuDemandeur(requete);
            Compte u = comptes.creer(new DepotComptes.Creation(username, password, role, texte(corps, "employeeId"), tenantId,
                    !permanent, true, null, permanent));
            consigner(requete, "auth.user.create", u.id(), Map.of("role", role));
            return ResponseEntity.status(201).body(u.vue());
        });
    }

    /** Un admin ne voit ni un superadmin ni un compte d'un autre établissement : 404, on ne divulgue rien. */
    private boolean horsDePortee(HttpServletRequest requete, Optional<Compte> cible) {
        ContexteRequete ctx = GardeAcces.contexte(requete);
        if (ctx.estSuperadmin() || cible.isEmpty()) {
            return false;
        }
        return "superadmin".equals(cible.get().role()) || !Objects.equals(cible.get().tenantId(), tenantDuDemandeur(requete));
    }

    @PatchMapping("/users/{id}")
    public ResponseEntity<Object> modifierUtilisateur(HttpServletRequest requete, @PathVariable String id,
                                                      @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            if (horsDePortee(requete, comptes.parId(id))) {
                return erreur(404, "user not found");
            }
            Compte u = comptes.modifier(id, modification(corps(c)));
            consigner(requete, "auth.user.update", id, null);
            return ResponseEntity.ok(u.vue());
        });
    }

    private static DepotComptes.Modification modification(Map<String, Object> corps) {
        return new DepotComptes.Modification(
                texte(corps, "role"),
                corps.get("active") instanceof Boolean b ? b : null,
                texte(corps, "password"),
                corps.containsKey("employeeId") ? Optional.ofNullable(texte(corps, "employeeId")) : null,
                corps.get("mustChangePassword") instanceof Boolean b ? b : null,
                corps.get("securitySetupRequired") instanceof Boolean b ? b : null,
                corps.containsKey("email") ? Optional.ofNullable(texte(corps, "email")) : null);
    }

    @PostMapping("/users/{id}/reset-security")
    public ResponseEntity<Object> reinitialiserSecurite(HttpServletRequest requete, @PathVariable String id) {
        return repondre(exigerAdmin(requete), () -> {
            Optional<Compte> cible = comptes.parId(id);
            if (cible.isEmpty() || horsDePortee(requete, cible)) {
                return erreur(404, "user not found");
            }
            questions.effacer(id);
            comptes.reinitialiserSecurite(id);
            consigner(requete, "auth.security.reset", id, null);
            return ResponseEntity.ok(Map.of("ok", true, "userId", id, "securitySetupRequired", true));
        });
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Object> supprimerUtilisateur(HttpServletRequest requete, @PathVariable String id) {
        return repondre(exigerAdmin(requete), () -> {
            if (GardeAcces.contexte(requete).userId().equals(id)) {
                return erreur(400, "cannot delete self");
            }
            if (horsDePortee(requete, comptes.parId(id))) {
                return ResponseEntity.status(404).build();
            }
            boolean fait = comptes.supprimer(id);
            if (fait) {
                consigner(requete, "auth.user.delete", id, null);
            }
            return ResponseEntity.status(fait ? 204 : 404).build();
        });
    }

    // ── Rôles et accès de l'établissement ─────────────────────────────────────────────────────

    private List<Map<String, Object>> catalogue(boolean avecRoles) {
        return acces().politique().capacites().stream().map(c -> {
            var regle = acces().politique().regle(c);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("capability", c);
            m.put("libelle", regle.libelle());
            if (avecRoles) {
                m.put("roles", regle.roles());
            }
            m.put("portee", regle.portee());
            return m;
        }).toList();
    }

    private RegistrePolitiqueAcces acces() {
        return roles.politique();
    }

    @GetMapping("/assignable-roles")
    public ResponseEntity<Object> rolesAttribuables(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> ResponseEntity.ok(
                roles.lister(GardeAcces.contexte(requete).tenantId()).stream().filter(RolesEtablissement.Role::active).map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("role", r.code());
                    m.put("label", r.label());
                    m.put("builtin", r.builtin());
                    m.put("capabilities", r.capabilities());
                    return m;
                }).toList()));
    }

    @GetMapping("/roles")
    public ResponseEntity<Object> rolesDeLEtablissement(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("roles", roles.lister(GardeAcces.contexte(requete).tenantId()).stream().map(RolesEtablissement.Role::vue).toList());
            r.put("catalog", catalogue(false));
            return ResponseEntity.ok(r);
        });
    }

    /** Un rôle appartient à un établissement : l'admin système sans établissement n'en définit pas. */
    private static ResponseEntity<Object> sansEtablissement() {
        return erreur(400, "un rôle appartient à un établissement");
    }

    @PostMapping("/roles")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> creerRole(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            String tenantId = GardeAcces.contexte(requete).tenantId();
            if (tenantId == null) {
                return sansEtablissement();
            }
            Map<String, Object> corps = corps(c);
            List<String> capacites = corps.get("capabilities") instanceof List<?> l ? (List<String>) l : List.of();
            return ResponseEntity.status(201).body(roles.creer(tenantId, texte(corps, "code"), texte(corps, "label"), capacites).vue());
        });
    }

    @PatchMapping("/roles/{code}")
    public ResponseEntity<Object> modifierRole(HttpServletRequest requete, @PathVariable String code, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            String tenantId = GardeAcces.contexte(requete).tenantId();
            if (tenantId == null) {
                return sansEtablissement();
            }
            Map<String, Object> corps = corps(c);
            return ResponseEntity.ok(roles.modifier(tenantId, code, texte(corps, "label"), corps.get("active") instanceof Boolean b ? b : null).vue());
        });
    }

    @DeleteMapping("/roles/{code}")
    public ResponseEntity<Object> supprimerRole(HttpServletRequest requete, @PathVariable String code) {
        return repondre(exigerAdmin(requete), () -> {
            String tenantId = GardeAcces.contexte(requete).tenantId();
            if (tenantId == null) {
                return sansEtablissement();
            }
            roles.supprimer(tenantId, code);
            return ResponseEntity.status(204).build();
        });
    }

    @PutMapping("/roles/{code}/capabilities/{capability}")
    public ResponseEntity<Object> definirCapacite(HttpServletRequest requete, @PathVariable String code, @PathVariable String capability,
                                                  @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            String tenantId = GardeAcces.contexte(requete).tenantId();
            if (tenantId == null) {
                return sansEtablissement();
            }
            roles.definirCapacite(tenantId, code, capability, !Boolean.FALSE.equals(corps(c).get("allowed")));
            return ResponseEntity.ok(roles.role(tenantId, code).vue());
        });
    }

    @GetMapping("/capabilities")
    public ResponseEntity<Object> capacites(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> {
            ContexteRequete ctx = GardeAcces.contexte(requete);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("role", ctx.role());
            r.put("mine", roles.capacitesDe(ctx.tenantId(), ctx.role()));
            r.put("catalog", catalogue(true));
            return ResponseEntity.ok(r);
        });
    }

    // ── Catalogue des questions secrètes ──────────────────────────────────────────────────────

    /** Questions proposées à l'utilisateur connecté : globales ∪ celles de son établissement. */
    @GetMapping("/security/questions")
    public ResponseEntity<Object> questionsProposees(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () ->
                ResponseEntity.ok(Map.of("questions", catalogueQuestions.effectives(GardeAcces.contexte(requete).tenantId()))));
    }

    /** La portée d'un geste sur le catalogue, ou {@code null} sans portée (admin sans établissement). */
    private record Portee(String tenantId, boolean interdite) {
    }

    private static Portee portee(HttpServletRequest requete, Map<String, Object> corps) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c.estSuperadmin()) {
            String demande = requete.getParameter("tenantId") != null ? requete.getParameter("tenantId") : texte(corps, "tenantId");
            return new Portee(demande == null || demande.isEmpty() || "global".equals(demande) ? null : demande, false);
        }
        String t = tenantDuDemandeur(requete);
        return t == null ? new Portee(null, true) : new Portee(t, false);
    }

    private static ResponseEntity<Object> sansPortee() {
        return erreur(403, "aucun établissement associé");
    }

    /** Un admin non superadmin n'agit que sur une ligne de son établissement. */
    private static boolean peutGerer(HttpServletRequest requete, DepotCatalogueQuestions.Question ligne) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return c.estSuperadmin() || (ligne.tenantId() != null && ligne.tenantId().equals(tenantDuDemandeur(requete)));
    }

    @GetMapping("/security/catalog")
    public ResponseEntity<Object> catalogueQuestions(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            Portee p = portee(requete, Map.of());
            if (p.interdite()) {
                return sansPortee();
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("scope", p.tenantId() == null ? "global" : p.tenantId());
            r.put("questions", catalogueQuestions.lister(p.tenantId()).stream().map(DepotCatalogueQuestions.Question::vue).toList());
            return ResponseEntity.ok(r);
        });
    }

    @PostMapping("/security/catalog")
    public ResponseEntity<Object> ajouterQuestion(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            Portee p = portee(requete, corps(c));
            if (p.interdite()) {
                return sansPortee();
            }
            DepotCatalogueQuestions.Question q = catalogueQuestions.ajouter(p.tenantId(), texte(corps(c), "question"));
            consigner(requete, "auth.security.catalog.add", null, Map.of("scope", p.tenantId() == null ? "global" : p.tenantId(), "id", q.id()));
            return ResponseEntity.status(201).body(q.vue());
        });
    }

    @PatchMapping("/security/catalog/{id}")
    public ResponseEntity<Object> modifierQuestion(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            Optional<DepotCatalogueQuestions.Question> ligne = catalogueQuestions.parId(id);
            if (ligne.isEmpty()) {
                return erreur(404, "question introuvable");
            }
            if (!peutGerer(requete, ligne.get())) {
                return erreur(403, "hors de votre établissement");
            }
            Map<String, Object> corps = corps(c);
            return ResponseEntity.ok(catalogueQuestions.modifier(ligne.get(), texte(corps, "question"), corps.get("active") instanceof Boolean b ? b : null).vue());
        });
    }

    @DeleteMapping("/security/catalog/{id}")
    public ResponseEntity<Object> supprimerQuestion(HttpServletRequest requete, @PathVariable String id) {
        return repondre(exigerAdmin(requete), () -> {
            Optional<DepotCatalogueQuestions.Question> ligne = catalogueQuestions.parId(id);
            if (ligne.isEmpty()) {
                return erreur(404, "question introuvable");
            }
            if (!peutGerer(requete, ligne.get())) {
                return erreur(403, "hors de votre établissement");
            }
            return ResponseEntity.status(catalogueQuestions.supprimer(id) ? 204 : 404).build();
        });
    }

    // ── Journal d'audit : lecture, SIEM, intégrité, revue, rétention ─────────────────────────

    private static String parametre(HttpServletRequest requete, String nom) {
        String v = requete.getParameter(nom);
        return v == null || v.isEmpty() ? null : v;
    }

    @GetMapping("/audit/siem-export")
    public ResponseEntity<Object> exportSiem(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            String demande = parametre(requete, "format");
            String format = demande != null && Siem.FORMATS.contains(demande) ? demande : "cef";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audit-siem." + format + "\"")
                    .contentType(new org.springframework.http.MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                    .body(Siem.exporter(registre.lister(parametre(requete, "action"), 5000), format));
        });
    }

    @GetMapping("/security/siem-console")
    public ResponseEntity<Object> consoleSiem(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            String demande = parametre(requete, "minLevel");
            String minimum = demande != null && Siem.NIVEAUX.contains(demande) ? demande : null;
            return ResponseEntity.ok(Siem.console(registre.lister(parametre(requete, "action"), 5000), minimum, 500));
        });
    }

    @PostMapping("/security/retention/purge")
    public ResponseEntity<Object> purger(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            Object brut = corps(c).get("retentionDays");
            double jours = brut instanceof Number n ? n.doubleValue() : nombre(brut);
            Map<String, Object> r = registre.purger(jours);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("purged", r.get("purged"));
            details.put("retentionDays", r.get("retentionDays"));
            consigner(requete, "security.retention.purge", (String) r.get("cutoff"), details);
            return ResponseEntity.ok(r);
        });
    }

    /** {@code Number(x)} du Node : une chaîne numérique passe, le reste vaut NaN. */
    private static double nombre(Object brut) {
        if (brut == null) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(String.valueOf(brut).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    @PostMapping("/security/retention/erase-user")
    public ResponseEntity<Object> effacerPersonne(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(exigerAdmin(requete), () -> {
            Map<String, Object> r = registre.effacer(texte(corps(c), "username"));
            consigner(requete, "security.retention.erase", (String) r.get("username"), Map.of("anonymized", r.get("anonymized")));
            return ResponseEntity.ok(r);
        });
    }

    @GetMapping("/audit/integrite")
    public ResponseEntity<Object> integrite(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            Map<String, Object> r = new LinkedHashMap<>(registre.integrite());
            r.put("revue", registre.echeance());
            return ResponseEntity.ok(r);
        });
    }

    @GetMapping("/audit/revues")
    public ResponseEntity<Object> revues(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> ResponseEntity.ok(registre.revues()));
    }

    /** La revue se consigne par le superadmin, indépendant de l'établissement qu'il lit. */
    @PostMapping("/audit/revues")
    public ResponseEntity<Object> consignerRevue(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return erreur(401, "unauthenticated");
        }
        ContexteRequete ctx = GardeAcces.contexte(requete);
        if (!ctx.estSuperadmin()) {
            return erreur(403, "insufficient role");
        }
        return repondre(v, () -> {
            Map<String, Object> corps = corps(c);
            return ResponseEntity.status(201).body(registre.consignerRevue(ctx.userId(), ctx.username(), texte(corps, "du"), texte(corps, "au"), texte(corps, "constat")));
        });
    }

    /**
     * Le journal, enrichi de l'établissement de chaque événement (résolu par le compte) et
     * cloisonné : un admin ne lit que l'audit des comptes de son établissement. Pagination
     * opt-in par {@code ?page=} ; sinon un tableau, borné par {@code ?limit=}.
     */
    @GetMapping("/audit")
    public ResponseEntity<Object> journal(HttpServletRequest requete) {
        return repondre(exigerAdmin(requete), () -> {
            ContexteRequete ctx = GardeAcces.contexte(requete);
            Map<String, Compte> parId = new LinkedHashMap<>();
            Map<String, Compte> parNom = new LinkedHashMap<>();
            for (Compte u : comptes.lister()) {
                parId.put(u.id(), u);
                parNom.put(u.nomDeConnexion(), u);
            }
            Map<String, String> noms = new LinkedHashMap<>();
            String q = parametre(requete, "q") == null ? null : parametre(requete, "q").toLowerCase(java.util.Locale.ROOT).trim();
            String tenantDemandeur = tenantDuDemandeur(requete);
            List<Map<String, Object>> lignes = new java.util.ArrayList<>();
            for (EvenementAudit e : registre.lister(parametre(requete, "action"), 5000)) {
                Compte u = e.userId() != null ? parId.get(e.userId()) : null;
                if (u == null && e.username() != null) {
                    u = parNom.get(e.username());
                }
                String tenantId = u == null ? null : u.tenantId();
                if (!ctx.estSuperadmin() && !Objects.equals(tenantId, tenantDemandeur)) {
                    continue;
                }
                String tenantNom = tenantId == null ? null : noms.computeIfAbsent(tenantId, t -> etablissements.nom(t).orElse(t));
                if (q != null && !q.isEmpty() && java.util.stream.Stream.of(e.username(), e.action(), e.target(), tenantNom)
                        .noneMatch(x -> String.valueOf(x == null ? "" : x).toLowerCase(java.util.Locale.ROOT).contains(q))) {
                    continue;
                }
                Map<String, Object> ligne = e.vue();
                ligne.put("tenantId", tenantId);
                ligne.put("tenantName", tenantNom);
                lignes.add(ligne);
            }
            return ResponseEntity.ok(paginer(requete, lignes));
        });
    }

    private static Object paginer(HttpServletRequest requete, List<Map<String, Object>> lignes) {
        if (requete.getParameter("page") != null) {
            int page = (int) Math.max(0, Double.isNaN(nombre(requete.getParameter("page"))) ? 0 : nombre(requete.getParameter("page")));
            double taille = nombre(requete.getParameter("pageSize"));
            int pageSize = (int) Math.min(200, Math.max(1, Double.isNaN(taille) || taille == 0 ? 20 : taille));
            int debut = Math.min(page * pageSize, lignes.size());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("items", lignes.subList(debut, Math.min(debut + pageSize, lignes.size())));
            r.put("total", lignes.size());
            r.put("page", page);
            r.put("pageSize", pageSize);
            return r;
        }
        double limite = nombre(requete.getParameter("limit"));
        return Double.isNaN(limite) || limite <= 0 ? lignes : lignes.subList(0, (int) Math.min(limite, lignes.size()));
    }

    // ── Second facteur ────────────────────────────────────────────────────────────────────────

    @GetMapping("/security/status")
    public ResponseEntity<Object> etatSecurite(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> {
            Compte u = comptes.parId(GardeAcces.contexte(requete).userId()).orElseThrow(DepotComptes.IntrouvableException::new);
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("mustChangePassword", u.mustChangePassword());
            r.put("securitySetupRequired", u.securitySetupRequired());
            r.put("securityMethod", u.securityMethod());
            r.put("hasSecurityQuestion", questions.enACompletes(u.id()));
            r.put("securityQuestionsCount", questions.compter(u.id()));
            r.put("minSecurityQuestions", QuestionsSecretes.MINIMUM);
            return ResponseEntity.ok(r);
        });
    }

    @PostMapping("/security/setup")
    public ResponseEntity<Object> configurerSecurite(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerSession(requete), () -> {
            Compte u = comptes.parId(GardeAcces.contexte(requete).userId()).orElseThrow(DepotComptes.IntrouvableException::new);
            Map<String, Object> corps = corps(c);
            String methode = texte(corps, "method");
            if ("question".equals(methode)) {
                return configurerQuestions(u, corps.get("questions"));
            }
            if ("totp".equals(methode)) {
                return configurerTotp(u, texte(corps, "code"));
            }
            return erreur(400, "method must be 'question' or 'totp'");
        });
    }

    private ResponseEntity<Object> configurerQuestions(Compte u, Object brut) {
        if (!(brut instanceof List<?> liste) || liste.size() < QuestionsSecretes.MINIMUM) {
            return erreur(400, "at least " + QuestionsSecretes.MINIMUM + " security questions are required");
        }
        questions.definir(u.id(), liste.stream().map(ControleurAuth::paire).toList());
        comptes.activerMethodeQuestion(u.id());
        audit.consigner(u.id(), u.username(), "auth.security.setup", null, Map.of("method", "question", "count", liste.size()));
        return ResponseEntity.ok(Map.of("ok", true, "method", "question", "count", liste.size()));
    }

    private static QuestionsSecretes.Paire paire(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return null;
        }
        Object q = m.get("question");
        Object r = m.get("answer");
        return new QuestionsSecretes.Paire(q == null ? null : String.valueOf(q), r == null ? null : String.valueOf(r));
    }

    private ResponseEntity<Object> configurerTotp(Compte u, String code) {
        if (u.pendingTotpSecret() == null) {
            return erreur(409, "call /security/totp/init first");
        }
        if (!Totp.verifier(u.pendingTotpSecret(), code == null ? "" : code, horloge.millis())) {
            return erreur(400, "invalid TOTP code");
        }
        comptes.activerMethodeTotp(u.id(), u.pendingTotpSecret());
        audit.consigner(u.id(), u.username(), "auth.security.setup", null, Map.of("method", "totp"));
        return ResponseEntity.ok(Map.of("ok", true, "method", "totp"));
    }

    @PostMapping("/security/verify")
    public ResponseEntity<Object> verifierSecurite(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(garde.exigerSession(requete), () -> {
            Compte u = comptes.parId(GardeAcces.contexte(requete).userId()).orElseThrow(DepotComptes.IntrouvableException::new);
            if (u.securityMethod() == null) {
                return erreur(409, "security method not configured");
            }
            Map<String, Object> corps = corps(c);
            boolean juste;
            if ("question".equals(u.securityMethod())) {
                Object reponses = corps.get("answers");
                Object reponse = corps.get("answer");
                juste = questions.verifier(u.id(), reponses instanceof List<?> l ? l.stream().map(String::valueOf).toList()
                        : reponse instanceof String s ? List.of(s) : List.of());
            } else if ("totp".equals(u.securityMethod())) {
                String code = texte(corps, "code");
                juste = u.totpSecret() != null && Totp.verifier(u.totpSecret(), code == null ? "" : code, horloge.millis());
            } else {
                return erreur(500, "unknown security method");
            }
            audit.consigner(u.id(), u.username(), juste ? "auth.security.verify.ok" : "auth.security.verify.fail", null, null);
            return ResponseEntity.ok(Map.of("ok", juste));
        });
    }

    @GetMapping("/security/question")
    public ResponseEntity<Object> questionsDuCompte(HttpServletRequest requete) {
        return repondre(garde.exigerSession(requete), () -> {
            Optional<Compte> u = comptes.parId(GardeAcces.contexte(requete).userId());
            if (u.isEmpty() || !"question".equals(u.get().securityMethod())) {
                return erreur(404, "no question configured");
            }
            return ResponseEntity.ok(Map.of("questions", questions.questions(u.get().id())));
        });
    }
}
