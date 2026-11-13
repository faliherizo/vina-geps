package ca.vina.nexu.notifications.infrastructure;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Un fournisseur HTTP factice : chaque requête reçue est retenue, la réponse est décidée par l'essai. */
public final class FournisseurFactice implements AutoCloseable {

    public record Requete(String methode, String chemin, Map<String, List<String>> enTetes, byte[] corps) {
        public String texte() {
            return new String(corps, StandardCharsets.UTF_8);
        }

        public String enTete(String nom) {
            List<String> v = enTetes.get(nom);
            return v == null ? null : v.get(0);
        }
    }

    public record Reponse(int statut, String corps) {
    }

    private final HttpServer serveur;
    public final List<Requete> requetes = new ArrayList<>();
    public volatile Function<Requete, Reponse> repondre = r -> new Reponse(200, "{}");
    public volatile long attenteMs = 0;

    public FournisseurFactice() throws IOException {
        serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serveur.createContext("/", this::traiter);
        serveur.start();
    }

    private void traiter(HttpExchange e) throws IOException {
        Requete r = new Requete(e.getRequestMethod(), e.getRequestURI().getPath(), e.getRequestHeaders(), e.getRequestBody().readAllBytes());
        synchronized (requetes) {
            requetes.add(r);
        }
        if (attenteMs > 0) {
            try {
                Thread.sleep(attenteMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        Reponse reponse = repondre.apply(r);
        byte[] corps = reponse.corps().getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("content-type", "application/json");
        e.sendResponseHeaders(reponse.statut(), corps.length == 0 ? -1 : corps.length);
        if (corps.length > 0) {
            e.getResponseBody().write(corps);
        }
        e.close();
    }

    public String url() {
        return "http://127.0.0.1:" + serveur.getAddress().getPort();
    }

    public Requete derniere() {
        synchronized (requetes) {
            return requetes.get(requetes.size() - 1);
        }
    }

    @Override
    public void close() {
        serveur.stop(0);
    }
}
