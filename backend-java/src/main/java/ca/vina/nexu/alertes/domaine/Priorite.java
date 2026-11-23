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

    /**
     * Les canaux d'une alerte. Les trois premiers passent par le port d'avis, et une alerte critique les impose tous ;
     * l'appel vocal (ST-G06) n'existe que dans une étape de cascade qui le nomme : il ne réveille personne d'office.
     */
    public enum Canal {
        COURRIEL("courriel", true), TEXTO("texto", true), POUSSEE("poussee", true), APPEL("appel", false);

        private final String code;
        private final boolean parAvis;

        Canal(String code, boolean parAvis) {
            this.code = code;
            this.parAvis = parAvis;
        }

        public String code() {
            return code;
        }

        /** Part-il par le port d'avis (courriel, texto, poussée), ou par un appel vocal ? */
        public boolean parAvis() {
            return parAvis;
        }

        public static Canal de(String code) {
            return Arrays.stream(values()).filter(c -> c.code.equals(code)).findFirst()
                    .orElseThrow(() -> RefusAlerte.invalide("« canaux » : courriel, texto, poussee ou appel"));
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
            case CRITIQUE -> Arrays.stream(Canal.values()).filter(Canal::parAvis).map(Canal::code).toList();
            case INFORMATION, URGENTE -> null;
        };
    }
}
