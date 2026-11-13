package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.Emprunt;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.securite.SessionsEmpruntees;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * L'emprunt d'identité — les cas d'utilisation (CSSMB 25 ; ADR 0038), port de {@code auth/serviceEmprunt.ts} :
 * le réglage de l'organisme, le début (un jeton d'une heure, tracé sous les deux identités), la fin (le
 * jeton de l'administrateur revient), le journal. Tient aussi le port {@link SessionsEmpruntees} du filtre.
 */
@Repository
public class DepotEmprunts implements SessionsEmpruntees {

    private static final String ACTION_AUTORISE = "auth.emprunt.autorise";
    private static final String ACTION_FERME = "auth.emprunt.ferme";
    private static final String ACTION_DEBUT = "auth.emprunt.debut";
    private static final String ACTION_FIN = "auth.emprunt.fin";
    private static final String GLOBAL = "global";

    public record Reglage(boolean empruntIdentite, String modifieLe, String modifiePar) {
        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("empruntIdentite", empruntIdentite);
            m.put("modifieLe", modifieLe);
            m.put("modifiePar", modifiePar);
            return m;
        }
    }

    public record Ligne(String id, String tenantId, String emprunteurId, String cibleId, String motif, String debuteLe, String expireLe, String termineLe) {
        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("tenantId", tenantId);
            m.put("emprunteurId", emprunteurId);
            m.put("cibleId", cibleId);
            m.put("motif", motif);
            m.put("debuteLe", debuteLe);
            m.put("expireLe", expireLe);
            m.put("termineLe", termineLe);
            return m;
        }
    }

    /** Ce que le début rend : le jeton emprunté, l'emprunt, la cible. */
    public record SessionEmpruntee(String token, Ligne emprunt, Compte cible) {
    }

    public record SessionRendue(String token, Compte emprunteur) {
    }

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final DepotComptes comptes;
    private final JournalAudit audit;
    private final JetonDeSession jetons;

    // Deux constructeurs (le secret pour Spring, le jeton pour les essais) : Spring exige qu'on lui désigne le sien.
    @Autowired
    public DepotEmprunts(JdbcClient jdbc, Clock horloge, DepotComptes comptes, JournalAudit audit, @Value("${nexu.jwt.secret}") String secret) {
        this(jdbc, horloge, comptes, audit, new JetonDeSession(secret));
    }

    public DepotEmprunts(JdbcClient jdbc, Clock horloge, DepotComptes comptes, JournalAudit audit, JetonDeSession jetons) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.comptes = comptes;
        this.audit = audit;
        this.jetons = jetons;
    }

    private String maintenant() {
        return Iso.de(horloge.instant());
    }

    private static String cleTenant(String tenantId) {
        return tenantId == null ? "" : tenantId;
    }

    @Override
    public boolean active(String empruntId) {
        return jdbc.sql("SELECT expireLe FROM auth_emprunts WHERE id = ? AND termineLe IS NULL").param(empruntId == null ? "" : empruntId).query(String.class).optional()
                .filter(expireLe -> expireLe.compareTo(maintenant()) > 0).isPresent();
    }

    // ── Réglage ────────────────────────────────────────────────────────────

    public Reglage lireReglage(String tenantId) {
        return jdbc.sql("SELECT empruntIdentite, modifieLe, modifiePar FROM auth_reglages_securite WHERE tenantId = ?").param(cleTenant(tenantId))
                .query((rs, i) -> new Reglage(rs.getInt("empruntIdentite") != 0, rs.getString("modifieLe"), rs.getString("modifiePar"))).optional()
                .orElse(new Reglage(false, null, null));
    }

    /** Fermé par défaut : l'organisme l'ouvre lui-même, et le journal d'audit le dit. */
    @Transactional
    public Reglage regler(String tenantId, ContexteRequete acteur, Object actif) {
        if (!(actif instanceof Boolean b)) {
            throw new IllegalArgumentException("actif : vrai ou faux");
        }
        jdbc.sql("INSERT INTO auth_reglages_securite (tenantId, empruntIdentite, modifieLe, modifiePar) VALUES (?,?,?,?) ON CONFLICT(tenantId) DO UPDATE SET empruntIdentite = excluded.empruntIdentite, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(cleTenant(tenantId), b ? 1 : 0, maintenant(), acteur.userId()).update();
        audit.consigner(acteur.userId(), acteur.username(), b ? ACTION_AUTORISE : ACTION_FERME, tenantId == null ? GLOBAL : tenantId, null);
        return lireReglage(tenantId);
    }

    // ── Début, fin, lecture ────────────────────────────────────────────────

    /** Débute un emprunt : la garde est le refus nommé des règles ; le jeton rendu vaut une heure. */
    @Transactional
    public SessionEmpruntee demarrer(ContexteRequete emprunteur, Object cibleId, Object motif) {
        if (!(cibleId instanceof String id) || id.isBlank()) {
            throw new IllegalArgumentException("cibleId requis");
        }
        Emprunt.refusDuMotif(motif).ifPresent(m -> {
            throw new IllegalArgumentException(m);
        });
        Optional<Compte> cible = comptes.parId(id.trim());
        String tenantDuReglage = emprunteur.tenantId() != null ? emprunteur.tenantId() : cible.map(Compte::tenantId).orElse(null);
        Emprunt.refus(new Emprunt.Emprunteur(emprunteur.userId(), emprunteur.role(), emprunteur.tenantId(), emprunteur.emprunteur()),
                cible.map(c -> new Emprunt.Cible(c.id(), c.role(), c.tenantId(), c.active())).orElse(null), lireReglage(tenantDuReglage).empruntIdentite())
                .ifPresent(m -> {
                    throw new RefusException(m);
                });
        Compte c = cible.orElseThrow();
        String instant = maintenant();
        String expireLe = Emprunt.expiration(instant, Emprunt.DUREE_MINUTES);
        String empruntId = UUID.randomUUID().toString();
        String motifPropre = ((String) motif).trim();
        jdbc.sql("INSERT INTO auth_emprunts (id, tenantId, emprunteurId, cibleId, motif, debuteLe, expireLe, termineLe) VALUES (?,?,?,?,?,?,?,NULL)")
                .params(empruntId, c.tenantId(), emprunteur.userId(), c.id(), motifPropre, instant, expireLe).update();
        audit.consigner(emprunteur.userId(), emprunteur.username(), ACTION_DEBUT, c.id(), Map.of("emprunt", empruntId, "motif", motifPropre, "expireLe", expireLe), emprunteur.userId());
        String token = jetons.emettre(new JetonDeSession.Revendications(c.id(), c.nomDeConnexion(), c.role(), c.employeeId(), c.tenantId(), emprunteur.userId(), empruntId), Emprunt.DUREE_MINUTES * 60L);
        return new SessionEmpruntee(token, new Ligne(empruntId, c.tenantId(), emprunteur.userId(), c.id(), motifPropre, instant, expireLe, null), c);
    }

    /** Termine l'emprunt de la session courante : le jeton de l'administrateur revient, l'audit le dit. */
    @Transactional
    public SessionRendue terminer(ContexteRequete session) {
        if (!session.estEmpruntee()) {
            throw new IllegalArgumentException("cette session n'est pas empruntée");
        }
        Compte emprunteur = comptes.parId(session.emprunteur()).filter(Compte::active)
                .orElseThrow(() -> new IllegalArgumentException("compte de l'emprunteur introuvable ou désactivé"));
        jdbc.sql("UPDATE auth_emprunts SET termineLe = ? WHERE id = ? AND termineLe IS NULL").params(maintenant(), session.empruntId()).update();
        audit.consigner(emprunteur.id(), emprunteur.nomDeConnexion(), ACTION_FIN, session.userId(), Map.of("emprunt", session.empruntId()), emprunteur.id());
        return new SessionRendue(jetons.emettre(new JetonDeSession.Revendications(emprunteur.id(), emprunteur.nomDeConnexion(), emprunteur.role(), emprunteur.employeeId(), emprunteur.tenantId())), emprunteur);
    }

    /** Ce que l'écran dit d'une session empruntée : qui agit, comme qui, jusqu'à quand ; {@code null} pour une session ordinaire. */
    public Map<String, Object> empruntDe(ContexteRequete session) {
        if (!session.estEmpruntee()) {
            return null;
        }
        return jdbc.sql("SELECT expireLe FROM auth_emprunts WHERE id = ?").param(session.empruntId()).query(String.class).optional().map(expireLe -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", session.empruntId());
            m.put("emprunteur", Map.of("id", session.emprunteur(), "username", comptes.parId(session.emprunteur()).map(Compte::nomDeConnexion).orElse(session.emprunteur())));
            m.put("expireLe", expireLe);
            return m;
        }).orElse(null);
    }

    /** Le journal des emprunts de l'organisme, les plus récents d'abord — avec les noms de compte. */
    public List<Map<String, Object>> journal(String tenantId, int limite) {
        String now = maintenant();
        return jdbc.sql("SELECT * FROM auth_emprunts WHERE COALESCE(tenantId, '') = ? ORDER BY debuteLe DESC LIMIT ?").params(cleTenant(tenantId), limite)
                .query((rs, i) -> new Ligne(rs.getString("id"), rs.getString("tenantId"), rs.getString("emprunteurId"), rs.getString("cibleId"), rs.getString("motif"), rs.getString("debuteLe"), rs.getString("expireLe"), rs.getString("termineLe")))
                .list().stream().map(l -> {
                    Map<String, Object> m = l.vue();
                    m.put("emprunteur", comptes.parId(l.emprunteurId()).map(Compte::nomDeConnexion).orElse(l.emprunteurId()));
                    m.put("cible", comptes.parId(l.cibleId()).map(Compte::nomDeConnexion).orElse(l.cibleId()));
                    m.put("actif", l.termineLe() == null && l.expireLe().compareTo(now) > 0);
                    return m;
                }).toList();
    }

    /** Un refus des règles d'emprunt : 403 au bord, un motif nommé. */
    public static final class RefusException extends RuntimeException {
        public RefusException(String message) {
            super(message);
        }
    }
}
