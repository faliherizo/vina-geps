package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.DisponibilitesDeposees;
import ca.vina.nexu.effectifs.domaine.Regles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des dépôts de disponibilités ({@code eff_depots}, {@code eff_disponibilites}). */
@Repository("effectifsDepotDisponibilites")
public class DepotDisponibilites implements DepotDisponibilitesPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Regles.Motif>> MOTIFS = new TypeReference<>() { };
    private static final String CLE = " WHERE tenantId = ? AND periodeId = ? AND ressourceId = ?";

    private final JdbcClient jdbc;

    public DepotDisponibilites(JdbcClient jdbc, SchemaDisponibilites schema) {
        this.jdbc = jdbc;
        // Le bassin lit les absences d'effectifs : leur table existe dès que les disponibilités servent.
        SchemaAbsencesEffectifs.DDL.forEach(d -> jdbc.sql(d).update());
    }

    @Override
    public boolean absenteLe(String tenantId, String ressourceId, String date) {
        return jdbc.sql("SELECT COUNT(*) FROM eff_absences WHERE tenantId = ? AND ressourceId = ? AND du <= ? AND au >= ? AND statut IN (?, ?)")
                .params(tenantId, ressourceId, date, date, AbsencesEffectifs.Statut.ACCEPTEE.code(), AbsencesEffectifs.Statut.A_APPROUVER.code())
                .query(Integer.class).single() > 0;
    }

    private record Entete(String conformite, String motifs, String deposeLe, String deposePar, String approuveLe, String approuvePar) { }

    @Override
    public Optional<DisponibilitesDeposees> lire(String tenantId, String periodeId, String ressourceId) {
        return jdbc.sql("SELECT conformite, motifs, deposeLe, deposePar, approuveLe, approuvePar FROM eff_depots" + CLE)
                .params(tenantId, periodeId, ressourceId)
                .query((rs, i) -> new Entete(rs.getString("conformite"), rs.getString("motifs"), rs.getString("deposeLe"), rs.getString("deposePar"),
                        rs.getString("approuveLe"), rs.getString("approuvePar"))).optional()
                .map(e -> new DisponibilitesDeposees(periodeId, ressourceId, Disponibilites.Conformite.valueOf(e.conformite()), JSON.readValue(e.motifs(), MOTIFS),
                        e.deposeLe(), e.deposePar(), e.approuveLe(), e.approuvePar(), plages(tenantId, periodeId, ressourceId)));
    }

    private List<Disponibilites.PlageDisponible> plages(String tenantId, String periodeId, String ressourceId) {
        return jdbc.sql("SELECT date, debut, fin, type FROM eff_disponibilites" + CLE + " ORDER BY date, debut").params(tenantId, periodeId, ressourceId)
                .query((rs, i) -> new Disponibilites.PlageDisponible(rs.getString("date"), rs.getInt("debut"), rs.getInt("fin"),
                        Disponibilites.TypeDisponibilite.valueOf(rs.getString("type")))).list();
    }

    @Override
    public void remplacer(String tenantId, NouveauDepot d) {
        jdbc.sql("DELETE FROM eff_disponibilites" + CLE).params(tenantId, d.periodeId(), d.ressourceId()).update();
        for (PlageIdentifiee p : d.plages()) {
            jdbc.sql("INSERT INTO eff_disponibilites (id, tenantId, periodeId, ressourceId, date, debut, fin, type) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")
                    .params(p.id(), tenantId, d.periodeId(), d.ressourceId(), p.plage().date(), p.plage().debut(), p.plage().fin(), p.plage().type().name())
                    .update();
        }
        jdbc.sql("INSERT INTO eff_depots (tenantId, periodeId, ressourceId, conformite, motifs, deposeLe, deposePar, approuveLe, approuvePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NULL, NULL) ON CONFLICT (tenantId, periodeId, ressourceId) DO UPDATE SET conformite = excluded.conformite, "
                        + "motifs = excluded.motifs, deposeLe = excluded.deposeLe, deposePar = excluded.deposePar, approuveLe = NULL, approuvePar = NULL")
                .params(tenantId, d.periodeId(), d.ressourceId(), d.conformite().name(), JSON.writeValueAsString(d.motifs()), d.deposeLe(), d.deposePar())
                .update();
    }

    @Override
    public void approuver(String tenantId, String periodeId, String ressourceId, Approbation a) {
        jdbc.sql("UPDATE eff_depots SET conformite = ?, approuveLe = ?, approuvePar = ?" + CLE)
                .params(Disponibilites.Conformite.CONFORME.name(), a.le(), a.par(), tenantId, periodeId, ressourceId).update();
    }
}
