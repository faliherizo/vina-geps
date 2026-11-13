package ca.vina.nexu.saas.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.notifications.domaine.Consommation;
import ca.vina.nexu.notifications.domaine.JournalTextos;
import ca.vina.nexu.notifications.domaine.Textos;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotConsommation;
import ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import ca.vina.nexu.saas.domaine.CatalogueVues;
import ca.vina.nexu.saas.infrastructure.persistance.DepotDestruction;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotMaintenance;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
import ca.vina.nexu.saas.infrastructure.persistance.DepotVuesMasquees;
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
 * {@code /api/saas/vues} et {@code /api/saas/tenants/{id}/vues} — le réglage des sous-menus.
 *
 * <p>Jumeau de {@code backend/tests/unit/vues-masquees.test.ts}. Ce qui est éprouvé :
 *
 * <ul>
 *   <li>c'est une <b>liste de masquage</b>, jamais d'autorisation : un organisme sans ligne voit
 *       tout. L'inverse aurait vidé le menu de tous les organismes le jour du déploiement ;</li>
 *   <li>une clé inconnue est <b>refusée</b>, jamais absorbée : enregistrée sans bruit, elle serait
 *       un menu qu'on croit masqué et qui reste visible ;</li>
 *   <li>la liste voyage avec les modules ({@code /my-modules}), parce que l'application lit les
 *       deux au même moment ;</li>
 *   <li>le réglage est <b>inter-organismes</b> : un administrateur d'établissement n'y touche pas.</li>
 * </ul>
 */
@DisplayName("/api/saas/vues — sous-menus masqués par organisme")
class ControleurVuesMasqueesHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-24T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete SUPERADMIN = ClientHttpEssai.superadmin();
    private static final ContexteRequete ADMIN_ETABLISSEMENT = ClientHttpEssai.session("admin", "t-1");
    private static final JournalTextos.Reglages REGLAGES = JournalTextos.Reglages.de(Map.of());

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotVuesMasquees depot;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, planCode) VALUES ('t-1', 'CSS des Chenes', 'enterprise'), ('t-2', 'Ecole Calme', 'starter')").update();
        depot = new DepotVuesMasquees(jdbc, HORLOGE);
        DepotSoutien soutien = new DepotSoutien(jdbc, HORLOGE);
        http = new ClientHttpEssai(new ControleurSaas(new DepotQuotas(jdbc), new DepotMaintenance(jdbc, soutien, HORLOGE),
                new DepotLocataires(jdbc, HORLOGE, new RegistrePays(), "ca-central-1"), new GardeAcces(new RegistrePolitiqueAcces()),
                new DepotDestruction(jdbc, HORLOGE), new JournalAudit(jdbc),
                new DepotConsommation(jdbc, REGLAGES, Consommation.ReglagesCourriel.de(Map.of())),
                new DepotJournalTextos(jdbc, HORLOGE, new Textos.SmsSimule(List.of(), () -> "2026-09-24T12:00:00.000Z", "+15145550000"), REGLAGES),
                HORLOGE, depot));
    }

    @Test
    @DisplayName("rien de masqué par défaut ; la liste se remplace d'un geste, sans doublon, isolée par organisme")
    void magasin() {
        // Un organisme sans ligne voit tout ce que ses modules lui ouvrent : c'est le
        // comportement d'avant cette table, et il le reste.
        assertThat(depot.masquees("t-1")).isEmpty();

        assertThat(depot.regler("t-1", List.of("usjournal", "usretention", "usjournal"))).containsExactly("usjournal", "usretention");
        assertThat(depot.masquees("t-2")).isEmpty();

        // Remplacer, jamais cumuler : deux superadmins ne se laissent pas une liste mi-cuite.
        assertThat(depot.regler("t-1", List.of("usretention"))).containsExactly("usretention");
        assertThat(depot.regler("t-1", List.of())).isEmpty();

        // Les onglets se masquent comme les pages — c'est le cran fin qui motive la fonction.
        assertThat(depot.regler("t-1", List.of("access:qui", "access:octrois")))
                .containsExactly("access:octrois", "access:qui");
    }

    @Test
    @DisplayName("une clé inconnue lève en la nommant, et rien n'est écrit — un refus partiel laisserait un état non demandé")
    void cleInconnue() {
        assertThatThrownBy(() -> depot.regler("t-1", List.of("usjournal", "inventé")))
                .isInstanceOf(DepotVuesMasquees.ClesInconnues.class)
                .hasMessageContaining("inventé");
        try {
            depot.regler("t-1", List.of("usjournal", "inventé", "aussi-faux"));
        } catch (DepotVuesMasquees.ClesInconnues e) {
            assertThat(e.cles()).containsExactly("inventé", "aussi-faux");
        }
        assertThat(depot.masquees("t-1")).isEmpty();
    }

    @Test
    @DisplayName("le catalogue dit les sections et les vues — l'écran n'invente rien")
    void catalogue() throws Exception {
        http.get("/api/saas/vues", SUPERADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.vues.length()").value(CatalogueVues.VUES.size()))
                .andExpect(jsonPath("$.sections.length()").value(CatalogueVues.SECTIONS.size()))
                .andExpect(jsonPath("$.vues[0].cle").exists())
                .andExpect(jsonPath("$.vues[0].section").exists())
                .andExpect(jsonPath("$.vues[0].page").exists());

        // Les vues de présentation portent bien tous les champs — un `null` y est une valeur, pas un oubli.
        Map<String, Object> section = CatalogueVues.SECTIONS.get(0).vue();
        assertThat(section).containsKeys("cle", "libelle", "module");
        Map<String, Object> vue = CatalogueVues.VUES.get(0).vue();
        assertThat(vue).containsKeys("cle", "section", "notion", "page", "onglet", "i18nKey", "module");
        // Et les accesseurs du disque sont bien ceux qu'attend l'écran.
        CatalogueVues.Vue qui = CatalogueVues.VUES.stream().filter(v -> "access:qui".equals(v.cle())).findFirst().orElseThrow();
        assertThat(qui.page()).isEqualTo("access");
        assertThat(qui.onglet()).isEqualTo("qui");
        assertThat(qui.section()).isEqualTo("admin");
        assertThat(qui.notion()).isEqualTo("access");
        assertThat(qui.i18nKey()).isEqualTo("vue.access.qui");
        assertThat(qui.module()).isNull();
        CatalogueVues.Section us = CatalogueVues.SECTIONS.stream().filter(s -> "urgencessante".equals(s.cle())).findFirst().orElseThrow();
        assertThat(us.libelle()).isEqualTo("Urgences-santé");
        assertThat(us.module()).isEqualTo("effectifs");
    }

    @Test
    @DisplayName("le superadmin pose et relit la liste ; l'organisme inconnu est refusé")
    void routes() throws Exception {
        http.get("/api/saas/tenants/t-1/vues", SUPERADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t-1"))
                .andExpect(jsonPath("$.masquees.length()").value(0));

        http.put("/api/saas/tenants/t-1/vues", SUPERADMIN, "{\"masquees\":[\"usjournal\",\"access:qui\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.masquees[0]").value("access:qui"))
                .andExpect(jsonPath("$.masquees[1]").value("usjournal"));
        http.get("/api/saas/tenants/t-1/vues", SUPERADMIN).andExpect(jsonPath("$.masquees.length()").value(2));

        // Le geste est consigné : régler un menu, c'est changer ce qu'un client voit.
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = 'saas.tenant.vues'").query(Integer.class).single()).isEqualTo(1);

        http.get("/api/saas/tenants/inconnu/vues", SUPERADMIN).andExpect(status().isNotFound());
        http.put("/api/saas/tenants/inconnu/vues", SUPERADMIN, "{\"masquees\":[]}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("refuse un corps mal formé et une clé inconnue, en la nommant ; le refus est entier")
    void refus() throws Exception {
        http.put("/api/saas/tenants/t-1/vues", SUPERADMIN, "{\"masquees\":\"usjournal\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("masquees doit être un tableau"));
        http.put("/api/saas/tenants/t-1/vues", SUPERADMIN, "{}").andExpect(status().isBadRequest());

        http.put("/api/saas/tenants/t-1/vues", SUPERADMIN, "{\"masquees\":[\"usjournal\",\"inventé\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("vues inconnues : inventé"));
        // Rien n'est posé, pas même la clé valide de la même demande.
        assertThat(depot.masquees("t-1")).isEmpty();
    }

    @Test
    @DisplayName("l'application lit la liste avec ses modules — un seul aller-retour au chargement")
    void avecLesModules() throws Exception {
        depot.regler("t-1", List.of("usjournal"));
        http.get("/api/saas/my-modules", ADMIN_ETABLISSEMENT).andExpect(status().isOk())
                .andExpect(jsonPath("$.vuesMasquees[0]").value("usjournal"));
    }

    @Test
    @DisplayName("un administrateur d'établissement ne règle pas les menus — ni les siens, ni ceux des autres")
    void autorisation() throws Exception {
        http.get("/api/saas/vues", ADMIN_ETABLISSEMENT).andExpect(status().isForbidden());
        http.get("/api/saas/tenants/t-1/vues", ADMIN_ETABLISSEMENT).andExpect(status().isForbidden());
        http.put("/api/saas/tenants/t-1/vues", ADMIN_ETABLISSEMENT, "{\"masquees\":[]}").andExpect(status().isForbidden());
        http.get("/api/saas/vues", null).andExpect(status().isUnauthorized());
    }
}
