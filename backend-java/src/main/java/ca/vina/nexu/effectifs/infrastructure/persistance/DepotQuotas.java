package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotQuotasPort;
import ca.vina.nexu.effectifs.domaine.Quota;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptateur SQLite des quotas ({@code eff_quotas}, une ligne par version) et des surcharges ({@code eff_surcharges}). */
@Repository("effectifsDepotQuotas")
public class DepotQuotas implements DepotQuotasPort {

    private final JdbcClient jdbc;

    public DepotQuotas(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    private record Ligne(String cle, String uniteCode, String plageCode, String natureCode, Quota.Version version) { }

    @Override
    public List<Quota> lister(String tenantId, String uniteCode) {
        String sql = "SELECT id, cle, uniteCode, plageCode, natureCode, quota, effectifDepuis, effectifJusqua FROM eff_quotas WHERE tenantId = :tenantId"
                + (uniteCode == null ? "" : " AND uniteCode = :uniteCode") + " ORDER BY cle, effectifDepuis";
        JdbcClient.StatementSpec s = jdbc.sql(sql).param("tenantId", tenantId);
        if (uniteCode != null) {
            s = s.param("uniteCode", uniteCode);
        }
        List<Ligne> lignes = s.query((rs, i) -> new Ligne(rs.getString("cle"), rs.getString("uniteCode"), rs.getString("plageCode"), rs.getString("natureCode"),
                new Quota.Version(rs.getString("id"), rs.getInt("quota"), rs.getString("effectifDepuis"), rs.getString("effectifJusqua")))).list();
        Map<String, List<Ligne>> parCle = new LinkedHashMap<>();
        lignes.forEach(l -> parCle.computeIfAbsent(l.cle(), k -> new ArrayList<>()).add(l));
        return parCle.values().stream().map(v -> quota(tenantId, v)).toList();
    }

    private Quota quota(String tenantId, List<Ligne> versions) {
        Ligne l = versions.get(0);
        List<Quota.SurchargeMotivee> surcharges = jdbc.sql("SELECT id, valeur, depuis, jusqua, creeLe, motif, auteur FROM eff_surcharges WHERE tenantId = ? AND cle = ? "
                        + "ORDER BY depuis").params(tenantId, l.cle())
                .query((rs, i) -> new Quota.SurchargeMotivee(rs.getString("id"), rs.getInt("valeur"), rs.getString("depuis"), rs.getString("jusqua"),
                        rs.getString("creeLe"), rs.getString("motif"), rs.getString("auteur"))).list();
        return new Quota(l.cle(), l.uniteCode(), l.plageCode(), l.natureCode(), versions.stream().map(Ligne::version).toList(), surcharges);
    }

    @Override
    public Optional<Cle> cleDeLaVersion(String tenantId, String versionId) {
        return jdbc.sql("SELECT cle, uniteCode FROM eff_quotas WHERE tenantId = ? AND id = ?").params(tenantId, versionId)
                .query((rs, i) -> new Cle(rs.getString("cle"), rs.getString("uniteCode"))).optional();
    }

    @Override
    public void fermerVersion(String tenantId, String versionId, String jusqua) {
        jdbc.sql("UPDATE eff_quotas SET effectifJusqua = ? WHERE tenantId = ? AND id = ?").params(jusqua, tenantId, versionId).update();
    }

    @Override
    public void insererVersion(String tenantId, String id, String uniteCode, String plageCode, String natureCode, int quota, String depuis, String creeLe) {
        jdbc.sql("INSERT INTO eff_quotas (id, tenantId, uniteCode, plageCode, natureCode, quota, effectifDepuis, effectifJusqua, cle, creeLe) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?)")
                .params(id, tenantId, uniteCode, plageCode, natureCode, quota, depuis, Quota.cleDe(uniteCode, plageCode, natureCode), creeLe).update();
    }

    @Override
    public void insererSurcharge(String tenantId, String id, String cle, Quota.SurchargeMotivee s) {
        jdbc.sql("INSERT INTO eff_surcharges (id, tenantId, cle, valeur, depuis, jusqua, motif, auteur, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, cle, s.valeur(), s.depuis(), s.jusqua(), s.motif(), s.auteur(), s.creeLe()).update();
    }
}
