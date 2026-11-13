package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotReferentielsPort;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;
import ca.vina.nexu.effectifs.domaine.ValeurReferentiel;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Adaptateur SQLite des référentiels datés ({@code eff_referentiels}). */
@Repository("effectifsDepotReferentiels")
public class DepotReferentiels implements DepotReferentielsPort {

    private static final RowMapper<ValeurReferentiel> VALEUR = (rs, i) -> new ValeurReferentiel(rs.getString("id"), rs.getString("type"), rs.getString("code"),
            rs.getString("libelleFr"), rs.getString("libelleEn"), rs.getString("comportement"), rs.getInt("ordre"), JsonEffectifs.carte(rs.getString("attributs")),
            rs.getString("effectifDepuis"), rs.getString("effectifJusqua"));

    private final JdbcClient jdbc;

    public DepotReferentiels(JdbcClient jdbc, SchemaEffectifs schema) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ValeurReferentiel> versions(String tenantId, TypeReferentiel type, String code) {
        return jdbc.sql("SELECT * FROM eff_referentiels WHERE tenantId = ? AND type = ? AND code = ? ORDER BY effectifDepuis")
                .params(tenantId, type.code(), code).query(VALEUR).list();
    }

    @Override
    public List<ValeurReferentiel> enVigueur(String tenantId, TypeReferentiel type, String date) {
        return jdbc.sql("SELECT * FROM eff_referentiels WHERE tenantId = ? AND type = ? AND effectifDepuis <= ? AND (effectifJusqua IS NULL OR ? < effectifJusqua) ORDER BY ordre, code")
                .params(tenantId, type.code(), date, date).query(VALEUR).list();
    }

    @Override
    public List<ValeurReferentiel> historique(String tenantId, TypeReferentiel type) {
        return jdbc.sql("SELECT * FROM eff_referentiels WHERE tenantId = ? AND type = ? ORDER BY code, effectifDepuis").params(tenantId, type.code()).query(VALEUR).list();
    }

    @Override
    public void inserer(String tenantId, ValeurReferentiel v, String creeLe, String creePar) {
        jdbc.sql("INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, libelleEn, comportement, ordre, attributs, effectifDepuis, effectifJusqua, creeLe, creePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(v.id(), tenantId, v.type(), v.code(), v.libelleFr(), v.libelleEn(), v.comportement(), v.ordre(), JsonEffectifs.ecrire(v.attributs()),
                        v.effectifDepuis(), v.effectifJusqua(), creeLe, creePar).update();
    }

    @Override
    public void fermer(String tenantId, String id, String date) {
        jdbc.sql("UPDATE eff_referentiels SET effectifJusqua = ? WHERE tenantId = ? AND id = ?").params(date, tenantId, id).update();
    }
}
