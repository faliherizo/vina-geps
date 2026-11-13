package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.DemandesFlux.DemandeApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleApprobation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Ce que les flux d'approbation des heures (E-GP-6) demandent au <strong>moteur d'approbation générique</strong> : ses règles,
 * ses demandes, ses décisions (délégation et séparation des tâches comprises). Les effectifs ne touchent pas ses tables.
 */
public interface FluxApprobationsPort {

    /** Une demande à ouvrir ; {@code regles} nulles : celles que l'organisme a configurées pour cet objet. */
    record NouvelleDemande(ObjetFlux objet, String documentId, Map<String, Object> contexte, String demandeurId, List<RegleApprobation> regles) { }

    /** Une décision ; {@code porte} : la désignation que les effectifs savent juger (le niveau de périmètre de l'unité). */
    record DecisionEtape(DemandesFlux.StatutApprobation decision, String acteurId, String role, Predicate<FluxApprobation.Approbateur> porte) { }

    /** Les règles configurées d'un objet, par niveau. */
    List<RegleApprobation> regles(String tenantId, ObjetFlux objet);

    /** Range une règle ; rend son identifiant. */
    String creerRegle(String tenantId, ObjetFlux objet, RegleApprobation r);

    void retirerRegle(String tenantId, String id);

    /** Au moins une de ces règles s'applique-t-elle à ce contexte ? */
    boolean sAppliquent(List<RegleApprobation> regles, Map<String, Object> contexte);

    /** La dernière demande d'un document. */
    Optional<DemandeApprobation> demandeDuDocument(String tenantId, ObjetFlux objet, String documentId);

    /** Les demandes en cours d'un objet. */
    List<DemandeApprobation> enCours(String tenantId, ObjetFlux objet);

    DemandeApprobation ouvrir(String tenantId, NouvelleDemande d);

    /**
     * Décide une étape ; un refus du moteur lève {@link DemandesFlux.EtapeRefuseeException} : {@code NON_PORTEE} quand l'acteur ne
     * porte pas l'étape, {@code DEMANDEUR} quand il a soumis la demande, {@code AUTRE} sinon (avec le message du moteur).
     */
    DemandeApprobation decider(String tenantId, String etapeId, DecisionEtape d);
}
