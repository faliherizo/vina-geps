package ca.vina.nexu.securite.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Antivirus — simulateur EICAR, lecture des réponses clamd, refus nommé")
class AntivirusTest {

    @Test
    @DisplayName("le simulateur ne reconnaît qu'EICAR et compte ses analyses")
    void simulateur() {
        Antivirus.AnalyseurSimule a = new Antivirus.AnalyseurSimule();
        assertThat(a.analyser("bonjour".getBytes(StandardCharsets.UTF_8))).isEqualTo(new Antivirus.Verdict(true, null));
        assertThat(a.analyser(("cv " + Antivirus.EICAR).getBytes(StandardCharsets.ISO_8859_1))).isEqualTo(new Antivirus.Verdict(false, "Eicar-Test-Signature"));
        assertThat(a.analyses()).containsExactly(7, 3 + Antivirus.EICAR.length());
    }

    @Test
    @DisplayName("une réponse clamd se lit : OK, FOUND avec sa signature, sinon erreur")
    void interpreter() {
        assertThat(Antivirus.interpreter("stream: OK\0")).isEqualTo(new Antivirus.Verdict(true, null));
        assertThat(Antivirus.interpreter("stream: Win.Test.EICAR_HDB-1 FOUND\n")).isEqualTo(new Antivirus.Verdict(false, "Win.Test.EICAR_HDB-1"));
        assertThatThrownBy(() -> Antivirus.interpreter("")).isInstanceOf(IllegalStateException.class).hasMessage("réponse antivirus inattendue : «  »");
        assertThatThrownBy(() -> Antivirus.interpreter("x".repeat(200))).hasMessageContaining("« " + "x".repeat(120) + " »");
    }

    @Test
    @DisplayName("exigerSain nomme la signature détectée")
    void exigerSain() {
        Antivirus.AnalyseurSimule a = new Antivirus.AnalyseurSimule();
        Antivirus.exigerSain(a, new byte[]{1, 2, 3});
        assertThatThrownBy(() -> Antivirus.exigerSain(a, Antivirus.EICAR.getBytes(StandardCharsets.ISO_8859_1)))
                .isInstanceOf(IllegalStateException.class).hasMessage("fichier refusé par l'antivirus (Eicar-Test-Signature)");
    }
}
