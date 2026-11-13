package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Abonnements aux notifications poussées Web (devis 2.6) — la partie pure : ce qu'un navigateur
 * doit livrer pour s'abonner (point de terminaison https, clés {@code p256dh} et {@code auth}),
 * avec les messages du Node.
 */
public final class AbonnementsPoussee {

    private AbonnementsPoussee() {
    }

    public record Abonnement(String endpoint, String p256dh, String auth) { }

    public static List<String> valider(Object a) {
        if (!(a instanceof Map<?, ?> m)) {
            return List.of("abonnement attendu (endpoint, keys.p256dh, keys.auth)");
        }
        List<String> erreurs = new ArrayList<>();
        if (!(m.get("endpoint") instanceof String e) || !e.startsWith("https://")) {
            erreurs.add("endpoint : URL https attendue");
        }
        Object cles = m.get("keys");
        if (!(cles instanceof Map<?, ?> k) || !(k.get("p256dh") instanceof String p) || p.isEmpty()) {
            erreurs.add("keys.p256dh requis");
        }
        if (!(cles instanceof Map<?, ?> k2) || !(k2.get("auth") instanceof String s) || s.isEmpty()) {
            erreurs.add("keys.auth requis");
        }
        return erreurs;
    }

    /** À n'appeler qu'après {@link #valider}. */
    public static Abonnement lire(Map<?, ?> m) {
        Map<?, ?> k = (Map<?, ?>) m.get("keys");
        return new Abonnement((String) m.get("endpoint"), (String) k.get("p256dh"), (String) k.get("auth"));
    }
}
