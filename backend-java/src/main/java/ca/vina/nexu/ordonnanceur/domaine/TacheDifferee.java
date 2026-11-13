package ca.vina.nexu.ordonnanceur.domaine;

import java.util.Map;

/**
 * Une tâche différée : un type, une cible, une échéance, un état. Elle ne porte aucune règle
 * métier — c'est le module qui l'a planifiée qui sait l'exécuter (ADR 0014). {@code statut} :
 * {@code en_attente}, {@code en_cours}, {@code faite}, {@code echouee}, {@code annulee}.
 */
public record TacheDifferee(String id, String tenantId, String type, String cibleId, Map<String, Object> charge,
                            String echeanceLe, String statut, int tentatives, String derniereErreur, String verrouLe, String verrouPar) {

    public TacheDifferee {
        charge = charge == null ? Map.of() : Map.copyOf(charge);
    }

    /** Port : les tâches différées d'un établissement, et le tic qui prend l'échu. */
    public interface Taches {
        java.util.List<TacheDifferee> lister(String tenantId, String statut, String type);

        BilanTic tic(String maintenant, PolitiqueRetentative politique, String identite);
    }
}
