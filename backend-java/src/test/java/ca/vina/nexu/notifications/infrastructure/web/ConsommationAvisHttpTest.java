package ca.vina.nexu.notifications.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.Consommation;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Segments;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotConsommation;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import ca.vina.nexu.saas.infrastructure.persistance.DepotDestruction;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotMaintenance;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.saas.infrastructure.web.ControleurSaas;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/saas/consommation} — l'écran d'où l'exploitant plafonne et facture.
 *
 * <p>Jumeau de {@code backend/tests/unit/consommation-avis.test.ts}. Ce qui est éprouvé porte sur
 * de l'argent et sur une frontière d'autorisation : le comptage <b>en segments</b>, le refus d'un
 * message qui ne tient pas dans ce qui reste, l'imputation au bon organisme, et le fait qu'un
 * administrateur d'établissement n'a pas accès à une vue inter-organismes.
 */
@DisplayName("/api/saas/consommation — textos en segments, courriels comptés, plafonds et dépassement")
class ConsommationAvisHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-23T12:00:00Z"), ZoneOffset.UTC);
    private static final String MOIS = "2026-09";
    private static final ContexteRequete SUPERADMIN = ClientHttpEssai.superadmin();
    private static final ContexteRequete ADMIN_ETABLISSEMENT = ClientHttpEssai.session("admin", "t-1");
    private static final JournalTextos.Reglages REGLAGES = JournalTextos.Reglages.de(Map.of());

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotJournalTextos textos;
    private DepotConsommation consommation;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, planCode) VALUES ('t-1', 'CSS des Chenes', 'enterprise'), ('t-2', 'Ecole Calme', 'starter')").update();
        DepotSoutien soutien = new DepotSoutien(jdbc, HORLOGE);
        textos = new DepotJournalTextos(jdbc, HORLOGE, new Textos.SmsSimule(List.of(), () -> "2026-09-23T12:00:00.000Z", "+15145550000"), REGLAGES);
        consommation = new DepotConsommation(jdbc, REGLAGES, Consommation.ReglagesCourriel.de(Map.of()));
        http = new ClientHttpEssai(new ControleurSaas(new DepotQuotas(jdbc), new DepotMaintenance(jdbc, soutien, HORLOGE),
                new DepotLocataires(jdbc, HORLOGE, new RegistrePays(), "ca-central-1"), new GardeAcces(new RegistrePolitiqueAcces()),
                new DepotDestruction(jdbc, HORLOGE), new JournalAudit(jdbc), consommation, textos, HORLOGE,
                new ca.vina.nexu.saas.infrastructure.persistance.DepotVuesMasquees(jdbc, HORLOGE)));
    }

    /** Un courriel déjà parti, inscrit directement : le journal est alimenté par le mailer du Node. */
    private void courriel(String tenantId, String contexte, String statut) {
        jdbc.sql("INSERT INTO courriel_message (tenant_id, destinataire, contexte, statut, fournisseur, created_at) VALUES (?, ?, ?, ?, 'smtp', ?)")
                .params(tenantId, "m••••@cssdeschenes.gouv.qc.ca", contexte, statut, "2026-09-23T12:00:00.000Z").update();
    }

    @Test
    @DisplayName("le quota compte les segments, pas les messages — et refuse le message entier s'il ne tient pas")
    void segmentsEtRefus() {
        textos.reglerQuota("t-1", 100);
        // « ça » en minuscule suffit à faire passer le message en UCS-2 : un message, plusieurs segments.
        String long_ = "ça commence à 8 h précises, merci de confirmer votre présence auprès du secrétariat".repeat(2);
        JournalTextos.Texto t = textos.envoyer(new JournalTextos.Demande("t-1", "+15145550101", long_, null, "scolaire:recherche"));
        assertThat(t.echoue()).isFalse();
        assertThat(t.segments()).isGreaterThan(1);

        JournalTextos.Quota q = textos.quotaDe("t-1");
        assertThat(q.messages()).isEqualTo(1);
        assertThat(q.utilises()).isEqualTo(t.segments());
        assertThat(q.utilises()).isGreaterThan(q.messages());

        // Deux segments restants ne laissent pas passer un message de quatre.
        textos.reglerQuota("t-2", 2);
        JournalTextos.Texto refuse = textos.envoyer(new JournalTextos.Demande("t-2", "+15145550102", "ê".repeat(200), null, null));
        assertThat(refuse.echoue()).isTrue();
        assertThat(refuse.erreur()).contains("0/2 segments").contains("« ê »").contains("UCS-2");
        assertThat(textos.quotaDe("t-2").utilises()).isZero();
    }

    @Test
    @DisplayName("la vue inter-organismes : chacun figure, même à zéro ; le total porte les segments et les courriels")
    void vueDuMois() {
        textos.envoyer(new JournalTextos.Demande("t-1", "+15145550101", "Bonjour", null, "scolaire:recherche"));
        courriel("t-1", "acces:remise", "envoye");
        courriel("t-1", "acces:remise", "echoue");

        Consommation.Vue v = consommation.duMois(MOIS);
        Map<String, Object> vue = v.vue();
        assertThat(v.organismes()).extracting(Consommation.Organisme::tenantId).contains("t-1", "t-2");
        // Un zéro se lit « rien n'est parti » ; une ligne absente se lirait « je n'ai pas regardé ».
        assertThat(v.organismes().stream().filter(o -> o.tenantId().equals("t-2")).findFirst().orElseThrow().textos().utilises()).isZero();
        @SuppressWarnings("unchecked")
        Map<String, Object> totaux = (Map<String, Object>) vue.get("totaux");
        assertThat(totaux).containsEntry("textosSegments", 1).containsEntry("courriels", 1);
        // L'échec ne compte pas comme envoyé, mais il est dénombré à part.
        assertThat(v.organismes().stream().filter(o -> o.tenantId().equals("t-1")).findFirst().orElseThrow().courriels().echecs()).isEqualTo(1);
    }

    @Test
    @DisplayName("ce qui n'appartient à aucun organisme vivant figure sans plafond — sinon le total ne colle pas à la facture")
    void horsOrganisme() {
        courriel(null, "soutien:ouverture", "envoye");
        courriel("t-disparu", "acces:remise", "envoye");

        Consommation.Vue v = consommation.duMois(MOIS);
        Consommation.Organisme hors = v.organismes().stream().filter(o -> o.tenantId().equals(Consommation.NON_ATTRIBUE)).findFirst().orElseThrow();
        assertThat(hors.nom()).isEqualTo(Consommation.NOM_NON_ATTRIBUE);
        assertThat(hors.courriels().illimite()).isTrue();
        assertThat(hors.courriels().depassement()).isZero();

        // Un organisme résilié dont les lignes restent : sans lui, sa consommation s'évaporait du total.
        Consommation.Organisme retire = v.organismes().stream().filter(o -> o.tenantId().equals("t-disparu")).findFirst().orElseThrow();
        assertThat(retire.nom()).isEqualTo("Organisme retiré (t-disparu)");
        @SuppressWarnings("unchecked")
        Map<String, Object> totaux = (Map<String, Object>) v.vue().get("totaux");
        assertThat(totaux).containsEntry("courriels", 2);
    }

    @Test
    @DisplayName("le dépassement est ce que l'exploitant facture ; un plafond à zéro reste illimité")
    void depassement() {
        assertThat(Consommation.Canal.de(3100, 1200, 2000, 0).depassement()).isEqualTo(1100);
        assertThat(Consommation.Canal.de(3100, 1200, 0, 0).depassement()).isZero();
        assertThat(Consommation.Canal.de(3100, 1200, 0, 0).illimite()).isTrue();
        assertThat(Consommation.Canal.de(10, 10, 2000, 3).vue()).containsEntry("echecs", 3).containsEntry("limite", 2000);
        assertThat(Consommation.Canal.de(10, 10, 5, 0).horsQuota().depassement()).isZero();
    }

    @Test
    @DisplayName("le plafond de courriels par défaut vient de l'environnement — la même variable que le Node")
    void reglagesCourriel() {
        // Écrit en dur, ce plafond aurait divergé du Node : un organisme aurait eu deux plafonds
        // différents selon la pile qui répond.
        assertThat(Consommation.ReglagesCourriel.de(Map.of()).limiteMensuelleParDefaut()).isZero();
        assertThat(Consommation.ReglagesCourriel.de(Map.of("COURRIEL_QUOTA_MENSUEL", "  ")).limiteMensuelleParDefaut()).isZero();
        assertThat(Consommation.ReglagesCourriel.de(Map.of("COURRIEL_QUOTA_MENSUEL", "40000")).limiteMensuelleParDefaut()).isEqualTo(40_000);
        // Une faute de frappe ou un nombre négatif vaut ILLIMITÉ, jamais un plafond inventé :
        // plafonner sur une valeur illisible couperait des avis sans que personne ne l'ait demandé.
        assertThat(Consommation.ReglagesCourriel.de(Map.of("COURRIEL_QUOTA_MENSUEL", "beaucoup")).limiteMensuelleParDefaut()).isZero();
        assertThat(Consommation.ReglagesCourriel.de(Map.of("COURRIEL_QUOTA_MENSUEL", "-5")).limiteMensuelleParDefaut()).isZero();

        // Et il s'applique vraiment : un organisme sans plafond propre hérite de celui de l'instance.
        DepotConsommation plafonne = new DepotConsommation(jdbc, REGLAGES, Consommation.ReglagesCourriel.de(Map.of("COURRIEL_QUOTA_MENSUEL", "500")));
        Consommation.Organisme o = plafonne.duMois(MOIS).organismes().stream()
                .filter(x -> x.tenantId().equals("t-1")).findFirst().orElseThrow();
        assertThat(o.courriels().limite()).isEqualTo(500);
        assertThat(o.courriels().illimite()).isFalse();
    }

    @Test
    @DisplayName("le mois se valide plutôt que de se deviner")
    void mois() {
        assertThat(Consommation.moisDemande(null, "2026-09-23T12:00:00.000Z")).isEqualTo(MOIS);
        assertThat(Consommation.moisDemande("  ", "2026-09-23T12:00:00.000Z")).isEqualTo(MOIS);
        assertThat(Consommation.moisDemande("2026-01", "2026-09-23T12:00:00.000Z")).isEqualTo("2026-01");
        for (String faux : new String[] {"2026-13", "2026-00", "2026-1", "septembre"}) {
            assertThatThrownBy(() -> Consommation.moisDemande(faux, "2026-09-23T12:00:00.000Z"))
                    .as(faux).isInstanceOf(IllegalArgumentException.class).hasMessage("mois attendu au format AAAA-MM");
        }
    }

    @Test
    @DisplayName("le détail par contexte explique la facture, textos et courriels confondus, du plus coûteux au moins")
    void detail() {
        textos.envoyer(new JournalTextos.Demande("t-1", "+15145550101", "ê".repeat(200), null, "scolaire:recherche"));
        courriel("t-1", "acces:remise", "envoye");
        List<Map<String, Object>> d = consommation.detailParContexte("t-1", MOIS);
        assertThat(d).hasSize(2);
        assertThat(d.get(0)).containsEntry("contexte", "scolaire:recherche").containsEntry("canal", "texto");
        assertThat((int) d.get(0).get("utilises")).isGreaterThan((int) d.get(1).get("utilises"));
        assertThat(d.get(1)).containsEntry("canal", "courriel").containsEntry("messages", 1);
        // Un envoi sans contexte est nommé plutôt que perdu.
        courriel("t-2", null, "envoye");
        assertThat(consommation.detailParContexte("t-2", MOIS).get(0)).containsEntry("contexte", "sans contexte");
    }

    @Test
    @DisplayName("un administrateur d'établissement n'y accède pas — il verrait les autres organismes")
    void autorisation() throws Exception {
        http.get("/api/saas/consommation", ADMIN_ETABLISSEMENT).andExpect(status().isForbidden());
        http.get("/api/saas/consommation/t-1", ADMIN_ETABLISSEMENT).andExpect(status().isForbidden());
        http.put("/api/saas/consommation/t-1/quotas", ADMIN_ETABLISSEMENT, "{\"textos\":10}").andExpect(status().isForbidden());
        http.get("/api/saas/consommation", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("le superadmin lit la consommation, règle les deux plafonds d'un seul appel, et ses refus sont nommés")
    void routes() throws Exception {
        textos.envoyer(new JournalTextos.Demande("t-1", "+15145550101", "Bonjour", null, "scolaire:recherche"));
        http.get("/api/saas/consommation", SUPERADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.mois").value(MOIS))
                .andExpect(jsonPath("$.totaux.textosSegments").value(1))
                .andExpect(jsonPath("$.organismes[?(@.tenantId=='t-1')].textos.messages").value(org.hamcrest.Matchers.contains(1)));

        http.put("/api/saas/consommation/t-1/quotas", SUPERADMIN, "{\"textos\":2500,\"courriels\":40000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.textos.limite").value(2500))
                .andExpect(jsonPath("$.courriels.limite").value(40000));
        assertThat(jdbc.sql("SELECT monthly_limit FROM courriel_quota WHERE tenant_id = 't-1'").query(Integer.class).single()).isEqualTo(40000);
        // Le geste est consigné : régler un plafond, c'est changer ce qu'un client peut faire.
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'saas.consommation.quotas'").query(Integer.class).single()).isEqualTo(1);

        http.get("/api/saas/consommation/t-1?mois=2026-09", SUPERADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.contextes[0].contexte").value("scolaire:recherche"));
        // Le « non attribué » se consulte, bien qu'il ne soit pas un organisme.
        http.get("/api/saas/consommation/" + Consommation.NON_ATTRIBUE, SUPERADMIN).andExpect(status().isOk());

        http.get("/api/saas/consommation?mois=2026-99", SUPERADMIN).andExpect(status().isBadRequest());
        http.get("/api/saas/consommation/inconnu", SUPERADMIN).andExpect(status().isNotFound());
        http.put("/api/saas/consommation/inconnu/quotas", SUPERADMIN, "{\"textos\":10}").andExpect(status().isNotFound());
        http.put("/api/saas/consommation/t-1/quotas", SUPERADMIN, "{}").andExpect(status().isBadRequest());
        http.put("/api/saas/consommation/t-1/quotas", SUPERADMIN, "{\"textos\":-1}").andExpect(status().isBadRequest());
    }

    /** Les deux piles doivent facturer le même chiffre : un écart changerait la facture à la bascule. */
    @Test
    @DisplayName("le découpage est identique à celui du Node — sinon la bascule changerait la facture")
    void memeDecoupageQueLeNode() {
        assertThat(Segments.compter("Vous devez être present demain. ".repeat(15).substring(0, 480))).isEqualTo(8);
        assertThat(Segments.compter("Vous devez etre present demain. ".repeat(15).substring(0, 480))).isEqualTo(4);
    }

}
