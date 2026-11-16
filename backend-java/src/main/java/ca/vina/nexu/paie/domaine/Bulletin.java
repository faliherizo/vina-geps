package ca.vina.nexu.paie.domaine;

import java.math.BigDecimal;
import java.util.List;

/**
 * Le bulletin d'une personne pour un cycle : ses heures, ses gains (ADR 0084), son calcul et les feuilles qu'il paie.
 * Les gains sont figés avec lui : une règle modifiée plus tard ne réécrit pas une paie passée.
 */
public record Bulletin(String id, String cycleId, String ressourceId, String matricule, String nom, String datePaie,
                       BigDecimal heuresRegulieres, BigDecimal heuresSupplementaires, Retenues retenues, List<String> feuilles,
                       List<Gain> gains) {

    public Bulletin {
        feuilles = List.copyOf(feuilles);
        gains = List.copyOf(gains);
    }
}
