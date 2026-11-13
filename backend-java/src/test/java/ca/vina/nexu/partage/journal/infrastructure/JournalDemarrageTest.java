package ca.vina.nexu.partage.journal.infrastructure;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class JournalDemarrageTest {

    @Test
    void laPremiereLigneDitLePortLeNiveauEtLaRetention() {
        Logger journal = (Logger) LoggerFactory.getLogger(JournalDemarrage.class);
        ListAppender<ILoggingEvent> lignes = new ListAppender<>();
        lignes.start();
        journal.addAppender(lignes);
        try {
            new JournalDemarrage(4002, "INFO", "").pret();
        } finally {
            journal.detachAppender(lignes);
        }
        assertThat(lignes.list).hasSize(1);
        assertThat(lignes.list.get(0).getFormattedMessage()).isEqualTo("backend Java à l'écoute sur le port 4002");
        assertThat(lignes.list.get(0).getKeyValuePairs()).extracting(p -> p.key + "=" + p.value).containsExactly("evenement=demarrage", "port=4002", "niveau=info", "retentionJours=365");
    }
}
