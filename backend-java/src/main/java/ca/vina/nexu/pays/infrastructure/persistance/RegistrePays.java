package ca.vina.nexu.pays.infrastructure.persistance;

import ca.vina.nexu.pays.domaine.Pays;
import ca.vina.nexu.pays.domaine.PaysPack;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registre des packs pays — adaptateur d'infrastructure.
 *
 * <p>Les données sont <strong>extraites</strong> du registre Node vers {@code pays-packs.json},
 * jamais retranscrites : quatre barèmes fiscaux recopiés à la main auraient été la source
 * d'écarts la plus probable de cette migration. Le fichier se régénère par
 * {@code node -e "…listCountryPacks()"} (voir README du module).
 *
 * <p>L'ordre de la liste est celui du Node : il détermine l'ordre du catalogue renvoyé au
 * sélecteur de pays de la page de connexion, donc il fait partie du contrat.
 */
@Component
public class RegistrePays implements Pays.Catalogue {

    private final String paysParDefaut;
    private final Map<String, PaysPack> packs = new LinkedHashMap<>();
    /**
     * Le JSON d'origine, conservé tel quel.
     *
     * <p>Le catalogue ne fait que <strong>retransmettre</strong> des données statiques. Les
     * resérialiser depuis des records impose une politique d'inclusion unique, alors que la
     * source mêle les deux cas : {@code ceiling} y est explicitement {@code null} et
     * {@code employeeRate} y est parfois absent. Aucune annotation ne sait exprimer les deux sur
     * un même type — retransmettre l'original le fait sans effort et sans risque.
     *
     * <p>Les records typés restent nécessaires aux moteurs de calcul, qui ont besoin d'accès
     * typé. Les deux vues coexistent : l'une pour calculer, l'autre pour répondre.
     */
    private final Map<String, JsonNode> bruts = new LinkedHashMap<>();

    public RegistrePays() {
        this("/pays-packs.json");
    }

    /**
     * Le chemin de la ressource est un paramètre, et non une constante enfouie : sans cette
     * couture, l'échec au démarrage — ressource absente ou illisible — ne peut être vérifié
     * par aucun test. C'est pourtant lui qui décide si l'application s'arrête en le disant ou
     * démarre silencieusement sur un registre vide.
     */
    RegistrePays(String ressource) {
        // Spring Boot 4 sérialise avec Jackson 3 (`tools.jackson`) : le mapper de lecture doit
        // être celui-là, sinon deux bibliothèques cohabitent avec des conventions différentes.
        //
        // La lecture est **stricte** : un champ des données que le modèle Java ignore doit faire
        // échouer le démarrage. En mode tolérant, j'avais silencieusement perdu le champ `form`
        // des autorités de remise — une donnée absente de la réponse, sans le moindre signal.
        ObjectMapper mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        try (InputStream flux = getClass().getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IllegalStateException("registre des pays absent du classpath : " + ressource);
            }
            JsonNode racine = mapper.readTree(flux);
            Donnees d = mapper.treeToValue(racine, Donnees.class);
            this.paysParDefaut = d.defaultCountry();
            for (PaysPack p : d.packs()) {
                packs.put(p.code(), p);
            }
            for (JsonNode n : racine.get("packs")) {
                bruts.put(n.get("code").asString(), n);
            }
        } catch (Exception e) {
            throw new IllegalStateException("registre des pays illisible", e);
        }
    }

    private record Donnees(String defaultCountry, List<PaysPack> packs) {
    }

    public String paysParDefaut() {
        return paysParDefaut;
    }

    public boolean existe(String code) {
        return packs.containsKey(code);
    }

    @Override
    public Optional<PaysPack> pack(String code) {
        return Optional.ofNullable(packs.get(code));
    }

    /** Champs exposés par {@code publicView} du Node, dans son ordre ; {@code additionalRates} est traité à part. */
    private static final List<String> CHAMPS_VUE_PUBLIQUE = List.of(
            "code", "name", "flag", "currency", "locale", "accountingStandard", "fiscalYearNote",
            "salesTaxLabel", "salesTaxes", "payrollContributions", "corporateTaxRate",
            "personalIncomeTax", "remittanceAuthorities", "remittanceEngine", "notes");

    /**
     * Retransmission des données d'origine, champ par champ dans l'ordre de {@code publicView} du
     * Node. On ne resérialise pas depuis les records : la source mêle clés absentes et clés
     * explicitement nulles, ce qu'aucune politique d'inclusion ne sait reproduire.
     */
    @Override
    public Map<String, Object> vuePublique(String code) {
        JsonNode brut = bruts.get(code);
        Map<String, Object> vue = new LinkedHashMap<>();
        for (String champ : CHAMPS_VUE_PUBLIQUE) {
            vue.put(champ, brut.get(champ));
        }
        // `additionalRates ?? []` du Node : la clé est toujours présente, jamais absente.
        JsonNode taux = brut.get("additionalRates");
        vue.put("additionalRates", taux == null || taux.isNull() ? List.of() : taux);
        return vue;
    }

    public List<PaysPack> tous() {
        return List.copyOf(packs.values());
    }

    /** Le pack tel qu'il figure dans les données d'origine — pour retransmission fidèle. */
    public JsonNode brut(String code) {
        return bruts.get(code);
    }
}
