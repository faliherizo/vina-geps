package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.HorsPerimetreException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;
import ca.vina.nexu.partage.web.JournalErreurs;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La garde des effectifs rend toute exception en réponse ; avant cette ligne au journal, une
 * exception imprévue — un {@code NullPointerException} — devenait un 400 sans trace de sa cause.
 */
@DisplayName("GardeEffectifs — ce que le journal garde d'une erreur rendue")
class GardeEffectifsJournalTest {

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

    @Test
    @DisplayName("chaque refus du contexte est un fait, au niveau info et sans pile")
    void refusDuContexte() {
        List<RuntimeException> refus = List.of(new EffectifsInvalideException("a"), new IntrouvableException("b"), new ConflitException("c"),
                new HorsPerimetreException("d"), new SourceIndisponibleException("e"));
        refus.forEach(e -> GardeEffectifs.consigner(GardeEffectifs.statutDe(e), e));
        assertThat(lignes.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly("refus 422 : EffectifsInvalideException",
                "refus 404 : IntrouvableException", "refus 409 : ConflitException", "refus 403 : HorsPerimetreException", "refus 502 : SourceIndisponibleException");
        assertThat(lignes.list).allSatisfy(l -> {
            assertThat(l.getLevel()).isEqualTo(Level.INFO);
            assertThat(l.getThrowableProxy()).isNull();
        });
    }

    @Test
    @DisplayName("une exception venue d'ailleurs, rendue en 400, garde sa pile au niveau warn")
    void imprevue() {
        NullPointerException npe = new NullPointerException("corps incomplet");
        GardeEffectifs.consigner(GardeEffectifs.statutDe(npe), npe);
        ILoggingEvent ligne = lignes.list.getFirst();
        assertThat(ligne.getLevel()).isEqualTo(Level.WARN);
        assertThat(ligne.getFormattedMessage()).isEqualTo("erreur imprévue rendue en 400 : java.lang.NullPointerException");
        assertThat(ligne.getThrowableProxy().getMessage()).isEqualTo("corps incomplet");
    }
}
