package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.acces.infrastructure.persistance.DepotSurchargesAcces;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.domaine.RolesEtablissement;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rôles et surcharges d'accès <strong>par établissement</strong> — port de
 * {@code backend/src/auth/tenantRoles.ts} : les tables {@code tenant_roles} et
 * {@code tenant_role_capabilities}, appliquées par la décision d'accès du domaine.
 */
@Repository
public class DepotRolesEtablissement {

    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException() {
            super("role not found");
        }
    }

    private record Personnalise(String code, String label, boolean active) {
    }

    private final JdbcClient jdbc;
    private final RegistrePolitiqueAcces acces;
    private final Clock horloge;

    public DepotRolesEtablissement(JdbcClient jdbc, RegistrePolitiqueAcces acces, Clock horloge) {
        this.jdbc = jdbc;
        this.acces = acces;
        this.horloge = horloge;
    }

    public RegistrePolitiqueAcces politique() {
        return acces;
    }

    /** Capacité → autorisée ou refusée, telles que l'établissement les a décidées pour ce rôle. */
    public Map<String, Boolean> surcharges(String tenantId, String role) {
        return new DepotSurchargesAcces(jdbc).pour(tenantId, role);
    }

    /** Capacités effectives d'un rôle dans un établissement ({@code capabilitiesForRoleInTenant}). */
    public List<String> capacitesDe(String tenantId, String role) {
        return acces.decision().capacitesDe(role, surcharges(tenantId, role));
    }

    private List<Personnalise> personnalises(String tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return jdbc.sql("SELECT code, label, active FROM tenant_roles WHERE tenantId = ? ORDER BY label").params(tenantId)
                .query((rs, i) -> new Personnalise(rs.getString("code"), rs.getString("label"), rs.getInt("active") == 1)).list();
    }

    /** Rôles disponibles dans un établissement : les livrés, puis les siens ({@code listTenantRoles}). */
    public List<RolesEtablissement.Role> lister(String tenantId) {
        List<RolesEtablissement.Role> roles = new ArrayList<>();
        for (String code : RolesEtablissement.LIVRES) {
            roles.add(new RolesEtablissement.Role(code, RolesEtablissement.LIBELLES_LIVRES.get(code), true, true, capacitesDe(tenantId, code)));
        }
        for (Personnalise p : personnalises(tenantId)) {
            roles.add(new RolesEtablissement.Role(p.code(), p.label(), false, p.active(), capacitesDe(tenantId, p.code())));
        }
        return roles;
    }

    public RolesEtablissement.Role role(String tenantId, String code) {
        return lister(tenantId).stream().filter(r -> r.code().equals(code)).findFirst().orElseThrow(IntrouvableException::new);
    }

    /** Codes de rôle attribuables à un compte : les livrés, plus les rôles actifs de l'établissement. */
    public List<String> codesAttribuables(String tenantId) {
        return lister(tenantId).stream().filter(RolesEtablissement.Role::active).map(RolesEtablissement.Role::code).toList();
    }

    private boolean existe(String tenantId, String code) {
        return jdbc.sql("SELECT COUNT(*) FROM tenant_roles WHERE tenantId = ? AND code = ?").params(tenantId, code).query(Integer.class).single() > 0;
    }

    public RolesEtablissement.Role creer(String tenantId, String codeBrut, String labelBrut, List<String> capacites) {
        String code = RolesEtablissement.codeValide(codeBrut);
        String label = RolesEtablissement.libelleValide(labelBrut);
        if (existe(tenantId, code)) {
            throw new IllegalArgumentException("role code already exists");
        }
        // Les capacités sont vérifiées avant d'écrire : le Node insérait le rôle puis échouait sur
        // la première capacité inconnue, laissant un rôle fantôme sans droits. Même message, sans le fantôme.
        for (String c : capacites) {
            RolesEtablissement.verifierSurcharge(code, acces.politique().regle(c) != null, true);
        }
        jdbc.sql("INSERT INTO tenant_roles (tenantId, code, label, active, createdAt) VALUES (?, ?, ?, 1, ?)")
                .params(tenantId, code, label, Iso.de(horloge.instant())).update();
        for (String c : capacites) {
            definirCapacite(tenantId, code, c, true);
        }
        return role(tenantId, code);
    }

    public void definirCapacite(String tenantId, String role, String capacite, boolean autorisee) {
        RolesEtablissement.verifierSurcharge(role, acces.politique().regle(capacite) != null, autorisee);
        if (!RolesEtablissement.estLivre(role) && !existe(tenantId, role)) {
            throw new IllegalArgumentException("unknown role");
        }
        jdbc.sql("INSERT INTO tenant_role_capabilities (tenantId, role, capability, allowed) VALUES (?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, role, capability) DO UPDATE SET allowed = excluded.allowed")
                .params(tenantId, role, capacite, autorisee ? 1 : 0).update();
    }

    /** Renomme ou désactive un rôle personnalisé ; {@code null} = inchangé. */
    public RolesEtablissement.Role modifier(String tenantId, String code, String label, Boolean active) {
        RolesEtablissement.exigerPersonnalise(code, "renamed");
        if (!existe(tenantId, code)) {
            throw new IntrouvableException();
        }
        if (label != null) {
            jdbc.sql("UPDATE tenant_roles SET label = ? WHERE tenantId = ? AND code = ?")
                    .params(RolesEtablissement.libelleValide(label), tenantId, code).update();
        }
        if (active != null) {
            jdbc.sql("UPDATE tenant_roles SET active = ? WHERE tenantId = ? AND code = ?").params(active ? 1 : 0, tenantId, code).update();
        }
        return role(tenantId, code);
    }

    public void supprimer(String tenantId, String code) {
        RolesEtablissement.exigerPersonnalise(code, "deleted");
        int porteurs = jdbc.sql("SELECT COUNT(*) FROM users WHERE tenantId = ? AND role = ?").params(tenantId, code).query(Integer.class).single();
        RolesEtablissement.exigerSansPorteurs(porteurs);
        jdbc.sql("DELETE FROM tenant_role_capabilities WHERE tenantId = ? AND role = ?").params(tenantId, code).update();
        jdbc.sql("DELETE FROM tenant_roles WHERE tenantId = ? AND code = ?").params(tenantId, code).update();
    }
}
