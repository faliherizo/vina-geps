package ca.vina.nexu.paie.domaine;

import ca.vina.nexu.partage.paie.HeuresApprouveesPort.Plage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Les minutes d'un segment qui ouvrent droit à une prime, par jour civil (ADR 0084). Un jour compte si le prédicat
 * l'admet (jour de semaine, férié, validité de la règle). On y compte le chevauchement du segment avec la plage
 * horaire de ce jour, ou avec le jour entier quand la règle n'a pas de plage. Un quart de 22 h à 6 h, de samedi à
 * dimanche, compte donc 2 h le samedi et 6 h le dimanche.
 */
public final class HeuresAdmissibles {

    private HeuresAdmissibles() {
    }

    /** {@code plage} nulle : toute la journée. */
    public static Map<LocalDate, Long> minutes(Plage segment, PlageHoraire plage, Predicate<LocalDate> jourAdmis) {
        Map<LocalDate, Long> parJour = new TreeMap<>();
        if (!segment.fin().isAfter(segment.debut())) {
            return parJour;
        }
        // La plage qui commence la veille peut déborder sur le premier jour du segment (une nuit de 23 h à 7 h).
        for (LocalDate jour = segment.debut().toLocalDate().minusDays(1); !jour.isAfter(segment.fin().toLocalDate()); jour = jour.plusDays(1)) {
            LocalDateTime debut = plage == null ? jour.atStartOfDay() : plage.debutLe(jour);
            LocalDateTime fin = plage == null ? jour.plusDays(1).atStartOfDay() : plage.finPourDebutLe(jour);
            repartir(max(debut, segment.debut()), min(fin, segment.fin()), jourAdmis, parJour);
        }
        return parJour;
    }

    /** Le chevauchement, coupé à minuit : chaque morceau va au jour civil où il tombe, si ce jour est admis. */
    private static void repartir(LocalDateTime debut, LocalDateTime fin, Predicate<LocalDate> jourAdmis, Map<LocalDate, Long> parJour) {
        LocalDateTime curseur = debut;
        while (curseur.isBefore(fin)) {
            LocalDateTime minuit = curseur.toLocalDate().plusDays(1).atStartOfDay();
            LocalDateTime borne = min(minuit, fin);
            if (jourAdmis.test(curseur.toLocalDate())) {
                parJour.merge(curseur.toLocalDate(), Duration.between(curseur, borne).toMinutes(), Long::sum);
            }
            curseur = borne;
        }
    }

    private static LocalDateTime max(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
