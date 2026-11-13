package ca.vina.nexu.partage.journal.infrastructure;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ca.vina.nexu.partage.journal.Niveau;
import org.slf4j.event.KeyValuePair;
import org.springframework.boot.logging.structured.StructuredLogFormatter;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une ligne JSON par événement, avec <strong>les mêmes champs que le Node</strong> (ADR 0041,
 * JOURNALISATION.md § 4) : {@code instant}, {@code niveau}, {@code service}, {@code evenement}, le
 * contexte de la requête (MDC), les champs de l'événement (paires clé-valeur), {@code erreur} avec sa
 * pile à {@code error} seulement, {@code message}. Choisi par {@code logging.structured.format.console}.
 */
public class FormatJsonJournal implements StructuredLogFormatter<ILoggingEvent> {

    static final String SERVICE = "nexu-java";
    static final String CLE_EVENEMENT = "evenement";
    static final String EVENEMENT_PAR_DEFAUT = "application";
    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Override
    public String format(ILoggingEvent evenement) {
        Map<String, Object> ligne = new LinkedHashMap<>();
        ligne.put("instant", Instant.ofEpochMilli(evenement.getTimeStamp()).toString());
        String niveau = Niveau.de(evenement.getLevel().toString()).map(Niveau::code).orElse(evenement.getLevel().toString().toLowerCase());
        ligne.put("niveau", niveau);
        ligne.put("service", SERVICE);
        Map<String, Object> champs = new LinkedHashMap<>();
        List<KeyValuePair> paires = evenement.getKeyValuePairs();
        if (paires != null) {
            paires.forEach(p -> champs.put(p.key, p.value));
        }
        ligne.put(CLE_EVENEMENT, champs.getOrDefault(CLE_EVENEMENT, EVENEMENT_PAR_DEFAUT));
        champs.remove(CLE_EVENEMENT);
        ligne.putAll(evenement.getMDCPropertyMap());
        ligne.putAll(champs);
        if (evenement.getThrowableProxy() != null) {
            ligne.put("erreur", erreur(evenement.getThrowableProxy(), Niveau.ERROR.code().equals(niveau)));
        }
        ligne.put("message", evenement.getFormattedMessage());
        ligne.put("source", evenement.getLoggerName());
        return JSON.writeValueAsString(ligne) + "\n";
    }

    /** `{type, message}` et la pile à `error` seulement — le contrat du Node. */
    static Map<String, Object> erreur(IThrowableProxy t, boolean avecPile) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("type", t.getClassName());
        e.put("message", t.getMessage());
        if (avecPile) {
            StringBuilder pile = new StringBuilder(t.getClassName()).append(": ").append(t.getMessage());
            for (StackTraceElementProxy element : t.getStackTraceElementProxyArray()) {
                pile.append("\n\tat ").append(element.getSTEAsString());
            }
            e.put("pile", pile.toString());
        }
        return e;
    }
}
