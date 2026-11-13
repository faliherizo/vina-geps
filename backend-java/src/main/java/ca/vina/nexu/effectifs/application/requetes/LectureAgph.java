package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphChoix;
import ca.vina.nexu.effectifs.domaine.AgphPosteNonPourvu;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Les lectures des campagnes AGPH — jumeau de la partie lecture de {@code serviceCampagnes.ts}. Une campagne se voit
 * par le périmètre de son unité, ou parce qu'on y figure : l'employé suit la campagne qui décide de son poste.
 */
public class LectureAgph {

    /**
     * Une campagne telle que l'écran la lit : ses rangs, ses choix et ses postes non pourvus à plat, comme le Node
     * l'étale. Les postes non pourvus deviennent vacants à la clôture et passent à la gestion régulière (E-CH-10, E-GR-2) :
     * l'écran les nomme.
     */
    public record VueCampagne(String id, String uniteCode, int annee, String nom, String corpsEmploiCode, Agph.EtatCampagne etat, int delaiChoixMinutes,
                              String creeLe, String ouverteLe, String closeLe, Double budgetHeuresSemaine, String reviseeLe, List<AgphRang> rangs,
                              List<AgphChoix> choix, List<AgphPosteNonPourvu> postesNonPourvus) {
        public static VueCampagne de(AgphCampagne c, List<AgphRang> rangs, List<AgphChoix> choix, List<AgphPosteNonPourvu> postesNonPourvus) {
            return new VueCampagne(c.id(), c.uniteCode(), c.annee(), c.nom(), c.corpsEmploiCode(), c.etat(), c.delaiChoixMinutes(), c.creeLe(), c.ouverteLe(),
                    c.closeLe(), c.budgetHeuresSemaine(), c.reviseeLe(), rangs, choix, postesNonPourvus);
        }
    }

    private final DepotCampagnesAgphPort depot;
    private final LecturePostes postes;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;

    public LectureAgph(DepotCampagnesAgphPort depot, LecturePostes postes, LectureRessources ressources, LecturePerimetres perimetres) {
        this.depot = depot;
        this.postes = postes;
        this.ressources = ressources;
        this.perimetres = perimetres;
    }

    public AgphCampagne exiger(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> introuvable(id));
    }

    private static IntrouvableException introuvable(String id) {
        return new IntrouvableException("campagne « " + id + " » introuvable");
    }

    /** La campagne, si l'acteur peut la voir ; sinon « introuvable » — rien à divulguer. */
    public AgphCampagne visible(Contexte ctx, String id) {
        AgphCampagne c = exiger(ctx, id);
        if (ctx.estAdministrateur() || perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE).contains(c.uniteCode())) {
            return c;
        }
        Optional<Ressource> moi = ressources.deLActeur(ctx);
        if (moi.isPresent() && rangDe(ctx, id, moi.get().id()).isPresent()) {
            return c;
        }
        throw introuvable(id);
    }

    /** Les campagnes visibles, par année décroissante puis unité. */
    public List<AgphCampagne> visibles(Contexte ctx) {
        Set<String> unites = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        Set<String> miennes = ressources.deLActeur(ctx).map(r -> depot.campagnesDe(ctx.tenantId(), r.id())).orElse(Set.of());
        return depot.lister(ctx.tenantId()).stream().filter(c -> unites == null || unites.contains(c.uniteCode()) || miennes.contains(c.id())).toList();
    }

    public List<AgphRang> rangs(Contexte ctx, String campagneId) {
        return depot.rangs(ctx.tenantId(), campagneId);
    }

    public Optional<AgphRang> rangDe(Contexte ctx, String campagneId, String ressourceId) {
        return rangs(ctx, campagneId).stream().filter(g -> g.ressourceId().equals(ressourceId)).findFirst();
    }

    /** Le rang dont c'est le tour, s'il y en a un. */
    public Optional<AgphRang> tourEnCours(Contexte ctx, String campagneId) {
        return rangs(ctx, campagneId).stream().filter(g -> g.etat() == Agph.EtatRang.A_SON_TOUR).findFirst();
    }

    public List<AgphChoix> choix(Contexte ctx, String campagneId) {
        return depot.choix(ctx.tenantId(), campagneId);
    }

    /** Les postes offerts et encore libres, dans l'ordre de leur code. */
    public List<Poste> postesLibres(Contexte ctx, String campagneId) {
        return depot.postesLibres(ctx.tenantId(), campagneId).stream().map(id -> postes.exiger(ctx, id)).toList();
    }

    public VueCampagne vue(Contexte ctx, AgphCampagne c) {
        return VueCampagne.de(c, rangs(ctx, c.id()), choix(ctx, c.id()), depot.postesNonPourvus(ctx.tenantId(), c.id()));
    }
}
