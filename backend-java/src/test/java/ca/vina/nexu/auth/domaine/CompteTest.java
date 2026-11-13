package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Compte — clé technique, nom de connexion, orphelin, forme publique")
class CompteTest {

    private static Compte compte(String tenantId, String employeeId, String loginName) {
        return new Compte("usr-1", Compte.nomStocke("alice", tenantId), loginName, tenantId, "$2a$08$x", "admin", employeeId,
                "2026-09-13T00:00:00.000Z", true, false, true, "a@x.qc.ca", "question", null, null);
    }

    @Test
    void laCleTechniqueEstSuffixeeDuTenant() {
        assertThat(Compte.nomStocke("alice", null)).isEqualTo("alice");
        assertThat(Compte.nomStocke("alice", "")).isEqualTo("alice");
        assertThat(Compte.nomStocke("alice", "t-1")).isEqualTo("alice@@t-1");
    }

    @Test
    void leNomDeConnexionRetombeSurLaCleTechniqueQuandIlManque() {
        assertThat(compte("t-1", null, null).nomDeConnexion()).isEqualTo("alice@@t-1");
        assertThat(compte("t-1", null, "alice").nomDeConnexion()).isEqualTo("alice");
    }

    @Test
    void orphelinSeulementSansTenantAvecUnEmployeDUnVraiEtablissement() {
        assertThat(compte("t-1", "emp-1", "alice").estOrphelin("t-9")).isFalse();
        assertThat(compte(null, null, "alice").estOrphelin("t-9")).isFalse();
        assertThat(compte(null, "emp-1", "alice").estOrphelin(null)).isFalse();
        assertThat(compte(null, "emp-1", "alice").estOrphelin("default")).isFalse();
        assertThat(compte(null, "emp-1", "alice").estOrphelin("t-9")).isTrue();
    }

    @Test
    void laVuePubliqueNeMontreNiEmpreinteNiSecret() {
        assertThat(compte("t-1", "emp-1", "alice").vue())
                .containsEntry("username", "alice").containsEntry("tenantId", "t-1").containsEntry("active", true)
                .containsEntry("mustChangePassword", false).containsEntry("securitySetupRequired", true)
                .containsEntry("securityMethod", "question").containsEntry("email", "a@x.qc.ca")
                .doesNotContainKeys("passwordHash", "totpSecret", "pendingTotpSecret");
    }
}
