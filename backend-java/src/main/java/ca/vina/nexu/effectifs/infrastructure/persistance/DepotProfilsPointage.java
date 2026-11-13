package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureProfilsPointagePort;
import ca.vina.nexu.effectifs.domaine.LectureProfilsPointagePort;
import ca.vina.nexu.effectifs.domaine.NaturePause;
import ca.vina.nexu.effectifs.domaine.PorteeRegle;
import ca.vina.nexu.effectifs.domaine.ProfilsPointage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Adaptateur SQLite de {@code eff_profils_pointage}, identique à {@code presence/serviceProfils.ts} : les natures non payées
 * en tableau JSON de leurs codes, les booléens en 0/1.
 */
@Repository("effectifsDepotProfilsPointage")
public class DepotProfilsPointage implements LectureProfilsPointagePort, EcritureProfilsPointagePort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> CODES = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public DepotProfilsPointage(JdbcClient jdbc, SchemaPresenceEffectifs schema) {
        this.jdbc = jdbc;
    }

    private static ProfilsPointage.ProfilPointage profil(ResultSet rs, int i) throws SQLException {
        List<NaturePause> natures = JSON.readValue(rs.getString("naturesNonPayees"), CODES).stream().map(NaturePause::valueOf).toList();
        int fenetre = rs.getInt("fenetreAPosterioriJours");
        return new ProfilsPointage.ProfilPointage(rs.getString("code"), rs.getString("libelle"), ProfilsPointage.ModeSaisie.valueOf(rs.getString("mode")),
                PorteeRegle.valueOf(rs.getString("porteeType")), rs.getString("porteeCode"), natures, rs.getInt("pausesManuelles") == 1,
                rs.getInt("presencePrealableMinutes"), rs.wasNull() ? null : fenetre, rs.getInt("actif") == 1);
    }

    @Override
    public List<ProfilsPointage.ProfilPointage> lister(String tenantId) {
        return jdbc.sql("SELECT * FROM eff_profils_pointage WHERE tenantId = ? ORDER BY code").param(tenantId).query(DepotProfilsPointage::profil).list();
    }

    @Override
    public void poser(Contexte ctx, ProfilsPointage.ProfilPointage p) {
        jdbc.sql("INSERT INTO eff_profils_pointage (tenantId, code, libelle, mode, porteeType, porteeCode, naturesNonPayees, pausesManuelles, "
                        + "presencePrealableMinutes, fenetreAPosterioriJours, actif, majLe, majPar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, code) DO UPDATE SET libelle = excluded.libelle, mode = excluded.mode, porteeType = excluded.porteeType, "
                        + "porteeCode = excluded.porteeCode, naturesNonPayees = excluded.naturesNonPayees, pausesManuelles = excluded.pausesManuelles, "
                        + "presencePrealableMinutes = excluded.presencePrealableMinutes, fenetreAPosterioriJours = excluded.fenetreAPosterioriJours, "
                        + "actif = excluded.actif, majLe = excluded.majLe, majPar = excluded.majPar")
                .params(ctx.tenantId(), p.code(), p.libelle(), p.mode().name(), p.porteeType().name(), p.porteeCode(),
                        JSON.writeValueAsString(p.naturesNonPayees().stream().map(NaturePause::code).toList()), p.pausesManuelles() ? 1 : 0,
                        p.presencePrealableMinutes(), p.fenetreAPosterioriJours(), p.actif() ? 1 : 0, ctx.maintenant(), ctx.acteurId())
                .update();
    }

    @Override
    public void retirer(String tenantId, String code) {
        jdbc.sql("DELETE FROM eff_profils_pointage WHERE tenantId = ? AND code = ?").params(tenantId, code).update();
    }
}
