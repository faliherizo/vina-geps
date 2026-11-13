package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.BoiteEnvoiIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Acceptee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EtatMessage;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Issue;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageSortant;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.Rejetee;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Adaptateur SQLite de la boîte d'envoi ({@code eff_messages_sortants}). */
@Repository("effectifsDepotBoiteEnvoiIntegration")
public class DepotBoiteEnvoiIntegration implements BoiteEnvoiIntegrationPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public DepotBoiteEnvoiIntegration(JdbcClient jdbc, SchemaIntegrationsEffectifs schema) {
        this.jdbc = jdbc;
    }

    private static MessageSortant message(ResultSet rs) throws SQLException {
        return new MessageSortant(rs.getString("id"), CodeConnecteur.valueOf(rs.getString("connecteur")), TypeMessage.valueOf(rs.getString("type")),
                rs.getString("cle"), JsonEffectifs.carte(rs.getString("charge")), EtatMessage.valueOf(rs.getString("etat")), rs.getInt("tentatives"),
                rs.getString("refExterne"), rs.getString("motif"), rs.getString("deposeLe"), rs.getString("deposePar"), rs.getString("traiteLe"));
    }

    @Override
    public Map<EtatMessage, Integer> compter(String tenantId, CodeConnecteur code) {
        Map<EtatMessage, Integer> n = new EnumMap<>(EtatMessage.class);
        jdbc.sql("SELECT etat, COUNT(*) AS n FROM eff_messages_sortants WHERE tenantId = ? AND connecteur = ? GROUP BY etat").params(tenantId, code.name())
                .query((rs, i) -> n.put(EtatMessage.valueOf(rs.getString("etat")), rs.getInt("n"))).list();
        return n;
    }

    @Override
    public Optional<String> idParCle(String tenantId, CodeConnecteur code, String cle) {
        return jdbc.sql("SELECT id FROM eff_messages_sortants WHERE tenantId = ? AND connecteur = ? AND cle = ?").params(tenantId, code.name(), cle)
                .query(String.class).optional();
    }

    @Override
    public void inserer(String tenantId, MessageSortant m) {
        jdbc.sql("INSERT INTO eff_messages_sortants (id, tenantId, connecteur, type, cle, charge, etat, tentatives, deposeLe, deposePar) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(m.id(), tenantId, m.connecteur().name(), m.type().name(), m.cle(), JSON.writeValueAsString(m.charge()), m.etat().name(), m.tentatives(),
                        m.deposeLe(), m.deposePar()).update();
    }

    @Override
    public Optional<MessageSortant> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM eff_messages_sortants WHERE tenantId = ? AND id = ?").params(tenantId, id).query((rs, i) -> message(rs)).optional();
    }

    @Override
    public List<MessageSortant> lister(String tenantId, Filtre f) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eff_messages_sortants WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        if (f.connecteur() != null) {
            sql.append(" AND connecteur = ?");
            params.add(f.connecteur().name());
        }
        if (f.etat() != null) {
            sql.append(" AND etat = ?");
            params.add(f.etat().name());
        }
        params.add(f.limite());
        return jdbc.sql(sql + " ORDER BY deposeLe DESC, id LIMIT ?").params(params).query((rs, i) -> message(rs)).list();
    }

    @Override
    public void consigner(String tenantId, String id, Issue issue, String traiteLe) {
        String sql = "UPDATE eff_messages_sortants SET etat = ?, refExterne = ?, motif = ?, tentatives = tentatives + 1, traiteLe = ? WHERE tenantId = ? AND id = ?";
        switch (issue) {
            case Acceptee a -> jdbc.sql(sql).params(EtatMessage.ENVOYE.name(), a.refExterne(), null, traiteLe, tenantId, id).update();
            case Rejetee r -> jdbc.sql(sql).params(EtatMessage.REJETE.name(), null, r.motif(), traiteLe, tenantId, id).update();
        }
    }

    @Override
    public void consignerPanne(String tenantId, String id, String motif, EtatMessage etat) {
        jdbc.sql("UPDATE eff_messages_sortants SET tentatives = tentatives + 1, motif = ?, etat = ? WHERE tenantId = ? AND id = ?")
                .params(motif, etat.name(), tenantId, id).update();
    }

    @Override
    public void remettreEnAttente(String tenantId, String id) {
        jdbc.sql("UPDATE eff_messages_sortants SET etat = ?, tentatives = 0, motif = NULL WHERE tenantId = ? AND id = ?")
                .params(EtatMessage.EN_ATTENTE.name(), tenantId, id).update();
    }
}
