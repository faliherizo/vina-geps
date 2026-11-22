package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.domaine.ParametresOffresPort;

import java.util.Map;

/**
 * <strong>Le réglage de l'offre des quarts</strong> d'un organisme (AD-07) : le mode — simultané ou par ancienneté et
 * à tour de rôle —, le délai de réponse, le seuil d'urgence et la fenêtre de répartition. Un réglage s'applique aux
 * offres ouvertes ensuite ; une offre en cours garde l'ordre et l'urgence qu'elle a annoncés.
 */
public class GestionnaireParametresOffres {

    private static final String OBJET = "organisme";

    private final ParametresOffresPort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireParametresOffres(ParametresOffresPort depot, JournalEffectifsPort journal) {
        this.depot = depot;
        this.journal = journal;
    }

    public OffresQuart.Parametres lire(Contexte ctx) {
        return depot.lire(ctx.tenantId());
    }

    /** Modifie les champs reçus ; un champ hors de son ensemble ou de ses bornes est refusé en le nommant (422). */
    public OffresQuart.Parametres modifier(Contexte ctx, Map<String, Object> entree) {
        OffresQuart.Parametres avant = depot.lire(ctx.tenantId());
        OffresQuart.Parametres apres = avant.modifiesPar(entree);
        depot.enregistrer(ctx.tenantId(), apres, ctx.acteurId(), ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de(OffresQuart.Evenement.PARAMETRES.code(), OBJET, ctx.tenantId(), null, avant, apres));
        return apres;
    }
}
