package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Le taux horaire d'une personne pour une date (ADR 0084) : celui de son dossier s'il est saisi (hors échelle), sinon
 * celui de l'échelle — la règle {@code taux-de-base} en vigueur ce jour-là qui la vise, la plus précise, puis la plus
 * récente. Vide : aucun taux, et la personne ne sera pas payée à 0 $ en silence.
 */
public final class TauxDeBase {

    private static final Comparator<ReglePaie> PREFERENCE = Comparator.comparingInt((ReglePaie r) -> r.conditions().precision())
            .thenComparing(ReglePaie::valideDu);

    private TauxDeBase() {
    }

    public static Optional<BigDecimal> resoudre(DossierRemuneration dossier, ProfilPaie profil, List<ReglePaie> regles, LocalDate jour) {
        if (dossier.tauxHoraire() != null) {
            return Optional.of(dossier.tauxHoraire());
        }
        return regles.stream().filter(r -> r.type() == TypeRegle.TAUX_DE_BASE && r.enVigueurLe(jour) && r.vise(profil))
                .max(PREFERENCE).map(ReglePaie::valeur);
    }
}
