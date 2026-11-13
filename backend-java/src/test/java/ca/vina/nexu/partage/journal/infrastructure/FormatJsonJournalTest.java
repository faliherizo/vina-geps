package ca.vina.nexu.partage.journal.infrastructure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.event.KeyValuePair;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Une ligne JSON aux champs du Node (ADR 0041) : contexte MDC, paires clé-valeur, erreur avec pile à error seulement. */
@DisplayName("Journal — format JSON, les mêmes champs que le Node")
class FormatJsonJournalTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static ILoggingEvent evenement(Level niveau, String message, Throwable erreur, Map<String, String> mdc, KeyValuePair... paires) {
        LoggerContext contexte = new LoggerContext();
        Logger logger = contexte.getLogger("ca.vina.nexu.essai");
        LoggingEvent e = new LoggingEvent("ca.vina.nexu.essai", logger, niveau, message, erreur, new Object[0]);
        e.setMDCPropertyMap(mdc);
        for (KeyValuePair p : paires) {
            e.addKeyValuePair(p);
        }
        return e;
    }

    @Test
    void ligneHttpAvecContexte() {
        String ligne = new FormatJsonJournal().format(evenement(Level.INFO, "POST /x → 201", null, Map.of("requete", "abc", "utilisateur", "usr-1"),
                new KeyValuePair("evenement", "http"), new KeyValuePair("statut", 201)));
        assertThat(ligne).endsWith("\n");
        JsonNode j = JSON.readTree(ligne);
        assertThat(j.get("niveau").asText()).isEqualTo("info");
        assertThat(j.get("service").asText()).isEqualTo("nexu-java");
        assertThat(j.get("evenement").asText()).isEqualTo("http");
        assertThat(j.get("requete").asText()).isEqualTo("abc");
        assertThat(j.get("utilisateur").asText()).isEqualTo("usr-1");
        assertThat(j.get("statut").asInt()).isEqualTo(201);
        assertThat(j.get("message").asText()).isEqualTo("POST /x → 201");
        assertThat(j.get("instant").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(j.has("erreur")).isFalse();
    }

    @Test
    void erreurAvecPileAErrorSeulement() {
        IllegalStateException cassee = new IllegalStateException("cassé");
        JsonNode error = JSON.readTree(new FormatJsonJournal().format(evenement(Level.ERROR, "tic en échec", cassee, Map.of())));
        assertThat(error.get("evenement").asText()).isEqualTo("application");
        assertThat(error.get("erreur").get("type").asText()).isEqualTo("java.lang.IllegalStateException");
        assertThat(error.get("erreur").get("message").asText()).isEqualTo("cassé");
        assertThat(error.get("erreur").get("pile").asText()).contains("cassé").contains("\tat ");
        JsonNode warn = JSON.readTree(new FormatJsonJournal().format(evenement(Level.WARN, "lent", cassee, Map.of())));
        assertThat(warn.get("niveau").asText()).isEqualTo("warn");
        assertThat(warn.get("erreur").has("pile")).isFalse();
        // Un niveau Logback hors des quatre (TRACE) garde son nom en minuscules.
        assertThat(JSON.readTree(new FormatJsonJournal().format(evenement(Level.TRACE, "t", null, Map.of()))).get("niveau").asText()).isEqualTo("trace");
        assertThat(List.of("instant", "niveau", "service", "evenement", "message", "source")).allSatisfy(c -> assertThat(warn.has(c)).isTrue());
    }
}
