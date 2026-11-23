package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.domaine.Bareme;
import ca.vina.nexu.paie.domaine.CalculRetenues;
import ca.vina.nexu.paie.domaine.ConditionsRegle;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.Frequence;
import ca.vina.nexu.paie.domaine.ModeRemuneration;
import ca.vina.nexu.paie.domaine.PlageHoraire;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.paie.domaine.ReglePaie;
import ca.vina.nexu.paie.domaine.TypeRegle;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                decimalOu(c, "montantPersonnelFederal", base.federal()), decimalOu(c, "montantPersonnelQuebec", base.quebec())),
                entier(c, "echelon"));
    }

    static ReglagesPaie reglages(Map<String, Object> c) {
        return new ReglagesPaie(decimal(c, "tauxFss"), decimal(c, "tauxCnesst"), decimal(c, "majorationSupplementaire"),
                booleen(c, "validationTempsExigee", false));
    }

    /** Une règle de paie variable (ADR 0084) ; l'identifiant vient de la route ou du serveur, jamais du corps. */
    static ReglePaie regle(Map<String, Object> c) {
        return new ReglePaie(null, texte(c, "code"), texte(c, "libelle"), TypeRegle.de(c.get("type")), decimal(c, "valeur"),
                conditions(c), date(c, "valideDu"), date(c, "valideAu"), booleen(c, "actif", true));
    }

    private static ConditionsRegle conditions(Map<String, Object> c) {
        String debut = vide(texte(c, "heureDebut"));
        String fin = vide(texte(c, "heureFin"));
        PlageHoraire plage = debut == null && fin == null ? null : new PlageHoraire(heure(debut, "heureDebut"), heure(fin, "heureFin"));
        return new ConditionsRegle(vide(texte(c, "titreEmploi")), vide(texte(c, "convention")), entier(c, "echelon"), plage, jours(c),
                booleen(c, "feriesSeulement", false), Set.copyOf(textes(c, "codesPaie")));
    }

    /** Les jours de la semaine, en numéros ISO : 1 pour lundi, 7 pour dimanche. */
    private static Set<DayOfWeek> jours(Map<String, Object> c) {
        Set<DayOfWeek> jours = new HashSet<>();
        for (Object v : liste(c, "jours")) {
            if (!(v instanceof Number n) || n.intValue() < 1 || n.intValue() > DayOfWeek.values().length) {
                throw RefusPaie.invalide("« jours » : des numéros de 1 (lundi) à 7 (dimanche)");
            }
            jours.add(DayOfWeek.of(n.intValue()));
        }
        return jours;
    }

    private static List<String> textes(Map<String, Object> c, String champ) {
        return liste(c, champ).stream().map(v -> {
            if (!(v instanceof String t) || t.isBlank()) {
                throw RefusPaie.invalide("« " + champ + " » : une liste de codes");
            }
            return t.strip();
        }).toList();
    }

    private static List<?> liste(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v == null) {
            return List.of();
        }
        if (!(v instanceof List<?> l)) {
            throw RefusPaie.invalide("« " + champ + " » doit être une liste");
        }
        return l;
    }

    private static LocalTime heure(String v, String champ) {
        try {
            return LocalTime.parse(v == null ? "" : v);
        } catch (DateTimeException e) {
            throw RefusPaie.invalide("« " + champ + " » : une heure HH:MM");
        }
    }

    private static LocalDate date(Map<String, Object> c, String champ) {
        String v = vide(texte(c, champ));
        try {
            return v == null ? null : LocalDate.parse(v);
        } catch (DateTimeException e) {
            throw RefusPaie.invalide("« " + champ + " » : une date AAAA-MM-JJ");
        }
    }

    static Integer entier(Map<String, Object> c, String champ) {
        Object v = c.get(champ);
        if (v == null || v instanceof String t && t.isBlank()) {
            return null;
        }
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue())) {
            return n.intValue();
        }
        throw RefusPaie.invalide("« " + champ + " » doit être un entier");
    }

    static boolean booleen(Map<String, Object> c, String champ, boolean parDefaut) {
        Object v = c.get(champ);
        if (v == null) {
            return parDefaut;
        }
        if (!(v instanceof Boolean b)) {
            throw RefusPaie.invalide("« " + champ + " » doit être vrai ou faux");
        }
        return b;
    }

    private static String vide(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    /** Une période à contrôler : les mêmes bornes qu'un cycle, sans date de paie. */
    static CyclePaie.Periode periodeTemps(String debut, String fin) {
        return new CyclePaie.Periode(debut, fin, fin);
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
