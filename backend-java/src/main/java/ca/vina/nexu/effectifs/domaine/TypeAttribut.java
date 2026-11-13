package ca.vina.nexu.effectifs.domaine;

import java.util.Locale;

/** La nature d'un attribut de référentiel : ce que le logiciel sait valider. */
public enum TypeAttribut {
    TEXTE, NOMBRE, BOOLEEN, REFERENCE, REFERENCES, CHOIX;

    /** Le code du contrat HTTP, en minuscules comme le Node ({@code "reference"}, {@code "choix"}…). */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
