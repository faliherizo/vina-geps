package ca.vina.nexu.superadmin.infrastructure.persistance;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Les administrateurs d'établissement ({@code tenant_admins}) — port de {@code superadmin/service.ts} :
 * un admin naît avec un compte {@code admin} de l'établissement, sécurité à configurer au premier
 * accès ; sa réinitialisation rend un mot de passe provisoire et l'envoie par courriel s'il en a un ;
 * on le désactive plutôt que de le supprimer, jamais le dernier actif.
 */
public class DepotAdminsEtablissement implements AdministrationSysteme.Administrateurs {


    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotComptes comptes;
    private final DepotLocataires locataires;
    private final JournalAudit audit;
    private final Courrier courrier;
    private final Supplier<String> motDePasseProvisoire;
    private final String urlPublique;

    public DepotAdminsEtablissement(JdbcClient jdbc, Clock horloge, DepotComptes comptes, DepotLocataires locataires, JournalAudit audit, Courrier courrier,
                                    Supplier<String> motDePasseProvisoire, String urlPublique) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.comptes = comptes;
        this.locataires = locataires;
        this.audit = audit;
        this.courrier = courrier;
        this.motDePasseProvisoire = motDePasseProvisoire;
        this.urlPublique = urlPublique;
    }

    public String motDePasseProvisoire() {
        return motDePasseProvisoire.get();
    }

    private static Map<String, Object> admin(ResultSet rs, int i) throws SQLException {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", rs.getString("id"));
        a.put("tenantId", rs.getString("tenantId"));
        a.put("userId", rs.getString("userId"));
        a.put("username", rs.getString("username"));
        a.put("createdAt", rs.getString("createdAt"));
        return a;
    }

    public Map<String, Object> creer(String tenantId, String username, String motDePasse, boolean changementImpose) {
        locataires.lire(tenantId).orElseThrow(() -> new IntrouvableException("tenant not found"));
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("username required");
        }
        AdministrationSysteme.verifierMotDePasseAdmin(motDePasse, changementImpose);
        if (comptes.parNom(username.trim(), tenantId).isPresent()) {
            throw new IllegalArgumentException("username already exists for this tenant");
        }
        Compte u = comptes.creer(new DepotComptes.Creation(username.trim(), motDePasse, "admin", null, tenantId, changementImpose, true, null, !changementImpose));
        String id = UUID.randomUUID().toString();
        String createdAt = Iso.de(horloge.instant());
        jdbc.sql("INSERT INTO tenant_admins (id, tenantId, userId, username, createdAt) VALUES (?, ?, ?, ?, ?)").params(id, tenantId, u.id(), u.nomDeConnexion(), createdAt).update();
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("id", id);
        a.put("tenantId", tenantId);
        a.put("userId", u.id());
        a.put("username", u.nomDeConnexion());
        a.put("createdAt", createdAt);
        return a;
    }

    public List<Map<String, Object>> lister(String tenantId) {
        return jdbc.sql("SELECT ta.id, ta.tenantId, ta.userId, ta.username, ta.createdAt, COALESCE(u.active, 0) AS activeFlag FROM tenant_admins ta "
                        + "LEFT JOIN users u ON u.id = ta.userId WHERE ta.tenantId = ? ORDER BY ta.createdAt")
                .params(tenantId).query((rs, i) -> {
                    Map<String, Object> a = admin(rs, i);
                    a.put("active", rs.getInt("activeFlag") == 1);
                    return a;
                }).list();
    }

    /** Chaque établissement avec ses modules et ses administrateurs ({@code listTenantsWithAdmins}). */
    public List<Map<String, Object>> etablissementsAvecAdmins(java.util.function.Function<String, List<String>> modulesDe) {
        return locataires.lister().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>(t);
            m.put("modules", modulesDe.apply((String) t.get("id")));
            m.put("admins", lister((String) t.get("id")));
            return m;
        }).toList();
    }

    private Map<String, Object> exiger(String tenantId, String userId) {
        return jdbc.sql("SELECT id, tenantId, userId, username, createdAt FROM tenant_admins WHERE tenantId = ? AND userId = ?").params(tenantId, userId)
                .query(DepotAdminsEtablissement::admin).optional().orElseThrow(() -> new IntrouvableException("admin not found"));
    }

    /** Un mot de passe provisoire, à changer au prochain accès ; envoyé par courriel si le compte en a un. */
    public Map<String, Object> reinitialiserMotDePasse(String tenantId, String userId) {
        Map<String, Object> a = exiger(tenantId, userId);
        String provisoire = motDePasseProvisoire.get();
        comptes.modifier(userId, new DepotComptes.Modification(null, null, provisoire, null, true, null, null));
        audit.consigner(null, (String) a.get("username"), "superadmin.admin.reset-password", userId, null);
        Optional<Compte> compte = comptes.parId(userId);
        boolean envoye = false;
        if (compte.isPresent() && compte.get().email() != null) {
            String nom = compte.get().nomDeConnexion();
            AdministrationSysteme.Acces acces = new AdministrationSysteme.Acces(nom, nom, provisoire,
                    locataires.lire(tenantId).map(t -> (String) t.get("name")).orElse(null), true, AdministrationSysteme.urlConnexion(urlPublique).orElse(null));
            envoye = courrier.envoyer(new Courrier.Message(compte.get().email(), acces.sujet(), acces.texte()));
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("userId", userId);
        r.put("username", a.get("username"));
        r.put("temporaryPassword", provisoire);
        r.put("emailSent", envoye);
        return r;
    }

    public Map<String, Object> activer(String tenantId, String userId, boolean actif) {
        Map<String, Object> a = exiger(tenantId, userId);
        if (!actif) {
            int actifs = jdbc.sql("SELECT COUNT(*) FROM tenant_admins ta JOIN users u ON u.id = ta.userId WHERE ta.tenantId = ? AND u.active = 1").params(tenantId).query(Integer.class).single();
            AdministrationSysteme.verifierDesactivation(actifs);
        }
        comptes.modifier(userId, new DepotComptes.Modification(null, actif, null, null, null, null, null));
        audit.consigner(null, (String) a.get("username"), actif ? "superadmin.admin.activate" : "superadmin.admin.deactivate", userId, null);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("userId", userId);
        r.put("username", a.get("username"));
        r.put("active", actif);
        return r;
    }
}
