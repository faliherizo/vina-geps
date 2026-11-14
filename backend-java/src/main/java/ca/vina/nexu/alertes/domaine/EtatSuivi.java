package ca.vina.nexu.alertes.domaine;

import java.util.Arrays;
import java.util.Optional;

/**
 * <strong>Où en est une alerte</strong>, tel que le suivi le filtre (ST-G03) : prise en charge par quelqu'un, encore sans
 * prise en charge, ou pas encore lue de tous. Un ensemble fermé, converti une fois à la frontière.
 */
public enum EtatSuivi {
    PRISE_EN_CHARGE("prise_en_charge"),
    SANS_PRISE_EN_CHARGE("sans_prise_en_charge"),
    NON_LUE("non_lue");

    private final String code;

    EtatSuivi(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<EtatSuivi> de(String code) {
        return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst();
    }

    /** L'alerte est-elle dans cet état ? */
    public boolean decrit(Alertes.Alerte a) {
        return switch (this) {
            case PRISE_EN_CHARGE -> a.bilan().premierePriseEnCharge().isPresent();
            case SANS_PRISE_EN_CHARGE -> a.bilan().premierePriseEnCharge().isEmpty();
            case NON_LUE -> a.remises().stream().anyMatch(r -> !r.lue());
        };
    }
}
