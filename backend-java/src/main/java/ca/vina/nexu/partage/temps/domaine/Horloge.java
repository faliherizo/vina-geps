package ca.vina.nexu.partage.temps.domaine;

/** Port : l'instant courant en ISO-8601 — injecté, jamais lu du système par le métier. */
public interface Horloge {
    String maintenant();

    default String aujourdhui() {
        return maintenant().substring(0, 10);
    }
}
