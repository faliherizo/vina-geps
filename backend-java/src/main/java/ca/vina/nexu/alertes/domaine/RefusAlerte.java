package ca.vina.nexu.alertes.domaine;

/**
 * Le refus d'un geste sur une alerte, porté par sa <strong>nature</strong> : le statut HTTP en découle
 * une seule fois, à la frontière web. Le message est montré tel quel à l'écran.
 */
public final class RefusAlerte extends RuntimeException {

    /** Pourquoi le geste est refusé. */
    public enum Nature {
        /** La saisie est fautive : elle se corrige en changeant la demande. */
        INVALIDE,
        /** L'objet visé n'existe pas, ou n'est pas lisible par cette personne (jamais « interdit » : ce serait avouer qu'il existe). */
        INTROUVABLE,
        /** L'état courant empêche le geste. */
        CONFLIT,
        /** L'acteur n'a pas le droit de ce geste-là (la priorité critique sans la capacité qui l'ouvre). */
        INTERDIT
    }

    private final Nature nature;

    private RefusAlerte(Nature nature, String message) {
        super(message);
        this.nature = nature;
    }

    public Nature nature() {
        return nature;
    }

    public static RefusAlerte invalide(String message) {
        return new RefusAlerte(Nature.INVALIDE, message);
    }

    public static RefusAlerte introuvable(String message) {
        return new RefusAlerte(Nature.INTROUVABLE, message);
    }

    public static RefusAlerte conflit(String message) {
        return new RefusAlerte(Nature.CONFLIT, message);
    }

    public static RefusAlerte interdit(String message) {
        return new RefusAlerte(Nature.INTERDIT, message);
    }
}
