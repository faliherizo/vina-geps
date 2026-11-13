package ca.vina.nexu.demonstration.domaine;

import java.util.List;

/**
 * Données fictives des tiers de démonstration — port de {@code integrations/mocks/donnees.ts} :
 * un annuaire imaginaire, des numéros réservés à la fiction (indicatif 555, plage 0100–0199),
 * des identifiants de client connus d'avance. Tout est déterministe, rien n'est un secret.
 */
public final class DonneesDemo {

    private DonneesDemo() {
    }

    public static final String ENTRA_TENANT = "d3d3d3d3-0000-4000-8000-c55dc55dc55d";
    public static final String ENTRA_CLIENT_ID = "0a1b2c3d-d3a0-4000-8000-0000d3a00001";
    public static final String ENTRA_CLIENT_SECRET = "secret-demonstration-entra";

    public record CompteEntra(String oid, String courriel, String nom, List<String> roles) {
        /** Le suffixe de {@code data-testid} : {@code Nexu.Admin} → {@code admin}. */
        public String cle() {
            return roles.get(0).replace("Nexu.", "").toLowerCase();
        }
    }

    public static final List<CompteEntra> COMPTES_ENTRA = List.of(
            new CompteEntra("7b1f0c2e-0001-4000-8000-000000000001", "josee.tremblay@cssdc-demo.qc.ca", "Josée Tremblay", List.of("Nexu.Admin")),
            new CompteEntra("7b1f0c2e-0002-4000-8000-000000000002", "marc.gagnon@cssdc-demo.qc.ca", "Marc Gagnon", List.of("Nexu.Manager")),
            new CompteEntra("7b1f0c2e-0003-4000-8000-000000000003", "amina.roy@cssdc-demo.qc.ca", "Amina Roy", List.of("Nexu.Employee")));

    public static CompteEntra compte(String courriel) {
        return COMPTES_ENTRA.stream().filter(c -> c.courriel().equals(courriel)).findFirst().orElse(null);
    }

    public static final String SAML_ISSUER = "urn:nexu-vina:idp-demonstration";

    public static final String TWILIO_ACCOUNT_SID = "ACd3m0d3m0d3m0d3m0d3m0d3m0d3m0d3m0";
    public static final String TWILIO_AUTH_TOKEN = "jeton-demonstration-twilio";
    public static final String TWILIO_FROM = "+15550100100";

    public static final String TELNYX_CLE_API = "KEYd3m0-demonstration-telnyx";
    public static final String TELNYX_FROM = "+15550100101";

    public static final String NUMERO_INJOIGNABLE = "+15550100199";
    public static final String NUMERO_LIMITE = "+15550100198";


    public static final java.util.regex.Pattern E164 = java.util.regex.Pattern.compile("^\\+[1-9]\\d{6,14}$");
}
