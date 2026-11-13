package ca.vina.nexu.effectifs.domaine;

import java.util.Map;

/** Ce qui change d'une version de règle à l'autre : condition, action, message, priorité. */
public record ContenuRegle(ConditionRegle condition, ActionRegle action, String message, int priorite) {

    public static final String CONDITION = "condition";
    public static final String ACTION = "action";
    public static final String MESSAGE = "message";
    public static final String PRIORITE = "priorite";

    /** Valide une saisie dans l'ordre du Node : la condition d'abord, parce que c'est elle qu'on se trompe à écrire. */
    public static ContenuRegle valider(ContexteRegle contexte, Map<String, Object> entree) {
        return new ContenuRegle(ConditionsRegles.valider(contexte, entree.get(CONDITION)), ActionRegle.de(entree.get(ACTION)),
                Regles.messageValide(entree.get(MESSAGE)), Regles.prioriteValide(entree.get(PRIORITE)));
    }
}
