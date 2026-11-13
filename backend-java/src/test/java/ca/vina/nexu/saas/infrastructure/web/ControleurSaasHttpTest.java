package ca.vina.nexu.saas.infrastructure.web;

import ca.vina.nexu.saas.infrastructure.persistance.DepotDestruction;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.saas.infrastructure.persistance.DepotMaintenance;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.pays.infrastructure.persistance.RegistrePays;
import org.springframework.test.web.servlet.MvcResult;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.domaine.CatalogueModules;
import ca.vina.nexu.saas.domaine.Destruction;
import java.util.List;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Port de `modules-rh.integration` et `modules-nouveaux.integration` : catalogue, modules courants, réglage superadmin. */
@DisplayName("API SaaS — catalogue et modules par établissement")
class ControleurSaasHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete SUPERADMIN = ClientHttpEssai.superadmin();
    private static final ContexteRequete ADMIN_GLOBAL = new ContexteRequete("usr-g", "g", "admin", null, null);
    private ClientHttpEssai http;
    private DepotSoutien soutien;
    private final HorlogeEssai horloge = new HorlogeEssai();
    /** Réglages de textos par défaut : le fournisseur est simulé, la limite est celle de l'instance. */
    private static final ca.vina.nexu.notifications.domaine.JournalTextos.Reglages REGLAGES_TEXTOS =
            ca.vina.nexu.notifications.domaine.JournalTextos.Reglages.de(java.util.Map.of());

    /** Une horloge que l'essai avance. */
    static final class HorlogeEssai extends Clock {
        Instant instant = Instant.parse("2026-09-12T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, planCode) VALUES ('t-1', 'CSS', 'enterprise'), ('t-2', 'Sans quota', 'starter')").update();
        jdbc.sql("INSERT INTO tenant_quotas VALUES ('t-1', 10, 10, '[\"approvals\",\"effectifs\",\"effectifs_regles\",\"effectifs_communication\"]')").update();
        soutien = new DepotSoutien(jdbc, horloge);
        this.jdbc = jdbc;
        http = new ClientHttpEssai(new ControleurSaas(new DepotQuotas(jdbc), new DepotMaintenance(jdbc, soutien, horloge),
                new DepotLocataires(jdbc, horloge, new RegistrePays(), "ca-central-1"), new GardeAcces(new RegistrePolitiqueAcces()),
                new DepotDestruction(jdbc, horloge), new ca.vina.nexu.partage.audit.infrastructure.JournalAudit(jdbc),
                new ca.vina.nexu.notifications.infrastructure.persistance.DepotConsommation(jdbc, REGLAGES_TEXTOS,
                        ca.vina.nexu.notifications.domaine.Consommation.ReglagesCourriel.de(java.util.Map.of())),
                new ca.vina.nexu.notifications.infrastructure.persistance.DepotJournalTextos(jdbc, horloge,
                        new ca.vina.nexu.notifications.domaine.Textos.SmsSimule(java.util.List.of(), () -> "2026-09-23T12:00:00.000Z", "+15145550000"), REGLAGES_TEXTOS),
                horloge,
                new ca.vina.nexu.saas.infrastructure.persistance.DepotVuesMasquees(jdbc, horloge)));
    }

    private JdbcClient jdbc;

    private static String champ(String json, String nom) {
        return json.replaceAll("^.*?\"" + nom + "\":\"([^\"]+)\".*$", "$1");
    }

    @Test
    void unLocataireSeCreeAvecSonQuotaSansRegleDePaie() throws Exception {
        ContexteRequete sa = ClientHttpEssai.superadmin();
        http.post("/api/saas/tenants", ClientHttpEssai.session("admin", "t-1"), "{}").andExpect(status().isForbidden());
        http.post("/api/saas/tenants", sa, "{\"name\":\" \",\"type\":\"club\",\"country\":\"Canada\",\"planCode\":\"or\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors", hasSize(4)))
                .andExpect(jsonPath("$.errors[0]").value("le nom de l'établissement est requis"));
        http.post("/api/saas/tenants", sa, "{\"name\":\"X\",\"type\":\"corporate\",\"country\":\"ZZ\",\"planCode\":\"starter\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]", org.hamcrest.Matchers.startsWith("pays « ZZ » non pris en charge (disponibles : ")));
        MvcResult r = http.post("/api/saas/tenants", sa, "{\"name\":\"CSS des Chênes\",\"type\":\"public_institution\",\"country\":\"ca\",\"region\":\"QC\","
                        + "\"planCode\":\"enterprise\",\"trialDays\":10,\"slug\":\"CSS Chênes\",\"email\":\"a@b.qc\",\"phone\":\"418 555-0100\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("trial")).andExpect(jsonPath("$.country").value("CA"))
                .andExpect(jsonPath("$.trialEndsAt").value("2026-09-22T10:00:00.000Z")).andExpect(jsonPath("$.subscriptionEndsAt").isEmpty())
                .andExpect(jsonPath("$.slug").value("css-ch-nes")).andExpect(jsonPath("$.dataResidency").value("ca-central-1")).andReturn();
        String corps = r.getResponse().getContentAsString();
        assertThat(corps).startsWith("{\"id\":\"");
        String id = champ(corps, "id");
        // La paie n'appartient pas à ce produit : la création ne sème rien hors du quota.
        assertThat(jdbc.sql("SELECT COUNT(*) FROM sqlite_master WHERE name = 'varpay_pay_rules'").query(Integer.class).single()).isZero();
        http.get("/api/saas/tenants/" + id, sa).andExpect(jsonPath("$.quota.maxTeachers").value(-1))
                .andExpect(jsonPath("$.quota.modules", org.hamcrest.Matchers.hasItems("effectifs", "effectifs_temps", "integrations", "approvals")));
        // Le slug est unique : 409, pas 400.
        http.post("/api/saas/tenants", sa, "{\"name\":\"Autre\",\"type\":\"corporate\",\"country\":\"CA\",\"planCode\":\"starter\",\"slug\":\"css-ch-nes\"}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("le slug d'URL « css-ch-nes » est déjà utilisé par un autre établissement"));
        http.get("/api/saas/tenants", sa).andExpect(jsonPath("$", hasSize(3))).andExpect(jsonPath("$[0].id").value(id));
        http.get("/api/saas/tenants", ClientHttpEssai.session("rh", "t-1")).andExpect(status().isForbidden());
    }

    @Test
    void cycleDeVieVerrouDuPaysEtUsage() throws Exception {
        ContexteRequete sa = ClientHttpEssai.superadmin();
        ContexteRequete adminSysteme = new ContexteRequete("usr-admin", "admin", "admin", null, null);
        http.get("/api/saas/tenants/absent", sa).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("tenant not found"));
        http.get("/api/saas/tenants/t-2", sa).andExpect(jsonPath("$.quota").isEmpty()).andExpect(jsonPath("$.name").value("Sans quota"));

        http.post("/api/saas/tenants/t-1/activate", adminSysteme, "{\"subscriptionMonths\":2}").andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.subscriptionEndsAt").value("2026-11-11T10:00:00.000Z"));
        http.post("/api/saas/tenants/t-1/activate", adminSysteme, null).andExpect(jsonPath("$.subscriptionEndsAt").value("2027-09-07T10:00:00.000Z"));
        http.post("/api/saas/tenants/t-1/suspend", adminSysteme, null).andExpect(jsonPath("$.status").value("suspended"));
        http.post("/api/saas/tenants/t-1/terminate", adminSysteme, null).andExpect(jsonPath("$.status").value("terminated"));
        http.post("/api/saas/tenants/absent/suspend", adminSysteme, null).andExpect(status().isNotFound());
        http.post("/api/saas/tenants/absent/activate", adminSysteme, null).andExpect(status().isNotFound());
        http.post("/api/saas/tenants/absent/terminate", adminSysteme, null).andExpect(status().isNotFound());

        // Le pays : réservé au superadmin, deux lettres, et verrouillé dès la première transaction.
        http.patch("/api/saas/tenants/t-1/country", adminSysteme, "{\"country\":\"FR\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("réservé au superadmin"));
        http.patch("/api/saas/tenants/t-1/country", ClientHttpEssai.session("rh", "t-1"), "{}").andExpect(status().isForbidden());
        http.patch("/api/saas/tenants/t-1/country", sa, "{\"country\":\"FRA\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("country must be 2-letter ISO code"));
        http.patch("/api/saas/tenants/absent/country", sa, "{\"country\":\"FR\"}").andExpect(status().isNotFound());
        http.get("/api/saas/tenants/t-1/country-lock", sa).andExpect(jsonPath("$.country").value("CA")).andExpect(jsonPath("$.locked").value(false));
        http.patch("/api/saas/tenants/t-1/country", sa, "{\"country\":\"fr\"}").andExpect(jsonPath("$.country").value("FR"));
        jdbc.sql("INSERT INTO client_invoices (id, tenantId) VALUES ('f-1', 't-1')").update();
        http.get("/api/saas/tenants/t-1/country-lock", sa).andExpect(jsonPath("$.locked").value(true));
        http.patch("/api/saas/tenants/t-1/country", sa, "{\"country\":\"CA\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]", org.hamcrest.Matchers.startsWith("le pays/la devise ne peut plus être modifié")));
        http.get("/api/saas/tenants/absent/country-lock", sa).andExpect(status().isNotFound());

        // L'usage : zéros sans ligne, le mois demandé sinon.
        http.get("/api/saas/tenants/t-1/usage", sa).andExpect(jsonPath("$.teachersCount").value(0)).andExpect(jsonPath("$.payCalcsCount").value(0));
        jdbc.sql("INSERT INTO tenant_usage (tenantId, month, teachersCount, contractsCount, payCalcsCount) VALUES ('t-1', '2026-08', 3, 4, 5)").update();
        http.get("/api/saas/tenants/t-1/usage?month=2026-08", sa).andExpect(jsonPath("$.contractsCount").value(4));
        http.get("/api/saas/tenants/absent/usage", sa).andExpect(status().isNotFound());
    }

    @Test
    void forfaitsSansTarification() throws Exception {
        http.get("/api/saas/plans", null).andExpect(status().isOk()).andExpect(jsonPath("$.plans[*].code", contains("starter", "professional", "enterprise", "sovereign")))
                .andExpect(jsonPath("$.plans[0].maxTeachers").value(50)).andExpect(jsonPath("$.plans[2].maxTeachers").value(-1))
                .andExpect(jsonPath("$.plans[2].modules", org.hamcrest.Matchers.hasItems("effectifs", "effectifs_temps", "effectifs_communication", "integrations", "confidentialite")))
                .andExpect(jsonPath("$.plans[3].modules", org.hamcrest.Matchers.hasItems("byok", "dedicated"))).andExpect(jsonPath("$.plans[0].modules", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("effectifs_temps"))));
        // La tarification commerciale n'appartient pas à ce produit (D3) : la route n'existe pas.
        http.get("/api/saas/pricing", ADMIN).andExpect(status().isNotFound());
    }

    @Test
    void banniereDeMaintenanceLecturePubliqueEcritureSuperadminFenetreEnCoursAutomatique() throws Exception {
        // sur une base que le Node n'a jamais semée (aucune ligne), la ligne unique naît au premier réglage
        http.get("/api/saas/maintenance", null).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("")).andExpect(jsonPath("$.level").value("info"))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.updatedAt").isEmpty()).andExpect(jsonPath("$.prochaine").isEmpty());
        http.put("/api/saas/maintenance", null, "{}").andExpect(status().isUnauthorized());
        http.put("/api/saas/maintenance", ADMIN, "{\"active\":true}").andExpect(status().isForbidden());
        http.put("/api/saas/maintenance", SUPERADMIN, "{\"active\":true}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("message required to activate the banner"));
        http.put("/api/saas/maintenance", SUPERADMIN, "{\"message\":\"Mise à jour ce soir\",\"level\":\"critical\",\"active\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Mise à jour ce soir")).andExpect(jsonPath("$.level").value("critical")).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.updatedAt").value("2026-09-12T10:00:00.000Z"));
        // Un niveau inconnu garde le précédent ; désactiver garde le message.
        http.put("/api/saas/maintenance", SUPERADMIN, "{\"level\":\"rouge\",\"active\":false}").andExpect(jsonPath("$.level").value("critical")).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.message").value("Mise à jour ce soir"));
        http.put("/api/saas/maintenance", SUPERADMIN, null).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        // Une fenêtre annoncée apparaît à tous ; en cours, elle allume la bannière d'elle-même, à l'heure de Québec.
        soutien.planifierMaintenance("usr-sa", Map.of("debut", "2026-09-16T04:00:00.000Z", "fin", "2026-09-16T06:00:00.000Z", "description", "Migration de la base de données"));
        http.get("/api/saas/maintenance", null).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.prochaine.debut").value("2026-09-16T04:00:00.000Z")).andExpect(jsonPath("$.prochaine.description").value("Migration de la base de données"));
        horloge.instant = Instant.parse("2026-09-16T05:00:00Z");
        http.get("/api/saas/maintenance", null).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.level").value("warning"))
                .andExpect(jsonPath("$.message").value("Maintenance en cours jusqu'à 16 septembre 2026 à 02 h 00 : Migration de la base de données")).andExpect(jsonPath("$.prochaine").isEmpty());
        // Le message explicite du superadmin prime sur la fenêtre en cours.
        http.put("/api/saas/maintenance", SUPERADMIN, "{\"message\":\"Avis du soutien\",\"active\":true}").andExpect(jsonPath("$.message").value("Avis du soutien")).andExpect(jsonPath("$.level").value("warning"));   // le niveau courant (calculé) est repris, comme au Node
        horloge.instant = Instant.parse("2026-09-16T07:00:00Z");
        http.put("/api/saas/maintenance", SUPERADMIN, "{\"active\":false}").andExpect(jsonPath("$.active").value(false));
        assertThat(DepotMaintenance.heureQuebec("2026-01-05T18:07:00Z")).isEqualTo("5 janvier 2026 à 13 h 07");
    }

    @Test
    void leCatalogueAnnonceLaFamilleLePrerequisEtLesDependantsDeChaqueModule() throws Exception {
        http.get("/api/saas/modules", null).andExpect(status().isUnauthorized());
        http.get("/api/saas/modules", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.familles", hasSize(CatalogueModules.FAMILLES.size()))).andExpect(jsonPath("$.familles[1].libelle").value("Santé — Urgences-santé (AO 26-015-DO)"))
                .andExpect(jsonPath("$.modules", hasSize(CatalogueModules.MODULES.size())))
                .andExpect(jsonPath("$.modules[?(@.code == 'effectifs_regles')].requiert", contains("effectifs"))).andExpect(jsonPath("$.modules[?(@.code == 'effectifs_regles')].famille", contains("effectifs")))
                .andExpect(jsonPath("$.modules[?(@.code == 'effectifs')].requiert", contains((Object) null))).andExpect(jsonPath("$.modules[?(@.code == 'effectifs')].dependants[*]", hasSize(2)))
                .andExpect(jsonPath("$.modules[?(@.code == 'effectifs_planification')].requiert", contains("effectifs_regles"))).andExpect(jsonPath("$.modules[?(@.code == 'effectifs_planification')].dependants[*]", hasSize(5)))
                .andExpect(jsonPath("$.modules[?(@.code == 'byok')].debloque", contains("Option d'hébergement — aucun écran ; se règle au déploiement")));
    }

    @Test
    void mesModulesPilotentLesMenusJamaisEnCache() throws Exception {
        http.get("/api/saas/my-modules", null).andExpect(status().isUnauthorized());
        http.get("/api/saas/my-modules", ADMIN).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
                .andExpect(jsonPath("$.modules", contains("approvals", "effectifs", "effectifs_regles", "effectifs_communication"))).andExpect(jsonPath("$.planCode").value("enterprise"));
        // Un compte global sans organisme choisi n'a aucun module ; un établissement sans quota non plus.
        http.get("/api/saas/my-modules", SUPERADMIN).andExpect(jsonPath("$.modules", hasSize(0))).andExpect(jsonPath("$.planCode").isEmpty());
        http.get("/api/saas/my-modules", ClientHttpEssai.session("admin", "t-2")).andExpect(jsonPath("$.modules", hasSize(0))).andExpect(jsonPath("$.planCode").value("starter"));
        http.get("/api/saas/my-modules", ClientHttpEssai.session("admin", "t-9")).andExpect(jsonPath("$.modules", hasSize(0))).andExpect(jsonPath("$.planCode").isEmpty());
    }

    @Test
    void reglerLesModulesEstReserveALAdministrationDuSystemeEtDitCeQuiAChange() throws Exception {
        http.put("/api/saas/tenants/t-1/modules", null, "{\"modules\":[]}").andExpect(status().isUnauthorized());
        http.put("/api/saas/tenants/t-1/modules", ADMIN, "{\"modules\":[]}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("réservé à l'administration du système"));
        http.put("/api/saas/tenants/t-1/modules", SUPERADMIN, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("modules must be an array"));
        http.put("/api/saas/tenants/t-1/modules", SUPERADMIN, "{\"modules\":\"effectifs\"}").andExpect(status().isBadRequest());
        http.put("/api/saas/tenants/t-1/modules", SUPERADMIN, "{\"modules\":[\"effectifs\",\"fantome\",\"autre\"]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("invalid modules: fantome, autre"));
        http.put("/api/saas/tenants/t-2/modules", SUPERADMIN, "{\"modules\":[\"effectifs\"]}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("tenant quota not found"));
        // Décocher le socle emporte ses enfants ; cocher un enfant rend son socle ; l'écran lit ce qui a changé.
        http.put("/api/saas/tenants/t-1/modules", ADMIN_GLOBAL, "{\"modules\":[\"approvals\",\"effectifs_regles\",\"effectifs_communication\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t-1")).andExpect(jsonPath("$.maxTeachers").value(10)).andExpect(jsonPath("$.modules", contains("approvals")))
                .andExpect(jsonPath("$.retires", contains("effectifs_regles", "effectifs_communication"))).andExpect(jsonPath("$.ajoutes", hasSize(0)));
        http.put("/api/saas/tenants/t-1/modules", SUPERADMIN, "{\"modules\":[\"approvals\",\"effectifs_communication\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.modules", contains("approvals", "effectifs", "effectifs_communication"))).andExpect(jsonPath("$.ajoutes", contains("effectifs")));
        http.get("/api/saas/my-modules", ADMIN).andExpect(jsonPath("$.modules", contains("approvals", "effectifs", "effectifs_communication")));
    }

    /** Contrat 9.25.06 / EFVP 7 — port de `saas-destruction.integration.test.ts` : verrous, destruction, attestation, une seule fois. */
    @Test
    void destructionAttesteeDesDonneesDUnEtablissementResilie() throws Exception {
        ContexteRequete sa = ClientHttpEssai.superadmin();
        String cible = champ(http.post("/api/saas/tenants", ADMIN_GLOBAL, "{\"name\":\"CSS Résilié\",\"type\":\"public_institution\",\"country\":\"CA\",\"region\":\"QC\",\"planCode\":\"enterprise\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
        String voisin = champ(http.post("/api/saas/tenants", ADMIN_GLOBAL, "{\"name\":\"CSS Voisin\",\"type\":\"public_institution\",\"country\":\"CA\",\"region\":\"QC\",\"planCode\":\"enterprise\"}").andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "id");
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId) VALUES ('e-cible','Anne','Cible',?), ('e-voisin','Anne','Voisine',?)").params(cible, voisin).update();
        // Avant résiliation : refus ; mauvais nom : refus nommé ; admin d'établissement : 403 ; inconnu : 404 ; pas d'attestation.
        http.post("/api/saas/tenants/" + cible + "/detruire-donnees", sa, "{\"confirmation\":\"CSS Résilié\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("l'établissement doit être résilié avant la destruction de ses données"));
        http.post("/api/saas/tenants/" + cible + "/terminate", ADMIN_GLOBAL, null).andExpect(status().isOk());
        http.post("/api/saas/tenants/" + cible + "/detruire-donnees", sa, "{\"confirmation\":\"autre\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("confirmation : retapez le nom exact de l'établissement"));
        http.post("/api/saas/tenants/" + cible + "/detruire-donnees", sa, null).andExpect(status().isBadRequest());
        http.post("/api/saas/tenants/" + cible + "/detruire-donnees", ClientHttpEssai.session("admin", "t-1"), "{\"confirmation\":\"CSS Résilié\"}").andExpect(status().isForbidden());
        http.post("/api/saas/tenants/inexistant/detruire-donnees", sa, "{\"confirmation\":\"x\"}").andExpect(status().isNotFound());
        http.get("/api/saas/tenants/" + cible + "/attestation-destruction", ADMIN_GLOBAL).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM tenant_quotas WHERE tenantId = ?").param(cible).query(Integer.class).single()).isEqualTo(1);

        MvcResult r = http.post("/api/saas/tenants/" + cible + "/detruire-donnees", sa, "{\"confirmation\":\"CSS Résilié\",\"motif\":\"fin de contrat 2026-PLAT-GA\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.tenantNom").value("CSS Résilié")).andExpect(jsonPath("$.totalLignes").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.tables[*].table", hasItems("employees", "tenant_quotas"))).andReturn();
        String empreinte = champ(r.getResponse().getContentAsString(), "empreinte");
        assertThat(empreinte).matches("[0-9a-f]{64}");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM employees WHERE tenantId = ?").param(cible).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM employees WHERE tenantId = ?").param(voisin).query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM tenant_quotas WHERE tenantId = ?").param(voisin).query(Integer.class).single()).isEqualTo(1);
        http.get("/api/saas/tenants/" + cible, ADMIN_GLOBAL).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("terminated"));
        http.get("/api/saas/tenants/" + cible + "/attestation-destruction", ADMIN_GLOBAL).andExpect(status().isOk()).andExpect(jsonPath("$.empreinte").value(empreinte));
        http.get("/api/saas/tenants/" + cible + "/attestation-destruction?format=texte", ADMIN_GLOBAL).andExpect(status().isOk())
                .andExpect(content().string(containsString("ATTESTATION DE DESTRUCTION DES DONNÉES"))).andExpect(content().string(containsString("fin de contrat 2026-PLAT-GA")));
        http.get("/api/saas/tenants/" + cible + "/attestation-destruction", ClientHttpEssai.session("rh", "t-1")).andExpect(status().isForbidden());
        // Une destruction ne se rejoue pas ; le journal d'audit garde la trace.
        http.post("/api/saas/tenants/" + cible + "/detruire-donnees", sa, "{\"confirmation\":\"CSS Résilié\"}").andExpect(status().isConflict());
        assertThat(jdbc.sql("SELECT target FROM audit_log WHERE action = 'saas.tenant.destruction'").query(String.class).single()).isEqualTo(cible);
        assertThat(Destruction.texte(new Destruction.Attestation("i", "t", "N", "d", "p", null, List.of(), 0, "e"))).contains("Motif : fin du contrat");
    }
}
