package ca.vina.nexu.partage.web;

import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Garde d'accès — tenant du jeton, rôles, capacités ; messages du Node")
class GardeAccesTest {

    private final GardeAcces garde = new GardeAcces(new RegistrePolitiqueAcces());

    private static MockHttpServletRequest requete(ContexteRequete c, String enTete) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        if (c != null) {
            r.setAttribute(ContexteRequete.ATTRIBUT, c);
        }
        if (enTete != null) {
            r.addHeader("X-Tenant-Id", enTete);
        }
        return r;
    }

    @SuppressWarnings("unchecked")
    private static String message(GardeAcces.Verdict v) {
        return ((Map<String, List<String>>) v.refus().getBody()).get("errors").getFirst();
    }

    @Test
    void sessionEtAdministrationDuSysteme() {
        var superadmin = new ContexteRequete("sa", "sa", "superadmin", null, null);
        var adminGlobal = new ContexteRequete("u", "a", "admin", null, null);
        var adminEtablissement = new ContexteRequete("u", "a", "admin", null, "t-1");
        assertThat(garde.exigerSession(requete(null, null)).refus().getStatusCode().value()).isEqualTo(401);
        assertThat(message(garde.exigerSession(requete(null, null)))).isEqualTo("unauthenticated");
        assertThat(garde.exigerSession(requete(adminEtablissement, null))).isEqualTo(new GardeAcces.Verdict("t-1", null));
        assertThat(garde.exigerSession(requete(superadmin, null))).isEqualTo(new GardeAcces.Verdict(null, null));
        assertThat(garde.exigerAdministrationSysteme(requete(null, null)).refus().getStatusCode().value()).isEqualTo(401);
        assertThat(garde.exigerAdministrationSysteme(requete(superadmin, null)).refuse()).isFalse();
        assertThat(garde.exigerAdministrationSysteme(requete(adminGlobal, null)).refuse()).isFalse();
        var refus = garde.exigerAdministrationSysteme(requete(adminEtablissement, null));
        assertThat(refus.refus().getStatusCode().value()).isEqualTo(403);
        assertThat(message(refus)).isEqualTo("réservé à l'administration du système");
        assertThat(garde.exigerAdministrationSysteme(requete(new ContexteRequete("u", "r", "rh", null, null), null)).refuse()).isTrue();
    }

    @Test
    void tenant() {
        var admin = new ContexteRequete("u", "a", "admin", null, "t-1");
        assertThat(garde.exigerTenant(requete(admin, null))).isEqualTo(new GardeAcces.Verdict("t-1", null));
        var discordance = garde.exigerTenant(requete(admin, "t-2"));
        assertThat(discordance.refuse()).isTrue();
        assertThat(discordance.refus().getStatusCode().value()).isEqualTo(403);
        assertThat(message(discordance)).isEqualTo("tenant mismatch");
        var sansSession = garde.exigerTenant(requete(null, null));
        assertThat(sansSession.refus().getStatusCode().value()).isEqualTo(401);
        assertThat(message(sansSession)).isEqualTo("authentication required");
        var superadmin = new ContexteRequete("s", "s", "superadmin", null, null);
        var manquant = garde.exigerTenant(requete(superadmin, null));
        assertThat(manquant.refus().getStatusCode().value()).isEqualTo(400);
        assertThat(message(manquant)).isEqualTo("X-Tenant-Id header required");
        assertThat(garde.exigerTenant(requete(superadmin, "t-9")).tenantId()).isEqualTo("t-9");
    }

    @Test
    void rolesEtCapacites() {
        var employe = new ContexteRequete("u", "e", "employee", "emp", "t-1");
        var rh = new ContexteRequete("u", "r", "rh", null, "t-1");
        var superadmin = new ContexteRequete("s", "s", "superadmin", null, null);
        assertThat(garde.exigerRole(requete(rh, null), "admin", "rh").refuse()).isFalse();
        var refus = garde.exigerRole(requete(employe, null), "admin", "rh");
        assertThat(refus.refus().getStatusCode().value()).isEqualTo(403);
        assertThat(message(refus)).isEqualTo("forbidden");
        assertThat(garde.exigerRole(requete(superadmin, "t-1"), "admin").tenantId()).isEqualTo("t-1");
        assertThat(garde.exigerRole(requete(null, null), "admin").refus().getStatusCode().value()).isEqualTo(401);

        assertThat(garde.exigerCapacite(requete(rh, null), "employee.create", Map.of()).refuse()).isFalse();
        var sansDroit = garde.exigerCapacite(requete(employe, null), "employee.create", Map.of());
        assertThat(message(sansDroit)).isEqualTo("droit requis : Créer un employé");
        var inconnue = garde.exigerCapacite(requete(employe, null), "lune.marcher", Map.of());
        assertThat(message(inconnue)).isEqualTo("droit requis : lune.marcher");
        assertThat(garde.exigerCapacite(requete(null, null), "employee.create", Map.of()).refus().getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void lesSurchargesDeLEtablissementDuJetonSAppliquent() {
        var employe = new ContexteRequete("u", "e", "employee", "emp", "t-1");
        // L'établissement t-1 a accordé la création d'employé aux employés ; t-2 non.
        GardeAcces avecSurcharges = new GardeAcces(new RegistrePolitiqueAcces(),
                (tenantId, role) -> "t-1".equals(tenantId) && "employee".equals(role) ? Map.of("employee.create", true) : Map.of());
        assertThat(avecSurcharges.exigerCapacite(requete(employe, null), "employee.create").refuse()).isFalse();
        var autre = new ContexteRequete("u", "e", "employee", "emp", "t-2");
        var refus = avecSurcharges.exigerCapacite(requete(autre, null), "employee.create");
        assertThat(refus.refus().getStatusCode().value()).isEqualTo(403);
        assertThat(((Map<?, ?>) refus.refus().getBody()).get("capability")).isEqualTo("employee.create");
        assertThat(avecSurcharges.exigerCapacite(requete(null, null), "employee.create").refus().getStatusCode().value()).isEqualTo(401);
    }
}
