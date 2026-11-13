package ca.vina.nexu.integrations.infrastructure.persistance;

import ca.vina.nexu.integrations.domaine.Transport;
import ca.vina.nexu.integrations.domaine.Webhooks;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;

/** Le transport réel : un POST avec délai de dix secondes, comme le {@code fetch} du Node. */
@Component
public class TransportHttp implements Transport {

    private final HttpClient client;
    private final Duration delai;

    public TransportHttp() {
        this(Webhooks.DELAI);
    }

    /** Le délai est un paramètre pour que l'essai du dépassement ne dure pas dix secondes. */
    TransportHttp(Duration delai) {
        this.delai = delai;
        this.client = HttpClient.newBuilder().connectTimeout(delai).build();
    }

    @Override
    public int envoyer(String url, String corps, Map<String, String> enTetes) throws EchecTransportException {
        HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create(url)).timeout(delai).POST(HttpRequest.BodyPublishers.ofString(corps));
        enTetes.forEach(requete::header);
        try {
            return client.send(requete.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new EchecTransportException(messageDe(e));
        }
    }

    /** Le même vocabulaire que le Node : un délai dépassé se lit, une connexion refusée aussi. */
    static String messageDe(Exception e) {
        if (e instanceof HttpTimeoutException) {
            return "délai dépassé (10 s)";
        }
        if (e instanceof InterruptedException) {
            return "envoi interrompu";
        }
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
