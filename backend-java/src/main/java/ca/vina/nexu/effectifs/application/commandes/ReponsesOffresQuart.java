package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotOffresQuartPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OffreQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart;
import ca.vina.nexu.effectifs.domaine.OffresQuart.EtatRang;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <strong>La réponse de l'employé à l'offre d'un quart</strong> (AD-07 ; art. 15.10, 16.02, 16.03). Seul le rang à qui
 * le quart est offert répond, et seulement avant l'échéance : l'offre est d'abord rattrapée à l'instant du geste.
 *
 * <p>Accepter attribue la place sous les règles {@code ATTRIBUTION} (repos, 16 h par 24 h…) : une règle qui refuse
 * écarte la personne, sans la compter, et l'offre passe au rang suivant — la réponse le dit, avec le motif. Refuser
 * compte les heures offertes (16.03) et passe aussitôt au suivant. La réponse ne rend que le rang de la personne.
 */
public class ReponsesOffresQuart {

    /** Ce que la réponse touche : l'offre et ses rangs, la cascade qui avance, l'attribution, le journal. */
    public record Collaborateurs(CascadeOffresQuart cascade, DepotOffresQuartPort depot, GestionnairePlaces places, JournalEffectifsPort journal) { }

    private static final String OBJET = "offre";

    /** Ce que la personne a fait, l'événement qui le trace, et le motif d'un écart. */
    private record Geste(EtatRang etat, OffresQuart.Evenement evenement, String motif) {
        static final Geste ACCEPTE = new Geste(EtatRang.ACCEPTE, OffresQuart.Evenement.ACCEPTEE, null);
        static final Geste REFUSE = new Geste(EtatRang.REFUSE, OffresQuart.Evenement.REFUSEE, null);

        static Geste ecarte(String motif) {
            return new Geste(EtatRang.ECARTE, OffresQuart.Evenement.ECARTEE, motif);
        }
    }

    private final Collaborateurs avec;
    private final LectureRessources ressources;

    public ReponsesOffresQuart(Collaborateurs avec, LectureRessources ressources) {
        this.avec = avec;
        this.ressources = ressources;
    }

    /** Accepte le quart offert : la place est attribuée, ou la personne est écartée par une règle et l'offre continue. */
    public OffreQuart accepter(Contexte ctx, String offreId) {
        Ressource r = ressources.exigerDeLActeur(ctx);
        OffreQuart o = aJour(ctx, offreId);
        o.exigerOffertA(r.id());
        Contexte sys = CascadeOffresQuart.duMecanisme(ctx.tenantId(), ctx.maintenant());
        try {
            avec.places().attribuer(sys, o.quart().placeId(), Map.of("ressourceId", r.id()), OffresQuart.MECANISME);
        } catch (ConflitException refus) {
            repondre(ctx, o, r, Geste.ecarte(refus.getMessage()));
            avec.cascade().avancer(sys, o.id());
            return avec.cascade().relire(ctx, o.id()).vuePour(r.id());
        }
        repondre(ctx, o, r, Geste.ACCEPTE);
        avec.cascade().fermer(ctx, o, OffresQuart.EtatOffre.POURVUE, r.id());
        return avec.cascade().relire(ctx, o.id()).vuePour(r.id());
    }

    /** Refuse le quart offert : refus compté (16.03), le rang suivant est avisé. */
    public OffreQuart refuser(Contexte ctx, String offreId) {
        Ressource r = ressources.exigerDeLActeur(ctx);
        OffreQuart o = aJour(ctx, offreId);
        o.exigerOffertA(r.id());
        repondre(ctx, o, r, Geste.REFUSE);
        avec.cascade().avancer(CascadeOffresQuart.duMecanisme(ctx.tenantId(), ctx.maintenant()), o.id());
        return avec.cascade().relire(ctx, o.id()).vuePour(r.id());
    }

    /** L'offre telle qu'elle est à l'instant du geste : une proposition échue a déjà expiré. */
    private OffreQuart aJour(Contexte ctx, String offreId) {
        avec.cascade().avancer(CascadeOffresQuart.duMecanisme(ctx.tenantId(), ctx.maintenant()), offreId);
        return avec.cascade().relire(ctx, offreId);
    }

    private void repondre(Contexte ctx, OffreQuart o, Ressource r, Geste g) {
        avec.depot().repondre(ctx.tenantId(), new DepotOffresQuartPort.Reponse(o.id(), r.id(), g.etat(), ctx.maintenant(), g.motif()));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", r.id());
        apres.put("motif", g.motif());
        avec.journal().ecrire(ctx, JournalEffectifs.Consignation.de(g.evenement().code(), OBJET, o.id(), o.quart().uniteCode(), null, apres));
    }
}
