package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.domaine.AlerteSuivie;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Bilan;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;

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
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.id());
        v.put("titre", m.titre());
        v.put("corps", m.contenu().corps());
        v.put("priorite", m.priorite().code());
        v.put("reponseIds", m.reponseIds());
        v.put("groupeIds", m.contenu().groupeIds());
        v.put("version", m.version());
        v.put("actif", m.actif());
        return v;
    }

    /** Une version d'un modèle : ce qu'il disait, qui l'a fait, et quand (ST-G02). */
    static Map<String, Object> version(LectureAlertesPort.VersionModele v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("version", v.modele().version());
        m.put("titre", v.modele().titre());
        m.put("corps", v.modele().contenu().corps());
        m.put("priorite", v.modele().priorite().code());
        m.put("reponseIds", v.modele().reponseIds());
        m.put("creeLe", v.creeLe());
        m.put("creePar", v.creePar());
        return m;
    }

    static Map<String, Object> liste(AnnuaireAlertes.Liste l) {
        return Map.of("id", l.id(), "nom", l.nom());
    }

    static Map<String, Object> reglages(ca.vina.nexu.alertes.domaine.Alertes.Reglages r) {
        return Map.of("copieCourriel", r.copieCourriel());
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
        m.put("modeleId", a.modele() == null ? null : a.modele().id());
        m.put("modeleVersion", a.modele() == null ? null : a.modele().version());
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

    /** Le suivi : le bilan (relances comprises, critère 11), et chaque remise. */
    static Map<String, Object> suivi(AlerteSuivie s) {
        Alerte a = s.alerte();
        Map<String, Object> m = entete(a);
        Bilan b = a.bilan();
        Map<String, Object> bilan = new LinkedHashMap<>();
        bilan.put("destinataires", b.destinataires());
        bilan.put("lues", b.lues());
        bilan.put("repondues", b.repondues());
        bilan.put("relances", s.relances());
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

    /** Une ligne de l'export du suivi : une alerte et son bilan, à plat. */
    static Map<String, Object> ligneExport(AlerteSuivie s, boolean csv) {
        Alerte a = s.alerte();
        Bilan b = a.bilan();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("envoyeeLe", a.envoyeeLe());
        m.put("titre", cellule(a.titre(), csv));
        m.put("priorite", a.priorite().code());
        m.put("expediteur", cellule(a.expediteurId(), csv));
        m.put("modele", a.modele() == null ? "" : a.modele().id() + " v" + a.modele().version());
        m.put("destinataires", b.destinataires());
        m.put("lues", b.lues());
        m.put("reponses", b.repondues());
        m.put("relances", s.relances());
        m.put("priseEnChargePar", cellule(b.premierePriseEnCharge().map(r -> r.destinataire().nom()).orElse(""), csv));
        m.put("priseEnChargeLe", b.premierePriseEnCharge().map(Remise::reponduLe).orElse(""));
        return m;
    }

    /**
     * Un texte saisi par un usager qui commence par « = », « + », « - », « @ » ou une tabulation s'exécuterait comme une
     * formule à l'ouverture du CSV dans un tableur : il est précédé d'une apostrophe, qui le garde texte.
     */
    static String cellule(String texte, boolean csv) {
        if (!csv || texte == null || texte.isEmpty()) {
            return texte;
        }
        return "=+-@\t\r".indexOf(texte.charAt(0)) >= 0 ? "'" + texte : texte;
    }
}
