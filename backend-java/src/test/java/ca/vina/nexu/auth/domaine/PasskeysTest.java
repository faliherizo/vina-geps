package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Passkeys — défis, options des cérémonies, vérificateur simulé, anti-rejeu")
class PasskeysTest {

    @Test
    void options() {
        String defi = Passkeys.defi(new Random(7));
        assertThat(defi).hasSize(43);
        Map<String, Object> reg = Passkeys.optionsEnregistrement(defi, "nexuvina.ca", "u1");
        assertThat(reg).containsEntry("challenge", defi).containsEntry("rp", Map.of("id", "nexuvina.ca", "name", "Nexu VINA")).containsEntry("user", Map.of("id", "u1"));
        assertThat((List<?>) reg.get("pubKeyCredParams")).hasSize(2);
        assertThat(((Map<?, ?>) ((List<?>) reg.get("pubKeyCredParams")).get(1)).get("alg")).isEqualTo(-257);
        Passkeys.Identifiant c = new Passkeys.Identifiant("id", "cred-1", 3, null, "2026-09-14T00:00:00.000Z");
        Map<String, Object> auth = Passkeys.optionsAuthentification(defi, "nexuvina.ca", List.of(c));
        assertThat(auth).containsEntry("rpId", "nexuvina.ca");
        assertThat((List<?>) auth.get("allowCredentials")).isEqualTo(List.of(Map.of("type", "public-key", "id", "cred-1")));
        assertThatThrownBy(() -> Passkeys.optionsAuthentification(defi, "nexuvina.ca", List.of())).hasMessage("no registered passkey for this user");
        assertThat(c.vue()).containsEntry("label", null).containsEntry("counter", 3);
        assertThat(new Passkeys.Defi("x", 0).expire(Passkeys.DUREE_DEFI_MS)).isFalse();
        assertThat(new Passkeys.Defi("x", 0).expire(Passkeys.DUREE_DEFI_MS + 1)).isTrue();
    }

    @Test
    void verificateurSimuleEtCompteur() {
        Map<String, Object> ok = Map.of("challenge", "d", "credentialId", "c", "publicKey", "pk");
        assertThat(Passkeys.SIMULE.enregistrement(ok, "d")).isTrue();
        assertThat(Passkeys.SIMULE.enregistrement(ok, "autre")).isFalse();
        assertThat(Passkeys.SIMULE.enregistrement(Map.of("challenge", "d", "credentialId", "c"), "d")).isFalse();
        assertThat(Passkeys.SIMULE.authentification(Map.of("challenge", "d", "credentialId", "c"), "d", "pk")).isTrue();
        assertThat(Passkeys.SIMULE.authentification(Map.of("challenge", "d"), "d", "pk")).isFalse();
        Map<String, Object> nul = new HashMap<>();
        nul.put("challenge", null);
        assertThat(Passkeys.SIMULE.authentification(nul, "d", "pk")).isFalse();
        assertThat(Passkeys.compteur(Map.of())).isZero();
        assertThat(Passkeys.compteur(Map.of("counter", 4))).isEqualTo(4);
        assertThat(Passkeys.compteur(Map.of("counter", "7"))).isEqualTo(7);
        assertThat(Passkeys.compteur(Map.of("counter", "x"))).isZero();
        Passkeys.exigerCompteurCroissant(2, 1);
        assertThatThrownBy(() -> Passkeys.exigerCompteurCroissant(1, 1)).hasMessage("replay detected (counter not incremented)");
    }
}
