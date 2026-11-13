package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port des postes ({@code eff_postes}). */
public interface DepotPostesPort {

    record Champs(String libelle, String uniteCode, String statutEmploiCode, String corpsEmploiCode, String natureCode, String milieuId,
                  List<List<String>> cycle, String debutCycle, Map<String, Object> parametres) { }

    Optional<Poste> lire(String tenantId, String id);

    /** Triés par code. */
    List<Poste> lister(String tenantId);

    boolean codeExiste(String tenantId, String code);

    void inserer(String tenantId, String id, String code, Champs c, String creeLe);

    void modifier(String tenantId, String id, Champs c);

    void poserEtat(String tenantId, String id, Planification.EtatPoste etat, String titulaireId);
}
