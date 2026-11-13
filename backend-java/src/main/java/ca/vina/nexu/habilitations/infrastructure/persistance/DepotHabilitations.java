package ca.vina.nexu.habilitations.infrastructure.persistance;

import ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort;
import ca.vina.nexu.habilitations.domaine.Habilitations;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

/** La persistance du contexte {@code habilitations} (migration 0119). */
@Repository
public class DepotHabilitations implements DepotHabilitationsPort {

    private final JdbcClient jdbc;

    public DepotHabilitations(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static String id(String prefixe) {
        return prefixe + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    @Override
    public List<Habilitations.Objet> listerObjets(String tenantId) {
        return jdbc.sql("SELECT type, code, libelle, parentCode FROM hab_objets WHERE tenantId = ? ORDER BY type, code")
                .param(tenantId)
                .query((ResultSet rs, int i) -> new Habilitations.Objet(
                        Habilitations.TypeObjet.de(rs.getString("type")), rs.getString("code"),
                        rs.getString("libelle"), rs.getString("parentCode")))
                .list();
    }

    @Override
    public void poserObjet(String tenantId, Habilitations.Objet o, String creeLe) {
        jdbc.sql("""
                INSERT INTO hab_objets (id, tenantId, type, code, libelle, parentCode, creeLe)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenantId, code) DO UPDATE SET type = excluded.type,
                  libelle = excluded.libelle, parentCode = excluded.parentCode""")
                .params(id("hobj"), tenantId, o.type().code(), o.code(), o.libelle(), o.parentCode(), creeLe)
                .update();
    }

    @Override
    public List<Habilitations.Octroi> listerOctrois(String tenantId) {
        return jdbc.sql("""
                SELECT sujetType, sujetId, objetType, objetCode, permission, effectifDu, effectifAu, motif
                FROM hab_octrois WHERE tenantId = ?""")
                .param(tenantId)
                .query((ResultSet rs, int i) -> new Habilitations.Octroi(
                        Habilitations.Sujet.de(rs.getString("sujetType")), rs.getString("sujetId"),
                        Habilitations.TypeObjet.de(rs.getString("objetType")), rs.getString("objetCode"),
                        Habilitations.Permission.de(rs.getString("permission")),
                        rs.getString("effectifDu"), rs.getString("effectifAu"), rs.getString("motif")))
                .list();
    }

    @Override
    public void poserOctroi(String tenantId, Habilitations.Octroi o, String auteurId, String creeLe) {
        jdbc.sql("""
                INSERT INTO hab_octrois (id, tenantId, sujetType, sujetId, objetType, objetCode, permission,
                                         effectifDu, effectifAu, motif, auteurId, creeLe)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (tenantId, sujetType, sujetId, objetCode, effectifDu)
                DO UPDATE SET permission = excluded.permission, effectifAu = excluded.effectifAu,
                  motif = excluded.motif, auteurId = excluded.auteurId""")
                .params(id("oct"), tenantId, o.sujetType().code(), o.sujetId(), o.objetType().code(),
                        o.objetCode(), o.permission().code(), o.effectifDu(), o.effectifAu(), o.motif(),
                        auteurId, creeLe)
                .update();
    }

    @Override
    public boolean retirerOctroi(String tenantId, Habilitations.Sujet sujetType, String sujetId,
                                 String objetCode, String effectifDu) {
        return jdbc.sql("""
                DELETE FROM hab_octrois WHERE tenantId = ? AND sujetType = ? AND sujetId = ?
                  AND objetCode = ? AND effectifDu = ?""")
                .params(tenantId, sujetType.code(), sujetId, objetCode, effectifDu).update() > 0;
    }

    @Override
    public List<String> profilsDe(String tenantId, String utilisateurId) {
        return jdbc.sql("""
                SELECT profilId FROM hab_profils_utilisateurs
                WHERE tenantId = ? AND utilisateurId = ? ORDER BY profilId""")
                .params(tenantId, utilisateurId).query(String.class).list();
    }

    @Override
    public void rattacherProfil(String tenantId, String utilisateurId, String profilId, String creeLe) {
        jdbc.sql("""
                INSERT OR IGNORE INTO hab_profils_utilisateurs (tenantId, utilisateurId, profilId, creeLe)
                VALUES (?, ?, ?, ?)""")
                .params(tenantId, utilisateurId, profilId, creeLe).update();
    }

    @Override
    public boolean detacherProfil(String tenantId, String utilisateurId, String profilId) {
        return jdbc.sql("""
                DELETE FROM hab_profils_utilisateurs
                WHERE tenantId = ? AND utilisateurId = ? AND profilId = ?""")
                .params(tenantId, utilisateurId, profilId).update() > 0;
    }
}
