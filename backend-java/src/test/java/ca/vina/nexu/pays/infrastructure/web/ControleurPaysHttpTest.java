package ca.vina.nexu.pays.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.pays.infrastructure.configuration.ConfigurationPays;
import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Le contrat de {@code /api/countries} — catalogue public, fiche, taxe de vente, moteurs, format officiel — messages du Node. */
@DisplayName("/api/countries — catalogue public et moteurs génériques par pays")
class ControleurPaysHttpTest {

    private ClientHttpEssai http;

    @BeforeEach
    void preparer() {
        ConfigurationPays config = new ConfigurationPays();
        RegistrePays registre = new RegistrePays();
        http = new ClientHttpEssai(new ControleurPays(config.lecturePays(registre), config.calculsPays(registre)));
    }

    @Test
    @DisplayName("catalogue et fiche : publics, pays par défaut, additionalRates toujours présent, pays inconnu en 404")
    void catalogueEtFiche() throws Exception {
        http.get("/api/countries", null).andExpect(status().isOk()).andExpect(jsonPath("$.defaultCountry").value("CA")).andExpect(jsonPath("$.countries[0].code").isString()).andExpect(jsonPath("$.countries[0].salesTaxLabel").exists());
        http.get("/api/countries/ca", null).andExpect(status().isOk()).andExpect(jsonPath("$.code").value("CA")).andExpect(jsonPath("$.additionalRates").isArray()).andExpect(jsonPath("$.salesTaxes").isArray());
        http.get("/api/countries/ZZ", null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("unknown country"));
    }

    @Test
    @DisplayName("taxe de vente : montant exigé positif ; la chaîne vide vaut 0 comme Number('')")
    void taxeDeVente() throws Exception {
        http.get("/api/countries/CA/sales-tax?amount=100", null).andExpect(status().isOk()).andExpect(jsonPath("$.amountHT").value(100.0)).andExpect(jsonPath("$.taxes[0].amount").isNumber()).andExpect(jsonPath("$.amountTTC").value(114.98));
        http.get("/api/countries/CA/sales-tax?amount=", null).andExpect(status().isOk()).andExpect(jsonPath("$.amountHT").value(0.0));
        http.get("/api/countries/CA/sales-tax?amount=abc", null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("amount must be a positive number"));
        http.get("/api/countries/CA/sales-tax?amount=-1", null).andExpect(status().isBadRequest());
        http.get("/api/countries/ZZ/sales-tax?amount=1", null).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("paie et impôt : brut et revenu exigés positifs, périodes à 12 par défaut, profil société ou particulier")
    void paieEtImpot() throws Exception {
        http.post("/api/countries/FR/payroll", null, "{\"annualGross\":48000}").andExpect(status().isOk()).andExpect(jsonPath("$.annualNet").value(32458.38)).andExpect(jsonPath("$.periodsPerYear").value(12));
        http.post("/api/countries/CA/payroll", null, "{\"annualGross\":\"150000\",\"periodsPerYear\":26}").andExpect(status().isOk()).andExpect(jsonPath("$.periodsPerYear").value(26));
        http.post("/api/countries/CA/payroll", null, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("annualGross must be a positive number"));
        http.post("/api/countries/ZZ/payroll", null, "{\"annualGross\":1}").andExpect(status().isNotFound());
        http.post("/api/countries/CA/income-tax", null, "{\"profile\":\"corporate\",\"taxableIncome\":250000}").andExpect(status().isOk()).andExpect(jsonPath("$.profile").value("corporate"));
        http.post("/api/countries/CA/income-tax", null, "{\"taxableIncome\":75000}").andExpect(status().isOk()).andExpect(jsonPath("$.profile").value("individual"));
        http.post("/api/countries/CA/income-tax", null, "{\"taxableIncome\":-5}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("taxableIncome must be a positive number"));
    }

    @Test
    @DisplayName("remise : employés exigés, chacun avec un brut positif ; format officiel pour FR seulement")
    void remises() throws Exception {
        http.post("/api/countries/CA/remittance", null, "{\"employees\":[{\"annualGross\":50000},{\"annualGross\":60000,\"periodsPerYear\":26}]}").andExpect(status().isOk()).andExpect(jsonPath("$.employeeCount").value(2));
        http.post("/api/countries/CA/remittance", null, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("employees[] (annualGross) required"));
        http.post("/api/countries/CA/remittance", null, "{\"employees\":[{\"annualGross\":\"x\"}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("each employee needs a positive annualGross"));
        http.post("/api/countries/FR/official-remittance", null, "{\"employees\":[{\"name\":\"A\",\"id\":\"1\",\"annualGross\":48000}],\"employer\":{\"siret\":\"123\"},\"periodLabel\":\"2026-01\"}").andExpect(status().isOk()).andExpect(jsonPath("$.format").isString());
        http.post("/api/countries/FR/official-remittance", null, "{\"employees\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("employees[] (annualGross) required"));
        http.post("/api/countries/FR/official-remittance", null, "{\"employees\":[{\"annualGross\":-1}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("each employee needs a positive annualGross"));
        http.post("/api/countries/CA/official-remittance", null, "{\"employees\":[{\"annualGross\":1}]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("format officiel non disponible pour CA (Canada : voir SAGIR/SAGIP + TPZ-1015/PD7A)"));
        http.post("/api/countries/ZZ/official-remittance", null, "{}").andExpect(status().isNotFound());
    }
}
