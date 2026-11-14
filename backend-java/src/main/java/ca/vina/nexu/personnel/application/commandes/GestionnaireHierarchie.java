package ca.vina.nexu.personnel.application.commandes;

import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import ca.vina.nexu.personnel.domaine.Hierarchie;

import java.util.List;

/** Désigner le supérieur hiérarchique d'une personne — ou le retirer. */
public final class GestionnaireHierarchie {

    private final Hierarchie.Ecriture ecriture;
    private final AnnuaireEmployes annuaire;

    public GestionnaireHierarchie(Hierarchie.Ecriture ecriture, AnnuaireEmployes annuaire) {
        this.ecriture = ecriture;
        this.annuaire = annuaire;
    }

    public AnnuaireEmployes.Fiche designer(String tenantId, String id, String superieurId) {
        String s = superieurId == null || superieurId.isBlank() ? null : superieurId.strip();
        Hierarchie.verifier(id, s, annuaire.tous(tenantId));
        ecriture.designerSuperieur(tenantId, id, s);
        return annuaire.lire(tenantId, id).orElseThrow();
    }

    /** L'organigramme de l'établissement : chaque personne et son supérieur. */
    public List<AnnuaireEmployes.Fiche> organigramme(String tenantId) {
        return annuaire.tous(tenantId);
    }
}
