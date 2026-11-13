package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.Milieu;
import ca.vina.nexu.effectifs.domaine.Milieux;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Adaptateur SQLite des milieux ({@code eff_milieux}) et de leurs preuves ({@code eff_milieu_preuves}). */
@Repository("effectifsDepotMilieux")
public class DepotMilieux implements DepotMilieuxPort {

    private final JdbcClient jdbc;

    public DepotMilieux(JdbcClient jdbc, SchemaEffectifs schema) {
        this.jdbc = jdbc;
    }

    private List<Milieux.Preuve> preuves(String tenantId, String milieuId) {
        return jdbc.sql("SELECT mecanisme, exige, parametres FROM eff_milieu_preuves WHERE tenantId = ? AND milieuId = ? ORDER BY mecanisme")
                .params(tenantId, milieuId)
                .query((rs, i) -> new Milieux.Preuve(Milieux.Mecanisme.valueOf(rs.getString("mecanisme")), rs.getInt("exige") == 1, JsonEffectifs.carte(rs.getString("parametres"))))
                .list();
    }

    private static Double decimal(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private static Integer entier(Object v) {
        return v == null ? null : ((Number) v).intValue();
    }

    private Milieu milieu(String tenantId, Map<String, Object> l) {
        return new Milieu((String) l.get("id"), (String) l.get("code"), (String) l.get("libelle"), (String) l.get("typeCode"), (String) l.get("uniteCode"),
                (String) l.get("adresse"), decimal(l.get("latitude")), decimal(l.get("longitude")), entier(l.get("rayonMetres")),
                ((Number) l.get("actif")).intValue() == 1, preuves(tenantId, (String) l.get("id")));
    }

    @Override
    public Optional<Milieu> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM eff_milieux WHERE tenantId = ? AND id = ?").params(tenantId, id).query().listOfRows().stream().findFirst().map(l -> milieu(tenantId, l));
    }

    @Override
    public List<Milieu> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_milieux WHERE tenantId = ? ORDER BY code").param(tenantId).query().listOfRows().stream().map(l -> milieu(tenantId, l)).toList();
    }

    @Override
    public boolean codeExiste(String tenantId, String code) {
        return !jdbc.sql("SELECT 1 FROM eff_milieux WHERE tenantId = ? AND code = ?").params(tenantId, code).query().listOfRows().isEmpty();
    }

    @Override
    public void inserer(String tenantId, String id, String code, Champs c, String creeLe) {
        Milieux.Coordonnees g = c.coordonnees();
        jdbc.sql("INSERT INTO eff_milieux (id, tenantId, code, libelle, typeCode, uniteCode, adresse, latitude, longitude, rayonMetres, actif, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?)")
                .params(id, tenantId, code, c.libelle(), c.typeCode(), c.uniteCode(), c.adresse(), g == null ? null : g.latitude(), g == null ? null : g.longitude(),
                        g == null ? null : g.rayonMetres(), creeLe).update();
    }

    @Override
    public void modifier(String tenantId, String id, Champs c) {
        Milieux.Coordonnees g = c.coordonnees();
        jdbc.sql("UPDATE eff_milieux SET libelle = ?, typeCode = ?, uniteCode = ?, adresse = ?, latitude = ?, longitude = ?, rayonMetres = ?, actif = ? WHERE tenantId = ? AND id = ?")
                .params(c.libelle(), c.typeCode(), c.uniteCode(), c.adresse(), g == null ? null : g.latitude(), g == null ? null : g.longitude(),
                        g == null ? null : g.rayonMetres(), c.actif() ? 1 : 0, tenantId, id).update();
    }

    @Override
    public void remplacerPreuves(String tenantId, String milieuId, List<Milieux.Preuve> preuves, Supplier<String> nouveauSecret) {
        Map<String, String> secrets = new HashMap<>();
        jdbc.sql("SELECT mecanisme, secret FROM eff_milieu_preuves WHERE tenantId = ? AND milieuId = ?").params(tenantId, milieuId).query().listOfRows()
                .forEach(l -> secrets.put((String) l.get("mecanisme"), (String) l.get("secret")));
        jdbc.sql("DELETE FROM eff_milieu_preuves WHERE tenantId = ? AND milieuId = ?").params(tenantId, milieuId).update();
        for (Milieux.Preuve p : preuves) {
            String secret = p.mecanisme() == Milieux.Mecanisme.CODE_QR_DE_LIEU
                    ? Optional.ofNullable(secrets.get(p.mecanisme().name())).orElseGet(nouveauSecret) : null;
            jdbc.sql("INSERT INTO eff_milieu_preuves (tenantId, milieuId, mecanisme, exige, parametres, secret) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(tenantId, milieuId, p.mecanisme().name(), p.exige() ? 1 : 0, JsonEffectifs.ecrire(p.parametres()), secret).update();
        }
    }

    @Override
    public Optional<String> secretQr(String tenantId, String milieuId) {
        return jdbc.sql("SELECT secret FROM eff_milieu_preuves WHERE tenantId = ? AND milieuId = ? AND mecanisme = ?")
                .params(tenantId, milieuId, Milieux.Mecanisme.CODE_QR_DE_LIEU.name()).query(String.class).optional();
    }
}
