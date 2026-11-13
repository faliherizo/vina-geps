package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EchangeEffectifs;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.FenetreHoraire;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.SourceEchangesEffectifsPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les lectures des échanges de quarts (lot R6) — jumeau de la partie lecture de
 * {@code backend/src/effectifs/echanges/service.ts}. Un échange se voit des deux personnes et de l'encadrement
 * de l'une des deux unités ; pour tout autre, il n'existe pas (404).
 */
public class LectureEchangesEffectifs {

    private final SourceEchangesEffectifsPort source;
    private final LectureRessources ressources;
    private final LecturePerimetres perimetres;

    public LectureEchangesEffectifs(SourceEchangesEffectifsPort source, LectureRessources ressources, LecturePerimetres perimetres) {
        this.source = source;
        this.ressources = ressources;
        this.perimetres = perimetres;
    }

    /** Une place avec son quart et sa période ; l'identifiant vient d'un corps de requête, d'où le type ouvert. */
    public EchangeEffectifs.PlaceDetaillee place(Contexte ctx, Object id) {
        return source.place(ctx.tenantId(), id instanceof String s ? s : "")
                .orElseThrow(() -> new IntrouvableException("place « " + (id == null ? "undefined" : String.valueOf(id)) + " » introuvable"));
    }

    private EchangeEffectifs.Fiche lire(Contexte ctx, String id) {
        return source.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("échange « " + id + " » introuvable"));
    }

    /** L'échange, pour l'une des deux personnes ou pour l'encadrement de l'une des deux unités ; sinon 404. */
    public EchangeEffectifs.Fiche exiger(Contexte ctx, String id) {
        EchangeEffectifs.Fiche x = lire(ctx, id);
        if (ctx.estAdministrateur() || x.estVisiblePar(source.ressourcesDuCompte(ctx.tenantId(), ctx.acteurId()),
                perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE))) {
            return x;
        }
        throw new IntrouvableException("échange « " + id + " » introuvable");
    }

    /** L'échange en cours qui retient déjà l'une de ces places, s'il y en a un. */
    public Optional<String> ouvertSur(Contexte ctx, String placeA, String placeB) {
        return source.ouvertSur(ctx.tenantId(), placeA, placeB);
    }

    /** Les échanges où l'acteur est partie, et ceux des unités de son périmètre. */
    public List<EchangeEffectifs.Fiche> lister(Contexte ctx, String statut) {
        boolean admin = ctx.estAdministrateur();
        Set<String> visibles = admin ? Set.of() : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        Set<String> moi = source.ressourcesDuCompte(ctx.tenantId(), ctx.acteurId());
        return source.lister(ctx.tenantId()).stream()
                .filter(x -> statut == null || statut.isEmpty() || x.statut().name().equals(statut))
                .filter(x -> admin || x.estVisiblePar(moi, visibles)).toList();
    }

    /** Les quarts publiés à venir d'une personne : ce qu'elle peut offrir en échange. */
    public List<EchangeEffectifs.QuartEchangeable> quartsDe(Contexte ctx, String ressourceId) {
        return quartsDe(ctx, ressources.exiger(ctx, ressourceId));
    }

    private List<EchangeEffectifs.QuartEchangeable> quartsDe(Contexte ctx, Ressource r) {
        ressources.exigerDroitSur(ctx, r, Perimetres.Niveau.LECTURE);
        return source.quartsDe(ctx.tenantId(), r.id(), ctx.aujourdhui());
    }

    /** Ce que voit l'employé connecté : sa ressource (ou rien) et ses quarts publiés à venir. */
    public Map<String, Object> mesQuarts(Contexte ctx) {
        return mesQuarts(ctx, Optional.empty());
    }

    /**
     * Le même horaire, dans la fenêtre demandée quand il y en a une : lire son horaire passé est une
     * autre question que proposer un échange, et la borne figée à « aujourd'hui » la laissait sans
     * réponse.
     */
    public Map<String, Object> mesQuarts(Contexte ctx, Optional<FenetreHoraire> fenetre) {
        Optional<Ressource> r = ressources.deLActeur(ctx);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ressource", r.orElse(null));
        m.put("quarts", r.map(x -> fenetre.map(f -> quartsEntre(ctx, x, f)).orElseGet(() -> quartsDe(ctx, x))).orElse(List.of()));
        return m;
    }

    private List<EchangeEffectifs.QuartEchangeable> quartsEntre(Contexte ctx, Ressource r, FenetreHoraire fenetre) {
        ressources.exigerDroitSur(ctx, r, Perimetres.Niveau.LECTURE);
        return source.quartsEntre(ctx.tenantId(), r.id(), fenetre);
    }

    /**
     * Les quarts qu'on peut demander en retour d'une place. Ce sont des horaires de collègues de la même unité —
     * ce que l'horaire publié montre déjà à chacun.
     */
    public List<EchangeEffectifs.QuartEchangeable> demandables(Contexte ctx, String placeCedeeId) {
        EchangeEffectifs.PlaceDetaillee pa = place(ctx, placeCedeeId);
        if (!pa.estTenue()) {
            throw new EffectifsInvalideException("cette place est vacante : elle ne s'échange pas");
        }
        ressources.exigerDroitSur(ctx, ressources.exiger(ctx, pa.ressourceId()), Perimetres.Niveau.PLANIFICATION);
        return source.demandables(ctx.tenantId(), pa, ctx.aujourdhui());
    }
}
