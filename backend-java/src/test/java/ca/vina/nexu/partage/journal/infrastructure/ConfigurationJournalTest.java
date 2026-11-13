package ca.vina.nexu.partage.journal.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le yml règle le journal (ADR 0041) : trois variables deviennent les propriétés Spring ; l'inconnu arrête le démarrage. */
@DisplayName("Journal — configuration depuis LOG_NIVEAU, LOG_NIVEAU_RACINE, LOG_FORMAT")
class ConfigurationJournalTest {

    @Test
    void defauts() {
        assertThat(ConfigurationJournal.proprietes(null, null, null))
                .containsEntry("logging.level.ca.vina.nexu", "INFO").containsEntry("logging.level.root", "WARN")
                .containsEntry("logging.structured.format.console", FormatJsonJournal.class.getName())
                .doesNotContainKey("logging.pattern.console");
    }

    @Test
    void valeursEtRefus() {
        assertThat(ConfigurationJournal.proprietes("debug", "info", "Texte"))
                .containsEntry("logging.level.ca.vina.nexu", "DEBUG").containsEntry("logging.level.root", "INFO")
                .containsEntry("logging.pattern.console", ConfigurationJournal.MOTIF_TEXTE).doesNotContainKey("logging.structured.format.console");
        assertThatThrownBy(() -> ConfigurationJournal.proprietes("verbose", null, null)).hasMessage("LOG_NIVEAU « verbose » inconnu : debug, info, warn, error");
        assertThatThrownBy(() -> ConfigurationJournal.proprietes(null, "trace", null)).hasMessage("LOG_NIVEAU « trace » inconnu : debug, info, warn, error");
        assertThatThrownBy(() -> ConfigurationJournal.proprietes(null, null, "xml")).hasMessage("LOG_FORMAT « xml » inconnu : json, texte");
    }

    @Test
    void poseLesProprietesEnTeteDeLEnvironnement() {
        MockEnvironment env = new MockEnvironment().withProperty("LOG_NIVEAU", "error");
        new ConfigurationJournal().postProcessEnvironment(env, null);
        assertThat(env.getProperty("logging.level.ca.vina.nexu")).isEqualTo("ERROR");
        assertThat(env.getProperty("logging.level.root")).isEqualTo("WARN");
    }
}
