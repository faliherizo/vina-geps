package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.Semantique;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Les alertes en base (changeset {@code 0006}). Les codes d'énumération sont convertis ici, une fois :
 * une valeur inconnue en base est une base corrompue, et elle le dit plutôt que de deviner.
 */
public class DepotAlertes implements LectureAlertesPort, EcritureAlertesPort {

    private static final String ALERTES = "SELECT id, tenantId, titre, corps, priorite, expediteurId, envoyeeLe FROM alr_alertes WHERE tenantId = ?";

    private final JdbcClient jdbc;

    public DepotAlertes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ── Réponses et modèles ─────────────────────────────────────────────────

    @Override
    public List<ReponsePredefinie> reponses(String tenantId) {
        return jdbc.sql("SELECT id, libelle, semantique, active FROM alr_reponses WHERE tenantId = ? ORDER BY libelle").param(tenantId)
                .query((rs, i) -> new ReponsePredefinie(rs.getString("id"), rs.getString("libelle"), semantique(rs.getString("semantique")), rs.getInt("active") == 1))
                .list();
    }

    @Override
    public List<Modele> modeles(String tenantId) {
        return jdbc.sql("SELECT id FROM alr_modeles WHERE tenantId = ? ORDER BY titre").param(tenantId).query(String.class).list()
                .stream().map(id -> modele(tenantId, id).orElseThrow()).toList();
    }

    @Override
    public Optional<Modele> modele(String tenantId, String id) {
        List<String> reponses = jdbc.sql("SELECT reponseId FROM alr_modele_reponses WHERE tenantId = ? AND modeleId = ? ORDER BY rang")
                .params(tenantId, id).query(String.class).list();
        return jdbc.sql("SELECT id, titre, corps, priorite, actif FROM alr_modeles WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query((rs, i) -> new Modele(rs.getString("id"), rs.getString("titre"), rs.getString("corps"), priorite(rs.getString("priorite")), reponses, rs.getInt("actif") == 1))
                .optional();
    }

    @Override
    public void enregistrer(String tenantId, ReponsePredefinie r) {
        jdbc.sql("INSERT INTO alr_reponses (id, tenantId, libelle, semantique, active) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET libelle = excluded.libelle, semantique = excluded.semantique, active = excluded.active")
                .params(r.id(), tenantId, r.libelle(), r.semantique().code(), r.active() ? 1 : 0).update();
    }

    @Override
    @Transactional
    public void enregistrer(String tenantId, Modele m) {
        jdbc.sql("INSERT INTO alr_modeles (id, tenantId, titre, corps, priorite, actif) VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET titre = excluded.titre, corps = excluded.corps, priorite = excluded.priorite, actif = excluded.actif")
                .params(m.id(), tenantId, m.titre(), m.corps(), m.priorite().code(), m.actif() ? 1 : 0).update();
        jdbc.sql("DELETE FROM alr_modele_reponses WHERE tenantId = ? AND modeleId = ?").params(tenantId, m.id()).update();
        for (int i = 0; i < m.reponseIds().size(); i++) {
            jdbc.sql("INSERT INTO alr_modele_reponses (tenantId, modeleId, rang, reponseId) VALUES (?, ?, ?, ?)").params(tenantId, m.id(), i, m.reponseIds().get(i)).update();
        }
    }

    // ── Alertes ─────────────────────────────────────────────────────────────

    @Override
    public Optional<Alerte> alerte(String tenantId, String id) {
        return jdbc.sql(ALERTES + " AND id = ?").params(tenantId, id).query(this::entete).optional().map(this::completer);
    }

    @Override
    public List<Alerte> envoyees(String tenantId, int limite) {
        return jdbc.sql(ALERTES + " ORDER BY envoyeeLe DESC, id DESC LIMIT ?").params(tenantId, limite).query(this::entete).list()
                .stream().map(this::completer).toList();
    }

    @Override
    public List<Alerte> recues(String tenantId, String personId, int limite) {
        return jdbc.sql("SELECT a.id, a.tenantId, a.titre, a.corps, a.priorite, a.expediteurId, a.envoyeeLe FROM alr_alertes a "
                        + "JOIN alr_remises r ON r.tenantId = a.tenantId AND r.alerteId = a.id "
                        + "WHERE a.tenantId = ? AND r.personId = ? ORDER BY a.envoyeeLe DESC, a.id DESC LIMIT ?")
                .params(tenantId, personId, limite).query(this::entete).list().stream().map(this::completer).toList();
    }

    @Override
    @Transactional
    public void creer(Alerte a) {
        jdbc.sql("INSERT INTO alr_alertes (id, tenantId, titre, corps, priorite, expediteurId, envoyeeLe) VALUES (?, ?, ?, ?, ?, ?, ?)")
                .params(a.id(), a.tenantId(), a.titre(), a.corps(), a.priorite().code(), a.expediteurId(), a.envoyeeLe()).update();
        for (int i = 0; i < a.reponses().size(); i++) {
            ReponsePredefinie r = a.reponses().get(i);
            jdbc.sql("INSERT INTO alr_alerte_reponses (tenantId, alerteId, rang, reponseId, libelle, semantique) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(a.tenantId(), a.id(), i, r.id(), r.libelle(), r.semantique().code()).update();
        }
        for (Remise r : a.remises()) {
            jdbc.sql("INSERT INTO alr_remises (tenantId, alerteId, personId, nom, compteAvis, envoyeeLe) VALUES (?, ?, ?, ?, ?, ?)")
                    .params(a.tenantId(), a.id(), r.destinataire().personId(), r.destinataire().nom(), r.destinataire().compteAvis(), r.envoyeeLe()).update();
        }
    }

    @Override
    public void mettreAJour(String tenantId, String alerteId, Remise r) {
        jdbc.sql("UPDATE alr_remises SET lueLe = ?, reponseId = ?, reponduLe = ? WHERE tenantId = ? AND alerteId = ? AND personId = ?")
                .params(r.lueLe(), r.reponseId(), r.reponduLe(), tenantId, alerteId, r.destinataire().personId()).update();
    }

    private Alerte entete(ResultSet rs, int i) throws SQLException {
        return new Alerte(rs.getString("id"), rs.getString("tenantId"), rs.getString("titre"), rs.getString("corps"), priorite(rs.getString("priorite")),
                rs.getString("expediteurId"), rs.getString("envoyeeLe"), List.of(), List.of());
    }

    private Alerte completer(Alerte a) {
        List<ReponsePredefinie> reponses = jdbc.sql("SELECT reponseId, libelle, semantique FROM alr_alerte_reponses WHERE tenantId = ? AND alerteId = ? ORDER BY rang")
                .params(a.tenantId(), a.id())
                .query((rs, i) -> new ReponsePredefinie(rs.getString("reponseId"), rs.getString("libelle"), semantique(rs.getString("semantique")), true)).list();
        List<Remise> remises = jdbc.sql("SELECT personId, nom, compteAvis, envoyeeLe, lueLe, reponseId, reponduLe FROM alr_remises WHERE tenantId = ? AND alerteId = ? ORDER BY nom")
                .params(a.tenantId(), a.id())
                .query((rs, i) -> new Remise(new Destinataire(rs.getString("personId"), rs.getString("nom"), rs.getString("compteAvis")),
                        rs.getString("envoyeeLe"), rs.getString("lueLe"), rs.getString("reponseId"), rs.getString("reponduLe"))).list();
        return new Alerte(a.id(), a.tenantId(), a.titre(), a.corps(), a.priorite(), a.expediteurId(), a.envoyeeLe(), reponses, remises);
    }

    private static Priorite priorite(String code) {
        return Priorite.de(code).orElseThrow(() -> new IllegalStateException("priorité inconnue en base : " + code));
    }

    private static Semantique semantique(String code) {
        return Semantique.de(code).orElseThrow(() -> new IllegalStateException("sémantique inconnue en base : " + code));
    }
}
