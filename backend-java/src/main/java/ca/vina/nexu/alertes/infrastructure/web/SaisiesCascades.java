package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnairePolitiques;
import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.util.List;
import java.util.Map;

/** La frontière des politiques : un corps JSON devient une politique typée, une fois ; un code inconnu répond 400 nommé. */
final class SaisiesCascades {

    private SaisiesCascades() {
    }

    static GestionnairePolitiques.Contenu contenu(Map<String, Object> c) {
        String arret = SaisiesAlertes.texte(c, "arret");
        return new GestionnairePolitiques.Contenu(SaisiesAlertes.texte(c, "nom"), etapes(c.get("etapes")), Cascades.CritereArret.de(arret == null ? "" : arret));
    }

    private static List<Cascades.Etape> etapes(Object v) {
        if (!(v instanceof List<?> l) || l.stream().anyMatch(x -> !(x instanceof Map<?, ?>))) {
            throw RefusAlerte.invalide("« etapes » doit être une liste d'étapes");
        }
        return l.stream().map(x -> etape(carte(x))).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> carte(Object x) {
        return (Map<String, Object>) x;
    }

    private static Cascades.Etape etape(Map<String, Object> e) {
        String cible = SaisiesAlertes.texte(e, "cible");
        return new Cascades.Etape(Cascades.Cible.de(cible == null ? "" : cible), SaisiesAlertes.texte(e, "cibleId"),
                SaisiesAlertes.textes(e, "canaux").stream().map(Priorite.Canal::de).toList(), entier(e, "delaiMinutes"));
    }

    static int entier(Map<String, Object> c, String champ) {
        if (!(c.get(champ) instanceof Number n) || n.doubleValue() != Math.rint(n.doubleValue())) {
            throw RefusAlerte.invalide("« " + champ + " » doit être un nombre entier");
        }
        return n.intValue();
    }
}
