package ca.vina.nexu.paie.domaine;

import java.util.function.Supplier;

/** Port : un geste en tout ou rien. Traiter une paie fige le cycle et ses feuilles ensemble, ou rien du tout. */
public interface UniteDeTravail {
    <T> T executer(Supplier<T> geste);
}
