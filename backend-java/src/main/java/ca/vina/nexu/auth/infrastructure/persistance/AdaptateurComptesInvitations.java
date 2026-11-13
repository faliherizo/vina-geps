package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Invitations;

/** Les comptes nés d'une invitation acceptée : changement de mot de passe épargné, configuration de sécurité exigée. */
public final class AdaptateurComptesInvitations implements Invitations.Comptes {

    private final DepotComptes comptes;

    public AdaptateurComptesInvitations(DepotComptes comptes) {
        this.comptes = comptes;
    }

    @Override
    public String creer(Invitations.Invitation i, String username, String password) {
        return comptes.creer(new DepotComptes.Creation(username, password, i.role(), i.employeeId(), i.tenantId(), false, true, i.courriel(), true)).id();
    }
}
