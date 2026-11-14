package ca.vina.nexu.alertes.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * La priorité d'une alerte. Elle décide des canaux : une alerte critique part sur <strong>tous</strong>
 * les canaux, quelles que soient les préférences — une règle obligatoire prévaut sur une préférence
 * personnelle (FX-AO-20260810, critère 22). Les autres suivent les préférences de la personne.
 */
public enum Priorite {
    INFORMATION("information"),
    URGENTE("urgente"),
    CRITIQUE("critique");

    /** Les canaux d'avis, dans le vocabulaire du port d'avis : ceux qu'une alerte critique impose tous. */
    public enum Canal {
        COURRIEL("courriel"), TEXTO("texto"), POUSSEE("poussee");

        private final String code;

        Canal(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final String code;

    Priorite(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<Priorite> de(String code) {
        return Arrays.stream(values()).filter(p -> p.code.equals(code)).findFirst();
    }

    /** Les canaux imposés ; {@code null} laisse les préférences de chaque personne décider. */
    public List<String> canauxImposes() {
        return switch (this) {
            case CRITIQUE -> Arrays.stream(Canal.values()).map(Canal::code).toList();
            case INFORMATION, URGENTE -> null;
        };
    }
}
