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
                        "msg_acheminement_unites")
                // La paie, la tarification et la facturation SaaS ne sont pas de ce produit (D3).
                .doesNotContain("varpay_pay_rules", "pricing_plans", "billing_invoices", "operator_company");
        liquibase(ds).afterPropertiesSet();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM DATABASECHANGELOG").query(Integer.class).single()).isEqualTo(3);
    }

    @Test
    @DisplayName("sur une base reprise (la table des comptes existe), le schéma initial est constaté, et seules les évolutions s'appliquent")
    void baseReprise(@TempDir Path dossier) throws Exception {
        SingleConnectionDataSource ds = new SingleConnectionDataSource("jdbc:sqlite:" + dossier.resolve("reprise.db"), true);
        JdbcClient jdbc = JdbcClient.create(ds);
        jdbc.sql("CREATE TABLE users (id TEXT PRIMARY KEY)").update();
        liquibase(ds).afterPropertiesSet();
        assertThat(tables(jdbc)).containsExactlyInAnyOrder("users", "msg_acheminement_unites");
        assertThat(jdbc.sql("SELECT EXECTYPE FROM DATABASECHANGELOG ORDER BY ORDEREXECUTED").query(String.class).list()).containsExactly("MARK_RAN", "EXECUTED", "EXECUTED");
    }
}
