package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.HistoriqueMotsDePasse;
import ca.vina.nexu.auth.domaine.PolitiqueMotDePasse;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * La table {@code users} — port de la partie stockage de {@code backend/src/auth/auth.ts}.
 *
 * <p>Le coût bcrypt (8) est celui du Node : une empreinte posée par l'un se vérifie par l'autre,
 * et les comptes créés ici se connectent encore au Node pendant l'étranglement. L'horloge est
 * injectée : l'identifiant d'un compte porte l'instant de sa création, comme côté Node.
 */
@Repository
public class DepotComptes {

    public static final class IntrouvableException extends RuntimeException {
        public IntrouvableException() {
            super("user not found");
        }
    }

    /** Ce que le Node accepte à la création. */
    public record Creation(String username, String password, String role, String employeeId, String tenantId,
                           boolean mustChangePassword, boolean securitySetupRequired, String email, boolean enforceStrong) {
    }

    /** Champs modifiables ; {@code null} = inchangé, comme un champ absent du PATCH côté Node. */
    public record Modification(String role, Boolean active, String password, Optional<String> employeeId,
                               Boolean mustChangePassword, Boolean securitySetupRequired, Optional<String> email) {
        public static Modification vide() {
            return new Modification(null, null, null, null, null, null, null);
        }
    }

    private static final BCryptPasswordEncoder ENCODEUR = new BCryptPasswordEncoder(8);
    private static final String COLONNES = "id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active, "
            + "mustChangePassword, securitySetupRequired, email, securityMethod, totpSecret, pendingTotpSecret";

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotComptes(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    private static Compte lire(ResultSet rs, int i) throws SQLException {
        return new Compte(rs.getString("id"), rs.getString("username"), rs.getString("loginName"), rs.getString("tenantId"),
                rs.getString("passwordHash"), rs.getString("role"), rs.getString("employeeId"), rs.getString("createdAt"),
                rs.getInt("active") == 1, rs.getInt("mustChangePassword") == 1, rs.getInt("securitySetupRequired") == 1,
                rs.getString("email"), rs.getString("securityMethod"), rs.getString("totpSecret"), rs.getString("pendingTotpSecret"));
    }

    public Optional<Compte> parId(String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM users WHERE id = ?").params(id).query(DepotComptes::lire).optional();
    }

    public Optional<Compte> parNom(String loginName, String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM users WHERE username = ?")
                .params(Compte.nomStocke(loginName, tenantId)).query(DepotComptes::lire).optional();
    }

    /** Établissements où ce nom de connexion existe (compte actif), {@code null} pour un compte global. */
    public List<String> candidats(String loginName) {
        String nom = loginName == null ? "" : loginName.trim();
        if (nom.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT tenantId FROM users WHERE active = 1 AND (username = ? OR username LIKE ?) ORDER BY tenantId")
                .params(nom, nom + "@@%").query((rs, i) -> rs.getString("tenantId")).list();
    }

    public List<Compte> lister() {
        return jdbc.sql("SELECT " + COLONNES + " FROM users ORDER BY username").query(DepotComptes::lire).list();
    }

    public List<Compte> listerParTenant(String tenantId) {
        return lister().stream().filter(c -> java.util.Objects.equals(c.tenantId(), tenantId)).toList();
    }

    public boolean verifierMotDePasse(Compte compte, String motDePasse) {
        return motDePasse != null && ENCODEUR.matches(motDePasse, compte.passwordHash());
    }

    /** Établissement de l'employé lié — {@code null} sans employé, sans ligne ou sans table. */
    public String tenantDeLEmploye(String employeeId) {
        if (employeeId == null) {
            return null;
        }
        try {
            return jdbc.sql("SELECT tenantId FROM employees WHERE id = ?").params(employeeId)
                    .query((rs, i) -> rs.getString("tenantId")).optional().orElse(null);
        } catch (org.springframework.dao.DataAccessException e) {
            return null;   // table absente : on ne bloque pas sur une incertitude de schéma
        }
    }

    public Compte creer(Creation c) {
        String stocke = Compte.nomStocke(c.username(), c.tenantId());
        if (jdbc.sql("SELECT COUNT(*) FROM users WHERE username = ?").params(stocke).query(Integer.class).single() > 0) {
            throw new IllegalArgumentException("username already exists");
        }
        if (c.enforceStrong()) {
            PolitiqueMotDePasse.exiger(c.password(), c.username());
        } else if (c.password() == null || c.password().length() < 4) {
            throw new IllegalArgumentException("password too short");
        }
        String id = "usr-" + horloge.millis() + "-" + ThreadLocalRandom.current().nextInt(1_000_000);
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt, active, "
                        + "mustChangePassword, securitySetupRequired, email) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)")
                .params(id, stocke, c.username(), c.tenantId(), ENCODEUR.encode(c.password()), c.role(), c.employeeId(),
                        Iso.de(horloge.instant()), c.mustChangePassword() ? 1 : 0, c.securitySetupRequired() ? 1 : 0, c.email())
                .update();
        return parId(id).orElseThrow();
    }

    public Compte modifier(String id, Modification m) {
        parId(id).orElseThrow(IntrouvableException::new);
        if (m.role() != null) {
            jdbc.sql("UPDATE users SET role = ? WHERE id = ?").params(m.role(), id).update();
        }
        if (m.active() != null) {
            jdbc.sql("UPDATE users SET active = ? WHERE id = ?").params(m.active() ? 1 : 0, id).update();
        }
        if (m.employeeId() != null) {
            jdbc.sql("UPDATE users SET employeeId = ? WHERE id = ?").params(m.employeeId().orElse(null), id).update();
        }
        if (m.email() != null) {
            jdbc.sql("UPDATE users SET email = ? WHERE id = ?").params(m.email().orElse(null), id).update();
        }
        if (m.password() != null) {
            if (m.password().length() < 4) {
                throw new IllegalArgumentException("password too short");
            }
            changerMotDePasse(id, m.password());
        }
        if (m.mustChangePassword() != null) {
            jdbc.sql("UPDATE users SET mustChangePassword = ? WHERE id = ?").params(m.mustChangePassword() ? 1 : 0, id).update();
        }
        if (m.securitySetupRequired() != null) {
            jdbc.sql("UPDATE users SET securitySetupRequired = ? WHERE id = ?").params(m.securitySetupRequired() ? 1 : 0, id).update();
        }
        return parId(id).orElseThrow();
    }

    /**
     * Refuse un mot de passe employé ces 6 derniers mois (ST-G10) ; sinon, range l'empreinte courante dans l'historique,
     * n'en garde que les 10 dernières, et pose la nouvelle.
     */
    private void changerMotDePasse(String id, String motDePasse) {
        Instant maintenant = horloge.instant();
        String courante = jdbc.sql("SELECT passwordHash FROM users WHERE id = ?").param(id).query(String.class).single();
        List<HistoriqueMotsDePasse.Empreinte> historique = new java.util.ArrayList<>(List.of(new HistoriqueMotsDePasse.Empreinte(courante, maintenant)));
        historique.addAll(jdbc.sql("SELECT empreinte, retireeLe FROM users_mots_de_passe WHERE userId = ? ORDER BY retireeLe DESC LIMIT ?")
                .params(id, HistoriqueMotsDePasse.TAILLE)
                .query((rs, i) -> new HistoriqueMotsDePasse.Empreinte(rs.getString("empreinte"), Instant.parse(rs.getString("retireeLe")))).list());
        if (HistoriqueMotsDePasse.reemploye(historique, e -> e != null && ENCODEUR.matches(motDePasse, e), maintenant)) {
            throw new IllegalArgumentException(HistoriqueMotsDePasse.REFUS);
        }
        jdbc.sql("INSERT INTO users_mots_de_passe (userId, empreinte, retireeLe) VALUES (?, ?, ?)").params(id, courante, maintenant.toString()).update();
        jdbc.sql("DELETE FROM users_mots_de_passe WHERE userId = ? AND rowid NOT IN "
                        + "(SELECT rowid FROM users_mots_de_passe WHERE userId = ? ORDER BY retireeLe DESC, rowid DESC LIMIT ?)")
                .params(id, id, HistoriqueMotsDePasse.TAILLE).update();
        jdbc.sql("UPDATE users SET passwordHash = ? WHERE id = ?").params(ENCODEUR.encode(motDePasse), id).update();
    }

    public boolean supprimer(String id) {
        return jdbc.sql("DELETE FROM users WHERE id = ?").params(id).update() > 0;
    }

    /** Récupération de compte : plus de second facteur, à reconfigurer au prochain accès. */
    public void reinitialiserSecurite(String id) {
        jdbc.sql("UPDATE users SET securityMethod = NULL, securityQuestion = NULL, securityAnswerHash = NULL, totpSecret = NULL, "
                + "pendingTotpSecret = NULL, securitySetupRequired = 1 WHERE id = ?").params(id).update();
    }

    public void activerMethodeQuestion(String id) {
        jdbc.sql("UPDATE users SET securityMethod = 'question', securityQuestion = NULL, securityAnswerHash = NULL, totpSecret = NULL, "
                + "pendingTotpSecret = NULL, securitySetupRequired = 0 WHERE id = ?").params(id).update();
    }

    public void activerMethodeTotp(String id, String secret) {
        jdbc.sql("UPDATE users SET securityMethod = 'totp', totpSecret = ?, pendingTotpSecret = NULL, securityQuestion = NULL, "
                + "securityAnswerHash = NULL, securitySetupRequired = 0 WHERE id = ?").params(secret, id).update();
    }

    public void mettreTotpEnAttente(String id, String secret) {
        jdbc.sql("UPDATE users SET pendingTotpSecret = ? WHERE id = ?").params(secret, id).update();
    }
}
