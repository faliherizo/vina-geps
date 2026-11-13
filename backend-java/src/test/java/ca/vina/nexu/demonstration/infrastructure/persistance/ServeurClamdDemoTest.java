package ca.vina.nexu.demonstration.infrastructure.persistance;

import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.securite.infrastructure.persistance.AnalyseurClamd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("clamd de démonstration — la prise TCP : une réponse par connexion, un client qui raccroche ne bloque rien")
class ServeurClamdDemoTest {

    private static String lire(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
    }

    @Test
    void prise() throws Exception {
        try (ServeurClamdDemo serveur = new ServeurClamdDemo(0)) {
            assertThat(serveur.port()).isPositive();
            try (Socket s = new Socket("127.0.0.1", serveur.port())) {
                s.getOutputStream().write("zPING\0".getBytes(StandardCharsets.ISO_8859_1));
                s.getOutputStream().flush();
                assertThat(lire(s.getInputStream())).isEqualTo("PONG\0");
            }
            // Un client qui raccroche avant la fin de sa commande : rien n'est compté, le démon continue.
            try (Socket s = new Socket("127.0.0.1", serveur.port())) {
                s.getOutputStream().write("zPI".getBytes(StandardCharsets.ISO_8859_1));
                s.getOutputStream().flush();
            }
            // L'adaptateur réel, de bout en bout.
            AnalyseurClamd analyseur = new AnalyseurClamd("127.0.0.1", serveur.port(), 2_000, 16);
            assertThat(analyseur.analyser("contrat".getBytes(StandardCharsets.UTF_8))).isEqualTo(new Antivirus.Verdict(true, null));
            assertThat(analyseur.analyser(Antivirus.EICAR.getBytes(StandardCharsets.US_ASCII)).signature()).isEqualTo("Eicar-Test-Signature");
            Thread.sleep(50);
            assertThat(serveur.analyses()).isEqualTo(3);
            serveur.close();
            serveur.close();   // deux fois : sans effet
        }
    }
}
