package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * <strong>Les réactions à un message</strong> — un ensemble fermé (REFONTE-MESSAGERIE.md § 2).
 *
 * Fermé, parce qu'une réaction libre deviendrait un second canal de messages, que le journal ne
 * consignerait pas. Chacune porte ses mots : c'est eux que l'interface lit à l'assistance technique.
 */
public enum Reaction {
    POUCE("pouce", "bien reçu"), FAIT("fait", "fait"), MERCI("merci", "merci"), REGARDE("regarde", "je regarde");

    private final String code;
    private final String mots;

    Reaction(String code, String mots) {
        this.code = code;
        this.mots = mots;
    }

    public String code() {
        return code;
    }

    public String mots() {
        return mots;
    }

    /** La réaction d'un code reçu ; un code inconnu est refusé en nommant ceux qui existent. */
    public static Reaction de(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst()
                .orElseThrow(() -> RefusCommunication.invalide("« reaction » : " + Arrays.stream(values()).map(Reaction::code).collect(Collectors.joining(", "))));
    }
}
