package ca.vina.nexu.partage.securite;

import java.util.Arrays;
import java.util.Optional;

/**
 * Les rôles **intégrés** d'un compte ; un établissement en définit d'autres (rôles dynamiques du catalogue), qui
 * restent un code libre dans {@link ContexteRequete#role()}. Le code est celui du jeton et de la base.
 */
public enum Role {
    SUPERADMIN("superadmin"), ADMIN("admin"), DIRECTOR("director"), MANAGER("manager"), RH("rh"), ACCOUNTANT("accountant"), EMPLOYEE("employee");

    private final String code;

    Role(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Voit l'établissement entier (pas seulement sa branche hiérarchique). */
    public boolean etablissementEntier() {
        return this == SUPERADMIN || this == ADMIN || this == DIRECTOR || this == RH;
    }

    public static Optional<Role> de(String code) {
        return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst();
    }
}
