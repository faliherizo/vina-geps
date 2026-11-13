package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Textos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** La passerelle générique du port SMS : {@code POST /messages} JSON {@code {to, from, body}}, réponse {@code {id, status}}. */
public class SmsHttp implements Textos.PorteurSms {

    private final ClientFournisseur client;
    private final String url;
    private final String jeton;
    private final String expediteur;
    private final Supplier<String> horloge;

    public SmsHttp(ClientFournisseur client, String url, String jeton, String expediteur, Supplier<String> horloge) {
        this.client = client;
        this.url = url.replaceFirst("/$", "");
        this.jeton = jeton;
        this.expediteur = expediteur;
        this.horloge = horloge;
    }

    private Map<String, Object> requete(String chemin, Object corps) {
        ClientFournisseur.Reponse r = client.appeler(url + chemin, Map.of("authorization", "Bearer " + jeton), "application/json", corps == null ? null : ClientFournisseur.json(corps), "fournisseur SMS");
        if (!r.ok()) {
            throw new IllegalStateException("fournisseur SMS : " + r.statut() + " " + r.extrait());
        }
        return r.json();
    }

    @Override
    public Textos.Envoi envoyer(String numero, String texte, String expediteurDemande) {
        String n = Textos.exigerNumeroEtTexte(numero, texte);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("to", n);
        corps.put("from", expediteurDemande == null ? expediteur : expediteurDemande);
        corps.put("body", texte);
        Map<String, Object> j = requete("/messages", corps);
        return new Textos.Envoi(String.valueOf(j.get("id")), Textos.traduireStatut(j.get("status")), horloge.get());
    }

    @Override
    public String statut(String envoiId) {
        return Textos.traduireStatut(requete("/messages/" + ClientFournisseur.encoder(envoiId), null).get("status"));
    }
}
