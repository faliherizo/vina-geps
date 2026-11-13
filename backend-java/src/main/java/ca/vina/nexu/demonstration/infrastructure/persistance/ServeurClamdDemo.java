package ca.vina.nexu.demonstration.infrastructure.persistance;

import ca.vina.nexu.demonstration.domaine.ClamdDemo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/** Le {@code clamd} de démonstration sur une prise TCP locale : un fil par connexion, une réponse puis la fermeture, comme le démon. */
public final class ServeurClamdDemo implements AutoCloseable {

    private final ServerSocket prise;
    private final Thread fil;
    private final AtomicInteger analyses = new AtomicInteger();

    /** Démarre le démon sur {@code port} (0 : un port libre, lisible par {@link #port()}). */
    public ServeurClamdDemo(int port) throws IOException {
        prise = new ServerSocket(port, 16, InetAddress.getLoopbackAddress());
        fil = new Thread(this::accepter, "clamd-demo");
        fil.setDaemon(true);
        fil.start();
    }

    public int port() {
        return prise.getLocalPort();
    }

    public int analyses() {
        return analyses.get();
    }

    private void accepter() {
        while (!prise.isClosed()) {
            try {
                Socket client = prise.accept();
                Thread.ofVirtual().start(() -> servir(client));
            } catch (IOException e) {
                return;   // la prise est fermée : le démon s'arrête
            }
        }
    }

    private void servir(Socket client) {
        try (client; InputStream entree = client.getInputStream()) {
            ByteArrayOutputStream recu = new ByteArrayOutputStream();
            byte[] tampon = new byte[8192];
            int n;
            while ((n = entree.read(tampon)) != -1) {
                recu.write(tampon, 0, n);
                ClamdDemo.Reponse r = ClamdDemo.repondre(recu.toByteArray());
                if (r != null) {
                    analyses.incrementAndGet();
                    client.getOutputStream().write(r.octets());
                    client.getOutputStream().flush();
                    return;
                }
            }
        } catch (IOException e) {
            // le client a raccroché : rien à répondre
        }
    }

    @Override
    public void close() throws IOException {
        prise.close();
    }
}
