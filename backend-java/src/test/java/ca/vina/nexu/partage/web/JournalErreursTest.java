package ca.vina.nexu.partage.web;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Une erreur rendue par le bord laisse une ligne au journal (ADR 0041) : le refus du métier y figure
 * par son type et son statut, jamais par son message, qui nomme des personnes ; l'erreur imprévue y
 * figure avec sa pile.
 */
@DisplayName("Journal des erreurs — refus et erreurs imprévues rendues par le bord")
class JournalErreursTest {

    private static final GardeAcces.Verdict OK = new GardeAcces.Verdict("t-1", null);
    private final ListAppender<ILoggingEvent> lignes = new ListAppender<>();
    private Logger journal;

    @BeforeEach
    void ecouter() {
        journal = (Logger) LoggerFactory.getLogger(JournalErreurs.class);
        journal.setLevel(Level.DEBUG);
        lignes.start();
        journal.addAppender(lignes);
    }

    @AfterEach
    void ranger() {
        journal.detachAppender(lignes);
        journal.setLevel(null);
    }

    private static Map<String, Object> champs(ILoggingEvent e) {
        return e.getKeyValuePairs().stream().collect(Collectors.toMap(p -> p.key, p -> p.value));
    }

    @Test
    @DisplayName("un refus traduit par le bord : une ligne info, son statut et son type, sans le message ni la pile")
    void refusDuBord() {
        assertThat(Bord.repondre(OK, t -> { throw new IntrouvableException("le dossier de Julie Tremblay est introuvable"); }).getStatusCode().value())
                .isEqualTo(404);
        assertThat(lignes.list).hasSize(1);
        ILoggingEvent ligne = lignes.list.getFirst();
        assertThat(ligne.getLevel()).isEqualTo(Level.INFO);
        assertThat(champs(ligne)).containsEntry("evenement", "refus").containsEntry("statut", 404).containsEntry("type", "IntrouvableException");
        assertThat(ligne.getFormattedMessage()).isEqualTo("refus 404 : IntrouvableException").doesNotContain("Julie");
        assertThat(ligne.getThrowableProxy()).isNull();
    }

    @Test
    @DisplayName("une exception que le bord ne connaît pas remonte sans ligne : le filtre du journal la consignera")
    void inconnueRemonte() {
        assertThatThrownBy(() -> Bord.repondre(OK, t -> { throw new UnsupportedOperationException("pas porté"); }))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(lignes.list).isEmpty();
    }

    @Test
    @DisplayName("une erreur imprévue rendue : une ligne warn qui porte la pile et le type complet")
    void imprevue() {
        JournalErreurs.imprevue(400, new NullPointerException("champ nul"));
        ILoggingEvent ligne = lignes.list.getFirst();
        assertThat(ligne.getLevel()).isEqualTo(Level.WARN);
        assertThat(champs(ligne)).containsEntry("evenement", "erreur").containsEntry("statut", 400).containsEntry("type", "java.lang.NullPointerException");
        assertThat(ligne.getThrowableProxy().getClassName()).isEqualTo("java.lang.NullPointerException");
    }
}
