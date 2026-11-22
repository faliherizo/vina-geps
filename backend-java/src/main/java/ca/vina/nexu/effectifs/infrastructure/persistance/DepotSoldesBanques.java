package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.SoldesBanques;
import ca.vina.nexu.effectifs.domaine.SoldesBanquesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/** Adaptateur SQLite des soldes reçus ({@code eff_soldes_banques}, changeset 0022) : un solde par personne et par banque. */
@Repository
public class DepotSoldesBanques implements SoldesBanquesPort {

    private static final String COLONNES = "ressourceId, banqueCode, heures, auJour, recuLe";

    private final JdbcClient jdbc;

    public DepotSoldesBanques(JdbcClient jdbc, SchemaEffectifs socle) {
        this.jdbc = jdbc;
    }

    @Override
    public void poser(String tenantId, List<SoldesBanques.Solde> soldes) {
        soldes.forEach(s -> jdbc.sql("INSERT INTO eff_soldes_banques (tenantId, " + COLONNES + ") VALUES (?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, ressourceId, banqueCode) DO UPDATE SET heures = excluded.heures, auJour = excluded.auJour, recuLe = excluded.recuLe")
                .params(tenantId, s.ressourceId(), s.banqueCode(), s.heures().toPlainString(), s.auJour(), s.recuLe()).update());
    }

    @Override
    public Optional<SoldesBanques.Solde> solde(String tenantId, String ressourceId, String banqueCode) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_soldes_banques WHERE tenantId = ? AND ressourceId = ? AND banqueCode = ?")
                .params(tenantId, ressourceId, banqueCode).query((rs, i) -> solde(rs)).optional();
    }

    @Override
    public List<SoldesBanques.Solde> deLaRessource(String tenantId, String ressourceId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM eff_soldes_banques WHERE tenantId = ? AND ressourceId = ? ORDER BY banqueCode")
                .params(tenantId, ressourceId).query((rs, i) -> solde(rs)).list();
    }

    private static SoldesBanques.Solde solde(ResultSet rs) throws SQLException {
        return new SoldesBanques.Solde(rs.getString("ressourceId"), rs.getString("banqueCode"), new java.math.BigDecimal(rs.getString("heures")),
                rs.getString("auJour"), rs.getString("recuLe"));
    }
}
