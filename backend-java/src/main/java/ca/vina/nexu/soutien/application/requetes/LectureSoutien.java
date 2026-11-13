package ca.vina.nexu.soutien.application.requetes;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.temps.domaine.Horloge;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import ca.vina.nexu.soutien.domaine.Soutien;

import java.util.List;

/** Ce que l'auteur et le soutien lisent : politique, billets, une pièce, le résumé, le dispositif d'avis, les maintenances, la disponibilité. */
public final class LectureSoutien {

    private final Soutien.Depot depot;
    private final Horloge horloge;
    private final EngagementsDeService.Registre engagements;

    public LectureSoutien(Soutien.Depot depot, Horloge horloge, EngagementsDeService.Registre engagements) {
        this.depot = depot;
        this.horloge = horloge;
        this.engagements = engagements;
    }

    /** La politique de l'organisme de l'appelant ; sans organisme (visiteur, superadmin), la standard. */
    public Soutien.Politique politique(String tenantId) {
        return Soutien.politique(EngagementsDeService.de(engagements.de(tenantId)));
    }

    public List<Soutien.Ticket> tickets(Soutien.Acteur acteur, String statut) {
        return depot.listerTickets(acteur, statut);
    }

    public List<Soutien.GroupeEtablissement> ticketsParEtablissement(Soutien.Acteur acteur) {
        return depot.listerParEtablissement(acteur);
    }

    public Soutien.Resume resume() {
        return depot.resume();
    }

    public Soutien.Ticket ticket(Soutien.Acteur acteur, String id) {
        return depot.lireTicket(acteur, id).orElseThrow(() -> new IntrouvableException("billet introuvable"));
    }

    public Soutien.Contenu piece(Soutien.Acteur acteur, String id, String pieceId) {
        return depot.lirePiece(acteur, id, pieceId).orElseThrow(() -> new IntrouvableException("pièce introuvable"));
    }

    public Soutien.EtatNotification notification(String userId) {
        return depot.etatNotification(userId);
    }

    public List<Soutien.Maintenance> maintenances() {
        return depot.listerMaintenances();
    }

    /** Le mois courant à défaut — celui de l'horloge, en UTC. */
    public Soutien.Disponibilite disponibilite(String mois) {
        return depot.disponibilite(mois == null || mois.isEmpty() ? horloge.aujourdhui().substring(0, 7) : mois);
    }
}
