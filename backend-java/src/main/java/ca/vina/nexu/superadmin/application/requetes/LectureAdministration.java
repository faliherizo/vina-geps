package ca.vina.nexu.superadmin.application.requetes;

import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;

import java.util.List;
import java.util.Map;

/** Ce que l'administration du système lit : établissements et leurs administrateurs. */
public final class LectureAdministration {

    private final AdministrationSysteme.Etablissements etablissements;
    private final AdministrationSysteme.Administrateurs admins;

    public LectureAdministration(AdministrationSysteme.Etablissements etablissements, AdministrationSysteme.Administrateurs admins) {
        this.etablissements = etablissements;
        this.admins = admins;
    }

    public List<Map<String, Object>> etablissements() {
        return admins.etablissementsAvecAdmins(etablissements::modules);
    }

    public List<Map<String, Object>> administrateurs(String tenantId) {
        return admins.lister(tenantId);
    }

}
