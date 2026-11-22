package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotOffresQuartPort;
import ca.vina.nexu.effectifs.domaine.OffreQuart;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.List;

/**
 * Les lectures des offres de quart (AD-07) : ce qui m'est offert, et l'ordre complet d'une offre pour l'encadrement —
 * rang, heures comptées, ancienneté, état, échéance et motif —, la traçabilité que la convention suppose (16.04).
 */
public class LectureOffresQuart {

    private final DepotOffresQuartPort depot;
    private final LectureRessources ressources;
    private final LectureAbsencesEffectifs absences;
    private final LecturePerimetres perimetres;

    public LectureOffresQuart(DepotOffresQuartPort depot, LectureRessources ressources, LectureAbsencesEffectifs absences, LecturePerimetres perimetres) {
        this.depot = depot;
        this.ressources = ressources;
        this.absences = absences;
        this.perimetres = perimetres;
    }

    /** Les quarts qui me sont offerts maintenant, réduits à mon rang ; vide pour un compte sans ressource planifiable. */
    public List<OffreQuart> mesOffres(Contexte ctx) {
        return ressources.deLActeur(ctx).map(r -> depot.offertesA(ctx.tenantId(), r.id()).stream().map(o -> o.vuePour(r.id())).toList())
                .orElse(List.of());
    }

    /** Les offres nées du comblement d'une absence, pour l'encadrement de son unité. */
    public List<OffreQuart> deLAbsence(Contexte ctx, String absenceId) {
        perimetres.exiger(ctx, absences.exiger(ctx, absenceId).uniteCode(), Perimetres.Niveau.LECTURE);
        return depot.deLAbsence(ctx.tenantId(), absenceId);
    }
}
