package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotQuotasPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Quota;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Les lectures des quotas de comblement — jumeau de la partie lecture de {@code serviceQuotas.ts}. */
public class LectureQuotas {

    private final DepotQuotasPort depot;
    private final LecturePerimetres perimetres;

    public LectureQuotas(DepotQuotasPort depot, LecturePerimetres perimetres) {
        this.depot = depot;
        this.perimetres = perimetres;
    }

    public List<Quota> deUnite(Contexte ctx, String uniteCode) {
        return depot.lister(ctx.tenantId(), uniteCode);
    }

    public Optional<Quota> parCle(Contexte ctx, String uniteCode, String cle) {
        return deUnite(ctx, uniteCode).stream().filter(q -> q.cle().equals(cle)).findFirst();
    }

    public List<Quota> lister(Contexte ctx) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId(), null).stream().filter(q -> visibles == null || visibles.contains(q.uniteCode())).toList();
    }
}
