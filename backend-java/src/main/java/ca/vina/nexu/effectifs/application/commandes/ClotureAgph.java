package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphChoix;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.Comportements;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>La clôture d'une campagne AGPH</strong> (E-CH-10, P-CH-5 à P-CH-7) — jumeau de {@code clore} de
 * {@code serviceCampagnes.ts} : chaque personne reçoit le statut du poste obtenu, ou le statut temps partiel ; les
 * postes offerts et non choisis deviennent vacants — la gestion régulière les comble.
 */
public class ClotureAgph {

    /** Ce que la clôture lit et modifie hors de la campagne : statuts d'emploi, titulaires, référentiels, périmètres. */
    public record Gestes(GestionnaireRessources ressources, GestionnairePostes postes, LecturePostes lecturePostes, LectureReferentiels referentiels,
                         LecturePerimetres perimetres, JournalEffectifsPort journal) { }

    private final DepotCampagnesAgphPort depot;
    private final LectureAgph lecture;
    private final TourDeRoleAgph tour;
    private final Gestes gestes;

    public ClotureAgph(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, Gestes gestes) {
        this.depot = depot;
        this.lecture = lecture;
        this.tour = tour;
        this.gestes = gestes;
    }

    /** Le statut « temps partiel occasionnel » en vigueur de l'organisme, pour qui n'a pas obtenu de poste (P-CH-7). */
    private String statutTempsPartiel(Contexte ctx) {
        String partiel = Comportements.StatutEmploi.TEMPS_PARTIEL_OCCASIONNEL.name();
        return gestes.referentiels().lister(ctx, TypeReferentiel.STATUTS_EMPLOI.code(), null, false).stream()
                .filter(v -> partiel.equals(v.comportement())).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("aucun statut d'emploi « temps partiel occasionnel » en vigueur : "
                        + "définissez-le dans les référentiels avant de clore")).code();
    }

    public LectureAgph.VueCampagne clore(Contexte ctx, String id) {
        AgphCampagne c = tour.rattraper(ctx, lecture.exiger(ctx, id));
        gestes.perimetres().exiger(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        Agph.etatSuivant(c.etat(), Agph.EtatCampagne.CLOSE);
        String partiel = statutTempsPartiel(ctx);
        Map<String, String> obtenus = new HashMap<>();
        for (AgphChoix x : lecture.choix(ctx, id)) {
            obtenus.put(x.ressourceId(), x.posteId());
        }
        List<Poste> libres = lecture.postesLibres(ctx, id);
        poserLesStatuts(ctx, id, obtenus, partiel);
        for (Poste p : libres) {
            gestes.postes().attribuerTitulaire(ctx, p.id(), null, TourDeRoleAgph.MECANISME);
        }
        depot.clore(ctx.tenantId(), id, ctx.maintenant());
        gestes.journal().ecrire(ctx, JournalEffectifs.Consignation.de("agph.campagne.close", TourDeRoleAgph.OBJET, id, c.uniteCode(), null,
                Map.of("obtenus", obtenus.size())));
        transferer(ctx, c, libres);
        return lecture.vue(ctx, lecture.visible(ctx, id));
    }

    /**
     * Les postes offerts et non choisis, devenus vacants, sont <strong>transférés</strong> à la gestion régulière sans autre
     * geste (E-CH-10, E-GR-2) : leurs quarts se génèrent avec une place libre, que le comblement, le bassin et
     * l'attribution de fin de collecte prennent en charge. Le transfert se consigne au nom du mécanisme.
     */
    private void transferer(Contexte ctx, AgphCampagne c, List<Poste> libres) {
        if (libres.isEmpty()) {
            return;
        }
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("vers", Agph.VERS_GESTION_REGULIERE);
        apres.put("postes", libres.stream().map(Poste::code).toList());
        gestes.journal().ecrire(ctx, new JournalEffectifs.Consignation("agph.postes.transferes", TourDeRoleAgph.OBJET, c.id(), c.uniteCode(), null, apres,
                TourDeRoleAgph.MECANISME));
    }

    /** Le statut suit le poste obtenu ; sans poste, le temps partiel. */
    private void poserLesStatuts(Contexte ctx, String id, Map<String, String> obtenus, String partiel) {
        for (AgphRang g : lecture.rangs(ctx, id)) {
            String posteId = obtenus.get(g.ressourceId());
            String statutDuPoste = posteId == null ? null : gestes.lecturePostes().exiger(ctx, posteId).statutEmploiCode();
            gestes.ressources().poserStatutEmploi(ctx, g.ressourceId(), Agph.statutApres(statutDuPoste, partiel), TourDeRoleAgph.MECANISME);
        }
    }
}
