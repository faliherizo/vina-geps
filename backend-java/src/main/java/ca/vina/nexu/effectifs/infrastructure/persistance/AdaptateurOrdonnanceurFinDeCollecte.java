package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.FinDeCollectePort;
import ca.vina.nexu.ordonnanceur.domaine.TacheDifferee;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * L'ordonnanceur partagé (ADR 0014) vu de la fin de collecte (P-GH-4) : une tâche différée de type
 * {@link Disponibilites#TACHE_FIN_DE_COLLECTE} par période, planifiée à l'ouverture de la collecte et annulée à la
 * publication. Le bilan du dernier passage se lit au journal des effectifs, où l'attribution le consigne.
 */
@Component("effectifsOrdonnanceurFinDeCollecte")
public class AdaptateurOrdonnanceurFinDeCollecte implements FinDeCollectePort {

    /** L'action que l'attribution de fin de collecte consigne : son bilan y est lu. */
    static final String ACTION_ATTRIBUTION = "periode.attribution.disponibilites";

    private final DepotTaches taches;
    private final RegistreExecuteurs executeurs;
    private final JdbcClient jdbc;

    public AdaptateurOrdonnanceurFinDeCollecte(DepotTaches taches, RegistreExecuteurs executeurs, JdbcClient jdbc, SchemaEffectifs schema) {
        this.taches = taches;
        this.executeurs = executeurs;
        this.jdbc = jdbc;
    }

    @Override
    public void planifier(String tenantId, Echeance e) {
        taches.planifier(tenantId, Disponibilites.TACHE_FIN_DE_COLLECTE, e.periodeId(), e.echeanceLe(), Map.of("finCollecte", e.finCollecte()), e.creePar());
    }

    @Override
    public void annuler(String tenantId, String periodeId) {
        taches.annulerPour(tenantId, Disponibilites.TACHE_FIN_DE_COLLECTE, periodeId);
    }

    /** La dernière par échéance, comme le Node la prend dans la liste ordonnée. */
    @Override
    public Optional<Tache> derniereTache(String tenantId, String periodeId) {
        List<TacheDifferee> siennes = taches.lister(tenantId, null, Disponibilites.TACHE_FIN_DE_COLLECTE).stream()
                .filter(t -> t.cibleId().equals(periodeId)).toList();
        return siennes.isEmpty() ? Optional.empty() : Optional.of(siennes.getLast()).map(t -> new Tache(t.echeanceLe(), t.statut()));
    }

    @Override
    public Optional<Bilan> dernierBilan(String tenantId, String periodeId, String acteurId) {
        return jdbc.sql("SELECT horodatage, apres FROM eff_journal WHERE tenantId = ? AND action = ? AND objetId = ? AND acteurId = ? "
                        + "ORDER BY horodatage DESC, rowid DESC LIMIT 1")
                .params(tenantId, ACTION_ATTRIBUTION, periodeId, acteurId)
                .query((rs, i) -> bilan(rs.getString("horodatage"), JsonEffectifs.carte(rs.getString("apres")))).optional();
    }

    private static Bilan bilan(String le, Map<String, Object> apres) {
        return new Bilan(le, ((Number) apres.get("attribuees")).intValue(), ((Number) apres.get("restees")).intValue());
    }

    /** La fin de collecte ne se reporte jamais : elle aboutit, ou elle lève et l'ordonnanceur retente. */
    @Override
    public void enregistrerExecuteur(Executeur executeur) {
        executeurs.enregistrer(Disponibilites.TACHE_FIN_DE_COLLECTE, (t, maintenant) -> {
            executeur.executer(t.tenantId(), t.cibleId(), maintenant);
            return Optional.empty();
        });
    }
}
