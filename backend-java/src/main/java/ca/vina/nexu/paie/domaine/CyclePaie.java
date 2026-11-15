package ca.vina.nexu.paie.domaine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * Un cycle de paie : une période de travail et une date de paie. Préparé, il se recalcule ou s'annule ; traité, il est
 * figé — et les feuilles qu'il a payées avec lui.
 */
public record CyclePaie(String id, String debut, String fin, String datePaie, EtatCycle etat, String prepareLe,
                        String preparePar, String traiteLe, String traitePar) {

    /** Un mois et un jour : au-delà, ce n'est plus une période de paie, quelle que soit la fréquence. */
    static final long JOURS_MAXIMUM = 31;

    /** Les dates d'un cycle à préparer, vérifiées : ISO, dans l'ordre, une période d'au plus un mois, payée après son début. */
    public record Periode(String debut, String fin, String datePaie) {

        public Periode {
            LocalDate d = date(debut, "debut");
            LocalDate f = date(fin, "fin");
            LocalDate p = date(datePaie, "datePaie");
            if (f.isBefore(d) || ChronoUnit.DAYS.between(d, f) > JOURS_MAXIMUM) {
                throw RefusPaie.invalide("la période va de « debut » à « fin », sur un mois au plus");
            }
            if (p.isBefore(d)) {
                throw RefusPaie.invalide("la date de paie ne précède pas le début de la période");
            }
        }

        /** L'année civile de la paie : c'est elle qui choisit le barème et le cumul. */
        public int annee() {
            return LocalDate.parse(datePaie).getYear();
        }
    }

    public static CyclePaie prepare(String id, Periode periode, Trace trace) {
        return new CyclePaie(id, periode.debut(), periode.fin(), periode.datePaie(), EtatCycle.PREPARE, trace.instant(), trace.acteur(), null, null);
    }

    public Periode periode() {
        return new Periode(debut, fin, datePaie);
    }

    /** Recalculer, traiter, annuler : seul un cycle préparé s'y prête. */
    public void exigerPrepare() {
        switch (etat) {
            case PREPARE -> { }
            case TRAITE -> throw RefusPaie.conflit("cycle traité : il est figé, avec les feuilles qu'il a payées");
            case ANNULE -> throw RefusPaie.conflit("cycle annulé : préparez-en un nouveau");
        }
    }

    private static LocalDate date(String valeur, String champ) {
        try {
            return LocalDate.parse(valeur == null ? "" : valeur);
        } catch (DateTimeParseException e) {
            throw RefusPaie.invalide("« " + champ + " » : une date AAAA-MM-JJ");
        }
    }
}
