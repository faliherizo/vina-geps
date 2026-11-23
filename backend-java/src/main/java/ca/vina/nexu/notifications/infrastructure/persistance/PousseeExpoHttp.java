package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.PousseeNative;
import ca.vina.nexu.notifications.domaine.PousseeWeb;

import ca.vina.nexu.notifications.domaine.Evenements;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * L'adaptateur réel du service de poussée d'Expo, avec le jeton d'accès s'il est réglé. Une poussée critique (ST-G07)
 * part en priorité haute sur le canal Android {@value #CANAL_CRITIQUE} — que l'application crée à importance maximale —
 * et, sur iOS, au niveau d'interruption critique avec le son critique : iOS ne l'honore que si Apple a accordé le droit
 * <em>Critical Alerts</em> (Q-ALC) ; sinon la poussée arrive comme une autre, et la cascade (texto, appel) prend le relais.
 */
public final class PousseeExpoHttp implements PousseeNative.PorteurNatif {

    /** Le canal de notification Android des alertes critiques, déclaré par l'application mobile. */
    public static final String CANAL_CRITIQUE = "critique";

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
        if (Evenements.ETIQUETTE_CRITIQUE.equals(charge.etiquette())) {
            critique(corps);
        } else {
            corps.put("sound", "default");
        }
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

    private static void critique(Map<String, Object> corps) {
        corps.put("priority", "high");
        corps.put("channelId", CANAL_CRITIQUE);
        corps.put("interruptionLevel", "critical");
        Map<String, Object> son = new LinkedHashMap<>();
        son.put("critical", true);
        son.put("name", "default");
        son.put("volume", 1);
        corps.put("sound", son);
    }
}
