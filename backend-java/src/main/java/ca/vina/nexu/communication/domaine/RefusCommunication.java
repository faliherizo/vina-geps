package ca.vina.nexu.communication.domaine;

/**
 * Le refus d'un geste de la communication, porté par sa <strong>nature</strong> et non par son texte : la
 * traduction en statut HTTP se fait sur le type, une seule fois, à la frontière web. Le message reste
 * celui du Node, mot pour mot — l'interface l'affiche tel quel.
 */
public final class RefusCommunication extends RuntimeException {

    /** Pourquoi le geste est refusé ; le statut HTTP en découle à la frontière. */
    public enum Nature {
        /** La saisie est fautive : elle se corrige en changeant la demande. */
        INVALIDE,
        /** Le lecteur n'a pas le droit : la saisie n'est pas en cause. */
        INTERDIT,
        /** L'objet visé n'existe pas, ou pas dans cet établissement. */
        INTROUVABLE,
        /** L'état courant empêche le geste : il se corrige en changeant l'état. */
        CONFLIT
    }

    private final Nature nature;

    private RefusCommunication(Nature nature, String message) {
        super(message);
        this.nature = nature;
    }

    public Nature nature() {
        return nature;
    }

    public static RefusCommunication invalide(String message) {
        return new RefusCommunication(Nature.INVALIDE, message);
    }

    public static RefusCommunication interdit(String message) {
        return new RefusCommunication(Nature.INTERDIT, message);
    }

    public static RefusCommunication introuvable(String message) {
        return new RefusCommunication(Nature.INTROUVABLE, message);
    }

    public static RefusCommunication conflit(String message) {
        return new RefusCommunication(Nature.CONFLIT, message);
    }
}
