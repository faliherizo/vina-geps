package ca.vina.nexu.paie.domaine;

import java.util.Arrays;

/** Un cycle préparé se recalcule ou s'annule ; traité, il est figé, comme les feuilles qu'il a payées. */
public enum EtatCycle {
    PREPARE("prepare"),
    TRAITE("traite"),
    ANNULE("annule");

    private final String code;

    EtatCycle(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static EtatCycle de(Object code) {
        return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusPaie.invalide("« etat » : prepare, traite ou annule"));
    }
}
