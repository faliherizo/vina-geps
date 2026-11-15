package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * Ce qu'il faut savoir d'une personne pour la payer : comment (à l'heure ou au salaire), à quelle fréquence, à quel taux,
 * et les montants personnels qu'elle a déclarés (TP-1015.3-V au Québec, TD1 au fédéral ; à défaut, ceux de base).
 *
 * @param tauxHoraire obligatoire à l'heure ; au salaire, il paie les heures supplémentaires (nul : elles ne se paient pas)
 */
public record DossierRemuneration(String ressourceId, ModeRemuneration mode, Frequence frequence, BigDecimal tauxHoraire,
                                  BigDecimal salaireAnnuel, CalculRetenues.MontantsPersonnels montants) {

    public DossierRemuneration {
        if (ressourceId == null || ressourceId.isBlank()) {
            throw RefusPaie.invalide("« ressourceId » est obligatoire");
        }
        exigerPositifOuNul(tauxHoraire, "« tauxHoraire »");
        exigerPositifOuNul(salaireAnnuel, "« salaireAnnuel »");
        exigerPositifOuNul(montants.federal(), "« montantPersonnelFederal »");
        exigerPositifOuNul(montants.quebec(), "« montantPersonnelQuebec »");
        switch (mode) {
            case HORAIRE -> exigerStrictementPositif(tauxHoraire, "à l'heure, « tauxHoraire » est obligatoire et positif");
            case SALAIRE -> exigerStrictementPositif(salaireAnnuel, "au salaire, « salaireAnnuel » est obligatoire et positif");
        }
    }

    /** Le brut d'une paie : les heures au taux, les supplémentaires majorées ; au salaire, la part de l'année. */
    public BigDecimal brut(BigDecimal heuresRegulieres, BigDecimal heuresSupplementaires, BigDecimal majoration) {
        BigDecimal supplementaires = tauxHoraire == null ? BigDecimal.ZERO
                : heuresSupplementaires.multiply(tauxHoraire).multiply(BigDecimal.ONE.add(majoration));
        BigDecimal base = switch (mode) {
            case HORAIRE -> heuresRegulieres.multiply(tauxHoraire);
            case SALAIRE -> salaireAnnuel.divide(BigDecimal.valueOf(frequence.paiesParAn()), MathContext.DECIMAL64);
        };
        return CalculRetenues.cent(base.add(supplementaires));
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
