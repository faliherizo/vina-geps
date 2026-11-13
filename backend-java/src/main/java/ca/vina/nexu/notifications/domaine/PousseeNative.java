package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * **Notifications natives** (refonte E ★) — le jeton de poussée d'un appareil mobile (Expo), sa
 * forme, et le port vers le service de poussée. Même modèle que le Web Push : une charge, un
 * résultat (envoyé, échoué, périmé), un simulateur pour les essais.
 */
public final class PousseeNative {

    private PousseeNative() {
    }

    public static final List<String> PLATEFORMES = List.of("ios", "android", "web");
    /** Le jeton d'Expo a une forme connue ; un jeton libre serait une adresse vers nulle part. */
    public static final Pattern JETON_EXPO = Pattern.compile("^(ExponentPushToken|ExpoPushToken)\\[[A-Za-z0-9_-]{8,}\\]$");
    public static final String PERIME = "DeviceNotRegistered";

    public record Appareil(String jeton, String plateforme, String nom) {
    }

    /** Lit et valide la forme d'un appareil ; les manques sont nommés ensemble, comme au Node. */
    public static Appareil lire(Map<String, Object> corps) {
        Map<String, Object> c = corps == null ? Map.of() : corps;
        List<String> erreurs = new ArrayList<>();
        Object jeton = c.get("jeton");
        if (!(jeton instanceof String j) || !JETON_EXPO.matcher(j).matches()) {
            erreurs.add("jeton : jeton de poussée Expo attendu (ExponentPushToken[…])");
        }
        Object plateforme = c.get("plateforme");
        if (!(plateforme instanceof String p) || !PLATEFORMES.contains(p)) {
            erreurs.add("plateforme : l'une de " + String.join(", ", PLATEFORMES));
        }
        Object nom = c.get("nom");
        if (nom != null && (!(nom instanceof String n) || n.length() > 80)) {
            erreurs.add("nom : texte de 80 caractères au plus");
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ; ", erreurs));
        }
        String n = nom == null ? null : ((String) nom).trim();
        return new Appareil((String) jeton, (String) plateforme, n == null || n.isEmpty() ? null : n);
    }

    public interface PorteurNatif {
        PousseeWeb.Resultat envoyer(String jeton, PousseeWeb.Charge charge);
    }

    /** Le simulateur retient les envois ; {@code perimes} rejoue un service qui ne connaît plus l'appareil. */
    public static final class Simulee implements PorteurNatif {
        private final List<Map.Entry<String, PousseeWeb.Charge>> envois = new ArrayList<>();
        private final Set<String> perimes = new HashSet<>();
        private final Set<String> pannes = new HashSet<>();

        public List<Map.Entry<String, PousseeWeb.Charge>> envois() {
            return envois;
        }

        public Simulee perimer(String jeton) {
            perimes.add(jeton);
            return this;
        }

        public Simulee tomberEnPanne(String jeton) {
            pannes.add(jeton);
            return this;
        }

        @Override
        public PousseeWeb.Resultat envoyer(String jeton, PousseeWeb.Charge charge) {
            envois.add(Map.entry(jeton, charge));
            if (perimes.contains(jeton)) {
                return new PousseeWeb.Resultat(PousseeWeb.EXPIRE, 200, PERIME);
            }
            if (pannes.contains(jeton)) {
                return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, 500, "service de poussée indisponible");
            }
            return new PousseeWeb.Resultat(PousseeWeb.ENVOYE, 200, null);
        }
    }

    /** Ce qu'une réponse du service d'Expo devient : ok → envoyé ; DeviceNotRegistered → périmé ; le reste → échoué, nommé. */
    public static PousseeWeb.Resultat interpreter(int statut, boolean ok, Map<String, Object> json) {
        if (!ok) {
            return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, statut, "réponse " + statut);
        }
        Object data = json == null ? null : json.get("data");
        Map<?, ?> d = data instanceof Map<?, ?> m ? m : Map.of();
        if ("ok".equals(d.get("status"))) {
            return new PousseeWeb.Resultat(PousseeWeb.ENVOYE, statut, null);
        }
        Object details = d.get("details");
        String erreur = details instanceof Map<?, ?> dm && dm.get("error") != null ? String.valueOf(dm.get("error")) : null;
        if (PERIME.equals(erreur)) {
            return new PousseeWeb.Resultat(PousseeWeb.EXPIRE, statut, PERIME);
        }
        String message = d.get("message") != null ? String.valueOf(d.get("message")) : erreur != null ? erreur : "réponse inattendue";
        return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, statut, message.length() > 200 ? message.substring(0, 200) : message);
    }
}
