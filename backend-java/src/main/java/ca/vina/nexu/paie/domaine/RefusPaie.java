package ca.vina.nexu.paie.domaine;

/** Un refus du domaine de la paie, avec sa nature : c'est elle, et non le message, qui fait le code HTTP. */
public final class RefusPaie extends RuntimeException {

    public enum Nature { INVALIDE, INTROUVABLE, CONFLIT }

    private final Nature nature;

    private RefusPaie(Nature nature, String message) {
        super(message);
        this.nature = nature;
    }

    public Nature nature() {
        return nature;
    }

    public static RefusPaie invalide(String message) {
        return new RefusPaie(Nature.INVALIDE, message);
    }

    public static RefusPaie introuvable(String message) {
        return new RefusPaie(Nature.INTROUVABLE, message);
    }

    public static RefusPaie conflit(String message) {
        return new RefusPaie(Nature.CONFLIT, message);
    }
}
