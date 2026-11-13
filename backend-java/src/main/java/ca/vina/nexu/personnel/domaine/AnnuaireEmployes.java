package ca.vina.nexu.personnel.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * L'annuaire des employés d'un établissement, réduit à ce que le socle en lit : qui est qui, et qui
 * relève de qui. La fiche complète (paie, coordonnées) n'appartient pas à ce produit.
 */
public interface AnnuaireEmployes {

    record Fiche(String id, String prenom, String nom, String gestionnaireId) {
        public String nomComplet() {
            return ((prenom == null ? "" : prenom) + " " + (nom == null ? "" : nom)).trim();
        }
    }

    Optional<Fiche> lire(String tenantId, String id);

    List<Fiche> tous(String tenantId);

    /** Toute la ligne hiérarchique sous un gestionnaire, en largeur d'abord. */
    static List<Fiche> subordonnes(String gestionnaireId, List<Fiche> bassin) {
        List<Fiche> resultat = new ArrayList<>();
        List<String> file = new ArrayList<>(List.of(gestionnaireId));
        while (!file.isEmpty()) {
            String id = file.remove(0);
            for (Fiche f : bassin) {
                if (id.equals(f.gestionnaireId())) {
                    resultat.add(f);
                    file.add(f.id());
                }
            }
        }
        return resultat;
    }
}
