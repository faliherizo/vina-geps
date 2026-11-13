package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.securite.domaine.Antivirus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("clamd de démonstration — le protocole, commande par commande")
class ClamdDemoTest {

    static byte[] instream(String prefixe, byte[]... morceaux) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        b.writeBytes((prefixe + "INSTREAM" + (prefixe.equals("z") ? "\0" : "\n")).getBytes(StandardCharsets.ISO_8859_1));
        for (byte[] m : morceaux) {
            b.writeBytes(ByteBuffer.allocate(4).putInt(m.length).array());
            b.writeBytes(m);
        }
        return b.toByteArray();
    }

    @Test
    void commandes() {
        assertThat(ClamdDemo.repondre("zPING\0".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo(new ClamdDemo.Reponse("PONG", "\0"));
        assertThat(ClamdDemo.repondre("nVERSION\n".getBytes(StandardCharsets.ISO_8859_1))).isEqualTo(new ClamdDemo.Reponse(ClamdDemo.VERSION, "\n"));
        assertThat(ClamdDemo.repondre("nSTATS\n".getBytes(StandardCharsets.ISO_8859_1)).texte()).isEqualTo("UNKNOWN COMMAND");
        assertThat(ClamdDemo.repondre("blabla\n".getBytes(StandardCharsets.ISO_8859_1)).texte()).isEqualTo("UNKNOWN COMMAND");
        assertThat(ClamdDemo.repondre("nPIN".getBytes(StandardCharsets.ISO_8859_1))).isNull();
        assertThat(new ClamdDemo.Reponse("PONG", "\0").octets()).isEqualTo("PONG\0".getBytes(StandardCharsets.ISO_8859_1));
    }

    @Test
    void instream() {
        byte[] sain = instream("z", "bonjour".getBytes(StandardCharsets.UTF_8), new byte[0]);
        assertThat(ClamdDemo.repondre(sain)).isEqualTo(new ClamdDemo.Reponse("stream: OK", "\0"));
        byte[] eicar = instream("n", "abc".getBytes(StandardCharsets.UTF_8), Antivirus.EICAR.getBytes(StandardCharsets.US_ASCII), new byte[0]);
        assertThat(ClamdDemo.repondre(eicar)).isEqualTo(new ClamdDemo.Reponse("stream: Eicar-Test-Signature FOUND", "\n"));
        // Incomplet : l'en-tête seul, un morceau tronqué, pas de morceau vide.
        assertThat(ClamdDemo.repondre(instream("z"))).isNull();
        byte[] tronque = instream("z", "bonjour".getBytes(StandardCharsets.UTF_8));
        assertThat(ClamdDemo.repondre(java.util.Arrays.copyOf(tronque, tronque.length - 2))).isNull();
        assertThat(ClamdDemo.repondre(tronque)).isNull();
        assertThat(ClamdDemo.verdictFlux(new byte[0])).isEqualTo("stream: OK");
        assertThat(ClamdDemo.contient("ab".getBytes(), "abc".getBytes())).isFalse();
    }
}
