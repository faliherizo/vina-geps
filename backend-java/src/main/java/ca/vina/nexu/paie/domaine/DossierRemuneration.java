package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;

/**
 * Ce qu'il faut savoir d'une personne pour la payer : comment (à l'heure ou au salaire), à quelle fréquence, à quel taux,
 * et les montants personnels qu'elle a déclarés (TP-1015.3-V au Québec, TD1 au fédéral ; à défaut, ceux de base).
 *
 * @param tauxHoraire un taux hors échelle ; nul, le taux vient de l'échelle (règles {@code taux-de-base}, ADR 0084).
 *                    Au salaire, il paie les heures supplémentaires.
 * @param echelon     l'échelon de la personne dans l'échelle de son titre d'emploi ; nul s'il n'y en a pas
 */
public record DossierRemuneration(String ressourceId, ModeRemuneration mode, Frequence frequence, BigDecimal tauxHoraire,
                                  BigDecimal salaireAnnuel, CalculRetenues.MontantsPersonnels montants, Integer echelon) {

    public DossierRemuneration {
        if (ressourceId == null || ressourceId.isBlank()) {
            throw RefusPaie.invalide("« ressourceId » est obligatoire");
        }
        exigerPositifOuNul(tauxHoraire, "« tauxHoraire »");
        exigerPositifOuNul(salaireAnnuel, "« salaireAnnuel »");
        exigerPositifOuNul(montants.federal(), "« montantPersonnelFederal »");
        exigerPositifOuNul(montants.quebec(), "« montantPersonnelQuebec »");
        if (echelon != null && (echelon < 1 || echelon > ConditionsRegle.ECHELON_MAXIMUM)) {
            throw RefusPaie.invalide("« echelon » : de 1 à " + ConditionsRegle.ECHELON_MAXIMUM);
        }
        switch (mode) {
            case HORAIRE -> exigerTaux(tauxHoraire, echelon);
            case SALAIRE -> exigerStrictementPositif(salaireAnnuel, "au salaire, « salaireAnnuel » est obligatoire et positif");
        }
    }

    /** À l'heure, un taux hors échelle ou un échelon : sans l'un ni l'autre, rien ne dit combien vaut une heure. */
    private static void exigerTaux(BigDecimal tauxHoraire, Integer echelon) {
        if (tauxHoraire == null && echelon == null) {
            throw RefusPaie.invalide("à l'heure, un « tauxHoraire » (hors échelle) ou un « echelon » (échelle salariale) est obligatoire");
        }
        if (tauxHoraire != null) {
            exigerStrictementPositif(tauxHoraire, "« tauxHoraire » est positif");
        }
    }

    private static void exigerPositifOuNul(BigDecimal v, String champ) {
        if (v != null && v.signum() < 0) {
            throw RefusPaie.invalide(champ + " ne peut pas être négatif");
        }
    }

    private static void exigerStrictementPositif(BigDecimal v, String message) {
        if (v == null || v.signum() <= 0) {
            throw RefusPaie.invalide(message);
        }
    }
}
