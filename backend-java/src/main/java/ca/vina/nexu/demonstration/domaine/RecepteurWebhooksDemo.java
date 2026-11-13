package ca.vina.nexu.demonstration.domaine;

import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.temps.Iso;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Récepteur de webhooks de démonstration — le système tiers qui reçoit nos envois signés :
 * vérifie {@code X-Signature-256} (HMAC-SHA256 du corps, comparaison à temps constant), journalise
 * (200 entrées au plus), et répond le code de panne réglé pour prouver la retentative. Le secret
 * ne s'affiche jamais : le journal dit seulement si la signature était valide.
 */
public final class RecepteurWebhooksDemo {

    private static final int TAILLE_JOURNAL = 200;

    private final Clock horloge;
    private String secret;
    private int codeReponse = 200;
    private final List<Map<String, Object>> journal = new ArrayList<>();

    public RecepteurWebhooksDemo(Clock horloge) {
        this.horloge = horloge;
    }

    public synchronized Map<String, Object> regler(Map<String, ?> r) {
        if (r.containsKey("secret")) {
            secret = r.get("secret") == null ? null : String.valueOf(r.get("secret"));
        }
        if (r.containsKey("codeReponse")) {
            Object c = r.get("codeReponse");
            if (!(c instanceof Number n) || n.doubleValue() != Math.floor(n.doubleValue()) || n.intValue() < 200 || n.intValue() > 599) {
                throw new IllegalArgumentException("codeReponse : un code HTTP entre 200 et 599");
            }
            codeReponse = n.intValue();
        }
        return reglages();
    }

    public synchronized Map<String, Object> reglages() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("secretConfigure", secret != null);
        m.put("codeReponse", codeReponse);
        return m;
    }

    public synchronized List<Map<String, Object>> journal() {
        return List.copyOf(journal);
    }

    public synchronized void vider() {
        journal.clear();
    }

    public synchronized void reinitialiser() {
        secret = null;
        codeReponse = 200;
        journal.clear();
    }

    /** Vérification à temps constant : une comparaison ordinaire fuit la longueur du préfixe commun. */
    public static boolean signatureValide(String secret, String corps, String signature) {
        if (secret == null || signature == null) {
            return false;
        }
        byte[] attendue = ("sha256=" + HexFormat.of().formatHex(Primitives.hmacSha256(secret, corps.getBytes(StandardCharsets.UTF_8)))).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(attendue, signature.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized Map<String, Object> recevoir(String corps, String evenement, String livraison, String signature) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("recuLe", Iso.de(horloge.instant()));
        e.put("evenement", evenement == null ? "?" : evenement);
        e.put("livraison", livraison == null ? "?" : livraison);
        e.put("signatureValide", signatureValide(secret, corps, signature));
        e.put("codeRendu", codeReponse);
        e.put("corps", corps);
        journal.add(0, e);
        while (journal.size() > TAILLE_JOURNAL) {
            journal.remove(journal.size() - 1);
        }
        return e;
    }

    public synchronized String pageJournal() {
        StringBuilder lignes = new StringBuilder();
        for (Map<String, Object> e : journal) {
            lignes.append("<tr><th scope=\"row\">").append(PageHtml.echapper((String) e.get("recuLe"))).append("</th><td>")
                    .append(PageHtml.echapper((String) e.get("evenement"))).append("</td><td>").append(PageHtml.echapper((String) e.get("livraison")))
                    .append("</td><td>").append(Boolean.TRUE.equals(e.get("signatureValide")) ? "Valide" : "Invalide ou absente").append("</td><td>")
                    .append(e.get("codeRendu")).append("</td></tr>");
        }
        String corps = "\n    <h1>Journal du récepteur de webhooks</h1>\n    <p>Récepteur <strong>de démonstration</strong> : il joue le système tiers. Secret "
                + (secret != null ? "configuré" : "<strong>non configuré</strong> (toute signature est invalide)") + " ; il répond actuellement <code>" + codeReponse + "</code>.</p>\n"
                + "    <div class=\"table-wrap\"><table><caption class=\"sr-only\">Envois reçus, du plus récent au plus ancien</caption>\n"
                + "    <thead><tr><th scope=\"col\">Reçu le</th><th scope=\"col\">Événement</th><th scope=\"col\">Livraison</th><th scope=\"col\">Signature</th><th scope=\"col\">Code rendu</th></tr></thead>\n"
                + "    <tbody>" + (lignes.isEmpty() ? "<tr><td colspan=\"5\">Aucun envoi reçu.</td></tr>" : lignes) + "</tbody></table></div>\n"
                + "    <p><a href=\"journal\">Le journal en JSON</a></p>";
        return PageHtml.page("Journal du récepteur de webhooks (démonstration)", corps);
    }
}
