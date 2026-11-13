package ca.vina.nexu.notifications.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Par où un avis parvient à la personne (ADR 0029) ; le code est celui du contrat HTTP et des réglages en base. */
public enum Canal {
    COURRIEL("courriel"), TEXTO("texto"), POUSSEE("poussee");

    private final String code;

    Canal(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Un canal écrit atteint la personne sans appareil allumé : c'est lui qu'un avis essentiel garde ouvert. */
    public boolean ecrit() {
        return this != POUSSEE;
    }

    public static Optional<Canal> de(String code) {
        return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst();
    }

    public static final List<String> CODES = Arrays.stream(values()).map(Canal::code).toList();
}
