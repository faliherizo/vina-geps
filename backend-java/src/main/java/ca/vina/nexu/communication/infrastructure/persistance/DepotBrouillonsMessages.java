package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.BrouillonsMessages;
import ca.vina.nexu.communication.domaine.EcritureBrouillonsMessagesPort;
import ca.vina.nexu.communication.domaine.LectureBrouillonsMessagesPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Les brouillons dans {@code msg_brouillons} ; l'index unique (tenant, conversation, auteur) porte le remplacement. */
public final class DepotBrouillonsMessages implements LectureBrouillonsMessagesPort, EcritureBrouillonsMessagesPort {

    private final JdbcClient jdbc;

    public DepotBrouillonsMessages(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<BrouillonsMessages.Brouillon> lire(String tenantId, String auteurId, String conversationId) {
        return jdbc.sql("SELECT conversationId, corps, modifieLe FROM msg_brouillons WHERE tenantId = ? AND conversationId = ? AND auteurId = ?")
                .params(tenantId, conversationId, auteurId)
                .query((rs, i) -> new BrouillonsMessages.Brouillon(rs.getString("conversationId"), rs.getString("corps"), rs.getString("modifieLe"))).optional();
    }

    @Override
    public List<BrouillonsMessages.Brouillon> deLAuteur(String tenantId, String auteurId) {
        return jdbc.sql("SELECT conversationId, corps, modifieLe FROM msg_brouillons WHERE tenantId = ? AND auteurId = ? ORDER BY modifieLe DESC")
                .params(tenantId, auteurId)
                .query((rs, i) -> new BrouillonsMessages.Brouillon(rs.getString("conversationId"), rs.getString("corps"), rs.getString("modifieLe"))).list();
    }

    @Override
    public void enregistrer(String tenantId, String auteurId, String nouvelId, BrouillonsMessages.Brouillon b) {
        jdbc.sql("INSERT INTO msg_brouillons (id, tenantId, conversationId, auteurId, corps, modifieLe) VALUES (?,?,?,?,?,?)"
                        + " ON CONFLICT (tenantId, conversationId, auteurId) DO UPDATE SET corps = excluded.corps, modifieLe = excluded.modifieLe")
                .params(nouvelId, tenantId, b.conversationId(), auteurId, b.corps(), b.modifieLe()).update();
    }

    @Override
    public int supprimer(String tenantId, String auteurId, String conversationId) {
        return jdbc.sql("DELETE FROM msg_brouillons WHERE tenantId = ? AND conversationId = ? AND auteurId = ?").params(tenantId, conversationId, auteurId).update();
    }
}
