package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.DepotRepercussionPort;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.RepercussionPoste;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Adaptateur SQLite de la répercussion d'un poste sur ses quarts (E-CH-3), jumeau de {@code serviceRepercussion.ts}. */
@Repository("effectifsDepotRepercussion")
public class DepotRepercussion implements DepotRepercussionPort {

    /** Les pointages qui font d'une place un fait accompli ; un pointage rejeté n'en est pas un. */
    private static final List<String> ACTIFS = Arrays.stream(PresenceEffectifs.StatutPointage.values())
            .filter(PresenceEffectifs.StatutPointage::estActif).map(Enum::name).toList();

    private final JdbcClient jdbc;

    public DepotRepercussion(JdbcClient jdbc, SchemaPlanification schema) {
        this.jdbc = jdbc;
    }

    /** Les places pointées : la table des pointages n'existe que si le module de présence a servi. */
    private Set<String> pointees(String tenantId) {
        boolean existe = !jdbc.sql("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'eff_pointages'").query().listOfRows().isEmpty();
        if (!existe) {
            return Set.of();
        }
        List<Object> params = new ArrayList<>(List.of(tenantId));
        params.addAll(ACTIFS);
        return new HashSet<>(jdbc.sql("SELECT placeId FROM eff_pointages WHERE tenantId = ? AND placeId IS NOT NULL AND statut IN ("
                + String.join(", ", Collections.nCopies(ACTIFS.size(), "?")) + ")").params(params).query(String.class).list());
    }

    @Override
    public List<PlaceFuture> placesFutures(String tenantId, String posteId, String jour) {
        Set<String> pointees = pointees(tenantId);
        return jdbc.sql("SELECT pl.id AS placeId, pl.equipageId, pl.corpsEmploiCode, pl.ressourceId, e.periodeId, e.date, e.uniteCode, e.plageCode, e.milieuId, "
                        + "e.natureCode FROM eff_places pl JOIN eff_equipages e ON e.id = pl.equipageId AND e.tenantId = pl.tenantId "
                        + "JOIN eff_periodes p ON p.id = e.periodeId AND p.tenantId = e.tenantId "
                        + "WHERE pl.tenantId = ? AND pl.posteId = ? AND e.date > ? AND p.etat <> ? ORDER BY e.date")
                .params(tenantId, posteId, jour, Planification.EtatPeriode.CLOSE.name())
                .query((rs, i) -> new PlaceFuture(rs.getString("placeId"), rs.getString("equipageId"), rs.getString("periodeId"), rs.getString("ressourceId"),
                        new RepercussionPoste.PlaceGeneree(rs.getString("date"), rs.getString("uniteCode"), rs.getString("plageCode"), rs.getString("milieuId"),
                                rs.getString("natureCode"), rs.getString("corpsEmploiCode"), pointees.contains(rs.getString("placeId"))))).list();
    }

    @Override
    public Optional<String> equipageDe(String tenantId, Cle cle) {
        return jdbc.sql("SELECT id FROM eff_equipages WHERE tenantId = ? AND periodeId = ? AND date = ? AND plageCode = ? AND milieuId = ? AND natureCode = ? "
                        + "ORDER BY creeLe LIMIT 1").params(tenantId, cle.periodeId(), cle.date(), cle.plageCode(), cle.milieuId(), cle.natureCode())
                .query(String.class).optional();
    }

    @Override
    public void insererEquipage(String tenantId, String id, Cle cle, Planification.Plage plage, String creeLe) {
        jdbc.sql("INSERT INTO eff_equipages (id, tenantId, periodeId, uniteCode, date, debut, fin, natureCode, plageCode, milieuId, modeAttribution, "
                        + "minimumOperationnel, note, creeLe) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, NULL, ?)")
                .params(id, tenantId, cle.periodeId(), cle.uniteCode(), cle.date(), plage.debut(), plage.fin(), cle.natureCode(), cle.plageCode(), cle.milieuId(),
                        Planification.ModeAttribution.AUTOMATISE.name(), creeLe).update();
    }

    @Override
    public int nombrePlaces(String tenantId, String equipageId) {
        return jdbc.sql("SELECT COUNT(*) FROM eff_places WHERE tenantId = ? AND equipageId = ?").params(tenantId, equipageId).query(Integer.class).single();
    }

    @Override
    public int rangMax(String tenantId, String equipageId) {
        return jdbc.sql("SELECT COALESCE(MAX(rang), 0) FROM eff_places WHERE tenantId = ? AND equipageId = ?").params(tenantId, equipageId).query(Integer.class).single();
    }

    @Override
    public int minimum(String tenantId, String equipageId) {
        return jdbc.sql("SELECT minimumOperationnel FROM eff_equipages WHERE tenantId = ? AND id = ?").params(tenantId, equipageId).query(Integer.class).single();
    }

    @Override
    public void poserMinimum(String tenantId, String equipageId, int minimum) {
        jdbc.sql("UPDATE eff_equipages SET minimumOperationnel = ? WHERE tenantId = ? AND id = ?").params(minimum, tenantId, equipageId).update();
    }

    @Override
    public void supprimerEquipage(String tenantId, String equipageId) {
        jdbc.sql("DELETE FROM eff_equipages WHERE tenantId = ? AND id = ?").params(tenantId, equipageId).update();
    }

    @Override
    public void supprimerPlace(String tenantId, String placeId) {
        jdbc.sql("DELETE FROM eff_places WHERE tenantId = ? AND id = ?").params(tenantId, placeId).update();
    }

    @Override
    public void deplacerPlace(String tenantId, String placeId, String equipageId, int rang, String corpsEmploiCode) {
        jdbc.sql("UPDATE eff_places SET equipageId = ?, rang = ?, corpsEmploiCode = ? WHERE tenantId = ? AND id = ?")
                .params(equipageId, rang, corpsEmploiCode, tenantId, placeId).update();
    }

    @Override
    public void requalifier(String tenantId, String placeId, String corpsEmploiCode) {
        jdbc.sql("UPDATE eff_places SET corpsEmploiCode = ? WHERE tenantId = ? AND id = ?").params(corpsEmploiCode, tenantId, placeId).update();
    }

    @Override
    public Planification.Creneau creneau(String tenantId, String equipageId) {
        return jdbc.sql("SELECT date, debut, fin FROM eff_equipages WHERE tenantId = ? AND id = ?").params(tenantId, equipageId)
                .query((rs, i) -> new Planification.Creneau(rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"))).single();
    }

    @Override
    public Optional<String> corpsDeRessource(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT corpsEmploiCode FROM eff_ressources WHERE tenantId = ? AND id = ?").params(tenantId, ressourceId).query(String.class).optional();
    }

    @Override
    public Bornes bornes(String tenantId, String periodeId) {
        return jdbc.sql("SELECT uniteCode, debut, fin FROM eff_periodes WHERE tenantId = ? AND id = ?").params(tenantId, periodeId)
                .query((rs, i) -> new Bornes(rs.getString("uniteCode"), rs.getString("debut"), rs.getString("fin"))).single();
    }

    @Override
    public Set<String> datesTenues(String tenantId, String posteId, String periodeId) {
        return new HashSet<>(jdbc.sql("SELECT e.date FROM eff_places pl JOIN eff_equipages e ON e.id = pl.equipageId AND e.tenantId = pl.tenantId "
                + "WHERE pl.tenantId = ? AND pl.posteId = ? AND e.periodeId = ?").params(tenantId, posteId, periodeId).query(String.class).list());
    }
}
