package ca.vina.nexu.communication.domaine;

import java.util.Arrays;
import java.util.Optional;

/** Le rôle d'un participant : le modérateur gère la composition d'un groupe, le membre y écrit. */
public enum RoleParticipant {
    MODERATEUR("moderateur"), MEMBRE("membre");

    private final String code;

    RoleParticipant(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<RoleParticipant> de(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }

    public boolean est(String code) {
        return this.code.equals(code);
    }
}
