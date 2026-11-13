package ca.vina.nexu.securite.infrastructure.persistance;

import ca.vina.nexu.securite.infrastructure.configuration.ConfigurationAntivirus;

import ca.vina.nexu.securite.domaine.Antivirus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AnalyseurClamd — protocole INSTREAM contre un clamd factice")
class AnalyseurClamdTest {

    /** Un clamd factice : lit l'entête et les morceaux, répond selon le contenu reçu. */
    private static Thread clamdFactice(ServerSocket serveur, AtomicReference<byte[]> recu) {
        Thread t = new Thread(() -> {
            try (Socket s = serveur.accept(); InputStream entree = s.getInputStream()) {
                DataInputStream data = new DataInputStream(entree);
                byte[] entete = new byte[10];
                data.readFully(entete);
                java.io.ByteArrayOutputStream contenu = new java.io.ByteArrayOutputStream();
                for (int taille = data.readInt(); taille > 0; taille = data.readInt()) {
                    byte[] morceau = new byte[taille];
                    data.readFully(morceau);
                    contenu.write(morceau);
                }
                recu.set(contenu.toByteArray());
                String reponse = new String(entete, StandardCharsets.US_ASCII).equals("zINSTREAM\0") && contenu.toString(StandardCharsets.ISO_8859_1).contains(Antivirus.EICAR)
                        ? "stream: Win.Test.EICAR_HDB-1 FOUND\0" : "stream: OK\0";
                s.getOutputStream().write(reponse.getBytes(StandardCharsets.US_ASCII));
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        t.start();
        return t;
    }

    @Test
    @DisplayName("le contenu part en morceaux préfixés de leur taille ; OK ou FOUND reviennent tels quels")
    void instream() throws Exception {
        try (ServerSocket serveur = new ServerSocket(0)) {
            AtomicReference<byte[]> recu = new AtomicReference<>();
            Thread t = clamdFactice(serveur, recu);
            AnalyseurClamd a = new AnalyseurClamd("127.0.0.1", serveur.getLocalPort(), 2000, 5);
            byte[] contenu = "un curriculum vitae".getBytes(StandardCharsets.UTF_8);
            assertThat(a.analyser(contenu)).isEqualTo(new Antivirus.Verdict(true, null));
            t.join();
            assertThat(recu.get()).isEqualTo(contenu);

            clamdFactice(serveur, recu);
            assertThat(a.analyser(Antivirus.EICAR.getBytes(StandardCharsets.ISO_8859_1)).signature()).isEqualTo("Win.Test.EICAR_HDB-1");
        }
    }

    @Test
    @DisplayName("injoignable, l'analyseur échoue plutôt que d'accepter — et la configuration choisit ClamAV ou le simulateur")
    void injoignable() throws Exception {
        int port;
        try (ServerSocket libre = new ServerSocket(0)) {
            port = libre.getLocalPort();
        }
        assertThatThrownBy(() -> new AnalyseurClamd("127.0.0.1", port, 500, 1024).analyser(new byte[]{1}))
                .isInstanceOf(IllegalStateException.class).hasMessageStartingWith("antivirus injoignable : ");
        assertThat(ConfigurationAntivirus.analyseur("", 3310)).isInstanceOf(Antivirus.AnalyseurSimule.class);
        assertThat(ConfigurationAntivirus.analyseur(null, 3310)).isInstanceOf(Antivirus.AnalyseurSimule.class);
        ConfigurationAntivirus configuration = new ConfigurationAntivirus();
        var commutateur = configuration.commutateurAntivirus("clamav", 3310);
        assertThat(commutateur.courant()).isInstanceOf(AnalyseurClamd.class);
        // L'analyseur vu par le reste du code suit le commutateur.
        commutateur.brancher(new Antivirus.AnalyseurSimule());
        assertThat(configuration.analyseurFichier(commutateur).analyser(new byte[]{1}).sain()).isTrue();
    }
}
