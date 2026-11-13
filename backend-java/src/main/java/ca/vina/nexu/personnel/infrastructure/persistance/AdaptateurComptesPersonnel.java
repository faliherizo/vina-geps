package ca.vina.nexu.personnel.infrastructure.persistance;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.personnel.domaine.ComptesPersonnelPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Les comptes passent par le dépôt de l'authentification, qui possède la table : le répertoire ne l'écrit jamais lui-même. */
@Component
public class AdaptateurComptesPersonnel implements ComptesPersonnelPort {

    private final DepotComptes comptes;

    public AdaptateurComptesPersonnel(DepotComptes comptes) {
        this.comptes = comptes;
    }

    @Override
    public Optional<CompteLie> compte(String utilisateurId) {
        return comptes.parId(utilisateurId).map(c -> new CompteLie(c.tenantId(), c.employeeId(), c.email()));
    }

    @Override
    public void rattacher(String utilisateurId, String ficheId) {
        DepotComptes.Modification vide = DepotComptes.Modification.vide();
        comptes.modifier(utilisateurId, new DepotComptes.Modification(vide.role(), vide.active(), vide.password(), Optional.of(ficheId),
                vide.mustChangePassword(), vide.securitySetupRequired(), vide.email()));
    }
}
