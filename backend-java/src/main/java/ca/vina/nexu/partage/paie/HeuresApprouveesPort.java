package ca.vina.nexu.partage.paie;

import java.math.BigDecimal;
import java.util.List;

/**
 * Port : ce que la paie reçoit des feuilles de temps, et le verrou qu'elle y pose (ADR 0076). Seules les
 * feuilles <strong>approuvées</strong> entrent en paie ; une feuille payée ne se rappelle ni ne se rouvre.
 * Le port vit dans {@code partage} : {@code paie} dépend d'{@code effectifs} par lui, et jamais l'inverse.
 */
public interface HeuresApprouveesPort {

    /** Une heure régulière se paie au taux, une heure supplémentaire au taux majoré. */
    enum NatureHeures { REGULIERE, SUPPLEMENTAIRE }

    record LigneHeures(String date, String codePaie, NatureHeures nature, BigDecimal heures) { }

    record FeuilleAPayer(String feuilleId, String ressourceId, String matricule, String nom, String debut, List<LigneHeures> lignes) { }

    /** Les feuilles approuvées de l'organisme dont la semaine commence entre {@code debut} et {@code fin}, bornes comprises. */
    List<FeuilleAPayer> approuvees(String tenantId, String debut, String fin);

    /**
     * Passe ces feuilles à PAYEE pour ce cycle. Lève {@link FeuilleNonApprouvee} dès qu'une feuille ne l'est plus :
     * rappelée ou rouverte depuis la préparation, elle n'entre pas dans cette paie.
     */
    void marquerPayees(Verrou verrou, List<String> feuilleIds);

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
