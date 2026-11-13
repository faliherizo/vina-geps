package ca.vina.nexu.notifications.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** Qui reçoit une notification du journal : un professeur du module d'enseignement, un établissement, un employé du dossier. */
public enum TypeDestinataire {
    TEACHER("teacher"), ESTABLISHMENT("establishment"), EMPLOYEE("employee");

    private final String code;

    TypeDestinataire(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Une personne (professeur ou employé), par opposition à un établissement : ses préférences s'appliquent. */
    public boolean personne() {
        return this != ESTABLISHMENT;
    }

    public static Optional<TypeDestinataire> de(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }

    public static final List<String> CODES = Arrays.stream(values()).map(TypeDestinataire::code).toList();
}
