package ca.vina.nexu.partage.identifiants.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SequenceEnBase — compteur persistant partagé, jetons tirés d'un aléa injecté")
class SequenceEnBaseTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC);

    private static JdbcClient base() {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        JdbcClient jdbc = JdbcClient.create(ds);
        jdbc.sql("CREATE TABLE seq (name TEXT PRIMARY KEY, value INTEGER NOT NULL)").update();
        return jdbc;
    }

    @Test
    @DisplayName("le compteur part de 1, se poursuit à partir de la base, et deux instances sur la même base ne se répètent pas")
    void compteur() {
        JdbcClient jdbc = base();
        SequenceEnBase une = new SequenceEnBase(jdbc, HORLOGE);
        SequenceEnBase autre = new SequenceEnBase(jdbc, HORLOGE);
        long millis = HORLOGE.millis();
        assertThat(une.suivant("msg")).isEqualTo("msg-" + millis + "-1");
        assertThat(autre.suivant("lst")).isEqualTo("lst-" + millis + "-2");
        assertThat(une.suivant("msg")).isEqualTo("msg-" + millis + "-3");
        assertThat(jdbc.sql("SELECT value FROM seq WHERE name = 'global'").query(Long.class).single()).isEqualTo(3L);
    }

    @Test
    @DisplayName("les jetons ont la longueur promise et se rejouent avec la même graine")
    void jetons() {
        SequenceEnBase a = new SequenceEnBase(base(), HORLOGE, new Random(42));
        SequenceEnBase b = new SequenceEnBase(base(), HORLOGE, new Random(42));
        String signature = a.jetonSignature();
        assertThat(signature).hasSize(48).matches("[0-9a-f]+").isEqualTo(b.jetonSignature());
        assertThat(a.jetonBase64Url(32)).hasSize(43).matches("[A-Za-z0-9_-]+").isEqualTo(b.jetonBase64Url(32));
        assertThat(a.uuid()).matches("[0-9a-f-]{36}").isEqualTo(b.uuid()).isNotEqualTo(a.uuid());
    }
}
