package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.domaine.Transport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Le transport réel fait un POST signé et rend le code HTTP du récepteur ; ses échecs se lisent. */
@DisplayName("Transport HTTP des webhooks")
class TransportHttpTest {

    @Test
    void postEtCodeDuRecepteur() throws Exception {
        AtomicReference<String> recu = new AtomicReference<>();
        HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/hook", echange -> {
            recu.set(echange.getRequestMethod() + " " + new String(echange.getRequestBody().readAllBytes()) + " " + echange.getRequestHeaders().getFirst("x-evenement"));
            echange.sendResponseHeaders(202, -1);
            echange.close();
        });
        serveur.start();
        try {
            int code = new TransportHttp().envoyer("http://127.0.0.1:" + serveur.getAddress().getPort() + "/hook", "{\"a\":1}", Map.of("content-type", "application/json", "x-evenement", "employe.cree"));
            assertThat(code).isEqualTo(202);
            assertThat(recu.get()).isEqualTo("POST {\"a\":1} employe.cree");
            // Un fil interrompu n'envoie pas, et garde son drapeau d'interruption.
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> new TransportHttp().envoyer("http://127.0.0.1:" + serveur.getAddress().getPort() + "/hook", "{}", Map.of()))
                    .isInstanceOf(Transport.EchecTransportException.class).hasMessage("envoi interrompu");
            assertThat(Thread.interrupted()).isTrue();
        } finally {
            serveur.stop(0);
        }
    }

    @Test
    void connexionRefuseeEtDelaiDepasse() throws Exception {
        assertThatThrownBy(() -> new TransportHttp().envoyer("http://127.0.0.1:9/hook", "{}", Map.of())).isInstanceOf(Transport.EchecTransportException.class);
        HttpServer lent = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        lent.createContext("/lent", echange -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            echange.sendResponseHeaders(200, -1);
            echange.close();
        });
        lent.start();
        try {
            assertThatThrownBy(() -> new TransportHttp(Duration.ofMillis(300)).envoyer("http://127.0.0.1:" + lent.getAddress().getPort() + "/lent", "{}", Map.of()))
                    .isInstanceOf(Transport.EchecTransportException.class).hasMessage("délai dépassé (10 s)");
        } finally {
            lent.stop(0);
        }
        assertThat(TransportHttp.messageDe(new HttpTimeoutException("x"))).isEqualTo("délai dépassé (10 s)");
        assertThat(TransportHttp.messageDe(new InterruptedException())).isEqualTo("envoi interrompu");
        assertThat(TransportHttp.messageDe(new IOException("connexion refusée"))).isEqualTo("connexion refusée");
        assertThat(TransportHttp.messageDe(new IOException())).isEqualTo("IOException");
    }
}
