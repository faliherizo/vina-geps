package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des absences d'effectifs ({@code eff_absences}). */
public interface DepotAbsencesEffectifsPort {

    /** Une décision humaine à enregistrer ; la dérogation nulle laisse en place celle qui existe déjà. */
    record DecisionEnregistree(AbsencesEffectifs.Statut vers, String codeAbsence, String decideeLe, String decideePar, String motifDecision,
                               AbsencesEffectifs.Derogation derogation) { }

    Optional<AbsenceEffectifs> lire(String tenantId, String id);

    /** {@code du} et {@code au} nuls : sans borne. Triées par début décroissant, puis identifiant. */
    List<AbsenceEffectifs> lister(String tenantId, String du, String au);

    List<AbsenceEffectifs> deLaRessource(String tenantId, String ressourceId);

    /** Les absences acceptées de l'unité qui touchent l'intervalle. */
    int accepteesSimultanees(String tenantId, String uniteCode, String du, String au);

    void inserer(String tenantId, AbsenceEffectifs a);

    void decider(String tenantId, String id, DecisionEnregistree d);
}
