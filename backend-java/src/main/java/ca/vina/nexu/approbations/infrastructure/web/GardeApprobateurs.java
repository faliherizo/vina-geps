package ca.vina.nexu.approbations.infrastructure.web;

import ca.vina.nexu.auth.domaine.DroitDApprouver;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * {@code requireSupervisor} du moteur d'approbation : <strong>qui peut lui-même approuver</strong>,
 * selon la même règle que le {@code peutApprouver} rendu à la connexion — un menu et une porte qui
 * divergent se lisent comme une panne.
 */
@Component
public class GardeApprobateurs {

    private final GardeAcces garde;
    private final DroitDApprouver droit;

    public GardeApprobateurs(GardeAcces garde, DroitDApprouver droit) {
        this.garde = garde;
        this.droit = droit;
    }

    public GardeAcces.Verdict approbateur(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        ContexteRequete c = GardeAcces.contexte(requete);
        return droit.peutApprouver(v.tenantId(), c.userId(), c.role(), c.employeeId()) ? v : new GardeAcces.Verdict(null, Bord.erreur(403, "forbidden"));
    }
}
