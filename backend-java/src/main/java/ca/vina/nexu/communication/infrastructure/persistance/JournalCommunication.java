package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.partage.temps.Iso;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Le journal des gestes de la communication (création de conversation, participants, envoi,
 * consultation d'une pièce jointe). Une panne du journal ne bloque pas le geste : elle se dit
 * dans les journaux techniques.
 */
@Repository
public class JournalCommunication implements JournalCommunicationPort {

    private static final Logger LOG = LoggerFactory.getLogger(JournalCommunication.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final JdbcClient jdbc;

    public JournalCommunication(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void consigner(String tenantId, Trace trace) {
        consigner(tenantId, trace.acteurId(), trace.action(), trace.cible(), trace.avant(), trace.apres());
    }

    public void consigner(String tenantId, String acteurId, String action, String cible, Object avant, Object apres) {
        try {
            jdbc.sql("INSERT INTO msg_journal (id, tenantId, acteurId, action, cible, avant, apres, horodatage) VALUES (?,?,?,?,?,?,?,?)")
                    .params(UUID.randomUUID().toString(), tenantId, acteurId, action, cible,
                            avant == null ? null : JSON.writeValueAsString(avant), apres == null ? null : JSON.writeValueAsString(apres), Iso.maintenant())
                    .update();
        } catch (DataAccessException e) {
            LOG.atWarn().addKeyValue("evenement", "journal").setCause(e).log("communication : journal des gestes indisponible");
        }
    }
}
