package ca.vina.nexu.effectifs.domaine;

/** Les opérateurs d'une comparaison, avec le code que le paramétrage (et le Node) emploie. */
public enum OperateurRegle {
    EGAL("="), DIFFERENT("!="), INFERIEUR("<"), INFERIEUR_OU_EGAL("<="), SUPERIEUR(">"), SUPERIEUR_OU_EGAL(">="), DANS("dans"), HORS("hors");

    private final String code;

    OperateurRegle(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static OperateurRegle de(Object v) {
        return Regles.enumDe(values(), OperateurRegle::code, v, "opérateur");
    }

    /** Un opérateur d'ordre : il exige un fait ordonnable. */
    public boolean ordonne() {
        return this == INFERIEUR || this == INFERIEUR_OU_EGAL || this == SUPERIEUR || this == SUPERIEUR_OU_EGAL;
    }

    /** Un opérateur d'appartenance : il attend une liste de valeurs. */
    public boolean ensembliste() {
        return this == DANS || this == HORS;
    }
}
