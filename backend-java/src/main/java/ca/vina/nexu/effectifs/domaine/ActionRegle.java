package ca.vina.nexu.effectifs.domaine;

/**
 * L'action d'une règle. <strong>L'ordre des constantes est la sévérité</strong>, du moins au plus restrictif :
 * il décide de la combinaison (l'action la plus restrictive l'emporte) et de ce qui bloque.
 */
public enum ActionRegle {
    ACCEPTER, AVERTIR, EXIGER_APPROBATION, EXCLURE_DU_BASSIN, BLOQUER, REFUSER;

    public String code() {
        return name();
    }

    public static ActionRegle de(Object v) {
        return Regles.enumDe(values(), ActionRegle::code, v, "action");
    }

    public ActionRegle laPlusRestrictive(ActionRegle autre) {
        return autre.ordinal() > ordinal() ? autre : this;
    }

    /** À partir de l'exclusion du bassin, la demande ne passe pas : en deçà, elle passe avec réserve. */
    public boolean estBloquant() {
        return ordinal() >= EXCLURE_DU_BASSIN.ordinal();
    }
}
