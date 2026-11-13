package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Agph;
import ca.vina.nexu.effectifs.domaine.AgphCampagne;
import ca.vina.nexu.effectifs.domaine.AgphRang;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.PorteesDuDossier;
import ca.vina.nexu.effectifs.domaine.Poste;
import ca.vina.nexu.effectifs.domaine.Regles;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>Le tour de rôle</strong> d'une campagne AGPH (P-CH-3, P-CH-4) — jumeau de {@code juger}, {@code tourSuivant},
 * {@code enregistrerChoix} et {@code rattraper} de {@code serviceCampagnes.ts}.
 *
 * <p>Le temps avance <strong>par rattrapage</strong> : chaque lecture ou geste sur une campagne commence par traiter les
 * tours écoulés jusqu'à l'instant du contexte. Un tour expiré se solde par un choix automatique, ou par « passé » quand
 * aucun poste restant n'est admissible ; le tour suivant commence à la fin du précédent. Ainsi le déroulé ne dépend
 * d'aucune tâche planifiée, et l'essai le rejoue à l'identique avec une horloge fixe.
 */
public class TourDeRoleAgph {

    /** Le mécanisme qui fait avancer le tour de rôle : c'est lui, pas un utilisateur, qui choisit à l'expiration. */
    public static final String MECANISME = "agph-tour-de-role";
    static final String OBJET = "campagne";

    /** Les collaborateurs du tour : lectures, moteur de règles, gestion des postes. */
    public record Collaborateurs(LectureAgph lecture, LectureRessources ressources, EvaluateurRegles regles, GestionnairePostes postes) { }

    private final DepotCampagnesAgphPort depot;
    private final Collaborateurs avec;
    private final JournalEffectifsPort journal;

    public TourDeRoleAgph(DepotCampagnesAgphPort depot, Collaborateurs avec, JournalEffectifsPort journal) {
        this.depot = depot;
        this.avec = avec;
        this.journal = journal;
    }

    /**
     * L'admissibilité d'un poste pour une personne (P-CH-3) : même corps d'emploi, exigence dure ; puis les règles
     * CHOIX_AGPH de l'organisme (statut offert, rang…).
     */
    public Agph.Jugement juger(Contexte ctx, Ressource r, Poste p, int rang) {
        if (!r.corpsEmploiCode().equals(p.corpsEmploiCode())) {
            return Agph.Jugement.refuse("le poste " + p.code() + " requiert le corps " + p.corpsEmploiCode());
        }
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("statutOffert", p.statutEmploiCode());
        faits.put("corpsRequis", p.corpsEmploiCode());
        faits.put("corpsEmploi", r.corpsEmploiCode());
        faits.put("rang", rang);
        return Agph.Jugement.de(avec.regles().evaluerDossier(ctx, new Regles.Dossier(ContexteRegle.CHOIX_AGPH, "poste", p.id(), ctx.aujourdhui(),
                p.uniteCode(), faits, portees(r))));
    }

    /** Les portées de la personne : ce qui choisit les règles qui la visent. */
    static Map<String, String> portees(Ressource r) {
        Map<String, String> p = new HashMap<>();
        p.put(PorteesDuDossier.CONVENTION, r.conventionCode());
        p.put(PorteesDuDossier.STATUT_EMPLOI, r.statutEmploiCode());
        p.put(PorteesDuDossier.CORPS_EMPLOI, r.corpsEmploiCode());
        p.put(PorteesDuDossier.TITRE_EMPLOI, r.titreEmploiCode());
        return p;
    }

    /** Consigne un choix : le rang est servi, le poste reçoit son titulaire. */
    public void enregistrerChoix(Contexte ctx, AgphCampagne c, Choix choix) {
        String systeme = choix.mode() == Agph.ModeChoix.AUTOMATIQUE ? MECANISME : null;
        String ressourceId = choix.ressource().id();
        depot.insererChoix(ctx.tenantId(), c.id(), new DepotCampagnesAgphPort.ChoixAConsigner(ressourceId, choix.poste().id(), choix.mode(), choix.instant(),
                systeme == null ? ctx.acteurId() : systeme));
        depot.poserEtatRang(ctx.tenantId(), c.id(), ressourceId, Agph.EtatRang.A_CHOISI);
        avec.postes().attribuerTitulaire(ctx, choix.poste().id(), ressourceId, systeme);
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("ressourceId", ressourceId);
        apres.put("posteId", choix.poste().id());
        apres.put("mode", choix.mode());
        journal.ecrire(ctx, new JournalEffectifs.Consignation("agph.choix", OBJET, c.id(), c.uniteCode(), null, apres, systeme));
    }

    /** Un choix à consigner : qui, quel poste, par quel moyen, à quel instant. */
    public record Choix(Ressource ressource, Poste poste, Agph.ModeChoix mode, String instant) { }

    /** Donne le tour au rang suivant, ou passe la campagne en attribution quand tous sont servis. */
    public void tourSuivant(Contexte ctx, AgphCampagne c, String instant) {
        Optional<AgphRang> suivant = avec.lecture().rangs(ctx, c.id()).stream().filter(g -> g.etat() == Agph.EtatRang.EN_ATTENTE).findFirst();
        if (suivant.isEmpty()) {
            depot.poserEtat(ctx.tenantId(), c.id(), Agph.EtatCampagne.ATTRIBUTION);
            journal.ecrire(ctx, new JournalEffectifs.Consignation("agph.campagne.attribution", OBJET, c.id(), c.uniteCode(), null, null, MECANISME));
            return;
        }
        depot.donnerTour(ctx.tenantId(), c.id(), suivant.get().ressourceId(), instant, Agph.ajouterMinutes(instant, c.delaiChoixMinutes()));
    }

    /** Traite les tours écoulés jusqu'à l'instant du contexte (P-CH-4), et rend la campagne relue. */
    public AgphCampagne rattraper(Contexte ctx, AgphCampagne c) {
        if (c.etat() != Agph.EtatCampagne.CHOIX) {
            return c;
        }
        Optional<AgphRang> courant = avec.lecture().tourEnCours(ctx, c.id());
        while (courant.isPresent() && Agph.tourEcoule(courant.get().tourFin(), ctx.maintenant())) {
            solder(ctx, c, courant.get());
            courant = avec.lecture().tourEnCours(ctx, c.id());
        }
        return avec.lecture().exiger(ctx, c.id());
    }

    /** Un tour expiré : le premier poste restant admissible est choisi pour la personne ; sans poste admissible, elle passe. */
    private void solder(Contexte ctx, AgphCampagne c, AgphRang courant) {
        Ressource r = avec.ressources().exiger(ctx, courant.ressourceId());
        Optional<Poste> retenu = avec.lecture().postesLibres(ctx, c.id()).stream().filter(p -> juger(ctx, r, p, courant.rang()).admissible()).findFirst();
        if (retenu.isPresent()) {
            enregistrerChoix(ctx, c, new Choix(r, retenu.get(), Agph.ModeChoix.AUTOMATIQUE, courant.tourFin()));
        } else {
            depot.poserEtatRang(ctx.tenantId(), c.id(), r.id(), Agph.EtatRang.PASSE);
            journal.ecrire(ctx, new JournalEffectifs.Consignation("agph.tour.passe", OBJET, c.id(), c.uniteCode(), null, Map.of("ressourceId", r.id()),
                    MECANISME));
        }
        tourSuivant(ctx, c, courant.tourFin());
    }
}
