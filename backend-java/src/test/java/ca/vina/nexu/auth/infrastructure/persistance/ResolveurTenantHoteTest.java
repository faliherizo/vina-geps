package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.ModeResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResolveurTenantHote — le slug de l'hôte dans la table des établissements ; l'adresse publique par établissement (ADR 0033)")
class ResolveurTenantHoteTest {

    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, slug, country, status, createdAt) VALUES "
                + "('T1', 'CSSDC', 'cssdc', 'CA', 'active', 'x'), ('T2', 'Résiliée', 'fermee', 'CA', 'terminated', 'x')").update();
    }

    private static MockHttpServletRequest sur(String hote) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("X-Forwarded-Host", hote);
        return r;
    }

    @Test
    void sousDomaine() {
        ResolveurTenantHote resolveur = new ResolveurTenantHote(jdbc, "sous_domaine", "nexus.example", "", "https://commune/");
        assertThat(resolveur.configuration().mode()).isEqualTo(ModeResolution.SOUS_DOMAINE);
        assertThat(resolveur.de(sur("cssdc.nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Etablissement("T1", "CSSDC", "cssdc"));
        assertThat(resolveur.tenantDeLHote(sur("cssdc.nexus.example"))).isEqualTo("T1");
        assertThat(resolveur.de(sur("fermee.nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Inconnu("fermee"));
        assertThat(resolveur.de(sur("csspi.nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Inconnu("csspi"));
        assertThat(resolveur.de(sur("nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Global());
        assertThat(resolveur.de(sur("admin.nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Global());
        assertThat(resolveur.de(sur("localhost"))).isEqualTo(new HoteEtablissement.Resolution.HorsDomaine("localhost"));
        assertThat(resolveur.tenantDeLHote(sur("localhost"))).isNull();
        assertThat(resolveur.pour("T1")).isEqualTo("https://cssdc.nexus.example");
        assertThat(resolveur.pour(null)).isEqualTo("https://admin.nexus.example");
        assertThat(resolveur.pour("inconnu")).isEqualTo("https://admin.nexus.example");
    }

    @Test
    void selecteur() {
        ResolveurTenantHote resolveur = new ResolveurTenantHote(jdbc, "", "", "", "https://commune/");
        assertThat(resolveur.de(sur("cssdc.nexus.example"))).isEqualTo(new HoteEtablissement.Resolution.Selecteur());
        assertThat(resolveur.pour("T1")).isEqualTo("https://commune");
        assertThat(new ResolveurTenantHote(jdbc, "selecteur", "", "", null).pour("T1")).isEmpty();
        // La configuration se vérifie à la construction — donc au démarrage.
        assertThatThrownBy(() -> new ResolveurTenantHote(jdbc, "sous_domaine", "", "", "")).isInstanceOf(IllegalStateException.class);
    }
}
