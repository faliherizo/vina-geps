package ca.vina.nexu.organisme.domaine;

import java.util.Map;

/**
 * La charte telle que l'interface la lit : ses champs, et les variables CSS qui en découlent. Le front pose
 * les variables telles quelles — il n'a pas à recalculer une nuance, ni à risquer d'en calculer une autre.
 */
public record CharteAppliquee(String nomAffiche, String couleurPrincipale, String couleurLaterale, String logo, Map<String, String> variables) {

    public static CharteAppliquee de(Charte c) {
        return new CharteAppliquee(c.nomAffiche(), c.couleurPrincipale(), c.couleurLaterale(), c.logo(), c.variables());
    }
}
