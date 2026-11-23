package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Les paramètres d'une année de paie (Revenu Québec, ARC, CNESST). Une <strong>donnée</strong>, lue d'un fichier
 * par année : mettre la paie à jour au 1er janvier, c'est ajouter un barème, pas modifier le calcul (ADR 0076).
 * Les taux sont des fractions (0,063 pour 6,30 %).
 */
public record Bareme(int annee, Impot federal, Impot quebec, Rrq rrq, Assurance ae, Assurance rqap, Plafonne cnt,
                     BigDecimal tauxFssParDefaut, BigDecimal tauxCnesstParDefaut) {

    /** {@code jusqua} nul : la dernière tranche, sans borne. */
    public record Tranche(BigDecimal jusqua, BigDecimal taux) { }

    /**
     * @param montantEmploi    fédéral : montant canadien pour emploi ; Québec : plafond de la déduction pour travailleur
     * @param tauxEmploi       Québec : taux de la déduction pour travailleur (sans objet au fédéral : 0)
     * @param abattement       fédéral : abattement du Québec ; Québec : 0
     */
    public record Impot(List<Tranche> tranches, BigDecimal montantPersonnel, BigDecimal montantEmploi, BigDecimal tauxEmploi,
                        BigDecimal abattement) {

        /** Le taux du premier palier : celui des crédits non remboursables. */
        public BigDecimal tauxCredit() {
            return tranches.getFirst().taux();
        }
    }

    /**
     * @param tauxBase          part de base du taux (5,30 %), seule à ouvrir le crédit fédéral
     * @param tauxSupplementaire première cotisation supplémentaire (1,00 %), déductible du revenu
     * @param plafondSupplementaire au-delà du MGA, la tranche soumise à la deuxième cotisation (RRQ2)
     */
    public record Rrq(BigDecimal tauxBase, BigDecimal tauxSupplementaire, BigDecimal exemption, BigDecimal mga,
                      BigDecimal plafondSupplementaire, BigDecimal tauxRrq2) {

        public BigDecimal taux() {
            return tauxBase.add(tauxSupplementaire);
        }
    }

    /** AE et RQAP : un taux salarié, un taux employeur, un maximum de rémunération assurable. */
    public record Assurance(BigDecimal tauxEmploye, BigDecimal tauxEmployeur, BigDecimal maximumAssurable) { }

    /** Une cotisation de l'employeur plafonnée (normes du travail). */
    public record Plafonne(BigDecimal taux, BigDecimal maximum) { }
}
