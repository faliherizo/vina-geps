package ca.vina.nexu.securite.infrastructure.web;

import ca.vina.nexu.securite.infrastructure.configuration.ConfigurationPareFeu;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.securite.domaine.PareFeu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Filtre du pare-feu — en-têtes, refus JSON, mesure, journal d'audit")
class FiltrePareFeuTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);

    @RestController
    static class Cible {
        @GetMapping({"/api/ping", "/autre"})
        public ResponseEntity<Object> ping() {
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    @Test
    void refuseSigneEtMesure(@TempDir Path dossier) throws Exception {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        ConfigurationPareFeu cfg = new ConfigurationPareFeu();
        PareFeu pareFeu = cfg.pareFeu(new JournalAudit(jdbc), HORLOGE, "bloquer", "3", "", "");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new Cible()).addFilters(new FiltrePareFeu(pareFeu, HORLOGE, true)).build();
        mvc.perform(get("/api/ping")).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000; includeSubDomains")).andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"));
        mvc.perform(get(java.net.URI.create("/api/ping?q=%3Cscript%3E"))).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("requête refusée par le pare-feu applicatif (script inséré)"));
        assertThat(jdbc.sql("SELECT action, target FROM audit_log").query().listOfRows()).hasSize(1).first().satisfies(l -> {
            assertThat(l.get("action")).isEqualTo("securite.waf.bloque");
            assertThat(l.get("target")).isEqualTo("/api/ping");
        });
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/ping")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.errors[0]").value("trop de requêtes : réessayez dans une minute"));
        mvc.perform(get("/autre")).andExpect(status().isOk());   // hors /api : ni inspection, ni en-têtes
        Map<String, Object> etat = pareFeu.etat();
        @SuppressWarnings("unchecked") Map<String, Object> capacite = (Map<String, Object>) etat.get("capacite");
        assertThat(capacite.get("requetesDerniereMinute")).isEqualTo(5);
        assertThat(capacite.get("enVol")).isEqualTo(0);
        // sans HSTS quand le service n'est pas en HTTPS
        MockMvc http = MockMvcBuilders.standaloneSetup(new Cible()).addFilters(new FiltrePareFeu(cfg.pareFeu(new JournalAudit(jdbc), HORLOGE, "inactif", "", "", ""), HORLOGE, false)).build();
        http.perform(get(java.net.URI.create("/api/ping?q=%3Cscript%3E"))).andExpect(status().isOk()).andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    void unJournalIndisponibleNeLaissePasPasser(@TempDir Path dossier) {
        JdbcClient jdbc = JdbcClient.create(SchemaEssai.source(dossier));   // aucune table audit_log
        PareFeu pareFeu = new ConfigurationPareFeu().pareFeu(new JournalAudit(jdbc), HORLOGE, "", "", "", "");
        assertThat(pareFeu.controler("/api/x/../y", "", "GET", "1.1.1.1", null, null)).isPresent();
    }
}
