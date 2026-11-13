package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;

/** Port des quotas de comblement ({@code eff_quotas}) et de leurs surcharges ({@code eff_surcharges}). */
public interface DepotQuotasPort {

    record Cle(String cle, String uniteCode) { }

    /** {@code uniteCode} nul : toutes les unités. Versions triées par clé puis date d'effet, surcharges par début. */
    List<Quota> lister(String tenantId, String uniteCode);

    Optional<Cle> cleDeLaVersion(String tenantId, String versionId);

    void fermerVersion(String tenantId, String versionId, String jusqua);

    void insererVersion(String tenantId, String id, String uniteCode, String plageCode, String natureCode, int quota, String depuis, String creeLe);

    void insererSurcharge(String tenantId, String id, String cle, Quota.SurchargeMotivee s);
}
