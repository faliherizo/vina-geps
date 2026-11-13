package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Telnyx de démonstration — l'API Messaging v2 : {@code POST /v2/messages} (JSON, Bearer) répond
 * {@code {data: {...}}} ; {@code GET /v2/messages/{id}} suit la livraison. Les erreurs ont la
 * forme Telnyx {@code {errors: [{code, title, detail}]}} : 401, 422, 404, 429.
 */
public final class TelnyxDemo {
    /** Les états d'un destinataire Telnyx, tels que l'API les écrit. */
    enum Statut {
        EN_FILE("queued"), LIVRE("delivered"), ENVOI_ECHOUE("sending_failed"), LIVRAISON_ECHOUEE("delivery_failed");

        private final String code;

        Statut(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        boolean est(Object valeur) {
            return code.equals(valeur);
        }
    }


    public record Reponse(int statut, Map<String, Object> corps) {
    }

    private final Clock horloge;
    private final Supplier<UUID> identifiants;
    private final Map<String, Map<String, Object>> messages = new ConcurrentHashMap<>();

    public TelnyxDemo(Clock horloge, Supplier<UUID> identifiants) {
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    public static Map<String, Object> erreurs(String code, String titre, String detail) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", code);
        e.put("title", titre);
        e.put("detail", detail);
        return Map.of("errors", List.of(e));
    }

    private static String texte(Map<String, ?> corps, String cle) {
        Object v = corps.get(cle);
        return v == null ? "" : String.valueOf(v);
    }

    public Reponse creerMessage(Map<String, ?> corps) {
        String to = texte(corps, "to");
        String from = texte(corps, "from");
        String text = texte(corps, "text");
        if (!DonneesDemo.E164.matcher(to).matches()) {
            return new Reponse(422, erreurs("40001", "Invalid phone number", "'to' must be a +E.164 formatted phone number, got '" + to + "'."));
        }
        if (from.isEmpty()) {
            return new Reponse(422, erreurs("40001", "Invalid phone number", "'from' is required."));
        }
        if (text.isBlank()) {
            return new Reponse(422, erreurs("40301", "Message body empty", "'text' must be a non-empty string."));
        }
        if (DonneesDemo.NUMERO_LIMITE.equals(to)) {
            return new Reponse(429, erreurs("10011", "Too many requests", "Rate limit exceeded; retry later."));
        }
        boolean injoignable = DonneesDemo.NUMERO_INJOIGNABLE.equals(to);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("record_type", "message");
        m.put("id", identifiants.get().toString());
        m.put("direction", "outbound");
        m.put("type", "SMS");
        m.put("from", Map.of("phone_number", from, "carrier", "Telnyx"));
        Map<String, Object> dest = new LinkedHashMap<>();
        dest.put("phone_number", to);
        dest.put("status", (injoignable ? Statut.ENVOI_ECHOUE : Statut.EN_FILE).code());
        dest.put("carrier", "Bell Mobility");
        m.put("to", new ArrayList<>(List.of(dest)));
        m.put("text", text);
        m.put("parts", Math.max(1, (text.length() + 159) / 160));
        m.put("received_at", Iso.de(horloge.instant()));
        m.put("sent_at", null);
        m.put("completed_at", null);
        Map<String, Object> panne = new LinkedHashMap<>();
        panne.put("code", "40008");
        panne.put("title", "Recipient unreachable");
        panne.put("detail", "The destination handset could not be reached.");
        m.put("errors", injoignable ? List.of(panne) : List.of());
        messages.put((String) m.get("id"), m);
        return new Reponse(200, Map.of("data", m));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> lireMessage(String id) {
        Map<String, Object> m = messages.get(id);
        if (m == null) {
            return null;
        }
        Map<String, Object> dest = ((List<Map<String, Object>>) m.get("to")).get(0);
        if (Statut.EN_FILE.est(dest.get("status"))) {
            dest.put("status", Statut.LIVRE.code());
            m.put("sent_at", Iso.de(horloge.instant()));
            m.put("completed_at", m.get("sent_at"));
        }
        if (Statut.ENVOI_ECHOUE.est(dest.get("status"))) {
            dest.put("status", Statut.LIVRAISON_ECHOUEE.code());
        }
        return m;
    }

    public static boolean authentifie(String enTete) {
        return ("Bearer " + DonneesDemo.TELNYX_CLE_API).equals(enTete);
    }

    public List<Map<String, Object>> messages() {
        return List.copyOf(messages.values());
    }

    public void vider() {
        messages.clear();
    }
}
