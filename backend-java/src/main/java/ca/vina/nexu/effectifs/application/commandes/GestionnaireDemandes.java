package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureDemandes;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DemandesOperationnelles;
import ca.vina.nexu.effectifs.domaine.DemandesPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.Map;

/**
 * <strong>Soumettre, annuler, décider une demande opérationnelle</strong> (addenda 8, M-08). La personne soumet et annule
 * les siennes ; l'encadrement de l'unité (périmètre Gestion) décide, un refus motivé. Chaque geste est au journal.
 */
public class GestionnaireDemandes {

    private static final String OBJET = "demande";

    /** Ce que le gestionnaire lit. */
    public record Lectures(LectureRessources ressources, LectureReferentiels referentiels, LecturePerimetres perimetres) { }

    /** Ce qu'il écrit. */
    public record Ecritures(DemandesPort depot, JournalEffectifsPort journal, Sequence sequence) { }

    private final Lectures lire;
    private final Ecritures ecrire;

    public GestionnaireDemandes(Lectures lire, Ecritures ecrire) {
        this.lire = lire;
        this.ecrire = ecrire;
    }

    public Map<String, Object> soumettre(Contexte ctx, Map<String, Object> entree) {
        Ressource r = lire.ressources().exigerDeLActeur(ctx);
        DemandesOperationnelles.Saisie s = DemandesOperationnelles.saisie(entree.get("typeCode"), entree.get("du"), entree.get("au"), entree.get("commentaire"));
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.TYPES_DEMANDE, s.typeCode(), s.du());
        DemandesOperationnelles.Demande d = new DemandesOperationnelles.Demande(ecrire.sequence().suivant("edem"), r.id(), r.uniteCode(), s.typeCode(),
                s.du(), s.au(), s.commentaire(), DemandesOperationnelles.Statut.EN_ATTENTE, ctx.maintenant(), null, null, null);
        ecrire.depot().enregistrer(ctx.tenantId(), d);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("demande.soumise", OBJET, d.id(), d.uniteCode(), null, LectureDemandes.vue(d, r)));
        return LectureDemandes.vue(d, r);
    }

    /** La personne annule la sienne, tant qu'elle attend. */
    public Map<String, Object> annuler(Contexte ctx, String id) {
        Ressource r = lire.ressources().exigerDeLActeur(ctx);
        DemandesOperationnelles.Demande avant = exiger(ctx, id);
        if (!avant.ressourceId().equals(r.id())) {
            throw new IntrouvableException("demande « " + id + " » introuvable");
        }
        DemandesOperationnelles.Demande apres = avant.annulee(ctx.acteurId(), ctx.maintenant());
        ecrire.depot().enregistrer(ctx.tenantId(), apres);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("demande.annulee", OBJET, id, apres.uniteCode(), avant.statut().name(), apres.statut().name()));
        return LectureDemandes.vue(apres, r);
    }

    /** L'encadrement de l'unité accepte ou refuse ; un refus se motive. */
    public Map<String, Object> decider(Contexte ctx, String id, Map<String, Object> entree) {
        DemandesOperationnelles.Demande avant = exiger(ctx, id);
        lire.perimetres().exiger(ctx, avant.uniteCode(), Perimetres.Niveau.GESTION);
        DemandesOperationnelles.Decision decision = DemandesOperationnelles.Decision.de(entree.get("decision"));
        Object motif = entree.get("motif");
        DemandesOperationnelles.Demande apres = avant.decidee(decision, motif == null ? null : String.valueOf(motif), ctx.acteurId(), ctx.maintenant());
        ecrire.depot().enregistrer(ctx.tenantId(), apres);
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de(decision == DemandesOperationnelles.Decision.ACCEPTER ? "demande.acceptee" : "demande.refusee",
                OBJET, id, apres.uniteCode(), avant.statut().name(), Map.of("statut", apres.statut().name(), "motif", apres.motifDecision() == null ? "" : apres.motifDecision())));
        return LectureDemandes.vue(apres, lire.ressources().exiger(ctx, apres.ressourceId()));
    }

    private DemandesOperationnelles.Demande exiger(Contexte ctx, String id) {
        return ecrire.depot().lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("demande « " + id + " » introuvable"));
    }
}
