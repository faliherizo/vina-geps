package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.Equipage;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Planification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des équipages ({@code eff_equipages}) et de leurs places ({@code eff_places}). */
@Repository("effectifsDepotEquipages")
public class DepotEquipages implements DepotEquipagesPort {

    private static final String COLONNES = "id, periodeId, uniteCode, date, debut, fin, natureCode, plageCode, milieuId, modeAttribution, minimumOperationnel, note";

    private final JdbcClient jdbc;

    public DepotEquipages(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    private static Equipage.Place place(ResultSet rs, int i) throws SQLException {
        String origine = rs.getString("attribueePar");
        return new Equipage.Place(rs.getString("id"), rs.getString("equipageId"), rs.getInt("rang"), rs.getString("corpsEmploiCode"), rs.getString("posteId"),
                rs.getString("ressourceId"), rs.getString("ressourceNom"), origine == null ? null : Equipage.OrigineAttribution.valueOf(origine),
                rs.getString("justificationForcage"), rs.getString("attribueeLe"));
    }

    private List<Equipage.Place> places(String tenantId, String equipageId) {
        return jdbc.sql("SELECT p.id, p.equipageId, p.rang, p.corpsEmploiCode, p.posteId, p.ressourceId, r.nom AS ressourceNom, p.attribueePar, "
                        + "p.justificationForcage, p.attribueeLe FROM eff_places p LEFT JOIN eff_ressources r ON r.id = p.ressourceId AND r.tenantId = p.tenantId "
                        + "WHERE p.tenantId = ? AND p.equipageId = ? ORDER BY p.rang")
                .params(tenantId, equipageId).query(DepotEquipages::place).list();
    }

    private static Equipage equipage(ResultSet rs, int i) throws SQLException {
        String id = rs.getString("id");
        return new Equipage(id, rs.getString("periodeId"), rs.getString("uniteCode"), rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"),
                rs.getString("natureCode"), rs.getString("plageCode"), rs.getString("milieuId"), Planification.ModeAttribution.valueOf(rs.getString("modeAttribution")),
                rs.getInt("minimumOperationnel"), rs.getString("note"), List.of());
    }

    private Equipage complet(String tenantId, Equipage e) {
        return new Equipage(e.id(), e.periodeId(), e.uniteCode(), e.date(), e.debut(), e.fin(), e.natureCode(), e.plageCode(), e.milieuId(), e.modeAttribution(),
                e.minimumOperationnel(), e.note(), places(tenantId, e.id()));
    }

    @Override
    public Optional<Equipage> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_equipages WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query(DepotEquipages::equipage).optional().map(e -> complet(tenantId, e));
    }

    @Override
    public List<Equipage> lister(String tenantId, String periodeId, String date) {
        String sql = "SELECT " + COLONNES + " FROM eff_equipages WHERE tenantId = :tenantId AND periodeId = :periodeId"
                + (date == null ? "" : " AND date = :date") + " ORDER BY date, debut, milieuId";
        JdbcClient.StatementSpec s = jdbc.sql(sql).param("tenantId", tenantId).param("periodeId", periodeId);
        if (date != null) {
            s = s.param("date", date);
        }
        return s.query(DepotEquipages::equipage).list().stream().map(e -> complet(tenantId, e)).toList();
    }

    @Override
    public void inserer(String tenantId, String id, Periode periode, Entete e, String creeLe) {
        jdbc.sql("INSERT INTO eff_equipages (id, tenantId, periodeId, uniteCode, date, debut, fin, natureCode, plageCode, milieuId, modeAttribution, "
                        + "minimumOperationnel, note, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, tenantId, periode.id(), periode.uniteCode(), e.date(), e.debut(), e.fin(), e.natureCode(), e.plageCode(), e.milieuId(),
                        e.modeAttribution().name(), e.minimumOperationnel(), e.note(), creeLe).update();
    }

    @Override
    public void insererPlace(String tenantId, String id, String equipageId, int rang, NouvellePlace p, String maintenant) {
        boolean pourvue = p.ressourceId() != null;
        jdbc.sql("INSERT INTO eff_places (id, tenantId, equipageId, rang, corpsEmploiCode, posteId, ressourceId, attribueePar, justificationForcage, attribueeLe) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)")
                .params(id, tenantId, equipageId, rang, p.corpsEmploiCode(), p.posteId(), p.ressourceId(),
                        pourvue ? Equipage.OrigineAttribution.POSTE.name() : null, pourvue ? maintenant : null).update();
    }

    @Override
    public void modifier(String tenantId, String id, Entete e) {
        jdbc.sql("UPDATE eff_equipages SET debut = ?, fin = ?, milieuId = ?, modeAttribution = ?, minimumOperationnel = ?, note = ? WHERE tenantId = ? AND id = ?")
                .params(e.debut(), e.fin(), e.milieuId(), e.modeAttribution().name(), e.minimumOperationnel(), e.note(), tenantId, id).update();
    }

    @Override
    public Optional<String> equipageDeLaPlace(String tenantId, String placeId) {
        return jdbc.sql("SELECT equipageId FROM eff_places WHERE tenantId = ? AND id = ?").params(tenantId, placeId).query(String.class).optional();
    }

    @Override
    public boolean posteDejaGenere(String tenantId, String posteId, String date) {
        return !jdbc.sql("SELECT 1 FROM eff_places pl JOIN eff_equipages e ON e.id = pl.equipageId AND e.tenantId = pl.tenantId "
                + "WHERE pl.tenantId = ? AND pl.posteId = ? AND e.date = ?").params(tenantId, posteId, date).query().listOfRows().isEmpty();
    }

    @Override
    public List<Planification.Creneau> creneauxDe(String tenantId, String ressourceId, String saufPlaceId) {
        return jdbc.sql("SELECT e.date, e.debut, e.fin FROM eff_places p JOIN eff_equipages e ON e.id = p.equipageId AND e.tenantId = p.tenantId "
                        + "WHERE p.tenantId = ? AND p.ressourceId = ? AND p.id <> ?").params(tenantId, ressourceId, saufPlaceId)
                .query((rs, i) -> new Planification.Creneau(rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"))).list();
    }

    @Override
    public void attribuer(String tenantId, String placeId, String ressourceId, Equipage.OrigineAttribution origine, String justification, String le) {
        jdbc.sql("UPDATE eff_places SET ressourceId = ?, attribueePar = ?, justificationForcage = ?, attribueeLe = ? WHERE tenantId = ? AND id = ?")
                .params(ressourceId, origine.name(), justification, le, tenantId, placeId).update();
    }

    @Override
    public void liberer(String tenantId, String placeId) {
        jdbc.sql("UPDATE eff_places SET ressourceId = NULL, attribueePar = NULL, justificationForcage = NULL, attribueeLe = NULL WHERE tenantId = ? AND id = ?")
                .params(tenantId, placeId).update();
    }
}
