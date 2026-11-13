package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Appels;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** La passerelle générique du port d'appel : {@code POST /calls {from, agent, to}} → {@code {id, status}}, {@code GET /calls/:id}. */
public class AppelHttp implements Appels.PorteurAppelSortant {

    private final ClientFournisseur client;
    private final String url;
    private final String jeton;
    private final Supplier<String> horloge;

    public AppelHttp(ClientFournisseur client, String url, String jeton, Supplier<String> horloge) {
        this.client = client;
        this.url = url.replaceFirst("/$", "");
        this.jeton = jeton;
        this.horloge = horloge;
    }

    private Map<String, Object> requete(String chemin, Object corps) {
        ClientFournisseur.Reponse r = client.appeler(url + chemin, Map.of("authorization", "Bearer " + jeton), "application/json", corps == null ? null : ClientFournisseur.json(corps), "fournisseur d'appels");
        if (!r.ok()) {
            throw new IllegalStateException("fournisseur d'appels : " + r.statut() + " " + r.extrait());
        }
        return r.json();
    }

    @Override
    public Appels.AppelSortant appeler(String expediteur, String agent, String destinataire) {
        Appels.Trio n = Appels.normaliserTrio(expediteur, agent, destinataire);
        Map<String, Object> corps = new LinkedHashMap<>();
        corps.put("from", n.expediteur());
        corps.put("agent", n.agent());
        corps.put("to", n.destinataire());
        Map<String, Object> j = requete("/calls", corps);
        return new Appels.AppelSortant(String.valueOf(j.get("id")), Appels.traduireStatutAppel(j.get("status")), horloge.get());
    }

    @Override
    public String statut(String appelId) {
        return Appels.traduireStatutAppel(requete("/calls/" + ClientFournisseur.encoder(appelId), null).get("status"));
    }
}
