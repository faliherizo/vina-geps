package ca.vina.nexu.pays.application.requetes;

import ca.vina.nexu.pays.application.dto.TaxeDeVenteDto;
import ca.vina.nexu.pays.domaine.Arrondi;
import ca.vina.nexu.pays.domaine.Pays;
import ca.vina.nexu.pays.domaine.PaysPack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Le catalogue public des pays hôtes, la fiche d'un pays et sa taxe de vente. */
public final class LecturePays {

    private final Pays.Catalogue catalogue;

    public LecturePays(Pays.Catalogue catalogue) {
        this.catalogue = catalogue;
    }

    public Map<String, Object> catalogue() {
        List<Map<String, Object>> pays = new ArrayList<>();
        for (PaysPack p : catalogue.tous()) {
            Map<String, Object> vue = new LinkedHashMap<>();
            vue.put("code", p.code());
            vue.put("name", p.name());
            vue.put("flag", p.flag());
            vue.put("currency", p.currency());
            vue.put("locale", p.locale());
            vue.put("salesTaxLabel", p.salesTaxLabel());
            vue.put("accountingStandard", p.accountingStandard());
            pays.add(vue);
        }
        Map<String, Object> sortie = new LinkedHashMap<>();
        sortie.put("defaultCountry", catalogue.paysParDefaut());
        sortie.put("countries", pays);
        return sortie;
    }

    public Map<String, Object> detail(String code) {
        return catalogue.vuePublique(Pays.exiger(catalogue, code).code());
    }

    public TaxeDeVenteDto taxeDeVente(String code, String amount) {
        PaysPack p = Pays.exiger(catalogue, code);
        double base = Math.max(0, Pays.positif(amount, "amount must be a positive number"));
        List<Map<String, Object>> taxes = new ArrayList<>();
        double total = 0;
        for (PaysPack.TaxeVente t : p.salesTaxes()) {
            double m = Arrondi.r2(base * t.rate());
            total += m;
            Map<String, Object> ligne = new LinkedHashMap<>();
            ligne.put("code", t.code());
            ligne.put("label", t.label());
            ligne.put("rate", t.rate());
            ligne.put("amount", m);
            taxes.add(ligne);
        }
        return new TaxeDeVenteDto(base, taxes, Arrondi.r2(total), Arrondi.r2(base + Arrondi.r2(total)));
    }
}
