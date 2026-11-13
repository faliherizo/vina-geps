package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Textos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * L'adaptateur Telnyx du port SMS — l'API Messaging v2 ({@code POST /v2/messages} JSON
 * {@code {from, to, text}}, Bearer, {@code GET /v2/messages/{id}} pour le suivi). Le Telnyx de
 * démonstration embarqué se branche par {@code url} sur {@code /mock/telnyx}.
 */
public class SmsTelnyx implements Textos.PorteurSms {

    public static final String URL = "https://api.telnyx.com";

    private final ClientFournisseur client;
    private final String url;
    private final String cleApi;
    private final String from;
    private final Supplier<String> horloge;

    public SmsTelnyx(ClientFournisseur client, String url, String cleApi, String from, Supplier<String> horloge) {
        this.client = client;
        this.url = (url == null || url.isBlank() ? URL : url).replaceFirst("/$", "");
        this.cleApi = cleApi;
        this.from = from;
        this.horloge = horloge;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requete(String chemin, Object corps) {
        ClientFournisseur.Reponse r = client.appeler(url + chemin, Map.of("authorization", "Bearer " + cleApi, "accept", "application/json"), "application/json",
                corps == null ? null : ClientFournisseur.json(corps), "Telnyx");
        Map<String, Object> j = r.json();
        if (!r.ok()) {
            Map<String, Object> e = j.get("errors") instanceof List<?> l && !l.isEmpty() && l.get(0) instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
            throw new IllegalStateException(("Telnyx : " + r.statut() + " (code " + (e.get("code") == null ? "?" : e.get("code")) + ") " + (e.get("title") == null ? "" : e.get("title"))).trim());
        }
        return j.get("data") instanceof Map<?, ?> d ? (Map<String, Object>) d : Map.of();
    }

    @Override
    public Textos.Envoi envoyer(String numero, String texte, String expediteur) {
        String n = Textos.exigerNumeroEtTexte(numero, texte);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("from", expediteur == null ? from : expediteur);
        corps.put("to", n);
        corps.put("text", texte);
        Map<String, Object> d = requete("/v2/messages", corps);
        return new Textos.Envoi(String.valueOf(d.get("id")), Textos.traduireStatutTelnyx(ClientFournisseur.chemin(d, "to", "0", "status")), horloge.get());
    }

    @Override
    public String statut(String envoiId) {
        return Textos.traduireStatutTelnyx(ClientFournisseur.chemin(requete("/v2/messages/" + ClientFournisseur.encoder(envoiId), null), "to", "0", "status"));
    }
}
