package ca.vina.nexu.pays.application.requetes;

import ca.vina.nexu.pays.domaine.FormatsOfficiels;
import ca.vina.nexu.pays.domaine.MoteurImpot;
import ca.vina.nexu.pays.domaine.MoteurPaie;
import ca.vina.nexu.pays.domaine.MoteurRemise;
import ca.vina.nexu.pays.domaine.Pays;
import ca.vina.nexu.pays.domaine.PaysPack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Les moteurs génériques d'un pays : paie, impôt, remise et format officiel — des calculs, sans écriture. */
public final class CalculsPays {

    private final Pays.Catalogue catalogue;

    public CalculsPays(Pays.Catalogue catalogue) {
        this.catalogue = catalogue;
    }

    public MoteurPaie.ResultatPaie paie(String code, Map<String, Object> corps) {
        PaysPack p = Pays.exiger(catalogue, code);
        return MoteurPaie.calculer(p, Pays.positif(corps.get("annualGross"), "annualGross must be a positive number"), Pays.periodes(corps.get("periodsPerYear")));
    }

    public Object impot(String code, Map<String, Object> corps) {
        PaysPack p = Pays.exiger(catalogue, code);
        String profil = "corporate".equals(corps.get("profile")) ? "corporate" : "individual";
        return MoteurImpot.calculer(p, profil, Pays.positif(corps.get("taxableIncome"), "taxableIncome must be a positive number"));
    }

    public Object remise(String code, Map<String, Object> corps) {
        PaysPack p = Pays.exiger(catalogue, code);
        List<MoteurPaie.ResultatPaie> paies = new ArrayList<>();
        for (Map<String, Object> e : salaries(corps)) {
            paies.add(MoteurPaie.calculer(p, Pays.positif(e.get("annualGross"), "each employee needs a positive annualGross"), Pays.periodes(e.get("periodsPerYear"))));
        }
        return MoteurRemise.calculer(p, paies);
    }

    @SuppressWarnings("unchecked")
    public Object remiseOfficielle(String code, Map<String, Object> corps) {
        PaysPack p = Pays.exiger(catalogue, code);
        if (!FormatsOfficiels.aUnFormatOfficiel(p.code())) {
            throw new IllegalArgumentException("format officiel non disponible pour " + p.code() + " (Canada : voir SAGIR/SAGIP + TPZ-1015/PD7A)");
        }
        List<FormatsOfficiels.Salarie> salaries = new ArrayList<>();
        for (Map<String, Object> e : salaries(corps)) {
            salaries.add(new FormatsOfficiels.Salarie(e.get("name") instanceof String s ? s : null, e.get("id") instanceof String s ? s : null,
                    MoteurPaie.calculer(p, Pays.positif(e.get("annualGross"), "each employee needs a positive annualGross"), Pays.periodes(e.get("periodsPerYear")))));
        }
        Map<String, String> employeur = corps.get("employer") instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of();
        return FormatsOfficiels.generer(p, salaries, employeur, corps.get("periodLabel") instanceof String s ? s : null);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> salaries(Map<String, Object> corps) {
        List<Map<String, Object>> l = corps.get("employees") instanceof List<?> v ? (List<Map<String, Object>>) v : List.of();
        if (l.isEmpty()) {
            throw new IllegalArgumentException("employees[] (annualGross) required");
        }
        return l;
    }
}
