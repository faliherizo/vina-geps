package ca.vina.nexu.ordonnanceur.application.commandes;

import ca.vina.nexu.ordonnanceur.domaine.BilanTic;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.partage.temps.domaine.Horloge;

/** Un tic à la demande (administration) : à l'instant présent, jamais dans le passé — un tic rejoué fausserait les journaux. */
public final class DeclencherTic {

    private final TacheDifferee.Taches taches;
    private final Horloge horloge;
    private final String identite;

    public DeclencherTic(TacheDifferee.Taches taches, Horloge horloge, String identite) {
        this.taches = taches;
        this.horloge = horloge;
        this.identite = identite;
    }

    public BilanTic executer() {
        return taches.tic(horloge.maintenant(), PolitiqueRetentative.PAR_DEFAUT, identite);
    }
}
