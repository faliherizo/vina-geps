package ca.vina.nexu.soutien.application.commandes;

import ca.vina.nexu.soutien.domaine.Soutien;

import java.util.List;
import java.util.Map;

/** Ce qui change un billet ou le service : ouverture, réponse, satisfaction, statut, courriel d'avis, maintenances. */
public final class GestionnaireSoutien {

    private final Soutien.Depot depot;

    public GestionnaireSoutien(Soutien.Depot depot) {
        this.depot = depot;
    }

    public Soutien.Ticket ouvrir(Soutien.Acteur acteur, Map<String, Object> corps) {
        return depot.ouvrirTicket(acteur, corps);
    }

    @SuppressWarnings("unchecked")
    public Soutien.Ticket repondre(Soutien.Acteur acteur, String id, Map<String, Object> m) {
        List<Map<String, Object>> pieces = m.get("pieces") instanceof List<?> l ? l.stream().map(x -> x instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.<String, Object>of()).toList() : List.of();
        return depot.repondre(acteur, id, m.get("texte") == null ? "" : String.valueOf(m.get("texte")), Boolean.TRUE.equals(m.get("interne")), pieces);
    }

    public Soutien.Ticket noterSatisfaction(Soutien.Acteur acteur, String id, Map<String, Object> m) {
        return depot.noterSatisfaction(acteur, id, m.get("note"), m.get("commentaire"));
    }

    public Soutien.EtatNotification reglerCourriel(String userId, Map<String, Object> m) {
        return depot.reglerCourriel(userId, m.get("courriel"));
    }

    public Soutien.Ticket changerStatut(Soutien.Acteur acteur, String id, Map<String, Object> m) {
        return depot.changerStatut(acteur, id, String.valueOf(m.get("statut")));
    }

    public Soutien.Maintenance planifier(String acteurId, Map<String, Object> m) {
        return depot.planifierMaintenance(acteurId, m);
    }

    public Soutien.Maintenance annuler(String id) {
        return depot.annulerMaintenance(id);
    }
}
