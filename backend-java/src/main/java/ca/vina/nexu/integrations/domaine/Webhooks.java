package ca.vina.nexu.integrations.domaine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * <strong>Webhooks sortants</strong> (écart E5 du cadrage RH, §4.17 et §29) — port fidèle de
 * {@code integrations/webhooks.ts}. Un système tiers s'abonne à des événements métier ; chaque
 * événement lui est envoyé en POST JSON signé (HMAC-SHA256 du corps avec le secret partagé). Un
 * envoi qui échoue est retenté à 1 min, 5 min, 30 min, 2 h, 12 h ; au dixième échec consécutif,
 * le webhook est suspendu — écrit, jamais silencieux. Aucune E/S ici.
 */
public final class Webhooks {

    private Webhooks() {
    }

    public static final List<String> EVENEMENTS = List.of("absence.declaree", "absence.annulee", "besoin.cree", "besoin.attribue", "besoin.annule",
            "contrat.expirant", "document.expirant", "employe.cree",
            // AO 26-015-DO, lot R10 (E-TN-7) : ce que les effectifs annoncent au bus d'entreprise.
            "effectifs.feuille.approuvee", "effectifs.lot_paie.transmis", "effectifs.echange.applique", "effectifs.absence.acceptee",
            // 26-015-DO, addenda 5 b (AD-06c) : un quart publié a changé ; l'avis renvoie à la lecture des correctifs.
            "effectifs.quart.modifie");
    /** {@code Map.ofEntries} : {@code Map.of} s'arrête à dix paires. */
    public static final Map<String, String> LIBELLE_EVENEMENT = Map.ofEntries(
            Map.entry("absence.declaree", "Absence déclarée"),
            Map.entry("absence.annulee", "Absence annulée"),
            Map.entry("besoin.cree", "Besoin de remplacement créé"),
            Map.entry("besoin.attribue", "Remplacement attribué"),
            Map.entry("besoin.annule", "Besoin de remplacement annulé"),
            Map.entry("contrat.expirant", "Contrat arrivant à échéance (30 jours)"),
            Map.entry("document.expirant", "Document arrivant à expiration (30 jours)"),
            Map.entry("employe.cree", "Employé créé"),
            Map.entry("effectifs.feuille.approuvee", "Feuille de temps approuvée (effectifs)"),
            Map.entry("effectifs.lot_paie.transmis", "Lot de paie transmis à la paie (effectifs)"),
            Map.entry("effectifs.echange.applique", "Échange de quarts appliqué (effectifs)"),
            Map.entry("effectifs.absence.acceptee", "Absence d'effectifs acceptée"),
            Map.entry("effectifs.quart.modifie", "Quart publié modifié (effectifs)"));
    /** Attentes avant chaque nouvelle tentative, en minutes (règle WH-4). */
    public static final List<Integer> ATTENTES_MINUTES = List.of(1, 5, 30, 120, 720);
    public static final int ECHECS_AVANT_SUSPENSION = 10;
    public static final Duration DELAI = Duration.ofSeconds(10);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public record Webhook(String id, String url, String description, List<String> evenements, boolean actif, boolean suspendu, int echecsConsecutifs, String creeLe) { }

    public record DecisionEchec(String statut, String prochaineTentativeLe, boolean suspendre) { }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean urlAdmise(String url, List<String> erreurs) {
        URI u;
        try {
            u = new URI(url);
        } catch (URISyntaxException e) {
            u = null;
        }
        if (u == null || u.getScheme() == null || u.getHost() == null) {
            erreurs.add("url requise (adresse absolue)");
            return false;
        }
        boolean localhost = "http".equals(u.getScheme()) && List.of("localhost", "127.0.0.1").contains(u.getHost());
        if (!"https".equals(u.getScheme()) && !localhost) {
            erreurs.add("url : https:// obligatoire (http admis sur localhost seulement)");
        }
        return true;
    }

    public static List<String> validerWebhook(Map<String, Object> w) {
        List<String> erreurs = new ArrayList<>();
        urlAdmise(texte(w.get("url")).trim(), erreurs);
        if (!(w.get("evenements") instanceof List<?> evenements) || evenements.isEmpty()) {
            erreurs.add("evenements : au moins un");
        } else {
            List<String> inconnus = evenements.stream().map(Webhooks::texte).filter(e -> !EVENEMENTS.contains(e)).toList();
            if (!inconnus.isEmpty()) {
                erreurs.add("evenements inconnus : " + String.join(", ", inconnus) + " (admis : " + String.join(", ", EVENEMENTS) + ")");
            }
        }
        if (texte(w.get("description")).length() > 200) {
            erreurs.add("description trop longue (200 caractères au plus)");
        }
        return erreurs;
    }

    /** Dans l'ordre du catalogue, sans doublon : l'écran les lit toujours pareil, quel que soit l'ordre des cases cochées. */
    public static List<String> evenementsOrdonnes(List<?> demandes) {
        List<String> voulus = demandes.stream().map(Webhooks::texte).toList();
        return EVENEMENTS.stream().filter(voulus::contains).toList();
    }

    /** {@code whsec_} + 24 octets aléatoires en base64url — l'aléa est fourni par l'appelant. */
    public static String secretDepuis(byte[] aleatoire) {
        return "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(aleatoire);
    }

    public static String signer(String secret, String corps) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    /** Une chaîne JSON comme {@code JSON.stringify} l'écrit : guillemets, barre oblique inverse et caractères de contrôle échappés. */
    static String chaineJson(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    /** Le corps envoyé, stable et vérifiable : événement, locataire, instant, identifiant de livraison, données (déjà en JSON). */
    public static String corpsLivraison(String evenement, String tenantId, String survenuLe, String livraisonId, String donneesJson) {
        return "{\"evenement\":" + chaineJson(evenement) + ",\"tenantId\":" + chaineJson(tenantId) + ",\"survenuLe\":" + chaineJson(survenuLe)
                + ",\"livraison\":" + chaineJson(livraisonId) + ",\"donnees\":" + donneesJson + "}";
    }

    /**
     * Après un échec à la tentative {@code tentative} (1 = première) : retenter selon l'attente
     * suivante, ou déclarer l'envoi échoué ; et suspendre le webhook si les échecs consécutifs
     * atteignent le seuil.
     */
    public static DecisionEchec apresEchec(int tentative, int echecsConsecutifsApres, String maintenant) {
        boolean suspendre = echecsConsecutifsApres >= ECHECS_AVANT_SUSPENSION;
        if (tentative > ATTENTES_MINUTES.size() || suspendre) {
            return new DecisionEchec("echoue", null, suspendre);
        }
        Instant prochaine = Instant.parse(maintenant).plus(Duration.ofMinutes(ATTENTES_MINUTES.get(tentative - 1)));
        return new DecisionEchec("en_attente", ISO.format(prochaine), false);
    }
}
