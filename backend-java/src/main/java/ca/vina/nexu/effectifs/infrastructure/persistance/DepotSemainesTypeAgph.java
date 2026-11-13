package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AgphSemaineDeposee;
import ca.vina.nexu.effectifs.domaine.AgphSemaineType;
import ca.vina.nexu.effectifs.domaine.DepotSemainesTypeAgphPort;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.Regles;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des semaines types déposées dans les campagnes AGPH ({@code eff_agph_depots}, {@code eff_agph_plages}). */
@Repository("effectifsDepotSemainesTypeAgph")
public class DepotSemainesTypeAgph implements DepotSemainesTypeAgphPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<Regles.Motif>> MOTIFS = new TypeReference<>() { };
    private static final String CLE = " WHERE tenantId = ? AND campagneId = ? AND ressourceId = ?";

    private final JdbcClient jdbc;

    public DepotSemainesTypeAgph(JdbcClient jdbc, SchemaAgph schema) {
        this.jdbc = jdbc;
    }

    private record Entete(String conformite, String motifs, double heures, String deposeLe, String deposePar) { }

    @Override
    public Optional<AgphSemaineDeposee> lire(String tenantId, String campagneId, String ressourceId) {
        return jdbc.sql("SELECT conformite, motifs, heures, deposeLe, deposePar FROM eff_agph_depots" + CLE).params(tenantId, campagneId, ressourceId)
                .query((rs, i) -> new Entete(rs.getString("conformite"), rs.getString("motifs"), rs.getDouble("heures"), rs.getString("deposeLe"),
                        rs.getString("deposePar"))).optional()
                .map(e -> new AgphSemaineDeposee(ressourceId, Disponibilites.Conformite.valueOf(e.conformite()), JSON.readValue(e.motifs(), MOTIFS), e.heures(),
                        e.deposeLe(), e.deposePar(), plages(tenantId, campagneId, ressourceId)));
    }

    private List<AgphSemaineType.PlageType> plages(String tenantId, String campagneId, String ressourceId) {
        return jdbc.sql("SELECT jour, debut, fin, type FROM eff_agph_plages" + CLE + " ORDER BY jour, debut").params(tenantId, campagneId, ressourceId)
                .query((rs, i) -> new AgphSemaineType.PlageType(rs.getInt("jour"), rs.getInt("debut"), rs.getInt("fin"),
                        Disponibilites.TypeDisponibilite.valueOf(rs.getString("type")))).list();
    }

    @Override
    public double heuresDesAutres(String tenantId, String campagneId, String ressourceId) {
        return jdbc.sql("SELECT COALESCE(SUM(heures), 0) FROM eff_agph_depots WHERE tenantId = ? AND campagneId = ? AND ressourceId <> ?")
                .params(tenantId, campagneId, ressourceId).query(Double.class).single();
    }

    @Override
    public void enregistrer(String tenantId, NouvelleSemaine s) {
        jdbc.sql("DELETE FROM eff_agph_plages" + CLE).params(tenantId, s.campagneId(), s.ressourceId()).update();
        for (AgphSemaineType.PlageType p : s.plages()) {
            jdbc.sql("INSERT INTO eff_agph_plages (campagneId, tenantId, ressourceId, jour, debut, fin, type) VALUES (?, ?, ?, ?, ?, ?, ?)")
                    .params(s.campagneId(), tenantId, s.ressourceId(), p.jour(), p.debut(), p.fin(), p.type().name()).update();
        }
        jdbc.sql("INSERT INTO eff_agph_depots (campagneId, tenantId, ressourceId, conformite, motifs, heures, deposeLe, deposePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, campagneId, ressourceId) DO UPDATE SET conformite = excluded.conformite, motifs = excluded.motifs, "
                        + "heures = excluded.heures, deposeLe = excluded.deposeLe, deposePar = excluded.deposePar")
                .params(s.campagneId(), tenantId, s.ressourceId(), s.conformite().name(), JSON.writeValueAsString(s.motifs()), s.heures(), s.deposeLe(),
                        s.deposePar()).update();
    }
}
