package ca.vina.nexu.rapports.infrastructure.web;

import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * <b>L'accès aux sources du catalogue</b> : ouverte à l'organisme (son module est actif), puis permise au
 * compte (la capacité qu'elle déclare). Rapports et indicateurs passent par ce même contrôle — une
 * seconde garde dériverait de la première.
 */
final class SourcesOuvertes {

    /** Les modules dont une source dépend, <b>déduits du catalogue</b>, jamais listés à côté. */
    private static final List<String> MODULES = CatalogueRapports.SOURCES.stream().map(CatalogueRapports.Source::module).distinct().toList();

    private final DepotQuotas quotas;
    private final GardeAcces garde;

    SourcesOuvertes(DepotQuotas quotas, GardeAcces garde) {
        this.quotas = quotas;
        this.garde = garde;
    }

    /** Les modules actifs de l'organisme, lus par le <b>même</b> mécanisme que la garde de module. */
    List<String> actifs(String tenantId) {
        List<String> ouverts = quotas.quota(tenantId).map(DepotQuotas.Quota::modules).orElse(List.of());
        return MODULES.stream().filter(ouverts::contains).toList();
    }

    /** Une source d'un module coupé se refuse ici : le domaine ne connaît pas les modules de l'organisme. */
    void exigerDisponible(String tenantId, Object source) {
        String code = source == null ? "" : String.valueOf(source);
        if (CatalogueRapports.sourcesDisponibles(actifs(tenantId)).stream().noneMatch(s -> s.code().equals(code))) {
            throw new CatalogueRapports.RapportInvalideException("la source « " + code
                    + " » n'est pas disponible : le module correspondant n'est pas activé pour cet organisme");
        }
    }

    /** Le refus (403, capacité nommée) si le compte n'a pas la capacité qu'exige la source ; {@code null} sinon. Chaque source en déclare une. */
    ResponseEntity<Object> refusCapacite(HttpServletRequest requete, String source) {
        GardeAcces.Verdict v = garde.exigerCapacite(requete, CatalogueRapports.sourceDe(source).capacite());
        return v.refuse() ? v.refus() : null;
    }
}
