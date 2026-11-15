package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.util.List;

/** Le bulletin d'une personne pour un cycle : ses heures, son calcul et les feuilles qu'il paie. */
public record Bulletin(String id, String cycleId, String ressourceId, String matricule, String nom, String datePaie,
                       BigDecimal heuresRegulieres, BigDecimal heuresSupplementaires, Retenues retenues, List<String> feuilles) {

    public Bulletin {
        feuilles = List.copyOf(feuilles);
    }
}
