package ca.vina.nexu.effectifs.domaine;

import java.util.Map;

/**
 * Les seuils d'heures supplémentaires propres à une convention collective (AD-03 ; addenda 7, Q15 ; ADR 0087). Un seuil nul
 * reprend celui de l'organisme ; une surcharge sans aucun seuil n'en est pas une, elle se retire.
 */
public record SeuilsConvention(String conventionCode, Integer seuilJourHeures, Integer seuilSemaineHeures, Integer seuilPeriodePaieHeures) {

    private static final int JOUR_MAX = 24;
    private static final int SEMAINE_MAX = 168;
    private static final int PERIODE_PAIE_MAX = 744;

    /** Aucun seuil : la convention suit l'organisme. */
    public boolean estVide() {
        return seuilJourHeures == null && seuilSemaineHeures == null && seuilPeriodePaieHeures == null;
    }

    /** Les seuils d'une convention, lus d'un corps : absent ou nul = celui de l'organisme ; sinon un entier dans ses bornes. */
    public static SeuilsConvention de(String conventionCode, Map<String, Object> brut) {
        return new SeuilsConvention(conventionCode, seuil(brut, "seuilJourHeures", JOUR_MAX), seuil(brut, "seuilSemaineHeures", SEMAINE_MAX),
                seuil(brut, "seuilPeriodePaieHeures", PERIODE_PAIE_MAX));
    }

    private static Integer seuil(Map<String, Object> brut, String cle, int max) {
        Object v = brut.get(cle);
        if (v == null) {
            return null;
        }
        if (!(v instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue()) || n.doubleValue() < 1 || n.doubleValue() > max) {
            throw new EffectifsInvalideException("« " + cle + " » : entier de 1 à " + max + ", ou vide pour celui de l'organisme");
        }
        return n.intValue();
    }
}
