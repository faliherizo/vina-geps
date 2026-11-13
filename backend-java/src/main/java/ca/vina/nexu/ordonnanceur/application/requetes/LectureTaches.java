package ca.vina.nexu.ordonnanceur.application.requetes;

import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;

import java.util.List;

/** Les tâches différées d'un établissement, filtrées par statut et type — lecture réservée à l'administration. */
public final class LectureTaches {

    private final TacheDifferee.Taches taches;

    public LectureTaches(TacheDifferee.Taches taches) {
        this.taches = taches;
    }

    public List<TacheDifferee> lister(String tenantId, String statut, String type) {
        return taches.lister(tenantId, statut, type);
    }
}
