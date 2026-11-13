package ca.vina.nexu.approbations.infrastructure.persistance;

import ca.vina.nexu.approbations.domaine.Delegataires;
import ca.vina.nexu.auth.domaine.DroitDApprouver;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import org.springframework.stereotype.Component;

/**
 * {@code peutApprouver} du Node ({@code payroll/scope.ts}) : le superadmin ; la capacité
 * {@code approval.decide} du rôle dans l'établissement ; l'encadrement réel (organigramme ou module
 * inscrit) ; une délégation reçue en vigueur. Le droit d'entrer et le droit de décider viennent du
 * même endroit — sinon l'un barre la porte que l'autre ouvre.
 */
@Component
public class AdaptateurDroitDApprouver implements DroitDApprouver {

    static final String CAPACITE = "approval.decide";
    private static final String SUPERADMIN = "superadmin";

    private final DepotRolesEtablissement roles;
    private final AnnuaireEmployes personnes;
    private final Delegataires.Comptes comptes;

    public AdaptateurDroitDApprouver(DepotRolesEtablissement roles, AnnuaireEmployes personnes, Delegataires.Comptes comptes) {
        this.roles = roles;
        this.personnes = personnes;
        this.comptes = comptes;
    }

    @Override
    public boolean peutApprouver(String tenantId, String userId, String role, String employeeId) {
        if (SUPERADMIN.equals(role) || roles.capacitesDe(tenantId, role).contains(CAPACITE)) {
            return true;
        }
        boolean encadre = employeeId != null && tenantId != null && !AnnuaireEmployes.subordonnes(employeeId, personnes.tous(tenantId)).isEmpty();
        return encadre || (userId != null && comptes.aRecuUneDelegation(tenantId, userId));
    }
}
