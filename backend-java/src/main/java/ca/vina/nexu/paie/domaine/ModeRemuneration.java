package ca.vina.nexu.paie.domaine;

import java.util.Arrays;

/** Payé à l'heure travaillée (les feuilles font le brut) ou au salaire (les feuilles attestent la présence). */
public enum ModeRemuneration {
    HORAIRE("horaire"),
    SALAIRE("salaire");

    private final String code;

    ModeRemuneration(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ModeRemuneration de(Object code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusPaie.invalide("« mode » : horaire ou salaire"));
    }
}
