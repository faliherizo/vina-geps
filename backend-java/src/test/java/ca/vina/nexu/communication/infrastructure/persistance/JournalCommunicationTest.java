package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Journal de la communication — chaque geste consigné ; un journal indisponible ne fait pas échouer le geste")
class JournalCommunicationTest {

    @Test
    @DisplayName("la trace porte l'acteur, l'action, la cible et les états avant/après en JSON ; une table absente est tolérée")
    void consigner(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        JournalCommunication journal = new JournalCommunication(jdbc);
        journal.consigner("T", new JournalCommunicationPort.Trace("roy", "liste.creer", "ld-1", null, Map.of("nom", "Est")));
        Map<String, Object> ligne = jdbc.sql("SELECT tenantId, acteurId, action, cible, avant, apres FROM msg_journal").query().singleRow();
        assertThat(ligne).containsEntry("tenantId", "T").containsEntry("acteurId", "roy").containsEntry("action", "liste.creer")
                .containsEntry("cible", "ld-1").containsEntry("avant", null).containsEntry("apres", "{\"nom\":\"Est\"}");
        jdbc.sql("DROP TABLE msg_journal").update();
        assertThatCode(() -> journal.consigner("T", "roy", "liste.supprimer", "ld-1", Map.of("nom", "Est"), null)).doesNotThrowAnyException();
    }
}
