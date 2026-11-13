package ca.vina.nexu.auth.infrastructure.services;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;

import ca.vina.nexu.auth.domaine.Compte;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Les deux SSO — Entra ID (OIDC) et SAML 2.0 — côté fournisseur de services : les demandes en
 * cours (state → PKCE, {@code InResponseTo}), l'obtention des revendications, et le
 * provisionnement : trouver ou créer le compte par courriel, rôle par mappage, jeton de session.
 * L'état est celui du processus, comme côté Node ; en multi-instance, un cache partagé.
 */
public class ServiceSso {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };

    /** L'échange du code contre les revendications vérifiées (réel et démonstration) : un port, pour que les essais le remplacent. */
    public interface EchangeurOidc {
        Map<String, Object> echanger(String code, Oidc.Configuration cfg, Oidc.Demande demande);
    }

    public record Session(String token, String userId, String role, String email, boolean provisioned, String tenantId) {
    }

    public record DebutSaml(String url, String id) {
    }

    private final Map<String, String> env;
    private final RegistreDemonstrations demonstrations;
    private final DepotComptes comptes;
    private final ServiceConnexion connexion;
    private final EchangeurOidc echangeur;
    private final Clock horloge;
    private final RandomGenerator alea;
    private final Map<String, Oidc.Demande> demandesEntra = new ConcurrentHashMap<>();
    private final Map<String, Long> demandesSaml = new ConcurrentHashMap<>();

    public ServiceSso(Map<String, String> env, RegistreDemonstrations demonstrations, DepotComptes comptes, ServiceConnexion connexion, EchangeurOidc echangeur, Clock horloge, RandomGenerator alea) {
        this.env = env;
        this.demonstrations = demonstrations;
        this.comptes = comptes;
        this.connexion = connexion;
        this.echangeur = echangeur;
        this.horloge = horloge;
        this.alea = alea;
    }

    // ── Entra ID ────────────────────────────────────────────────────────────

    public Oidc.Configuration configurationEntra() {
        Optional<Oidc.Demo> demo = Optional.empty();
        if (demonstrations.estBranchee("entra")) {
            Map<String, String> r = demonstrations.reglages("entra");
            demo = Optional.of(new Oidc.Demo(r.get("tenantId"), r.get("clientId"), r.get("clientSecret"), demonstrations.urlServeur(), demonstrations.urlApplication()));
        }
        return Oidc.configuration(env, demo);
    }

    private void purgerEntra() {
        long maintenant = horloge.millis();
        demandesEntra.values().removeIf(d -> d.expiree(maintenant));
    }

    public Oidc.Debut debuterEntra() {
        purgerEntra();
        Oidc.Demande d = Oidc.nouvelleDemande(alea, horloge.millis());
        demandesEntra.put(d.state(), d);
        return Oidc.debuter(configurationEntra(), d);
    }

    /** Valide le state, obtient les revendications (simulé : décodage ; réel et démonstration : échange + JWKS). */
    public Map<String, Object> completerEntra(String code, String state) {
        purgerEntra();
        Oidc.Demande d = demandesEntra.remove(state);
        if (d == null) {
            throw new IllegalArgumentException("invalid or expired SSO state");
        }
        Oidc.Configuration cfg = configurationEntra();
        Map<String, Object> claims = cfg.mock() || Oidc.estCodeSimule(code) ? JSON.readValue(Oidc.jsonDuCodeSimule(code), CARTE) : echangeur.echanger(code, cfg, d);
        Oidc.courriel(claims);
        return claims;
    }

    // ── SAML ────────────────────────────────────────────────────────────────

    public Saml.Configuration configurationSaml() {
        Optional<Saml.Demo> demo = Optional.empty();
        if (demonstrations.estBranchee("saml")) {
            Map<String, String> r = demonstrations.reglages("saml");
            PublicKey cle = Primitives.surement("clé de l'IdP de démonstration", () -> java.security.KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(r.get("clePublique")))));
            demo = Optional.of(new Saml.Demo(r.get("issuer"), cle, demonstrations.urlServeur(), demonstrations.urlApplication()));
        }
        return Saml.configuration(env, demo);
    }

    private void purgerSaml() {
        long maintenant = horloge.millis();
        demandesSaml.values().removeIf(creeLe -> maintenant - creeLe > Oidc.DUREE_DEMANDE_MS);
    }

    public DebutSaml debuterSaml() {
        Saml.Configuration cfg = configurationSaml();
        purgerSaml();
        byte[] b = new byte[16];
        alea.nextBytes(b);
        String id = "_" + HexFormat.of().formatHex(b);
        String url = Saml.urlDeConnexion(cfg, id, horloge.instant());
        demandesSaml.put(id, horloge.millis());
        return new DebutSaml(url, id);
    }

    public Saml.AssertionValidee recevoirSaml(String samlResponseB64) {
        purgerSaml();
        Saml.AssertionValidee a = Saml.recevoirReponse(samlResponseB64, configurationSaml(), horloge.instant(), demandesSaml::containsKey);
        demandesSaml.remove(a.inResponseTo());
        return a;
    }

    public Map<String, Object> revendications(Saml.Revendications r) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("email", r.email());
        m.put("name", r.name());
        m.put("roles", r.roles());
        m.put("oid", r.oid());
        return m;
    }

    // ── Provisionnement ─────────────────────────────────────────────────────

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
        return new MappageRole.Regle("roles", Oidc.MAPPAGE_PAR_DEFAUT, "employee");
    }

    /** Provisionne (ou retrouve) l'utilisateur SSO, applique le rôle mappé et émet un jeton de session. */
    public Session provisionner(Map<String, Object> claims) {
        String role = Optional.ofNullable(MappageRole.resoudre(claims, mappage())).orElse("employee");
        String email = Oidc.courriel(claims);
        Optional<Compte> existant = comptes.lister().stream()
                .filter(u -> (u.email() != null && u.email().equalsIgnoreCase(email)) || u.username().equalsIgnoreCase(email)).findFirst();
        boolean provisioned = existant.isEmpty();
        Compte u = existant.orElseGet(() -> {
            byte[] b = new byte[24];
            alea.nextBytes(b);
            // Mot de passe aléatoire : le compte SSO ne se connecte jamais par mot de passe.
            return comptes.creer(new DepotComptes.Creation(email, Primitives.base64Url(b), role, null, null, false, false, email, false));
        });
        return new Session(connexion.emettre(u), u.id(), u.role(), email, provisioned, u.tenantId());
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
