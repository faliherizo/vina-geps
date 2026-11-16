package ca.vina.nexu.partage.paie;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Port : ce que la paie reçoit des feuilles de temps, et le verrou qu'elle y pose (ADR 0076). Seules les
 * feuilles <strong>approuvées</strong> entrent en paie ; une feuille payée ne se rappelle ni ne se rouvre.
 * Le port vit dans {@code partage} : {@code paie} dépend d'{@code effectifs} par lui, et jamais l'inverse.
 * L'ADR 0084 y ajoute les plages en heure locale (primes de soir, de nuit, de fin de semaine), les jours fériés, la
 * vue de toutes les feuilles d'une période (validation des temps) et le geste « retourner ».
 */
public interface HeuresApprouveesPort {

    /** Une heure régulière se paie au taux, une heure supplémentaire au taux majoré. */
    enum NatureHeures { REGULIERE, SUPPLEMENTAIRE }

    /** L'état d'une feuille, dans le vocabulaire du port : la paie n'a pas à connaître celui des effectifs. */
    enum EtatFeuille { BROUILLON, SOUMISE, RETOURNEE, APPROUVEE, PAYEE }

    record LigneHeures(String date, String codePaie, NatureHeures nature, BigDecimal heures) { }

    /** Un segment payé, en heure de Montréal : c'est l'heure locale qui décide d'une prime de soir ou de nuit. */
    record Plage(LocalDateTime debut, LocalDateTime fin, String codePaie) { }

    record FeuilleAPayer(String feuilleId, String ressourceId, String matricule, String nom, String debut, List<LigneHeures> lignes,
                         List<Plage> plages) {

        public FeuilleAPayer {
            lignes = List.copyOf(lignes);
            plages = List.copyOf(plages);
        }
    }

    /**
     * Une feuille de la période, quel que soit son état. {@code heures} est vide quand les effectifs ne savent pas la
     * coder, et {@code incalculable} en dit alors la raison.
     */
    record FeuilleDeLaPeriode(String feuilleId, String ressourceId, String matricule, String nom, String debut, EtatFeuille etat,
                              Optional<FeuilleAPayer> heures, String incalculable) { }

    /** Les feuilles approuvées de l'organisme dont la semaine commence entre {@code debut} et {@code fin}, bornes comprises. */
    List<FeuilleAPayer> approuvees(String tenantId, String debut, String fin);

    /** Toutes les feuilles de la période, approuvées ou non : ce que la paie contrôle avant de valider. */
    List<FeuilleDeLaPeriode> feuilles(String tenantId, String debut, String fin);

    /** Une feuille de l'organisme, si elle existe. */
    Optional<FeuilleDeLaPeriode> feuille(String tenantId, String feuilleId);

    /** Les jours fériés du calendrier de l'organisme entre deux dates, bornes comprises. */
    Set<LocalDate> joursFeries(String tenantId, LocalDate debut, LocalDate fin);

    /**
     * Passe ces feuilles à PAYEE pour ce cycle. Lève {@link FeuilleNonApprouvee} dès qu'une feuille ne l'est plus :
     * rappelée ou rouverte depuis la préparation, elle n'entre pas dans cette paie.
     */
    void marquerPayees(Verrou verrou, List<String> feuilleIds);

    /**
     * Renvoie une feuille approuvée à la personne, avec ce qu'il faut corriger (ADR 0084). Lève
     * {@link FeuilleNonApprouvee} si elle n'est plus approuvée — payée, ou déjà rouverte.
     */
    void retourner(Verrou verrou, String feuilleId, String motif);

    /** {@code cycleId} est nul quand le geste ne vient pas d'un cycle (retourner une feuille). */
    record Verrou(String tenantId, String acteurId, String instant, String cycleId) { }

    /**
     * Les heures d'une feuille ne se calculent pas (un paramétrage des effectifs manque : type d'heure, seuil) : le motif
     * des effectifs, dans le vocabulaire du port. La paie le rend tel quel à l'administration, au lieu d'une erreur 500.
     */
    final class HeuresIncalculables extends RuntimeException {
        public HeuresIncalculables(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** Le refus du verrou, dans le vocabulaire du port : la paie n'a pas à connaître les exceptions des effectifs. */
    final class FeuilleNonApprouvee extends RuntimeException {
        public FeuilleNonApprouvee(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
