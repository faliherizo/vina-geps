package ca.vina.nexu.partage.journal.infrastructure;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.KeyValuePair;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le filtre du journal en situation (ADR 0041) : X-Requete-Id sur chaque réponse, une ligne http au
 * niveau de son statut avec le contexte de la session, le journal des actions pour une écriture d'un
 * compte (jamais pour une lecture ni un anonyme), le chemin masqué, la purge quotidienne annoncée.
 */
@DisplayName("Journal — filtre HTTP et journal des actions")
class FiltreJournalisationTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);
    private JdbcClient jdbc;
    private DepotJournalActions actions;
    private FiltreJournalisation filtre;
    private final ListAppender<ILoggingEvent> lignes = new ListAppender<>();
    private Logger journal;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        actions = new DepotJournalActions(jdbc, HORLOGE);
        filtre = new FiltreJournalisation(actions, HORLOGE, "30");
        journal = (Logger) LoggerFactory.getLogger(FiltreJournalisation.class);
        journal.setLevel(ch.qos.logback.classic.Level.DEBUG);
        lignes.start();
        journal.addAppender(lignes);
    }

    @AfterEach
    void ranger() {
        journal.detachAppender(lignes);
        journal.setLevel(null);
    }

    private MockHttpServletResponse requete(String methode, String uri, String query, int statut, ContexteRequete contexte) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest(methode, uri);
        r.setRequestURI(uri);
        r.setQueryString(query);
        MockHttpServletResponse reponse = new MockHttpServletResponse();
        // La chaîne joue le filtre de session (qui pose le contexte) et la route (qui fixe le statut).
        filtre.doFilter(r, reponse, new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                assertThat(MDC.get("requete")).isNotNull();
                if (contexte != null) {
                    req.setAttribute(ContexteRequete.ATTRIBUT, contexte);
                }
                ((MockHttpServletResponse) res).setStatus(statut);
            }
        });
        return reponse;
    }

    private static Map<String, Object> champs(ILoggingEvent e) {
        return e.getKeyValuePairs().stream().collect(Collectors.toMap(p -> p.key, p -> p.value));
    }

    @Test
    @DisplayName("lecture réussie → debug sans action ; écriture d'un compte → info + action avec le même identifiant ; 404 → libellé ; 500 → error ; anonyme → rien au journal des actions")
    void lignesEtActions() throws Exception {
        ContexteRequete lea = new ContexteRequete("usr-lea", "lea", "employee", "emp-1", "t-1");
        MockHttpServletResponse lecture = requete("GET", "/api/auth/me", null, 200, lea);
        assertThat(lecture.getHeader("X-Requete-Id")).matches("[0-9a-f]{12}");
        MockHttpServletResponse ecriture = requete("PUT", "/api/notifications/preferences", "x=1", 200, lea);
        requete("GET", "/api/auth/users/fantome", null, 404, lea);
        requete("DELETE", "/api/x/tRfL9w-3kQ0mZpXyV2bN8cH7jD5gS1aE", null, 500, lea);
        requete("POST", "/api/auth/login", null, 200, null);
        requete("POST", "/public/scolaire/disponibilites/jeton-secret-tres-long-abcdef", null, 200, lea);

        assertThat(lignes.list).extracting(e -> e.getLevel().toString()).containsExactly("DEBUG", "INFO", "INFO", "ERROR", "INFO", "INFO");
        ILoggingEvent http = lignes.list.get(1);
        assertThat(champs(http)).containsEntry("evenement", "http").containsEntry("methode", "PUT").containsEntry("chemin", "/api/notifications/preferences?x").containsEntry("statut", 200);
        assertThat(http.getMDCPropertyMap()).containsEntry("requete", ecriture.getHeader("X-Requete-Id")).containsEntry("utilisateur", "usr-lea").containsEntry("organisme", "t-1").doesNotContainKey("acteurReel");
        assertThat(champs(lignes.list.get(2))).containsEntry("motif", "introuvable");
        assertThat(champs(lignes.list.get(3))).containsEntry("chemin", "/api/x/{jeton}");
        assertThat(lignes.list.get(4).getMDCPropertyMap()).doesNotContainKey("utilisateur");
        assertThat(champs(lignes.list.get(5))).containsEntry("chemin", "/public/scolaire/disponibilites/{jeton}");
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();

        // Le journal des actions : les écritures sous /api d'un compte, la lecture non, l'anonyme non, la page publique non.
        DepotJournalActions.Page page = actions.lister(new DepotJournalActions.Filtre("t-1", null, null, null, null, null, 0, 50));
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(DepotJournalActions.Action::methode, DepotJournalActions.Action::chemin, DepotJournalActions.Action::statut)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("DELETE", "/api/x/{jeton}", 500), org.assertj.core.groups.Tuple.tuple("PUT", "/api/notifications/preferences", 200));
        assertThat(page.items().get(1).requeteId()).isEqualTo(ecriture.getHeader("X-Requete-Id"));
        assertThat(page.items().get(1).instant()).isEqualTo("2026-09-19T12:00:00Z");
    }

    @Test
    @DisplayName("sous emprunt, l'action et la ligne portent l'acteur réel ; la première écriture du jour purge et le dit")
    void empruntEtPurge() throws Exception {
        jdbc.sql("INSERT INTO journal_actions (id, instant, tenantId, userId, acteurReel, methode, chemin, statut, dureeMs, requeteId) VALUES ('vieille','2020-01-01T00:00:00Z','t-1','u',NULL,'POST','/api/x',200,1,'r')").update();
        ContexteRequete empruntee = new ContexteRequete("usr-lea", "lea", "employee", "emp-1", "t-1", "usr-admin", "empr-1");
        requete("POST", "/api/scolaire/absences", null, 201, empruntee);
        assertThat(lignes.list.get(0).getMDCPropertyMap()).containsEntry("acteurReel", "usr-admin");
        assertThat(champs(lignes.list.get(1))).containsEntry("evenement", "journal").containsEntry("purgees", 1).containsEntry("retentionJours", 30);
        DepotJournalActions.Page page = actions.lister(new DepotJournalActions.Filtre("t-1", null, null, null, null, null, 0, 50));
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).acteurReel()).isEqualTo("usr-admin");
        // La seconde écriture du jour ne purge plus rien : pas de ligne « journal ».
        requete("POST", "/api/scolaire/absences", null, 201, empruntee);
        assertThat(lignes.list.stream().filter(e -> "journal".equals(champs(e).get("evenement")))).hasSize(1);
        assertThatThrownBy(() -> new FiltreJournalisation(actions, HORLOGE, "dix")).hasMessageContaining("JOURNAL_ACTIONS_RETENTION_JOURS « dix » invalide");
        assertThat(DepotJournalActions.retentionLue(" ")).isEqualTo(365);
        assertThat(DepotJournalActions.retentionLue("0")).isZero();
        assertThatThrownBy(() -> DepotJournalActions.retentionLue("-1")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lectureFiltreeEtBornes() {
        for (String[] l : new String[][] {{"a", "2026-09-10T10:00:00Z", "t-1", "u-1", null, "POST", "/api/a", "201"}, {"b", "2026-09-15T10:00:00Z", "t-1", "u-2", "u-1", "PUT", "/api/b", "403"},
                {"c", "2026-09-15T11:00:00Z", "t-2", "u-3", null, "DELETE", "/api/c", "204"}, {"d", "2026-09-16T10:00:00Z", null, "u-9", null, "POST", "/api/d", "200"}}) {
            jdbc.sql("INSERT INTO journal_actions (id, instant, tenantId, userId, acteurReel, methode, chemin, statut, dureeMs, requeteId) VALUES (?,?,?,?,?,?,?,?,1,'r')")
                    .params(l[0], l[1], l[2], l[3], l[4], l[5], l[6], Integer.parseInt(l[7])).update();
        }
        assertThat(actions.lister(new DepotJournalActions.Filtre("t-1", "t-2", null, null, null, null, 0, 0)).items()).extracting(DepotJournalActions.Action::id).containsExactly("b", "a");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, "t-2", null, null, null, null, 0, 10)).items()).extracting(DepotJournalActions.Action::id).containsExactly("c");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, null, null, null, 0, 10)).total()).isEqualTo(4);
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, "u-1", null, null, null, 0, 10)).items()).extracting(DepotJournalActions.Action::id).containsExactly("b", "a");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, "2026-09-15", "2026-09-15", null, 0, 10)).items()).extracting(DepotJournalActions.Action::id).containsExactly("c", "b");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, null, null, "403", 0, 10)).items()).extracting(DepotJournalActions.Action::id).containsExactly("b");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, null, null, "delete", 0, 10)).items()).extracting(DepotJournalActions.Action::id).containsExactly("c");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, null, null, "/api/", 1, 3)).items()).extracting(DepotJournalActions.Action::id).containsExactly("a");
        assertThat(actions.lister(new DepotJournalActions.Filtre(null, null, null, null, null, null, -1, 9999)).items()).hasSize(4);
        assertThatThrownBy(() -> actions.lister(new DepotJournalActions.Filtre(null, null, null, "hier", null, null, 0, 10))).hasMessage("depuis : date AAAA-MM-JJ");
        assertThatThrownBy(() -> actions.lister(new DepotJournalActions.Filtre(null, null, null, null, "demain", null, 0, 10))).hasMessage("jusqua : date AAAA-MM-JJ");
        assertThat(actions.purger(0)).isZero();
        assertThat(actions.purger(2)).isEqualTo(4);
        assertThat(new DepotJournalActions.Action("i", "t", null, "u", null, "POST", "/x", 200, 5, "r").vue()).containsEntry("statut", 200).containsEntry("requeteId", "r");
    }
}
