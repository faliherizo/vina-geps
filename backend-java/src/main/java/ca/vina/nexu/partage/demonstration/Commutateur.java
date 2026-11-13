package ca.vina.nexu.partage.demonstration;

import java.util.Objects;

/**
 * Un port du processus que la démonstration peut remplacer à chaud : le simulateur ou le
 * fournisseur configuré par défaut, le tiers embarqué une fois branché, et le retour au défaut au
 * débranchement. Le reste du code ne voit que {@link #courant()} — il ignore qu'on a permuté.
 */
public final class Commutateur<T> {

    private final T defaut;
    private volatile T courant;

    public Commutateur(T defaut) {
        this.defaut = Objects.requireNonNull(defaut);
        this.courant = defaut;
    }

    public T courant() {
        return courant;
    }

    public T defaut() {
        return defaut;
    }

    public void brancher(T remplacant) {
        courant = Objects.requireNonNull(remplacant);
    }

    public void debrancher() {
        courant = defaut;
    }

    public boolean estBranche() {
        return courant != defaut;
    }
}
