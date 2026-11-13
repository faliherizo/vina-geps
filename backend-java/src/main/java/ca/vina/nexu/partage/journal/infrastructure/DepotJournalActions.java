package ca.vina.nexu.partage.journal.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/**
 * <strong>Journal des actions des utilisateurs</strong> (ADR 0041, JOURNALISATION.md § 6) : chaque
 * requête mutante d'un compte authentifié, sans son corps. Isolé par organisme à la lecture ; purgé
 * au-delà de la rétention, une fois par jour.
 */
@Repository
public class DepotJournalActions {

    public record Action(String id, String instant, String tenantId, String userId, String acteurReel, String methode, String chemin,
                         int statut, long dureeMs, String requeteId) {
        public Map<String, Object> vue() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", id);
            v.put("instant", instant);
            v.put("tenantId", tenantId);
            v.put("userId", userId);
            v.put("acteurReel", acteurReel);
            v.put("methode", methode);
            v.put("chemin", chemin);
            v.put("statut", statut);
            v.put("dureeMs", dureeMs);
            v.put("requeteId", requeteId);
            return v;
        }
    }

    /** Les filtres de lecture ; {@code perimetre} est l'organisme de l'administrateur, {@code null} pour un compte global. */
    public record Filtre(String perimetre, String organisme, String utilisateur, String depuis, String jusqua, String q, int page, int pageSize) {
    }

    public record Page(List<Action> items, int total) {
    }

    public static final int RETENTION_JOURS_PAR_DEFAUT = 365;
    static final int PAGE_PAR_DEFAUT = 50;
    static final int PAGE_MAXIMUM = 500;
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final JdbcClient jdbc;
    private final Clock horloge;
    private final AtomicReference<String> dernierePurge = new AtomicReference<>("");

    public DepotJournalActions(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    /** `JOURNAL_ACTIONS_RETENTION_JOURS` tel que le yml le pose : vide = 365, 0 = jamais, autre chose = refusé. */
    public static int retentionLue(String brut) {
        if (brut == null || brut.isBlank()) {
            return RETENTION_JOURS_PAR_DEFAUT;
        }
        try {
            int n = Integer.parseInt(brut.trim());
            if (n < 0) {
                throw new NumberFormatException();
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("JOURNAL_ACTIONS_RETENTION_JOURS « " + brut + " » invalide : un entier de jours, 0 = sans purge");
        }
    }

    public void consigner(Action a) {
        jdbc.sql("INSERT INTO journal_actions (id, instant, tenantId, userId, acteurReel, methode, chemin, statut, dureeMs, requeteId) VALUES (?,?,?,?,?,?,?,?,?,?)")
                .params(UUID.randomUUID().toString(), a.instant(), a.tenantId(), a.userId(), a.acteurReel(), a.methode(), a.chemin(), a.statut(), a.dureeMs(), a.requeteId())
                .update();
    }

    public Page lister(Filtre f) {
        List<String> ou = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        String tenant = f.perimetre() != null ? f.perimetre() : f.organisme();
        if (tenant != null) {
            ou.add("tenantId = ?");
            params.add(tenant);
        }
        if (f.utilisateur() != null && !f.utilisateur().isBlank()) {
            ou.add("(userId = ? OR acteurReel = ?)");
            params.add(f.utilisateur());
            params.add(f.utilisateur());
        }
        borne(f.depuis(), "depuis", "instant >= ?", "", ou, params);
        borne(f.jusqua(), "jusqua", "instant < ?", "T24", ou, params);
        if (f.q() != null && !f.q().isBlank()) {
            String q = f.q().trim();
            ou.add("(chemin LIKE ? OR methode = ? OR CAST(statut AS TEXT) = ?)");
            params.add("%" + q + "%");
            params.add(q.toUpperCase());
            params.add(q);
        }
        String where = ou.isEmpty() ? "" : " WHERE " + String.join(" AND ", ou);
        int pageSize = Math.min(Math.max(f.pageSize() <= 0 ? PAGE_PAR_DEFAUT : f.pageSize(), 1), PAGE_MAXIMUM);
        int page = Math.max(f.page(), 0);
        int total = jdbc.sql("SELECT COUNT(*) FROM journal_actions" + where).params(params).query(Integer.class).single();
        List<Object> avecPage = new ArrayList<>(params);
        avecPage.add(pageSize);
        avecPage.add(page * pageSize);
        List<Action> items = jdbc.sql("SELECT * FROM journal_actions" + where + " ORDER BY instant DESC, rowid DESC LIMIT ? OFFSET ?").params(avecPage)
                .query((rs, i) -> new Action(rs.getString("id"), rs.getString("instant"), rs.getString("tenantId"), rs.getString("userId"), rs.getString("acteurReel"),
                        rs.getString("methode"), rs.getString("chemin"), rs.getInt("statut"), rs.getLong("dureeMs"), rs.getString("requeteId")))
                .list();
        return new Page(items, total);
    }

    private static void borne(String date, String nom, String clause, String suffixe, List<String> ou, List<Object> params) {
        if (date == null || date.isBlank()) {
            return;
        }
        if (!DATE.matcher(date).matches()) {
            throw new IllegalArgumentException(nom + " : date AAAA-MM-JJ");
        }
        ou.add(clause);
        params.add(date + suffixe);
    }

    /** Purge les lignes plus vieilles que {@code retentionJours} (0 = jamais) ; renvoie le nombre retiré. */
    public int purger(int retentionJours) {
        if (retentionJours <= 0) {
            return 0;
        }
        String limite = Instant.now(horloge).minus(Duration.ofDays(retentionJours)).toString();
        return jdbc.sql("DELETE FROM journal_actions WHERE instant < ?").param(limite).update();
    }

    /** Une purge par jour, déclenchée par la première requête de la journée ; renvoie le nombre retiré (0 si déjà faite). */
    public int purgerSiDu(int retentionJours) {
        String jour = Instant.now(horloge).toString().substring(0, 10);
        return jour.equals(dernierePurge.getAndSet(jour)) ? 0 : purger(retentionJours);
    }
}
