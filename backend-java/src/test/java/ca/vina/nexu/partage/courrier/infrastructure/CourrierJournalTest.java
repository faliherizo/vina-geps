package ca.vina.nexu.partage.courrier.infrastructure;

import ca.vina.nexu.partage.courrier.Courrier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourrierJournalTest {

    @Test
    void gardeCeQuiEstParti() {
        CourrierJournal c = new CourrierJournal();
        assertThat(c.envoyer(new Courrier.Message("a@b.qc", "Sujet", "Texte"))).isTrue();
        assertThat(c.envoyes()).containsExactly(new Courrier.Message("a@b.qc", "Sujet", "Texte"));
    }
}
