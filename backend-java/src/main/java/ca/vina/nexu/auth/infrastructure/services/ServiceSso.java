package ca.vina.nexu.auth.infrastructure.services;

import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.domaine.Saml;
import ca.vina.nexu.integrations.domaine.MappageRole;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.demonstration.RegistreDemonstrations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Les deux SSO d'instance — Entra ID (OIDC) et SAML 2.0 — côté fournisseur de services : les demandes en
 * cours (state → PKCE, {@code InResponseTo}), l'obtention des revendications vérifiées, puis la résolution de
 * l'identité par {@code (émetteur, sujet)} (ADR 0093) : compte relié, provisionné si l'instance l'autorise
 * ({@code SSO_PROVISIONNEMENT_JAT}, ou démonstration et mode simulé, qui n'authentifient personne de réel), ou
 * liaison à prouver. L'état est celui du processus ; en multi-instance, un cache partagé.
 */
public class ServiceSso {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };
    /** Le provisionnement juste-à-temps du fournisseur d'instance : désactivé sauf demande explicite. */
    public static final String VARIABLE_JAT = "SSO_PROVISIONNEMENT_JAT";
    private static final Set<String> VRAI = Set.of("1", "true");
    private static final String ROLE_PAR_DEFAUT = "employee";

    /** L'échange du code contre les revendications vérifiées (réel et démonstration) : un port, pour que les essais le remplacent. */
    public interface EchangeurOidc {
        Map<String, Object> echanger(String code, Oidc.Configuration cfg, Oidc.Demande demande);
    }

    /** L'issue d'une connexion par le SSO d'instance : une session, ou une liaison à prouver. */
    public sealed interface Issue permits Session, LiaisonRequise {
    }

    public record Session(String token, String userId, String role, String email, boolean provisioned, String tenantId) implements Issue {
    }

    public record LiaisonRequise(String jetonLiaison, String email) implements Issue {
    }

    /** Ce que le fournisseur a prouvé : sa clé, ses revendications, et s'il est réel (sinon, démonstration ou simulé). */
    public record Verifiee(IdentitesExternes.Cle cle, Map<String, Object> revendications, boolean reel) {
    }

    public record DebutSaml(String url, String id) {
    }

    private final Map<String, String> env;
    private final RegistreDemonstrations demonstrations;
    private final ServiceResolutionIdentite resolution;
    private final ServiceConnexion connexion;
    private final EchangeurOidc echangeur;
    private final Clock horloge;
    private final RandomGenerator alea;
    private final Map<String, Oidc.Demande> demandesEntra = new ConcurrentHashMap<>();
    private final Map<String, Long> demandesSaml = new ConcurrentHashMap<>();

    public ServiceSso(Map<String, String> env, RegistreDemonstrations demonstrations, ServiceResolutionIdentite resolution, ServiceConnexion connexion,
                      EchangeurOidc echangeur, Clock horloge, RandomGenerator alea) {
        this.env = env;
        this.demonstrations = demonstrations;
        this.resolution = resolution;
        this.connexion = connexion;
        this.echangeur = echangeur;
        this.horloge = horloge;
        this.alea = alea;
    }

    // ── Entra ID ────────────────────────────────────────────────────────────

    /**
     * {@code origine} : l'adresse que le navigateur atteint pour l'hôte de la requête, vide hors déploiement par
     * sous-domaine. Le flux commence et finit sur le même hôte : le cookie de session n'est valable que là.
     */
    public Oidc.Configuration configurationEntra(Optional<String> origine) {
        Optional<Oidc.Demo> demo = Optional.empty();
        if (demonstrations.estBranchee("entra")) {
            Map<String, String> r = demonstrations.reglages("entra");
            demo = Optional.of(new Oidc.Demo(r.get("tenantId"), r.get("clientId"), r.get("clientSecret"), demonstrations.urlServeur(), demonstrations.urlApplication(), origine));
        }
        return Oidc.configuration(env, demo);
    }

    /** La configuration d'un Entra ID offert ; sinon {@link Oidc.DesactiveException} (404 à la frontière). */
    public Oidc.Configuration configurationEntraActive(Optional<String> origine) {
        Oidc.Configuration cfg = configurationEntra(origine);
        if (!cfg.actif()) {
            throw new Oidc.DesactiveException();
        }
        return cfg;
    }

    private void purgerEntra() {
        long maintenant = horloge.millis();
        demandesEntra.values().removeIf(d -> d.expiree(maintenant));
    }

    public Oidc.Debut debuterEntra(Optional<String> origine) {
        Oidc.Configuration cfg = configurationEntraActive(origine);
        purgerEntra();
        Oidc.Demande d = Oidc.nouvelleDemande(alea, horloge.millis());
        demandesEntra.put(d.state(), d);
        return Oidc.debuter(cfg, d);
    }

    /**
     * Valide le state, obtient les revendications : décodées du code en mode simulé seulement ; échangées et vérifiées
     * (JWKS, émetteur, audience, échéance, nonce) sinon. Un code simulé hors du mode simulé est refusé : ses
     * revendications sont écrites par qui le présente.
     */
    public Verifiee completerEntra(String code, String state, Optional<String> origine) {
        Oidc.Configuration cfg = configurationEntraActive(origine);
        purgerEntra();
        Oidc.Demande d = demandesEntra.remove(state);
        if (d == null) {
            throw new IllegalArgumentException("invalid or expired SSO state");
        }
        if (!cfg.mock() && Oidc.estCodeSimule(code)) {
            throw new Oidc.AuthentificationRefuseeException("code d'autorisation simulé refusé : le mode simulé n'est pas actif");
        }
        Map<String, Object> claims = cfg.mock() ? JSON.readValue(Oidc.jsonDuCodeSimule(code), CARTE) : echangeur.echanger(code, cfg, d);
        Oidc.courriel(claims);
        Object emetteur = cfg.mock() ? cfg.authority() : claims.get("iss");
        Object sujet = claims.get("sub") == null ? claims.get("oid") : claims.get("sub");
        return new Verifiee(IdentitesExternes.Cle.de(emetteur, sujet), claims, !cfg.mock() && !cfg.demonstration());
    }

    // ── SAML ────────────────────────────────────────────────────────────────

    public Saml.Configuration configurationSaml(Optional<String> origine) {
        Optional<Saml.Demo> demo = Optional.empty();
        if (demonstrations.estBranchee("saml")) {
            Map<String, String> r = demonstrations.reglages("saml");
            PublicKey cle = Primitives.surement("clé de l'IdP de démonstration", () -> java.security.KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(r.get("clePublique")))));
            demo = Optional.of(new Saml.Demo(r.get("issuer"), cle, demonstrations.urlServeur(), demonstrations.urlApplication(), origine));
        }
        return Saml.configuration(env, demo);
    }

    private void purgerSaml() {
        long maintenant = horloge.millis();
        demandesSaml.values().removeIf(creeLe -> maintenant - creeLe > Oidc.DUREE_DEMANDE_MS);
    }

    public DebutSaml debuterSaml(Optional<String> origine) {
        Saml.Configuration cfg = configurationSaml(origine);
        purgerSaml();
        byte[] b = new byte[16];
        alea.nextBytes(b);
        String id = "_" + HexFormat.of().formatHex(b);
        String url = Saml.urlDeConnexion(cfg, id, horloge.instant());
        demandesSaml.put(id, horloge.millis());
        return new DebutSaml(url, id);
    }

    /** Signature, émetteur, audience, validité et {@code InResponseTo} vérifiés ; la clé est (émetteur, NameID). */
    public Verifiee recevoirSaml(String samlResponseB64, Optional<String> origine) {
        purgerSaml();
        Saml.Configuration cfg = configurationSaml(origine);
        Saml.AssertionValidee a = Saml.recevoirReponse(samlResponseB64, cfg, horloge.instant(), demandesSaml::containsKey);
        demandesSaml.remove(a.inResponseTo());
        return new Verifiee(IdentitesExternes.Cle.de(cfg.idpIssuer(), a.claims().oid()), revendications(a.claims()), !cfg.demonstration());
    }

    public Map<String, Object> revendications(Saml.Revendications r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("email", r.email());
        m.put("name", r.name());
        m.put("roles", r.roles());
        m.put("oid", r.oid());
        return m;
    }

    // ── Résolution ──────────────────────────────────────────────────────────

    /** Le mappage des revendications ({@code roles}/{@code groups}) → rôle plateforme, surchargeable par {@code AZURE_ROLE_MAPPING} (JSON). */
    public MappageRole.Regle mappage() {
        String json = env.getOrDefault("AZURE_ROLE_MAPPING", "");
        if (!json.isEmpty()) {
            try {
                Map<String, Object> m = JSON.readValue(json, CARTE);
                @SuppressWarnings("unchecked") Map<String, String> table = (Map<String, String>) m.get("map");
                return new MappageRole.Regle(String.valueOf(m.get("claim")), table, m.get("fallback") == null ? null : String.valueOf(m.get("fallback")));
            } catch (RuntimeException e) {
                // JSON illisible : le défaut, comme côté Node
            }
        }
        return new MappageRole.Regle("roles", Oidc.MAPPAGE_PAR_DEFAUT, ROLE_PAR_DEFAUT);
    }

    /**
     * Résout l'identité vérifiée : session pour le compte relié (ou provisionné si l'instance l'autorise), sinon
     * liaison à prouver. Le rôle mappé ne s'applique qu'au compte provisionné : un compte existant garde le sien.
     */
    public Issue provisionner(Verifiee v) {
        String role = Optional.ofNullable(MappageRole.resoudre(v.revendications(), mappage())).orElse(ROLE_PAR_DEFAUT);
        String email = Oidc.courriel(v.revendications());
        boolean jat = !v.reel() || VRAI.contains(env.getOrDefault(VARIABLE_JAT, ""));
        IdentitesExternes.IdentiteVerifiee identite = new IdentitesExternes.IdentiteVerifiee(v.cle(), email, IdentitesExternes.Provenance.instance());
        return switch (resolution.resoudre(identite, new ServiceResolutionIdentite.Admission(Optional.of(role), false, jat))) {
            case ServiceResolutionIdentite.Reconnue r ->
                    new Session(connexion.emettre(r.compte()), r.compte().id(), r.compte().role(), email, r.cree(), r.compte().tenantId());
            case ServiceResolutionIdentite.LiaisonRequise l -> new LiaisonRequise(l.jeton(), email);
        };
    }

    /** Pour les essais : oublier les demandes en cours. */
    public void viderDemandes() {
        demandesEntra.clear();
        demandesSaml.clear();
    }

    public List<String> demandesSamlEnCours() {
        return List.copyOf(demandesSaml.keySet());
    }
}
