package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphChoix;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>La révision d'une campagne AGPH</strong> (E-CH-1) — jumeau de {@code reviser}, {@code reviserChoix} et
 * {@code posteRevisable} de {@code serviceCampagnes.ts}. Tous servis, le service de planification relit les attributions
 * avant de clore : il donne à une personne un autre poste encore libre — jugé par les mêmes règles que son choix —, ou lui
 * retire le sien. Toujours motivé, toujours journalisé avec l'avant et l'après.
 */
public class RevisionAgph {

    private static final int MOTIF_MAX = 500;

    /** Ce que la révision emprunte hors de la campagne : ressources, périmètres, titulaires des postes, journal. */
    public record Appuis(LectureRessources ressources, LecturePerimetres perimetres, GestionnairePostes postes, JournalEffectifsPort journal) { }

    private final DepotCampagnesAgphPort depot;
    private final LectureAgph lecture;
    private final TourDeRoleAgph tour;
    private final Appuis appuis;

    public RevisionAgph(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, Appuis appuis) {
        this.depot = depot;
        this.lecture = lecture;
        this.tour = tour;
        this.appuis = appuis;
    }

    /** Ouvrir la révision : la campagne doit être en attribution, tous servis. */
    public LectureAgph.VueCampagne reviser(Contexte ctx, String id) {
        AgphCampagne c = tour.rattraper(ctx, lecture.exiger(ctx, id));
        appuis.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        Agph.etatSuivant(c.etat(), Agph.EtatCampagne.REVISION);
        depot.reviser(ctx.tenantId(), id, ctx.maintenant());
        appuis.journal().ecrire(ctx, JournalEffectifs.Consignation.de("agph.revision.ouverte", TourDeRoleAgph.OBJET, id, c.uniteCode(), null, null));
        return vue(ctx, id);
    }

    private LectureAgph.VueCampagne vue(Contexte ctx, String id) {
        return lecture.vue(ctx, tour.rattraper(ctx, lecture.visible(ctx, id)));
    }

    /** Réviser une attribution : {@code posteId} donne un autre poste ; absent, il retire celui obtenu. */
    public LectureAgph.VueCampagne reviserChoix(Contexte ctx, String id, Map<String, Object> entree) {
        AgphCampagne c = enRevision(ctx, id);
        Ressource r = appuis.ressources().exiger(ctx, GestionnaireAgph.texte(entree.get("ressourceId"), "ressourceId"));
        AgphRang monRang = lecture.rangDe(ctx, id, r.id()).orElseThrow(() -> new EffectifsInvalideException(r.nom() + " ne figure pas dans cette campagne"));
        String motif = motifDe(entree.get("motif"));
        Optional<AgphChoix> ancien = lecture.choix(ctx, id).stream().filter(x -> x.ressourceId().equals(r.id())).findFirst();
        Optional<Poste> nouveau = entree.get("posteId") instanceof String p && !p.isEmpty()
                ? Optional.of(posteRevisable(ctx, c, new Vise(r, monRang.rang()), p)) : Optional.empty();
        if (ancien.isEmpty() && nouveau.isEmpty()) {
            throw new EffectifsInvalideException(r.nom() + " n'a aucun poste à retirer");
        }
        appliquer(ctx, c, new Revision(r, ancien, nouveau, motif));
        return vue(ctx, id);
    }

    private AgphCampagne enRevision(Contexte ctx, String id) {
        AgphCampagne c = lecture.exiger(ctx, id);
        appuis.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        if (c.etat() != Agph.EtatCampagne.REVISION) {
            throw new ConflitException("la campagne est en " + c.etat().name().toLowerCase() + " : une attribution se révise pendant la révision");
        }
        return c;
    }

    private static String motifDe(Object v) {
        String m = GestionnaireAgph.texte(v, "motif");
        if (m.length() > MOTIF_MAX) {
            throw new EffectifsInvalideException("« motif » : " + MOTIF_MAX + " caractères au plus");
        }
        return m;
    }

    /** La personne visée par la révision, et son rang figé à l'ouverture. */
    private record Vise(Ressource ressource, int rang) { }

    /** Le poste visé par une révision : offert, libre, admissible pour la personne. */
    private Poste posteRevisable(Contexte ctx, AgphCampagne c, Vise vise, String posteId) {
        Poste p = lecture.postesLibres(ctx, c.id()).stream().filter(x -> x.id().equals(posteId)).findFirst()
                .orElseThrow(() -> new ConflitException("le poste « " + posteId + " » n'est pas offert ou est déjà attribué"));
        tour.juger(ctx, vise.ressource(), p, vise.rang()).exiger();
        return p;
    }

    /** Une révision décidée : qui, le poste qu'on lui retire, celui qu'on lui donne, pourquoi. */
    private record Revision(Ressource ressource, Optional<AgphChoix> ancien, Optional<Poste> nouveau, String motif) { }

    /** Le poste retiré redevient libre et le rang « passé » ; le nouveau se consigne comme un choix du planificateur. */
    private void appliquer(Contexte ctx, AgphCampagne c, Revision x) {
        String rid = x.ressource().id();
        x.ancien().ifPresent(a -> {
            depot.retirerChoix(ctx.tenantId(), c.id(), rid);
            appuis.postes().attribuerTitulaire(ctx, a.posteId(), null, null);
        });
        x.nouveau().ifPresent(p -> tour.enregistrerChoix(ctx, c, new TourDeRoleAgph.Choix(x.ressource(), p, Agph.ModeChoix.PLANIFICATEUR, ctx.maintenant())));
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("ressourceId", rid);
        avant.put("posteId", x.ancien().map(AgphChoix::posteId).orElse(null));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", rid);
        apres.put("posteId", x.nouveau().map(Poste::id).orElse(null));
        apres.put("motif", x.motif());
        appuis.journal().ecrire(ctx, JournalEffectifs.Consignation.de("agph.revision.choix", TourDeRoleAgph.OBJET, c.id(), c.uniteCode(), avant, apres));
    }
}
