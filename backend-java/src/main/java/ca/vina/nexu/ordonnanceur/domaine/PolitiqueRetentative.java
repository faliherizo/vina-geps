package ca.vina.nexu.ordonnanceur.domaine;

import java.util.List;

/**
 * Combien de fois retenter, en attendant combien, et quand un verrou est réputé abandonné.
 * Trois retentatives à une, cinq et quinze minutes : un texto qui échoue une fois échoue pour
 * une raison passagère ; trois fois de suite, c'est à quelqu'un de le voir.
 */
public record PolitiqueRetentative(int tentativesMax, List<Integer> attenteMinutes, int verrouPerimeMinutes) {

    public static final PolitiqueRetentative PAR_DEFAUT = new PolitiqueRetentative(3, List.of(1, 5, 15), 10);

    public PolitiqueRetentative {
        attenteMinutes = List.copyOf(attenteMinutes);
    }
}
