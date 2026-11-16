package ca.vina.nexu.paie.domaine;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Une plage horaire quotidienne, en heure locale. Quand la fin précède le début, la plage passe minuit : 23 h – 7 h
 * commence un jour et finit le lendemain.
 */
public record PlageHoraire(LocalTime debut, LocalTime fin) {

    public PlageHoraire {
        if (debut == null || fin == null || debut.equals(fin)) {
            throw RefusPaie.invalide("une plage horaire a un début et une fin distincts (« heureDebut », « heureFin »)");
        }
    }

    /** La plage qui commence ce jour-là. */
    LocalDateTime debutLe(LocalDate jour) {
        return jour.atTime(debut);
    }

    LocalDateTime finPourDebutLe(LocalDate jour) {
        return fin.isAfter(debut) ? jour.atTime(fin) : jour.plusDays(1).atTime(fin);
    }
}
