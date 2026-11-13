package ca.vina.nexu.partage.securite;

import java.util.Arrays;
import java.util.Optional;

/** Comment le serveur connaît l'établissement d'une requête : le sélecteur de connexion, ou le sous-domaine de l'hôte (ADR 0033). */
public enum ModeResolution {
    SELECTEUR("selecteur"), SOUS_DOMAINE("sous_domaine");

    private final String code;

    ModeResolution(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<ModeResolution> de(String code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst();
    }
}
