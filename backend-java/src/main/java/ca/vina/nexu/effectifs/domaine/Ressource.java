package ca.vina.nexu.effectifs.domaine;

import java.util.HashMap;
import java.util.Map;

/** Une personne planifiable, avec ce que les règles doivent savoir d'elle ; chaque code renvoie à un référentiel. */
public record Ressource(String id, String matricule, String nom, String utilisateurId, String uniteCode, String corpsEmploiCode,
                        String titreEmploiCode, String statutEmploiCode, String conventionCode, double anciennete, boolean actif) {

    /**
     * Les <b>portées de règle</b> de cette personne : par quoi une règle peut la viser.
     *
     * <p>Cette carte se construisait à l'endroit du jugement. Elle est désormais portée par la
     * ressource elle-même, parce qu'elle est maintenant lue à deux endroits — au dépôt, qui juge, et
     * à l'annonce des règles applicables, qui ne juge pas. Deux copies auraient divergé au premier
     * axe ajouté, et la copie oubliée aurait annoncé d'autres règles que celles qui décident.</p>
     */
    public Map<String, String> porteesDeRegle() {
        Map<String, String> portees = new HashMap<>();
        portees.put("convention", conventionCode);
        portees.put("statutEmploi", statutEmploiCode);
        portees.put("corpsEmploi", corpsEmploiCode);
        portees.put("titreEmploi", titreEmploiCode);
        return portees;
    }
}
