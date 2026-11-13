package ca.vina.nexu.superadmin.infrastructure.persistance;

import ca.vina.nexu.saas.domaine.Locataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Les établissements de l'administration du système, servis par les dépôts du contexte {@code saas} — l'adaptateur vit ici pour ne pas faire dépendre {@code saas} de {@code superadmin}. */
@Component
public class AdaptateurEtablissements implements AdministrationSysteme.Etablissements {

    private final DepotLocataires locataires;
    private final DepotQuotas quotas;

    public AdaptateurEtablissements(DepotLocataires locataires, DepotQuotas quotas) {
        this.locataires = locataires;
        this.quotas = quotas;
    }

    @Override
    public Map<String, Object> creer(Locataires.Demande demande) {
        return locataires.creer(demande);
    }

    @Override
    public Map<String, Object> modifier(String id, AdministrationSysteme.Modification m) {
        return locataires.modifier(id, new DepotLocataires.Modification(m.name(), m.region(), m.address(), m.city(), m.postalCode(), m.phone(), m.email(), m.slug()));
    }

    @Override
    public Map<String, Object> activer(String id, int mois) {
        return locataires.activer(id, mois);
    }

    @Override
    public Map<String, Object> changerStatut(String id, String statut) {
        return locataires.changerStatut(id, statut);
    }

    @Override
    public List<String> modules(String id) {
        return quotas.quota(id).map(DepotQuotas.Quota::modules).orElse(List.of());
    }
}
