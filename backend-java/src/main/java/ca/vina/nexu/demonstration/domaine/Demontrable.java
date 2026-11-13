package ca.vina.nexu.demonstration.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Une intégration qu'on peut brancher sur un tiers de démonstration. Les ports <b>du processus</b>
 * (annuaire, SAML, textos et appels, antivirus) se commutent pour tout le serveur et se bloquent dès
 * qu'une configuration réelle est posée ; les autres (client d'API, webhooks) sont des données du
 * locataire.
 */
public enum Demontrable {
    ENTRA("entra", true, List.of("AZURE_CLIENT_ID")),
    SAML("saml", true, List.of("SAML_IDP_SSO_URL", "SAML_IDP_CERT")),
    // Un compte Twilio sert aux textos et aux appels : la carte « textos et appels » branche les deux ports.
    SMS("sms", true, List.of("SMS_FOURNISSEUR", "SMS_URL", "APPEL_FOURNISSEUR", "APPEL_URL")),
    ANTIVIRUS("antivirus", true, List.of("CLAMD_HOTE", "CLAMD_HOST")),
    API("api", false, List.of()),
    WEBHOOKS("webhooks", false, List.of());

    private final String code;
    private final boolean processus;
    private final List<String> configurationReelle;

    Demontrable(String code, boolean processus, List<String> configurationReelle) {
        this.code = code;
        this.processus = processus;
        this.configurationReelle = configurationReelle;
    }

    public String code() {
        return code;
    }

    public boolean processus() {
        return processus;
    }

    /** Les variables dont la présence prouve une configuration réelle, qui interdit la démonstration. */
    public List<String> configurationReelle() {
        return configurationReelle;
    }

    public static Optional<Demontrable> parCode(String code) {
        return Arrays.stream(values()).filter(d -> d.code.equals(code)).findFirst();
    }

    /** La conversion à la frontière : un code inconnu est refusé nommément. */
    public static Demontrable de(String code) {
        return parCode(code).orElseThrow(() -> new IllegalArgumentException("intégration introuvable : " + code));
    }
}
