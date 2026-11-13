package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Map;

/** Une ligne de quarts attribuable : un cycle de plages, un corps d'emploi, un milieu, un titulaire éventuel. */
public record Poste(String id, String uniteCode, String code, String libelle, String statutEmploiCode, String corpsEmploiCode, String milieuId,
                    String natureCode, List<List<String>> cycle, String debutCycle, Map<String, Object> parametres, Planification.EtatPoste etat,
                    String titulaireId) {
}
