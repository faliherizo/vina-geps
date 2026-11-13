package ca.vina.nexu.notifications.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Journal des textos — masque, fournisseur nommé, quota, rappel, signature Twilio, canaux")
class JournalTextosTest {

    @Test
    void masqueEtFournisseur() {
        assertThat(JournalTextos.masquer("+1 418 555-0101")).isEqualTo("••••••••0101");
        assertThat(JournalTextos.masquer("12")).isEqualTo("••••");
        assertThat(JournalTextos.masquer(null)).isEqualTo("••••");
        assertThat(JournalTextos.nomFournisseur(Map.of())).isEqualTo("simule");
        assertThat(JournalTextos.nomFournisseur(Map.of("SMS_FOURNISSEUR", "Twilio"))).isEqualTo("twilio");
        assertThat(JournalTextos.nomFournisseur(Map.of("SMS_URL", "https://x", "SMS_JETON", "j"))).isEqualTo("http");
        assertThat(JournalTextos.nomFournisseur(Map.of("SMS_URL", "https://x"))).isEqualTo("simule");
        assertThat(JournalTextos.limiteParDefaut(Map.of())).isEqualTo(1000);
        assertThat(JournalTextos.limiteParDefaut(Map.of("SMS_QUOTA_MENSUEL", " 0 "))).isZero();
        assertThat(JournalTextos.limiteParDefaut(Map.of("SMS_QUOTA_MENSUEL", "-5"))).isEqualTo(1000);
        assertThat(JournalTextos.limiteParDefaut(Map.of("SMS_QUOTA_MENSUEL", "dix"))).isEqualTo(1000);
        assertThat(JournalTextos.urlRappelTwilio(Map.of())).isNull();
        assertThat(JournalTextos.urlRappelTwilio(Map.of("PUBLIC_API_URL", " https://api.x.ca// "))).isEqualTo("https://api.x.ca/api/notifications/sms/statut/twilio");
    }

    @Test
    void etatEtReglages() {
        JournalTextos.Etat simule = JournalTextos.Etat.de(Map.of());
        assertThat(simule.vue()).containsEntry("fournisseur", "simule").containsEntry("simule", true).containsEntry("manquantes", List.of()).containsEntry("rappel", null).containsEntry("appel", "simule");
        JournalTextos.Etat incomplet = JournalTextos.Etat.de(Map.of("SMS_FOURNISSEUR", "twilio", "TWILIO_ACCOUNT_SID", "a", "PUBLIC_API_URL", "https://api"));
        assertThat(incomplet.manquantes()).containsExactly("TWILIO_AUTH_TOKEN", "TWILIO_FROM");
        assertThat(incomplet.rappel()).isEqualTo("https://api/api/notifications/sms/statut/twilio");
        assertThat(incomplet.appel()).isEqualTo("twilio");
        JournalTextos.Etat inconnu = JournalTextos.Etat.de(Map.of("SMS_FOURNISSEUR", "pigeon", "APPEL_FOURNISSEUR", "telnyx"));
        assertThat(inconnu.manquantes()).singleElement().asString().startsWith("SMS_FOURNISSEUR inconnu");
        assertThat(inconnu.appel()).isEqualTo("invalide");
        assertThat(inconnu.rappel()).isNull();
        JournalTextos.Reglages r = JournalTextos.Reglages.de(Map.of("SMS_FOURNISSEUR", "telnyx", "TELNYX_API_KEY", "k", "TELNYX_FROM", "f", "SMS_QUOTA_MENSUEL", "20", "TWILIO_AUTH_TOKEN", "t"));
        assertThat(r.fournisseur()).isEqualTo("telnyx");
        assertThat(r.limiteParDefaut()).isEqualTo(20);
        assertThat(r.jetonTwilio()).isEqualTo("t");
        assertThat(r.etat().simule()).isFalse();
    }

    @Test
    void quotaEtLimites() {
        JournalTextos.Quota q = JournalTextos.Quota.de("2026-09", 10, 4, 4);
        assertThat(q.restants()).isEqualTo(6);
        // « atteint » ne suffit plus : ce qui compte est de savoir si le **message demandé** tient.
        assertThat(q.refuse(1)).isFalse();
        assertThat(JournalTextos.Quota.de("2026-09", 10, 12, 12).restants()).isZero();
        assertThat(JournalTextos.Quota.de("2026-09", 10, 10, 10).refuse(1)).isTrue();
        // Le motif dit désormais le compte **en segments**, et pourquoi le message coûte cher :
        // « quota atteint » sans le pourquoi ne laisse rien à corriger à l'administrateur.
        assertThat(JournalTextos.Quota.de("2026-09", 10, 10, 10).motifDeRefus(Segments.decouper("court")))
                .isEqualTo("quota mensuel atteint (10/10 segments en 2026-09) ; ce message en coûte 1");
        assertThat(JournalTextos.Quota.de("2026-09", 10, 8, 8).motifDeRefus(Segments.decouper("ê".repeat(200))))
                .contains("8/10 segments").contains("« ê »").contains("UCS-2");
        // Le message entier doit tenir : deux segments restants ne laissent pas passer un message de quatre.
        assertThat(JournalTextos.Quota.de("2026-09", 10, 8, 8).refuse(2)).isFalse();
        assertThat(JournalTextos.Quota.de("2026-09", 10, 8, 8).refuse(3)).isTrue();
        assertThat(JournalTextos.Quota.de("2026-09", 0, 999, 999).refuse(99)).isFalse();
        assertThat(JournalTextos.Quota.de("2026-09", 10, 3, 1).vue()).containsEntry("messages", 1).containsEntry("utilises", 3);
        assertThat(JournalTextos.Quota.de("2026-09", 0, 999, 999).vue()).containsEntry("illimite", true).containsEntry("restants", null);
        assertThat(JournalTextos.Quota.de("2026-09", 0, 999, 999).refuse(1)).isFalse();
        assertThat(JournalTextos.moisDe("2026-09-14T12:00:00.000Z")).isEqualTo("2026-09");
        assertThat(JournalTextos.exigerLimite(1_000_000)).isEqualTo(1_000_000);
        assertThat(JournalTextos.exigerLimite(0L)).isZero();
        assertThatThrownBy(() -> JournalTextos.exigerLimite(1_000_001)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JournalTextos.exigerLimite(null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(JournalTextos.limiteListe(null)).isEqualTo(100);
        assertThat(JournalTextos.limiteListe(0)).isEqualTo(100);
        assertThat(JournalTextos.limiteListe(5)).isEqualTo(5);
        assertThat(JournalTextos.limiteListe(5000)).isEqualTo(1000);
    }

    @Test
    void textoEtComposition() {
        JournalTextos.Texto t = new JournalTextos.Texto(1, "t-1", "+14185550101", "m", "echoue", "twilio", null, "c", null, "quota mensuel atteint (1 textos en 2026-09)", null, null, 1);
        assertThat(t.echoue()).isTrue();
        assertThat(t.refuseAvantLeFournisseur()).isTrue();
        assertThat(t.refusePourQuota()).isTrue();
        assertThat(t.vue()).containsEntry("numero", "••••••••0101").containsEntry("id", 1L);
        JournalTextos.Texto ok = new JournalTextos.Texto(2, "t-1", "+14185550101", "m", "envoye", "twilio", "SM1", "c", "s", null, "besoin:1", "+1", 3);
        assertThat(ok.echoue()).isFalse();
        assertThat(ok.refuseAvantLeFournisseur()).isFalse();
        assertThat(ok.refusePourQuota()).isFalse();
        assertThat(new JournalTextos.Texto(3, "t-1", "n", "m", "echoue", null, "SM2", "c", null, "refusé par le fournisseur", null, null, 1).refuseAvantLeFournisseur()).isFalse();
        assertThat(JournalTextos.refusDeComposition(null, "")).containsExactly("numéro invalide : format international (+15145551234) ou nord-américain à dix chiffres attendu", "texte requis");
        assertThat(JournalTextos.refusDeComposition("+14185550101", "ok")).isEmpty();
    }

    @Test
    void rappelEtSignature() {
        assertThat(JournalTextos.erreurDuRappel(null, "x")).isNull();
        assertThat(JournalTextos.erreurDuRappel("", "x")).isNull();
        assertThat(JournalTextos.erreurDuRappel("30003", null)).isEqualTo("30003");
        assertThat(JournalTextos.erreurDuRappel("30003", "")).isEqualTo("30003");
        assertThat(JournalTextos.erreurDuRappel("30003", "Unreachable")).isEqualTo("30003 Unreachable");
        assertThat(JournalTextos.erreurApresRappel("livre", "x", "ancienne", "delivered")).isEqualTo("ancienne");
        assertThat(JournalTextos.erreurApresRappel("echoue", "30003", "ancienne", "failed")).isEqualTo("30003");
        assertThat(JournalTextos.erreurApresRappel("echoue", null, "ancienne", "failed")).isEqualTo("ancienne");
        assertThat(JournalTextos.erreurApresRappel("echoue", null, null, "undelivered")).isEqualTo("refusé par le fournisseur (undelivered)");
        // Vecteur de la documentation Twilio (« Validating requests are coming from Twilio »).
        String url = "https://mycompany.com/myapp.php?foo=1&bar=2";
        Map<String, String> params = Map.of("CallSid", "CA1234567890ABCDE", "Caller", "+12349013030", "Digits", "1234", "From", "+12349013030", "To", "+18005551212");
        String jeton = "12345";
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, "0/KCTR6DLpKmkAf8muzZqo1nDgQ=", jeton)).isTrue();
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, "0/KCTR6DLpKmkAf8muzZqo1nDgQ", jeton)).isFalse();
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, null, jeton)).isFalse();
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, "", jeton)).isFalse();
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, "x", null)).isFalse();
        assertThat(JournalTextos.verifierSignatureTwilio(url, params, "x", "")).isFalse();
        assertThat(JournalTextos.resume(List.of(Map.of("status", "livre", "n", 2, "dernier", "b"), Map.of("status", "echoue", "n", 1, "dernier", "a"))))
                .containsEntry("total", 3).containsEntry("dernierLe", "b");
        assertThat(JournalTextos.resume(List.of())).containsEntry("total", 0).containsEntry("dernierLe", null);
    }

    @Test
    void canaux() {
        assertThat(Preferences.PAR_DEFAUT.fusionner(Map.of("texto", false, "poussee", false), "t")).isEqualTo(new Preferences(true, false, false, Map.of(), "t"));
        assertThat(Preferences.PAR_DEFAUT.fusionner(Map.of(), "t").courriel()).isTrue();
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.fusionner(Map.of("courriel", false, "texto", false), "t")).hasMessage("au moins un canal écrit (courriel ou texto) doit rester actif");
        assertThatThrownBy(() -> Preferences.PAR_DEFAUT.fusionner(Map.of("courriel", 1, "poussee", "non"), "t")).hasMessage("courriel : vrai ou faux attendu; poussee : vrai ou faux attendu");
        assertThat(new Preferences(false, true, false, Map.of(), null).ouvert("courriel")).isFalse();
        assertThat(new Preferences(false, true, false, Map.of(), null).ouvert("texto")).isTrue();
        assertThat(new Preferences(false, true, false, Map.of(), null).ouvert("poussee")).isFalse();
        assertThat(Preferences.PAR_DEFAUT.vue()).containsEntry("updatedAt", null).containsEntry("courriel", true);
    }
}
