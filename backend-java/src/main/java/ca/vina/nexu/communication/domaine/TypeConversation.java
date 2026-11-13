package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.Optional;

/** La forme d'une conversation : à deux, dont la composition est figée, ou en groupe, modérée. */
public enum TypeConversation {
    INDIVIDUELLE("individuelle"), GROUPE("groupe");

    private final String code;

    TypeConversation(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<TypeConversation> de(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }

    public boolean est(String code) {
        return this.code.equals(code);
    }
}
