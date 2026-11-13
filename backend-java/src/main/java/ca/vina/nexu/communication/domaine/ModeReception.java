package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Comment l'avis se manifeste sur le canal : l'ensemble fermé du répertoire d'origine. */
public enum ModeReception {
    SONNERIE("sonnerie", "Sonnerie"),
    VIBRATION("vibration", "Vibration"),
    TONALITE_DISTINCTE("tonalite_distincte", "Tonalité distincte"),
    SILENCIEUX("silencieux", "Silencieux"),
    PRIORITAIRE("prioritaire", "Prioritaire — passe le mode silencieux");

    private final String code;
    private final String libelle;

    ModeReception(String code, String libelle) {
        this.code = code;
        this.libelle = libelle;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    public static ModeReception de(String code) {
        return Arrays.stream(values()).filter(m -> m.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusCommunication.invalide("mode inconnu « " + code + " » (attendus : "
                        + Arrays.stream(values()).map(ModeReception::code).collect(Collectors.joining(", ")) + ")"));
    }
}
