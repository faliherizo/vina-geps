package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;

import java.util.LinkedHashMap;
import java.util.Map;

/** L'adaptateur réel du service de poussée d'Expo, avec le jeton d'accès s'il est réglé. */
public final class PousseeExpoHttp implements PousseeNative.PorteurNatif {

    private final ClientFournisseur client;
    private final String url;
    private final String jetonAcces;

    public PousseeExpoHttp(ClientFournisseur client, String url, String jetonAcces) {
        this.client = client;
        this.url = url;
        this.jetonAcces = jetonAcces;
    }

    @Override
    public PousseeWeb.Resultat envoyer(String jeton, PousseeWeb.Charge charge) {
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("to", jeton);
        corps.put("title", charge.titre());
        corps.put("body", charge.corps());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", charge.url());
        data.put("etiquette", charge.etiquette());
        corps.put("data", data);
        corps.put("sound", "default");
        Map<String, String> enTetes = new LinkedHashMap<>();
        enTetes.put("Accept", "application/json");
        if (jetonAcces != null && !jetonAcces.isBlank()) {
            enTetes.put("Authorization", "Bearer " + jetonAcces);
        }
        try {
            ClientFournisseur.Reponse r = client.appeler(url, enTetes, "application/json", ClientFournisseur.json(corps), "service de poussée Expo");
            return PousseeNative.interpreter(r.statut(), r.ok(), r.ok() ? r.json() : null);
        } catch (IllegalStateException e) {
            return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, null, e.getMessage());
        }
    }
}
