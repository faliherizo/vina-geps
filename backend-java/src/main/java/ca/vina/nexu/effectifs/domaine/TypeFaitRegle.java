package ca.vina.nexu.effectifs.domaine;

import java.util.regex.Pattern;

/**
 * Le type d'un fait du moteur de règles (lot R2). Il décide des valeurs qu'une condition peut lui opposer
 * et des opérateurs qui ont un sens : comparer « plus petit que » deux corps d'emploi ne veut rien dire.
 */
public enum TypeFaitRegle {
    NOMBRE("nombre"), TEXTE("texte"), BOOLEEN("booleen"), DATE("date");

    private static final Pattern FORMAT_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final String code;

    TypeFaitRegle(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Une valeur de ce type ? Un nombre infini ou une date mal formée ne passent pas : ils fausseraient la comparaison. */
    public boolean admet(Object v) {
        return switch (this) {
            case NOMBRE -> v instanceof Number n && Double.isFinite(n.doubleValue());
            case TEXTE -> v instanceof String;
            case BOOLEEN -> v instanceof Boolean;
            case DATE -> v instanceof String s && FORMAT_DATE.matcher(s).matches();
        };
    }

    /** Les opérateurs d'ordre ne valent que pour un nombre ou une date (AAAA-MM-JJ s'ordonne comme un texte). */
    public boolean ordonnable() {
        return this == NOMBRE || this == DATE;
    }
}
