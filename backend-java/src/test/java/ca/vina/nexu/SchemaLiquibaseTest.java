package ca.vina.nexu;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le schéma du produit, tel que Liquibase le crée : c'est la seule façon dont une base de production
 * naît ici. Les autres essais montent `SchemaEssai` ; celui-ci éprouve le changelog lui-même.
 */
@DisplayName("Schéma Liquibase — base vierge créée, base reprise constatée")
class SchemaLiquibaseTest {

    private static SpringLiquibase liquibase(SingleConnectionDataSource ds) {
        SpringLiquibase l = new SpringLiquibase();
        l.setDataSource(ds);
        l.setChangeLog("classpath:db/changelog/db.changelog-master.xml");
        l.setResourceLoader(new DefaultResourceLoader());
        return l;
    }

    private static List<String> tables(JdbcClient jdbc) {
        return jdbc.sql("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'DATABASECHANGELOG%' AND name NOT LIKE 'sqlite_%'")
                .query(String.class).list();
    }

    @Test
    @DisplayName("sur une base vierge, le changelog crée le socle et le module ; rejoué, il ne refait rien")
    void baseVierge(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("vierge.db"), true);
        liquibase(ds).afterPropertiesSet();
        JdbcClient jdbc = JdbcClient.create(ds);
        assertThat(tables(jdbc)).hasSizeGreaterThan(120)
                .contains("users", "tenants", "tenant_quotas", "eff_ressources", "eff_postes", "eff_absences", "msg_conversations", "msg_journal", "hab_objets", "soutien_engagements",
                        "msg_acheminement_unites", "msg_reactions", "msg_appels_groupe", "msg_distributions", "msg_message_versions",
                        "alr_reponses", "alr_modeles", "alr_modele_reponses", "alr_alertes", "alr_alerte_reponses", "alr_remises", "eff_temps_champs",
                        "alr_modele_versions", "alr_modele_version_reponses", "alr_modele_groupes", "alr_reglages",
                        "msg_groupes_transverses", "msg_groupe_membres", "paie_dossiers", "paie_reglages", "paie_cycles", "paie_bulletins",
                        "alr_politiques", "alr_politique_versions", "alr_cascades", "alr_cascade_traces", "alr_textos_entrants", "alr_appels", "users_mots_de_passe", "alr_notes")
                // La paie est le contexte `paie` (ADR 0076), et non la table de règles de Nexu VINA ; la tarification et la
                // facturation SaaS ne sont pas de ce produit (D3).
                .doesNotContain("varpay_pay_rules", "pricing_plans", "billing_invoices", "operator_company");
        liquibase(ds).afterPropertiesSet();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM DATABASECHANGELOG").query(Integer.class).single()).isEqualTo(22);
    }

    @Test
    @DisplayName("sur une base reprise (la table des comptes existe), le schéma initial est constaté, et seules les évolutions s'appliquent")
    void baseReprise(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("reprise.db"), true);
        JdbcClient jdbc = JdbcClient.create(ds);
        jdbc.sql("CREATE TABLE users (id TEXT PRIMARY KEY)").update();
        liquibase(ds).afterPropertiesSet();
        // Les évolutions qui créent une table s'appliquent ; celle qui modifie `msg_messages`, absente d'une reprise, est constatée.
        assertThat(tables(jdbc)).containsExactlyInAnyOrder("users", "msg_acheminement_unites", "msg_reactions", "msg_appels_groupe",
                "msg_appels_groupe_participants", "msg_distributions", "msg_message_versions",
                "alr_reponses", "alr_modeles", "alr_modele_reponses", "alr_alertes", "alr_alerte_reponses", "alr_remises", "eff_temps_champs",
                "alr_modele_versions", "alr_modele_version_reponses", "alr_modele_groupes", "alr_reglages",
                "msg_groupes_transverses", "msg_groupe_membres", "paie_dossiers", "paie_reglages", "paie_cycles", "paie_bulletins",
                "alr_politiques", "alr_politique_versions", "alr_cascades", "alr_cascade_traces", "alr_textos_entrants", "alr_appels", "users_mots_de_passe", "alr_notes");
        assertThat(jdbc.sql("SELECT EXECTYPE FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED").query(String.class).list())
                .containsExactly("MARK_RAN", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED", "MARK_RAN", "MARK_RAN", "EXECUTED", "EXECUTED", "MARK_RAN", "MARK_RAN", "EXECUTED", "EXECUTED", "MARK_RAN", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED", "EXECUTED");
    }

    @Test
    @DisplayName("0005 : les identifiants « login@@organisme » de la messagerie redeviennent des noms de connexion, sans doublon")
    void nomsDeConnexion(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("noms.db"), true);
        liquibase(ds).afterPropertiesSet();
        JdbcClient jdbc = JdbcClient.create(ds);
        // Un fil ouvert à l'écran avant le correctif : Marc y figure sous sa clé technique, Julie sous les deux formes.
        jdbc.sql("INSERT INTO msg_participants (id, tenantId, conversationId, personId, role, depuis) VALUES "
                 + "('p1', 'T', 'c1', 'marc.dube@@T', 'membre', 'x'), ('p2', 'T', 'c1', 'julie.roy', 'moderateur', 'x')").update();
        jdbc.sql("INSERT INTO msg_appels (id, tenantId, appelantId, appeleId, conversationId, etat, lanceLe) VALUES ('a1', 'T', 'julie.roy', 'marc.dube@@T', 'c1', 'sonne', 'x')").update();
        jdbc.sql("INSERT INTO msg_lectures (id, tenantId, messageId, personId, luLe) VALUES ('l1', 'T', 'm1', 'julie.roy', 'x'), ('l2', 'T', 'm1', 'julie.roy@@T', 'x')").update();
        jdbc.sql("DELETE FROM DATABASECHANGELOG WHERE ID = '0005-messagerie-noms-de-connexion'").update();
        liquibase(ds).afterPropertiesSet();
        assertThat(jdbc.sql("SELECT personId FROM msg_participants ORDER BY personId").query(String.class).list()).containsExactly("julie.roy", "marc.dube");
        assertThat(jdbc.sql("SELECT appeleId FROM msg_appels").query(String.class).single()).isEqualTo("marc.dube");
        // La lecture au nom technique doublait celle au bon nom : elle est retirée, pas dupliquée.
        assertThat(jdbc.sql("SELECT personId FROM msg_lectures").query(String.class).list()).containsExactly("julie.roy");
    }
}
