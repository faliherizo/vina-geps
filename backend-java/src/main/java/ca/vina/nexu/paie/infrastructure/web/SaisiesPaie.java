package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.CalculRetenues;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.Frequence;
import ca.vina.nexu.paie.domaine.ModeRemuneration;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.ReglagesPaie;

import java.math.BigDecimal;
import java.util.Map;

/**
 * La frontière : un corps JSON devient des valeurs typées, <strong>une fois</strong>. Un montant est un nombre ou un
 * texte décimal ; tout autre type, ou un code inconnu, répond 400 en nommant le champ.
 */
final class SaisiesPaie {

    private SaisiesPaie() {
    }

    static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }

    static String texte(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v != null && !(v instanceof String)) {
            throw RefusPaie.invalide("« " + champ + " » doit être un texte");
        }
        return (String) v;
    }

    /** Un montant facultatif : absent ou vide, il est nul. */
    static BigDecimal decimal(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v == null || v instanceof String t && t.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(v.toString());
        } catch (NumberFormatException e) {
            throw RefusPaie.invalide("« " + champ + " » doit être un nombre");
        }
    }

    static BigDecimal decimalOu(Map<String, Object> c, String champ, BigDecimal defaut) {
        BigDecimal v = decimal(c, champ);
        return v == null ? defaut : v;
    }

    /** Les montants personnels absents valent ceux de base du barème de l'année (TP-1015.3-V et TD1 non remis). */
    static DossierRemuneration dossier(String ressourceId, Map<String, Object> c, Bareme b) {
        CalculRetenues.MontantsPersonnels base = CalculRetenues.MontantsPersonnels.deBase(b);
        return new DossierRemuneration(ressourceId, ModeRemuneration.de(c.get("mode")), Frequence.de(c.get("frequence")),
                decimal(c, "tauxHoraire"), decimal(c, "salaireAnnuel"), new CalculRetenues.MontantsPersonnels(
                decimalOu(c, "montantPersonnelFederal", base.federal()), decimalOu(c, "montantPersonnelQuebec", base.quebec())));
    }

    static ReglagesPaie reglages(Map<String, Object> c) {
        return new ReglagesPaie(decimal(c, "tauxFss"), decimal(c, "tauxCnesst"), decimal(c, "majorationSupplementaire"));
    }

    static CyclePaie.Periode periode(Map<String, Object> c) {
        return new CyclePaie.Periode(texte(c, "debut"), texte(c, "fin"), texte(c, "datePaie"));
    }

    static int annee(String valeur, int parDefaut) {
        if (valeur == null || valeur.isEmpty()) {
            return parDefaut;
        }
        try {
            return Integer.parseInt(valeur);
        } catch (NumberFormatException e) {
            throw RefusPaie.invalide("« annee » doit être une année, par exemple 2026");
        }
    }
}
