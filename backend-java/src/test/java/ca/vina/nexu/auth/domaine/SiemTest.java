package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SIEM — sévérité, CEF, NDJSON, console")
class SiemTest {

    private static final EvenementAudit LOGIN = new EvenementAudit("aud-1", "2026-09-13T12:00:00.000Z", "usr-1", "alice", "auth.login.ok", null, null);
    private static final EvenementAudit REFUS = new EvenementAudit("aud-2", "2026-09-13T12:01:00.000Z", null, "bob|x", "auth.login.fail", "cible=1\nbis", "{\"a\":\"b\\\\\"}");
    private static final EvenementAudit LECTURE = new EvenementAudit("aud-3", "2026-09-13T12:02:00.000Z", "", "", "rh.dossier.lecture", "", "");

    @Test
    void laSeveriteEtLeNiveauSuiventLAction() {
        assertThat(Siem.severite("auth.user.delete")).isEqualTo(7);
        assertThat(Siem.severite("auth.password.change")).isEqualTo(4);
        assertThat(Siem.severite("rh.dossier.lecture")).isEqualTo(3);
        assertThat(Siem.niveau("x.blocked")).isEqualTo("high");
        assertThat(Siem.niveau("sso.ok")).isEqualTo("medium");
        assertThat(Siem.niveau("lecture")).isEqualTo("low");
    }

    @Test
    void uneLigneCefEchappeEtOmetLesChampsVides() {
        assertThat(Siem.ligneCef(LOGIN)).isEqualTo("CEF:0|NexuVINA|Finance|1.0|auth.login.ok|auth.login.ok|4|rt=2026-09-13T12:00:00.000Z suser=alice suid=usr-1");
        assertThat(Siem.ligneCef(REFUS)).isEqualTo("CEF:0|NexuVINA|Finance|1.0|auth.login.fail|auth.login.fail|7|rt=2026-09-13T12:01:00.000Z suser=bob|x act=cible\\=1 bis msg={\"a\":\"b\\\\\\\\\"}");
        assertThat(Siem.ligneCef(LECTURE)).isEqualTo("CEF:0|NexuVINA|Finance|1.0|rh.dossier.lecture|rh.dossier.lecture|3|rt=2026-09-13T12:02:00.000Z");
        assertThat(Siem.ligneCef(new EvenementAudit("a", "t", null, null, "a|b\\c\nd", null, null))).startsWith("CEF:0|NexuVINA|Finance|1.0|a\\|b\\\\c d|");
    }

    @Test
    void leNdjsonEstCeluiDeJsonStringify() {
        assertThat(Siem.ligneNdjson(LOGIN)).isEqualTo("{\"id\":\"aud-1\",\"at\":\"2026-09-13T12:00:00.000Z\",\"userId\":\"usr-1\",\"username\":\"alice\",\"action\":\"auth.login.ok\",\"target\":null,\"details\":null}");
        assertThat(Siem.json("a\"b\\c\nd\re\tf\u0001")).isEqualTo("\"a\\\"b\\\\c\\nd\\re\\tf\\u0001\"");
        assertThat(Siem.exporter(List.of(), "ndjson")).isEmpty();
        assertThat(Siem.exporter(List.of(LOGIN, LECTURE), "ndjson")).endsWith("\"details\":\"\"}\n").contains("\n{\"id\":\"aud-3\"");
        assertThat(Siem.exporter(List.of(LOGIN), "cef")).isEqualTo(Siem.ligneCef(LOGIN) + "\n");
    }

    @Test
    void laConsoleEnrichitEtSynthetise() {
        Map<String, Object> c = Siem.console(List.of(LOGIN, REFUS, LECTURE), null, 500);
        assertThat(c.get("summary")).isEqualTo(Map.of("high", 1, "medium", 1, "low", 1, "total", 3));
        @SuppressWarnings("unchecked") List<Map<String, Object>> evenements = (List<Map<String, Object>>) c.get("events");
        assertThat(evenements).hasSize(3);
        assertThat(evenements.get(1)).containsEntry("severity", 7).containsEntry("level", "high").containsEntry("username", "bob|x");
        @SuppressWarnings("unchecked") List<Map<String, Object>> moyens = (List<Map<String, Object>>) Siem.console(List.of(LOGIN, REFUS, LECTURE), "medium", 500).get("events");
        assertThat(moyens).extracting(e -> e.get("id")).containsExactly("aud-1", "aud-2");
        @SuppressWarnings("unchecked") List<Map<String, Object>> limites = (List<Map<String, Object>>) Siem.console(List.of(LOGIN, REFUS, LECTURE), "low", 1).get("events");
        assertThat(limites).hasSize(1);
    }
}
