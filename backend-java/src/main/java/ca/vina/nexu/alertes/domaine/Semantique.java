package ca.vina.nexu.alertes.domaine;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ce que <strong>veut dire</strong> une réponse prédéfinie, quel que soit son libellé : « J'arrive » et
 * « Je m'en occupe » sont deux prises en charge. C'est la sémantique, et non le texte, que le suivi
 * compte — et que les relances (phase R2) consulteront pour s'arrêter.
 */
public enum Semantique {
    PRISE_EN_CHARGE("prise_en_charge"),
    REFUS("refus"),
    INFORMATION("information");

    private final String code;

    Semantique(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Semantique> de(String code) {
        return Arrays.stream(values()).filter(s -> s.code.equals(code)).findFirst();
    }
}
