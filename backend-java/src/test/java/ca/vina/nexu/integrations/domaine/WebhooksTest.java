package ca.vina.nexu.integrations.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Port de `integrations-webhooks.test.ts` — règles pures des webhooks sortants. */
@DisplayName("Webhooks sortants — domaine")
class WebhooksTest {

    @Test
    void unWebhookAUneUrlHttpsAuMoinsUnEvenementConnuUneDescriptionBornee() {
        assertThat(Webhooks.validerWebhook(Map.of("url", "https://paie.exemple.qc.ca/hooks", "evenements", List.of("absence.declaree")))).isEmpty();
        assertThat(Webhooks.validerWebhook(Map.of("url", "http://localhost:9999/x", "evenements", List.of("besoin.cree")))).isEmpty();
        assertThat(Webhooks.validerWebhook(Map.of("url", "http://127.0.0.1/x", "evenements", List.of("besoin.cree")))).isEmpty();
        assertThat(Webhooks.validerWebhook(Map.of("url", "http://paie.exemple.qc.ca", "evenements", List.of("besoin.cree"))))
                .containsExactly("url : https:// obligatoire (http admis sur localhost seulement)");
        assertThat(Webhooks.validerWebhook(Map.of("url", "pas une url", "evenements", List.of(), "description", "x".repeat(201))))
                .containsExactly("url requise (adresse absolue)", "evenements : au moins un", "description trop longue (200 caractères au plus)");
        assertThat(Webhooks.validerWebhook(Map.of("url", "relative/sans/hote", "evenements", List.of("besoin.cree")))).containsExactly("url requise (adresse absolue)");
        assertThat(Webhooks.validerWebhook(Map.of("url", "https://x.ca", "evenements", List.of("absence.declaree", "inconnu"))))
                .containsExactly("evenements inconnus : inconnu (admis : " + String.join(", ", Webhooks.EVENEMENTS) + ")");
        assertThat(Webhooks.validerWebhook(Map.of("url", "https://x.ca", "evenements", "absence.declaree"))).containsExactly("evenements : au moins un");
        assertThat(Webhooks.validerWebhook(Map.of("evenements", List.of("besoin.cree")))).containsExactly("url requise (adresse absolue)");
        Map<String, Object> sansDescription = new HashMap<>(Map.of("url", "https://x.ca", "evenements", List.of("besoin.cree")));
        sansDescription.put("description", null);
        assertThat(Webhooks.validerWebhook(sansDescription)).isEmpty();
        assertThat(Webhooks.evenementsOrdonnes(List.of("employe.cree", "absence.declaree", "employe.cree"))).containsExactly("absence.declaree", "employe.cree");
        assertThat(Webhooks.LIBELLE_EVENEMENT.get("besoin.attribue")).isEqualTo("Remplacement attribué");
    }

    @Test
    void leSecretSigneLeCorpsEnHmacSha256VerifiableParLeRecepteur() throws Exception {
        String s1 = Webhooks.secretDepuis(new byte[24]);
        String s2 = Webhooks.secretDepuis("abcdefghijklmnopqrstuvwx".getBytes(StandardCharsets.UTF_8));
        assertThat(s1).matches("^whsec_[A-Za-z0-9_-]{32}$");
        assertThat(s1).isNotEqualTo(s2);
        String corps = Webhooks.corpsLivraison("absence.declaree", "t", "2026-09-12T10:00:00.000Z", "l1", "{\"absenceId\":\"a1\"}");
        assertThat(corps).isEqualTo("{\"evenement\":\"absence.declaree\",\"tenantId\":\"t\",\"survenuLe\":\"2026-09-12T10:00:00.000Z\",\"livraison\":\"l1\",\"donnees\":{\"absenceId\":\"a1\"}}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(s1.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        assertThat(Webhooks.signer(s1, corps)).isEqualTo("sha256=" + HexFormat.of().formatHex(mac.doFinal(corps.getBytes(StandardCharsets.UTF_8))));
        assertThat(Webhooks.signer(s1, corps)).isNotEqualTo(Webhooks.signer(s2, corps));
        // Les chaînes s'échappent comme JSON.stringify : guillemets, barre oblique inverse, contrôles.
        assertThat(Webhooks.chaineJson("a\"b\\c\nd\re\tf" + (char) 1)).isEqualTo("\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\"");
    }

    @Test
    void apresUnEchecRetenterA1_5_30_120_720MinutesPuisEchouePuisSuspenduAuDixieme() {
        String t = "2026-09-12T10:00:00.000Z";
        assertThat(Webhooks.ATTENTES_MINUTES).containsExactly(1, 5, 30, 120, 720);
        assertThat(Webhooks.apresEchec(1, 1, t)).isEqualTo(new Webhooks.DecisionEchec("en_attente", "2026-09-12T10:01:00.000Z", false));
        assertThat(Webhooks.apresEchec(3, 3, t)).isEqualTo(new Webhooks.DecisionEchec("en_attente", "2026-09-12T10:30:00.000Z", false));
        assertThat(Webhooks.apresEchec(5, 5, t)).isEqualTo(new Webhooks.DecisionEchec("en_attente", "2026-09-12T22:00:00.000Z", false));
        assertThat(Webhooks.apresEchec(6, 6, t)).isEqualTo(new Webhooks.DecisionEchec("echoue", null, false));
        assertThat(Webhooks.apresEchec(2, Webhooks.ECHECS_AVANT_SUSPENSION, t)).isEqualTo(new Webhooks.DecisionEchec("echoue", null, true));
    }
}
