package ca.vina.nexu.partage.audit.infrastructure;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La chaîne d'empreintes est celle du Node : même formule, vérifiable par lui. */
@DisplayName("Journal d'audit — chaîne d'empreintes")
class JournalAuditTest {

    @Test
    void empreinteIdentiqueAuNode() throws Exception {
        // Même formule que `empreinteDe` de auth/audit.ts : sha256 des champs joints par U+0000, précédente vide en tête de chaîne.
        JournalAudit.Entree e = new JournalAudit.Entree("aud-1", "2026-09-13T00:00:00.000Z", "u1", "admin", "dossier.urgence.lecture", "e1", "{\"contacts\":2}");
        String texte = String.join("\u0000", "", "aud-1", "2026-09-13T00:00:00.000Z", "u1", "admin", "dossier.urgence.lecture", "e1", "{\"contacts\":2}");
        String attendu = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(texte.getBytes(StandardCharsets.UTF_8)));
        assertThat(JournalAudit.empreinteDe(null, e)).isEqualTo(attendu).hasSize(64);
        JournalAudit.Entree sans = new JournalAudit.Entree("aud-2", "t", null, null, "a", null, null);
        assertThat(JournalAudit.empreinteDe("abc", sans)).isNotEqualTo(JournalAudit.empreinteDe(null, sans));
        assertThatThrownBy(() -> JournalAudit.condensat("inexistant")).isInstanceOf(IllegalStateException.class).hasMessageContaining("inexistant");
    }

    @Test
    void chaqueEntreeEnchaineLaPrecedente(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        JournalAudit journal = new JournalAudit(jdbc);
        journal.consigner("u1", "admin", "a.b", "cible", Map.of("n", 1));
        journal.consigner("u1", "admin", "a.c", null, null);
        List<Map<String, Object>> lignes = jdbc.sql("SELECT id, at, userId, username, action, target, details, empreinte FROM audit_log ORDER BY rowid").query().listOfRows();
        assertThat(lignes).hasSize(2);
        Map<String, Object> premiere = lignes.get(0);
        Map<String, Object> seconde = lignes.get(1);
        assertThat(premiere.get("empreinte")).isEqualTo(JournalAudit.empreinteDe(null,
                new JournalAudit.Entree((String) premiere.get("id"), (String) premiere.get("at"), "u1", "admin", "a.b", "cible", "{\"n\":1}")));
        assertThat(seconde.get("empreinte")).isEqualTo(JournalAudit.empreinteDe((String) premiere.get("empreinte"),
                new JournalAudit.Entree((String) seconde.get("id"), (String) seconde.get("at"), "u1", "admin", "a.c", null, null)));
    }
}
