package ca.vina.nexu.paie.domaine;

import java.util.Optional;

/** Port : le barème d'une année civile, s'il est publié. */
@FunctionalInterface
public interface Baremes {
    Optional<Bareme> pour(int annee);
}
