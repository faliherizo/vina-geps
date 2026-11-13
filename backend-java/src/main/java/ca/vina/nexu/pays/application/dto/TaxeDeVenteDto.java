package ca.vina.nexu.pays.application.dto;

import java.util.List;
import java.util.Map;

/** Le calcul de taxe de vente d'un pays : base, lignes de taxe, total et montant TTC. */
public record TaxeDeVenteDto(double amountHT, List<Map<String, Object>> taxes, double totalTax, double amountTTC) {
}
