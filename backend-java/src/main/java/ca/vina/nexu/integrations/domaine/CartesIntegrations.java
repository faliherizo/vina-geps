package ca.vina.nexu.integrations.domaine;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * <strong>L'état des intégrations en cartes</strong> (écran ÉC-5, règle INT-1) — port de
 * {@code integrations/etat.ts} : pour chaque intégration, un état écrit (configuré, simulé, non
 * configuré), la dernière activité, les erreurs récentes — et jamais un secret. Tout vient de
 * l'environnement injecté et des chiffres que l'infrastructure a lus : aucune E/S ici.
 */
public final class CartesIntegrations {

    private CartesIntegrations() {
    }

    /** Ce que l'écran doit savoir pour offrir — ou non — le bouton de démonstration, et l'essai. */
    public record Demonstration(boolean possible, String motif, boolean branchee) { }

    public record Carte(String code, String nom, String etat, String detail, String derniereActiviteLe, int erreursRecentes, String ecran, Demonstration demonstration) { }

    /** Ce que l'infrastructure a compté : synchronisations GRICS, clients d'API, webhooks et envois échoués. */
    public record Chiffres(String derniereSynchroGricsLe, int synchrosGricsEchouees, int clientsApi, List<Webhooks.Webhook> webhooks,
                           Map<String, Integer> envoisEnAttenteParWebhook, List<String> derniersEnvois, int envoisEchoues) { }

    /**
     * Ce que le registre des démonstrations et les réglages de l'établissement disent : le mode de
     * chaque source scolaire ({@code simule}, {@code http}, {@code mock}), la possibilité et l'état
     * de chaque démonstration, le client d'API et le port clamd de démonstration s'ils existent.
     */
    public record Demonstrations(String modeGrics, String modeAts, Map<String, Demonstration> parCode, String clientApiDemo, Integer portClamdDemo, String urlServeur) {
        public static Demonstrations aucune(String urlServeur) {
            return new Demonstrations("simule", "simule", Map.of(), null, null, urlServeur);
        }
    }

    private static String env(Map<String, String> env, String cle) {
        return Optional.ofNullable(env.get(cle)).orElse("");
    }

    /** GRICS est configuré avec un couple client, ou une URL et un jeton (même règle que {@code configGricsDepuisEnv}). */
    static boolean gricsConfigure(Map<String, String> env) {
        boolean client = !env(env, "GRICS_CLIENT_ID").isEmpty() && !env(env, "GRICS_CLIENT_SECRET").isEmpty();
        boolean jeton = !env(env, "GRICS_URL").isEmpty() && !env(env, "GRICS_JETON").isEmpty();
        return client || jeton;
    }

    /** SAML est actif avec l'URL SSO de l'IdP et son certificat (même règle que {@code samlConfig}). */
    static boolean samlActif(Map<String, String> env) {
        return !env(env, "SAML_IDP_SSO_URL").isEmpty() && !env(env, "SAML_IDP_CERT").isEmpty();
    }

    private static String premier(Map<String, String> env, String... cles) {
        for (String c : cles) {
            if (!env(env, c).isEmpty()) {
                return env(env, c);
            }
        }
        return "";
    }

    public static List<Carte> cartes(Map<String, String> env, Chiffres c) {
        return cartes(env, c, Demonstrations.aucune(""));
    }

    public static List<Carte> cartes(Map<String, String> env, Chiffres c, Demonstrations d) {
        boolean grics = gricsConfigure(env);
        String entraClient = env(env, "AZURE_CLIENT_ID");
        boolean saml = samlActif(env);
        boolean ats = !env(env, "ATS_WEBHOOK_SECRET").isEmpty();
        String smsFournisseur = env(env, "SMS_FOURNISSEUR").isEmpty() ? (env(env, "SMS_URL").isEmpty() ? "" : URI.create(env(env, "SMS_URL")).getHost()) : env(env, "SMS_FOURNISSEUR");
        String clamdHote = premier(env, "CLAMD_HOTE", "CLAMD_HOST");
        long suspendus = c.webhooks().stream().filter(Webhooks.Webhook::suspendu).count();
        int enAttente = c.envoisEnAttenteParWebhook().values().stream().mapToInt(Integer::intValue).sum();
        boolean webhookActif = c.webhooks().stream().anyMatch(w -> w.actif() && !w.suspendu());
        String dernierEnvoi = c.derniersEnvois().stream().filter(Objects::nonNull).sorted().reduce((a, b) -> b).orElse(null);
        boolean gricsMock = "mock".equals(d.modeGrics());
        boolean atsMock = "mock".equals(d.modeAts());
        return List.of(
                new Carte("grics", "GRICS — Paie et GRH (Espace API)", gricsMock ? "demonstration" : "http".equals(d.modeGrics()) || grics ? "configure" : "simule",
                        gricsMock ? "GRICS de démonstration embarquée (/mock/grics) : jeton OAuth Mozaïk, organismes, employés"
                                : "http".equals(d.modeGrics()) ? "Espace API GRICS configuré pour cet établissement"
                                : grics ? "identifiants client ou jeton configurés (environnement)" : "simulateur : aucun identifiant client (GRICS_CLIENT_ID / GRICS_CLIENT_SECRET)",
                        c.derniereSynchroGricsLe(), c.synchrosGricsEchouees(), "scolaire:reprise", null),
                new Carte("ats", "Relevance Studio — ATS (webhook entrant)", atsMock ? "demonstration" : "http".equals(d.modeAts()) || ats ? "configure" : "non_configure",
                        atsMock ? "ATS de démonstration embarqué (/mock/ats) : candidats embauchés, jeton porteur"
                                : "http".equals(d.modeAts()) ? "ATS configuré pour cet établissement (jeton et webhook)"
                                : ats ? "secret partagé configuré : POST /api/scolaire/integrations/ats/candidats" : "ATS_WEBHOOK_SECRET absent : la route répond 404 ; le repli fichier reste disponible",
                        null, 0, "scolaire:reprise", null),
                carte(d, "entra", "Microsoft Entra ID (SSO)", entraClient.isEmpty() ? "simule" : "configure",
                        entraClient.isEmpty() ? "mode simulé : aucun AZURE_CLIENT_ID"
                                : "client " + entraClient.substring(0, Math.min(6, entraClient.length())) + "…, locataire " + Optional.ofNullable(env.get("AZURE_TENANT_ID")).orElse("common"),
                        "login", "Entra ID de démonstration embarqué (/mock/entra) : découverte OIDC, PKCE, id_token RS256 vérifié par JWKS"),
                carte(d, "saml", "SAML 2.0 (SSO)", saml ? "configure" : "non_configure",
                        saml ? "IdP déclaré ; métadonnées SP : /api/auth/sso/saml/metadata" : "aucun IdP SAML déclaré (SAML_*)", "superadmin:exploitation",
                        "IdP SAML de démonstration embarqué (/mock/saml) : HTTP-Redirect, réponse signée RSA-SHA256"),
                carte(d, "sms", "Textos et appels (fournisseur SMS / voix)", smsFournisseur.isEmpty() ? "simule" : "configure",
                        smsFournisseur.isEmpty() ? "simulateur : les textos sont journalisés, pas envoyés (SMS_FOURNISSEUR ou SMS_URL / SMS_JETON)" : "fournisseur " + smsFournisseur,
                        "scolaire:besoins", "Twilio de démonstration embarqué (/mock/twilio) : Messages.json et Calls.json, Basic, formulaire encodé"),
                carte(d, "antivirus", "Antivirus des fichiers (ClamAV)", clamdHote.isEmpty() ? "simule" : "configure",
                        clamdHote.isEmpty() ? "simulateur EICAR : refuse la signature de test, accepte le reste (CLAMD_HOTE)" : "clamd " + clamdHote + ":" + Optional.ofNullable(env.get("CLAMD_PORT")).orElse("3310"),
                        "personnel:documents", "clamd de démonstration embarqué (TCP 127.0.0.1:" + (d.portClamdDemo() == null ? "?" : d.portClamdDemo()) + ") : INSTREAM, PING, VERSION"),
                carte(d, "api", "Clients d'API (OAuth 2.0 client_credentials)", c.clientsApi() > 0 ? "configure" : "non_configure",
                        c.clientsApi() + " client(s) ; documentation : /api/integrations/swagger", "admin:api",
                        "client de démonstration " + (d.clientApiDemo() == null ? "(créé par un autre processus)" : d.clientApiDemo()) + " ; jeton : POST /api/oauth/token ; ressource d'essai : GET /mock/oauth/ressource"),
                avecActivite(carte(d, "webhooks", "Webhooks sortants (événements métier)", webhookActif ? "configure" : "non_configure",
                        c.webhooks().size() + " webhook(s), " + suspendus + " suspendu(s), " + enAttente + " envoi(s) en attente", "admin:integrations",
                        "récepteur de démonstration abonné à tous les événements ; journal : " + d.urlServeur() + "/mock/webhooks/recepteur/journal.html"), dernierEnvoi, c.envoisEchoues()));
    }

    /** Le tiers de démonstration embarqué, quand il est branché, l'emporte sur l'état d'environnement : c'est lui qui répond, et l'écran doit le dire. */
    private static Carte carte(Demonstrations d, String code, String nom, String etat, String detail, String ecran, String detailDemo) {
        Demonstration demo = d.parCode().getOrDefault(code, new Demonstration(true, "aucune configuration réelle : la démonstration peut être branchée", false));
        return new Carte(code, nom, demo.branchee() ? "demonstration" : etat, demo.branchee() ? detailDemo : detail, null, 0, ecran, demo);
    }

    private static Carte avecActivite(Carte c, String derniereActiviteLe, int erreursRecentes) {
        return new Carte(c.code(), c.nom(), c.etat(), c.detail(), derniereActiviteLe, erreursRecentes, c.ecran(), c.demonstration());
    }
}
