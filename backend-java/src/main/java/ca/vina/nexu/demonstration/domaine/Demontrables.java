package ca.vina.nexu.demonstration.domaine;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Les intégrations que l'on peut brancher sur un tiers de démonstration embarqué, et la règle qui
 * décide si on le peut : <strong>seulement si rien de réel n'est configuré</strong>. Un centre qui a
 * posé {@code AZURE_CLIENT_ID} ne doit pas pouvoir, d'un clic, envoyer ses connexions vers un IdP fictif.
 */
public final class Demontrables {

    private Demontrables() {
    }

    public static final List<String> CODES = Arrays.stream(Demontrable.values()).map(Demontrable::code).toList();
    /** Les ports du processus ; les autres (clients d'API, webhooks) sont des données du locataire. */
    public static final List<String> PROCESSUS = Arrays.stream(Demontrable.values()).filter(Demontrable::processus).map(Demontrable::code).toList();
    private static final java.util.Set<String> SCHEMAS_ADMIS = java.util.Set.of("http", "https");

    public static final String NOM_CLIENT_DEMO = "Client de démonstration";
    public static final String DESCRIPTION_WEBHOOK_DEMO = "Récepteur de démonstration";

    public record Possibilite(boolean possible, String motif) {
    }

    public static String exiger(String code) {
        return Demontrable.de(code).code();
    }

    public static Possibilite peutBrancher(String code, Map<String, String> env) {
        Demontrable d = Demontrable.de(code);
        return d.processus() ? bloque(env, d.configurationReelle())
                : new Possibilite(true, "données du locataire : la démonstration peut être branchée");
    }

    private static Possibilite bloque(Map<String, String> env, List<String> variables) {
        List<String> posees = variables.stream().filter(v -> env.get(v) != null && !env.get(v).isEmpty()).toList();
        return posees.isEmpty() ? new Possibilite(true, "aucune configuration réelle : la démonstration peut être branchée")
                : new Possibilite(false, "configuration réelle présente (" + String.join(", ", posees) + ")");
    }

    /**
     * L'origine de l'application telle que le navigateur la voit, et rien de plus : une adresse
     * arbitraire ferait rediriger un SSO n'importe où. Seul le préfixe {@code /admin} (déploiement nginx) est admis.
     */
    public static String origineApplication(String brut) {
        URI u;
        try {
            u = new URI(brut);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("urlApplication illisible : « " + brut + " »");
        }
        if (u.getHost() == null || !SCHEMAS_ADMIS.contains(u.getScheme())) {
            throw new IllegalArgumentException(u.getScheme() == null || u.getHost() == null ? "urlApplication illisible : « " + brut + " »" : "urlApplication : http ou https seulement");
        }
        String chemin = (u.getRawPath() == null ? "" : u.getRawPath()).replaceAll("/+$", "");
        if (!chemin.isEmpty() && !"/admin".equals(chemin)) {
            throw new IllegalArgumentException("urlApplication : l'origine seule, ou son préfixe /admin");
        }
        String port = u.getPort() == -1 ? "" : ":" + u.getPort();
        return u.getScheme() + "://" + u.getHost() + port + chemin;
    }

    /** Ce que {@code DEMONSTRATION_INTEGRATIONS} demande, vérifié au démarrage — une valeur inconnue arrête le serveur. */
    public static List<String> lireDemonstrationsEnv(Map<String, String> env) {
        String brut = env.getOrDefault("DEMONSTRATION_INTEGRATIONS", "");
        List<String> codes = new ArrayList<>();
        for (String s : brut.split(",")) {
            String code = s.trim();
            if (code.isEmpty()) {
                continue;
            }
            if (!PROCESSUS.contains(code)) {
                throw new IllegalArgumentException("DEMONSTRATION_INTEGRATIONS : « " + code + " » n'est pas un port du processus (admis : " + String.join(", ", PROCESSUS) + ")");
            }
            Possibilite p = peutBrancher(code, env);
            if (!p.possible()) {
                throw new IllegalArgumentException("DEMONSTRATION_INTEGRATIONS : " + code + " — " + p.motif());
            }
            codes.add(code);
        }
        return codes;
    }

    /** Les variables d'environnement que la règle consulte. */
    public static List<String> variables() {
        return Arrays.asList("AZURE_CLIENT_ID", "SAML_IDP_SSO_URL", "SAML_IDP_CERT", "SMS_FOURNISSEUR", "SMS_URL", "APPEL_FOURNISSEUR", "APPEL_URL", "CLAMD_HOTE", "CLAMD_HOST", "DEMONSTRATION_INTEGRATIONS", "MOCK_CLAMAV_PORT");
    }
}
