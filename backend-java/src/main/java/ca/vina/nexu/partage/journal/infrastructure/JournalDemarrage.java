package ca.vina.nexu.partage.journal.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** La première ligne du journal (ADR 0041) : le port, les niveaux, la rétention — ce que l'exploitant vérifie d'abord. */
@Component
public class JournalDemarrage {

    private static final Logger JOURNAL = LoggerFactory.getLogger(JournalDemarrage.class);
    static final String EVENEMENT = "demarrage";

    private final int port;
    private final String niveau;
    private final String retention;

    public JournalDemarrage(@Value("${server.port}") int port, @Value("${logging.level.ca.vina.nexu}") String niveau,
                            @Value("${JOURNAL_ACTIONS_RETENTION_JOURS:}") String retention) {
        this.port = port;
        this.niveau = niveau;
        this.retention = retention;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void pret() {
        JOURNAL.atInfo().addKeyValue("evenement", EVENEMENT).addKeyValue("port", port).addKeyValue("niveau", niveau.toLowerCase())
                .addKeyValue("retentionJours", DepotJournalActions.retentionLue(retention))
                .log("backend Java à l'écoute sur le port {}", port);
    }
}
