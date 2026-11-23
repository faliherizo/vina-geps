package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Cascades.Cascade;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.Cascades.Trace;

/** L'écriture des politiques, des cascades et de leur trace. */
public interface EcritureCascadesPort {

    /** La politique courante, et sa version si elle est nouvelle (une version rangée ne se réécrit jamais). */
    void enregistrer(String tenantId, Politique politique, String instant, String auteur);

    void armer(String tenantId, Cascade cascade);

    /**
     * Remplace {@code avant} par {@code apres} <strong>si</strong> la cascade est encore dans l'état {@code avant} (étape
     * et état) : c'est le verrou qui empêche deux exécutions de la même étape. Rend faux si elle a déjà bougé.
     */
    boolean avancer(String tenantId, Cascade avant, Cascade apres);

    void tracer(String tenantId, Trace trace);
}
