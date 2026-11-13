package ca.vina.nexu.integrations.infrastructure.web;

import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.web.TableDeRoutage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La spécification OpenAPI de ce produit, construite à partir des <strong>routes montées</strong>.
 *
 * <p>Le socle ({@code openapi-socle.json}) apporte les composants, les schémas et les opérations
 * rédigées à la main ; seules celles dont la route est servie ici sont gardées — une opération
 * documentée et jamais servie est un contrat qui ment. Chaque route montée que personne n'a
 * décrite reçoit une entrée générée : étiquetée par son préfixe, sécurisée par jeton, avec un corps
 * d'exemple pour « Try it out ».
 */
@Component
public class SpecificationOpenApi {

    static final List<String[]> ETIQUETTES_PAR_PREFIXE = List.of(
            new String[]{"/api/auth", "Auth"}, new String[]{"/api/oauth", "Auth"}, new String[]{"/api/effectifs", "Effectifs"},
            new String[]{"/api/communication", "Communication"}, new String[]{"/api/personnel", "Personnel"},
            new String[]{"/api/saas", "SaaS (organismes et modules)"}, new String[]{"/api/approvals", "Approbations"},
            new String[]{"/api/habilitations", "Habilitations"}, new String[]{"/api/integrations", "Intégrations"},
            new String[]{"/api/superadmin", "Superadmin"}, new String[]{"/api/countries", "Pays"},
            new String[]{"/api/ordonnanceur", "Ordonnanceur"}, new String[]{"/api/exploitation", "Exploitation"}, new String[]{"/api/soutien", "Soutien"},
            new String[]{"/api/securite", "Sécurité"}, new String[]{"/api/notifications", "Notifications"}, new String[]{"/api/rapports", "Rapports"});
    private static final List<Pattern> PUBLICS = List.of(Pattern.compile("^/api/auth/(login|tenants|sso|saml|forgot|reset|register|countries)"),
            Pattern.compile("^/api/oauth/"), Pattern.compile("^/api/countries"), Pattern.compile("^/public/"));
    private static final Pattern AVEC_LOCATAIRE = Pattern.compile("^/api/(effectifs|communication|personnel|approvals|habilitations|integrations)");
    private static final Pattern INTERNES = Pattern.compile("/_[A-Za-z]");
    private static final Pattern PARAMETRE = Pattern.compile("\\{([A-Za-z0-9_]+)\\}");
    private static final String PARAMETRE_ID = "id";

    /** Les verbes documentés ; une route servie sous un autre verbe (ou sans) n'entre pas dans la spécification. */
    enum Verbe {
        GET(false), POST(true), PUT(true), PATCH(true), DELETE(false);

        private final boolean ecriture;

        Verbe(boolean ecriture) {
            this.ecriture = ecriture;
        }

        String code() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        static java.util.Optional<Verbe> de(String code) {
            return java.util.Arrays.stream(values()).filter(v -> v.code().equals(code)).findFirst();
        }
    }
    private static final String SOCLE = "/openapi-socle.json";

    private final String socle;
    private final RequestMappingHandlerMapping mappings;

    @Autowired
    public SpecificationOpenApi(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings) {
        this(mappings, SOCLE);
    }

    SpecificationOpenApi(RequestMappingHandlerMapping mappings, String ressource) {
        this.mappings = mappings;
        try (InputStream flux = getClass().getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IllegalStateException("socle OpenAPI absent du classpath : " + ressource);
            }
            this.socle = new String(flux.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("socle OpenAPI illisible", e);
        }
    }

    public static String etiquette(String chemin) {
        return ETIQUETTES_PAR_PREFIXE.stream().filter(e -> chemin.equals(e[0]) || chemin.startsWith(e[0] + "/")).map(e -> e[1]).findFirst().orElse("Autres");
    }

    private static List<Object> parametres(String chemin) {
        List<Object> parametres = new ArrayList<>();
        Matcher m = PARAMETRE.matcher(chemin);
        while (m.find()) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("name", m.group(1));
            p.put("in", "path");
            p.put("required", true);
            p.put("schema", Map.of("type", "string"));
            p.put("example", PARAMETRE_ID.equals(m.group(1)) ? "00000000-0000-4000-8000-000000000001" : m.group(1));
            parametres.add(p);
        }
        if (AVEC_LOCATAIRE.matcher(chemin).find()) {
            parametres.add(Map.of("$ref", "#/components/parameters/TenantId"));
        }
        return parametres;
    }

    private static Map<String, Object> reponses(boolean publique) {
        Map<String, Object> reponses = new LinkedHashMap<>();
        reponses.put("200", Map.of("description", "OK"));
        reponses.put("400", Map.of("description", "Requête invalide (erreurs nommées)"));
        if (!publique) {
            reponses.put("401", Map.of("description", "Non authentifié"));
            reponses.put("403", Map.of("description", "Rôle ou module insuffisant"));
        }
        reponses.put("404", Map.of("description", "Introuvable"));
        return reponses;
    }

    /** Une route que personne n'a décrite : paramètres de chemin, organisme quand le préfixe le porte, codes, corps libre pour les écritures. */
    static Map<String, Object> operationGeneree(TableDeRoutage.Route r) {
        boolean publique = PUBLICS.stream().anyMatch(p -> p.matcher(r.chemin()).find());
        Map<String, Object> op = new LinkedHashMap<>();
        op.put("tags", List.of(etiquette(r.chemin())));
        op.put("summary", r.methode().toUpperCase() + " " + r.chemin());
        op.put("description", "Entrée générée à partir du routeur : la route existe et répond comme décrit par ses codes ; le corps est libre (objet JSON).");
        op.put("x-genere", true);
        op.put("parameters", parametres(r.chemin()));
        op.put("responses", reponses(publique));
        if (publique) {
            op.put("security", List.of());
        }
        if (Verbe.de(r.methode()).map(v -> v.ecriture).orElse(false)) {
            op.put("requestBody", Map.of("content", Map.of("application/json", Map.of("schema", Map.of("type", "object", "additionalProperties", true), "example", Map.of()))));
        }
        return op;
    }

    /** Le document complet : composants du socle, puis une opération par route montée — décrite si le socle la décrit, générée sinon. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> construire() {
        Map<String, Object> spec = Json.carte(socle);
        Map<String, Object> contrats = (Map<String, Object>) spec.getOrDefault("x-contrats", Map.of());
        spec.remove("x-contrats");
        Map<String, Object> decrits = (Map<String, Object>) spec.getOrDefault("paths", Map.of());
        Map<String, Object> chemins = new LinkedHashMap<>();
        for (TableDeRoutage.Route r : TableDeRoutage.routes(mappings)) {
            String methode = r.methode().toLowerCase();
            if (INTERNES.matcher(r.chemin()).find() || Verbe.de(methode).isEmpty()) {
                continue;
            }
            Map<String, Object> chemin = (Map<String, Object>) chemins.computeIfAbsent(r.chemin(), k -> new LinkedHashMap<String, Object>());
            Object decrit = decrits.get(r.chemin()) instanceof Map<?, ?> m ? m.get(methode) : null;
            Object contrat = contrats.get(methode + " " + r.chemin());
            chemin.put(methode, decrit != null ? decrit : contrat != null ? contrat : operationGeneree(new TableDeRoutage.Route(methode, r.chemin())));
        }
        spec.put("paths", chemins);
        return spec;
    }
}
