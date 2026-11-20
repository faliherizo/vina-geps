package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Les campagnes AGPH</strong> (lot R7 — E-CH-1, P-CH-1 à P-CH-4) — jumeau de {@code serviceCampagnes.ts} :
 * créer, ouvrir (les rangs se figent), démarrer le tour de rôle, choisir, et les lectures, qui toutes commencent par
 * rattraper les tours écoulés.
 */
public class GestionnaireAgph {

    private static final int ANNEE_MIN = 2000;
    private static final int ANNEE_MAX = 2100;
    /** Le délai de choix quand la campagne n'en précise pas : une demi-heure par rang. */
    private static final String BUDGET = "budgetHeuresSemaine";
    private static final String TRAITEMENT = "traitementDelaiEchu";
    private static final int DELAI_PAR_DEFAUT = 30;

    /** Le socle qu'emprunte la campagne : référentiels, périmètres, ressources, journal, identifiants. */
    public record Socle(LectureReferentiels referentiels, LecturePerimetres perimetres, LectureRessources ressources, JournalEffectifsPort journal,
                        Sequence sequence) { }

    /** Un poste tel que le voit la personne à son tour : admissible ou non, et pourquoi. */
    public record PosteDisponible(String id, String code, String libelle, String statutEmploiCode, String milieuId, String natureCode, boolean admissible,
                                  List<String> motifs) { }

    public record PostesDisponibles(AgphCampagne campagne, AgphRang monRang, boolean peutChoisir, List<PosteDisponible> postes) { }

    private final DepotCampagnesAgphPort depot;
    private final LectureAgph lecture;
    private final TourDeRoleAgph tour;
    private final Socle socle;

    public GestionnaireAgph(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, Socle socle) {
        this.depot = depot;
        this.lecture = lecture;
        this.tour = tour;
        this.socle = socle;
    }

    static String texte(Object v, String champ) {
        String s = v instanceof String t ? t.trim() : "";
        if (s.isEmpty()) {
            throw new EffectifsInvalideException("« " + champ + " » est obligatoire");
        }
        return s;
    }

    private void exigerPerimetre(Contexte ctx, String uniteCode, Perimetres.Niveau niveau) {
        socle.perimetres().exiger(ctx, uniteCode, niveau);
    }

    private void journaliser(Contexte ctx, AgphCampagne c, String action, Object apres) {
        socle.journal().ecrire(ctx, JournalEffectifs.Consignation.de(action, TourDeRoleAgph.OBJET, c.id(), c.uniteCode(), null, apres));
    }

    public AgphCampagne creer(Contexte ctx, Map<String, Object> entree) {
        String uniteCode = texte(entree.get("uniteCode"), "uniteCode");
        exigerPerimetre(ctx, uniteCode, Perimetres.Niveau.GESTION);
        String corps = texte(entree.get("corpsEmploiCode"), "corpsEmploiCode");
        socle.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, uniteCode, ctx.aujourdhui());
        socle.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.CORPS_EMPLOI, corps, ctx.aujourdhui());
        int annee = anneeDe(entree.get("annee"));
        Object delai = entree.get("delaiChoixMinutes");
        int delaiChoix = Agph.delaiDe(delai == null ? DELAI_PAR_DEFAUT : delai);
        Double budget = Agph.budgetDe(entree.get("budgetHeuresSemaine"));
        Agph.TraitementDelaiEchu traitement = Agph.TraitementDelaiEchu.de(entree.get(TRAITEMENT));
        Object nom = entree.get("nom");
        String libelle = texte(nom == null ? "AGPH " + annee : nom, "nom");
        if (depot.existe(ctx.tenantId(), uniteCode, corps, annee)) {
            throw new ConflitException("une campagne " + annee + " existe déjà pour " + uniteCode + " (" + corps + ")");
        }
        AgphCampagne c = new AgphCampagne(socle.sequence().suivant("eagp"), uniteCode, annee, libelle, corps, Agph.EtatCampagne.PREPARATION, delaiChoix,
                ctx.maintenant(), null, null, budget, null, traitement);
        depot.inserer(ctx.tenantId(), c);
        journaliser(ctx, c, "agph.campagne.creee", creation(c));
        return lecture.exiger(ctx, c.id());
    }

    /** Ce que la création consigne : l'année, le corps, le délai et le budget retenus. */
    private static Map<String, Object> creation(AgphCampagne c) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("annee", c.annee());
        apres.put("corpsEmploiCode", c.corpsEmploiCode());
        apres.put("delai", c.delaiChoixMinutes());
        apres.put("budget", c.budgetHeuresSemaine());
        apres.put(TRAITEMENT, c.traitementDelaiEchu());
        return apres;
    }

    /**
     * Paramétrer la campagne (E-CH-1, P-CH-2) : le service de planification fixe ou corrige le budget d'heures hebdomadaires
     * et le traitement du délai échu tant que la campagne n'est pas close. Une clé absente garde sa valeur. Les dépôts déjà
     * faits gardent leur verdict ; les suivants se jugent sur le nouveau budget.
     */
    public AgphCampagne parametrer(Contexte ctx, String id, Map<String, Object> entree) {
        AgphCampagne c = lecture.exiger(ctx, id);
        exigerPerimetre(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        if (c.etat() == Agph.EtatCampagne.CLOSE) {
            throw new ConflitException("la campagne est close : ses paramètres ne changent plus");
        }
        if (entree.containsKey(BUDGET)) {
            Double budget = Agph.budgetDe(entree.get(BUDGET));
            depot.parametrer(ctx.tenantId(), id, budget);
            journaliserParametre(ctx, c, "budget", c.budgetHeuresSemaine(), budget);
        }
        if (entree.containsKey(TRAITEMENT)) {
            Agph.TraitementDelaiEchu t = Agph.TraitementDelaiEchu.de(entree.get(TRAITEMENT));
            depot.parametrerDelaiEchu(ctx.tenantId(), id, t);
            journaliserParametre(ctx, c, TRAITEMENT, c.traitementDelaiEchu(), t);
        }
        return lecture.exiger(ctx, id);
    }

    private void journaliserParametre(Contexte ctx, AgphCampagne c, String cle, Object avant, Object apres) {
        socle.journal().ecrire(ctx, JournalEffectifs.Consignation.de("agph.campagne.parametree", TourDeRoleAgph.OBJET, c.id(), c.uniteCode(),
                Collections.singletonMap(cle, avant), Collections.singletonMap(cle, apres)));
    }

    /**
     * Renoncer à tout poste (addenda 7, Q12 ; convention 14.10 b) : à son tour seulement, la personne déclare ne prendre
     * aucun poste ; son rang passe à {@code RENONCE}, le tour va au suivant, et la clôture lui donnera le temps partiel
     * occasionnel (P-CH-7).
     */
    public LectureAgph.VueCampagne renoncer(Contexte ctx, String id) {
        AgphCampagne c = tour.rattraper(ctx, lecture.visible(ctx, id));
        if (c.etat() != Agph.EtatCampagne.CHOIX) {
            throw new ConflitException("la campagne est en " + c.etat().name().toLowerCase() + " : le tour de rôle n'est pas en cours");
        }
        Ressource moi = choisisseur(ctx, c, null).ressource();
        exigerSonTour(ctx, id, moi);
        tour.renoncer(ctx, c, moi);
        tour.tourSuivant(ctx, c, ctx.maintenant());
        return lire(ctx, id);
    }

    private static int anneeDe(Object v) {
        double n = Agph.nombre(v);
        if (!Agph.estEntier(n) || n < ANNEE_MIN || n > ANNEE_MAX) {
            throw new EffectifsInvalideException("« annee » : une année de quatre chiffres");
        }
        return (int) n;
    }

    /**
     * Ouvrir : les rangs se figent (ancienneté du jour, instantané), les postes publiés et sans titulaire de l'unité et
     * du corps sont offerts. Une campagne sans candidat ou sans poste n'a pas de sens : elle ne s'ouvre pas.
     */
    public AgphCampagne ouvrir(Contexte ctx, String id) {
        AgphCampagne c = lecture.exiger(ctx, id);
        exigerPerimetre(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        Agph.etatSuivant(c.etat(), Agph.EtatCampagne.DEPOT);
        List<Agph.Candidat> candidats = depot.candidats(ctx.tenantId(), c.uniteCode(), c.corpsEmploiCode());
        List<String> postes = depot.postesOffrables(ctx.tenantId(), c.uniteCode(), c.corpsEmploiCode());
        String quoi = c.corpsEmploiCode() + " dans " + c.uniteCode();
        if (candidats.isEmpty()) {
            throw new EffectifsInvalideException("aucune ressource active de " + quoi + " : rien à ordonner");
        }
        if (postes.isEmpty()) {
            throw new EffectifsInvalideException("aucun poste publié et libre de " + quoi + " : rien à offrir");
        }
        depot.ouvrir(ctx.tenantId(), id, new DepotCampagnesAgphPort.Ouverture(Agph.ordonner(candidats), postes, ctx.maintenant()));
        journaliser(ctx, c, "agph.campagne.ouverte", Map.of("rangs", candidats.size(), "postes", postes.size()));
        return lecture.exiger(ctx, id);
    }

    public AgphCampagne demarrer(Contexte ctx, String id) {
        AgphCampagne c = lecture.exiger(ctx, id);
        exigerPerimetre(ctx, c.uniteCode(), Perimetres.Niveau.GESTION);
        Agph.etatSuivant(c.etat(), Agph.EtatCampagne.CHOIX);
        depot.poserEtat(ctx.tenantId(), id, Agph.EtatCampagne.CHOIX);
        journaliser(ctx, c, "agph.tour.demarre", null);
        tour.tourSuivant(ctx, c, ctx.maintenant());
        return lecture.exiger(ctx, id);
    }

    public LectureAgph.VueCampagne lire(Contexte ctx, String id) {
        return lecture.vue(ctx, tour.rattraper(ctx, lecture.visible(ctx, id)));
    }

    public List<AgphCampagne> lister(Contexte ctx) {
        return lecture.visibles(ctx).stream().map(c -> tour.rattraper(ctx, c)).toList();
    }

    /** Ce que voit une personne à son tour (P-CH-1) : les postes restants, admissibles ou non, et pourquoi. */
    public PostesDisponibles postesDisponibles(Contexte ctx, String id, String pourRessourceId) {
        AgphCampagne c = tour.rattraper(ctx, lecture.visible(ctx, id));
        Optional<Ressource> r = pourRessourceId == null || pourRessourceId.isBlank() ? socle.ressources().deLActeur(ctx)
                : Optional.of(socle.ressources().exiger(ctx, pourRessourceId.trim()));
        AgphRang monRang = r.flatMap(x -> lecture.rangDe(ctx, id, x.id())).orElse(null);
        List<PosteDisponible> postes = lecture.postesLibres(ctx, id).stream().map(p -> disponible(p, monRang == null
                ? Agph.Jugement.refuse("vous ne figurez pas dans cette campagne") : tour.juger(ctx, r.orElseThrow(), p, monRang.rang()))).toList();
        boolean peutChoisir = c.etat() == Agph.EtatCampagne.CHOIX && monRang != null && monRang.etat() == Agph.EtatRang.A_SON_TOUR;
        return new PostesDisponibles(c, monRang, peutChoisir, postes);
    }

    private static PosteDisponible disponible(Poste p, Agph.Jugement j) {
        return new PosteDisponible(p.id(), p.code(), p.libelle(), p.statutEmploiCode(), p.milieuId(), p.natureCode(), j.admissible(), j.motifs());
    }

    /**
     * Choisir un poste (P-CH-2, P-CH-3) : la personne à son tour, pour elle-même ; ou son planificateur pour elle
     * (saisie d'un choix reçu au téléphone). Un seul choix par rang ; un refus des règles est motivé ; le tour passe
     * aussitôt au rang suivant.
     */
    public LectureAgph.VueCampagne choisir(Contexte ctx, String id, Map<String, Object> entree) {
        AgphCampagne c = tour.rattraper(ctx, lecture.visible(ctx, id));
        if (c.etat() != Agph.EtatCampagne.CHOIX) {
            throw new ConflitException("la campagne est en " + c.etat().name().toLowerCase() + " : le tour de rôle n'est pas en cours");
        }
        Choisisseur pour = choisisseur(ctx, c, entree.get("ressourceId"));
        AgphRang monRang = exigerSonTour(ctx, id, pour.ressource());
        String posteId = texte(entree.get("posteId"), "posteId");
        Poste p = lecture.postesLibres(ctx, id).stream().filter(x -> x.id().equals(posteId)).findFirst()
                .orElseThrow(() -> new ConflitException("le poste « " + posteId + " » n'est pas offert ou a déjà été choisi"));
        tour.juger(ctx, pour.ressource(), p, monRang.rang()).exiger();
        tour.enregistrerChoix(ctx, c, new TourDeRoleAgph.Choix(pour.ressource(), p, pour.mode(), ctx.maintenant()));
        tour.tourSuivant(ctx, c, ctx.maintenant());
        return lire(ctx, id);
    }

    private record Choisisseur(Ressource ressource, Agph.ModeChoix mode) { }

    /** Pour qui l'on choisit, et en quelle qualité : pour autrui, il faut le périmètre PLANIFICATION de l'unité de la campagne. */
    private Choisisseur choisisseur(Contexte ctx, AgphCampagne c, Object ressourceId) {
        if (ressourceId instanceof String rid && !rid.isEmpty()) {
            Ressource r = socle.ressources().exiger(ctx, rid);
            boolean pourSoi = r.utilisateurId() != null && r.utilisateurId().equals(ctx.acteurId());
            if (!pourSoi) {
                exigerPerimetre(ctx, c.uniteCode(), Perimetres.Niveau.PLANIFICATION);
            }
            return new Choisisseur(r, pourSoi ? Agph.ModeChoix.EMPLOYE : Agph.ModeChoix.PLANIFICATEUR);
        }
        Ressource moi = socle.ressources().deLActeur(ctx)
                .orElseThrow(() -> new IntrouvableException("votre compte n'est relié à aucune ressource planifiable"));
        return new Choisisseur(moi, Agph.ModeChoix.EMPLOYE);
    }

    private AgphRang exigerSonTour(Contexte ctx, String id, Ressource r) {
        AgphRang monRang = lecture.rangDe(ctx, id, r.id())
                .orElseThrow(() -> new EffectifsInvalideException(r.nom() + " ne figure pas dans cette campagne"));
        if (monRang.etat() == Agph.EtatRang.A_SON_TOUR) {
            return monRang;
        }
        if (monRang.etat() == Agph.EtatRang.EN_ATTENTE) {
            String enCours = lecture.tourEnCours(ctx, id).map(g -> " ; tour en cours : rang " + g.rang()).orElse("");
            throw new ConflitException("ce n'est pas encore le tour de " + r.nom() + " (rang " + monRang.rang() + enCours + ")");
        }
        String comment = switch (monRang.etat()) {
            case A_CHOISI -> "poste choisi";
            case RENONCE -> "a renoncé à tout poste";
            case PASSE, EN_ATTENTE, A_SON_TOUR -> "tour passé";
        };
        throw new ConflitException(r.nom() + " a déjà été servi (" + comment + ") : un seul choix par rang");
    }
}
