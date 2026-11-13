package ca.vina.nexu.pays.domaine;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Pack pays — les <strong>données</strong> qui pilotent tous les moteurs de calcul.
 *
 * <p>Un même code sert les quatre pays : taux, plafonds et barèmes viennent d'ici. C'est le
 * principe ouvert/fermé appliqué — ajouter un pays est un ajout de données, pas une modification
 * de code.
 *
 * <p>Ces données ne sont pas retranscrites à la main : elles sont extraites du registre Node vers
 * {@code pays-packs.json}. Une transcription manuelle de quatre barèmes fiscaux aurait été la
 * source d'écarts la plus probable de toute cette migration.
 *
 * <p>Le domaine ne dépend de rien : ni Spring, ni Jackson, ni base de données.
 */
public record PaysPack(
        String code,
        String name,
        String flag,
        String currency,
        String locale,
        String accountingStandard,
        String fiscalYearNote,
        String salesTaxLabel,
        List<TaxeVente> salesTaxes,
        List<TauxAdditionnel> additionalRates,
        List<Cotisation> payrollContributions,
        double corporateTaxRate,
        List<Tranche> personalIncomeTax,
        List<Autorite> remittanceAuthorities,
        // Types relevés dans les données extraites, pas déduits des noms de l'interface
        // TypeScript : `remittanceEngine` est un booléen (le pays a-t-il un moteur ?) et
        // `notes` une chaîne unique. Se fier au nom aurait produit un contrat faux.
        boolean remittanceEngine,
        String notes) {

    public record TaxeVente(String code, String label, double rate) {
    }

    public record TauxAdditionnel(String code, String label, double rate) {
    }

    /**
     * Cotisation sociale. {@code ceiling} nul signifie « sans plafond » — le confondre avec zéro
     * annulerait la cotisation au lieu de la déplafonner.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cotisation(String code, String label, Double employeeRate, Double employerRate, Double ceiling) {
    }

    /** Tranche d'un barème progressif. {@code upTo} nul désigne la tranche supérieure. */
    public record Tranche(Double upTo, double rate) {
    }

    /**
     * {@code form} nommait le formulaire officiel (TPZ-1015.R.14, PD7A…). Je l'avais omis, et la
     * désérialisation tolérante l'avalait en silence : la réponse Java perdait une donnée sans
     * que rien ne le signale. D'où {@code FAIL_ON_UNKNOWN_PROPERTIES} réactivé dans le registre.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Autorite(String code, String label, String form, String note) {
    }
}
