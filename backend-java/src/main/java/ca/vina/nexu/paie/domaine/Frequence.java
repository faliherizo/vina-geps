package ca.vina.nexu.paie.domaine;

import java.util.Arrays;

/** La fréquence de paie : le nombre de paies dans l'année est le facteur d'annualisation du calcul. */
public enum Frequence {
    HEBDOMADAIRE("hebdomadaire", 52),
    AUX_DEUX_SEMAINES("aux-deux-semaines", 26),
    BIMENSUELLE("bimensuelle", 24),
    MENSUELLE("mensuelle", 12);

    private final String code;
    private final int paiesParAn;

    Frequence(String code, int paiesParAn) {
        this.code = code;
        this.paiesParAn = paiesParAn;
    }

    public String code() {
        return code;
    }

    public int paiesParAn() {
        return paiesParAn;
    }

    public static Frequence de(Object code) {
        return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusPaie.invalide("« frequence » : hebdomadaire, aux-deux-semaines, bimensuelle ou mensuelle"));
    }
}
