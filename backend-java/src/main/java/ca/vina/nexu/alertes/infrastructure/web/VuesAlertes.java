package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Bilan;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ce que l'écran reçoit. Le destinataire ne voit <strong>que sa remise</strong> : la liste des autres
 * destinataires et leurs réponses appartiennent au suivi des expéditeurs (R6, R7).
 */
final class VuesAlertes {

    private VuesAlertes() {
    }

    static Map<String, Object> reponse(ReponsePredefinie r) {
        return Map.of("id", r.id(), "libelle", r.libelle(), "semantique", r.semantique().code(), "active", r.active());
    }

    static Map<String, Object> modele(Modele m) {
        return Map.of("id", m.id(), "titre", m.titre(), "corps", m.corps(), "priorite", m.priorite().code(), "reponseIds", m.reponseIds(), "actif", m.actif());
    }

    static Map<String, Object> personne(ca.vina.nexu.alertes.domaine.Alertes.Destinataire d) {
        return Map.of("personId", d.personId(), "nom", d.nom());
    }

    static Map<String, Object> groupe(AnnuaireAlertes.Groupe g) {
        return Map.of("id", g.id(), "nom", g.nom());
    }

    private static Map<String, Object> entete(Alerte a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("titre", a.titre());
        m.put("corps", a.corps());
        m.put("priorite", a.priorite().code());
        m.put("expediteurId", a.expediteurId());
        m.put("envoyeeLe", a.envoyeeLe());
        m.put("reponses", a.reponses().stream().map(VuesAlertes::reponse).toList());
        return m;
    }

    static Map<String, Object> remise(Remise r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personId", r.destinataire().personId());
        m.put("nom", r.destinataire().nom());
        m.put("envoyeeLe", r.envoyeeLe());
        m.put("lueLe", r.lueLe());
        m.put("reponseId", r.reponseId());
        m.put("reponduLe", r.reponduLe());
        return m;
    }

    /** Le suivi : le bilan, et chaque remise. */
    static Map<String, Object> suivi(Alerte a) {
        Map<String, Object> m = entete(a);
        Bilan b = a.bilan();
        Map<String, Object> bilan = new LinkedHashMap<>();
        bilan.put("destinataires", b.destinataires());
        bilan.put("lues", b.lues());
        bilan.put("repondues", b.repondues());
        bilan.put("priseEnChargePar", b.premierePriseEnCharge().map(r -> r.destinataire().nom()).orElse(null));
        bilan.put("priseEnChargeLe", b.premierePriseEnCharge().map(Remise::reponduLe).orElse(null));
        m.put("bilan", bilan);
        m.put("remises", a.remises().stream().map(VuesAlertes::remise).toList());
        return m;
    }

    /** Pour le destinataire : l'alerte et sa propre remise, rien des autres. */
    static Map<String, Object> recue(Alerte a, String personId) {
        Map<String, Object> m = entete(a);
        m.put("maRemise", remise(a.remiseDe(personId)));
        return m;
    }

    static List<Map<String, Object>> recues(List<Alerte> l, String personId) {
        return l.stream().map(a -> recue(a, personId)).toList();
    }
}
