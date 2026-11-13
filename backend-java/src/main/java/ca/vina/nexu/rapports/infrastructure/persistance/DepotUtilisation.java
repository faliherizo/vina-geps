package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.rapports.domaine.Utilisation;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <b>Les listes d'utilisation</b> (BF-130) — persistance seule, jumeau de {@code serviceUtilisation.ts} ;
 * les règles vivent dans {@link Utilisation}.
 *
 * <p>La « dernière activité » est lue dans le <b>journal d'audit</b>, et non dans une colonne qu'il
 * aurait fallu tenir à jour à chaque requête : la colonne aurait menti dès le premier chemin qui l'oublie.
 */
@Repository
public class DepotUtilisation {

    private static final String SUFFIXE_ETABLISSEMENT = "@@";
    private static final String ROLE_INCONNU = "—";

    private final JdbcClient jdbc;

    public DepotUtilisation(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Listes(Utilisation.UsageDesQuarts quarts, List<Utilisation.CompteObserve> comptes) {
    }

    /** Les quarts <b>publiés</b> de la période et les comptes de l'établissement avec leur dernière action consignée. */
    public Listes lire(String tenantId, String du, String au, String maintenant) {
        Map<String, Object> l = jdbc.sql("SELECT COUNT(*) AS publies, SUM(CASE WHEN q.personId IS NOT NULL THEN 1 ELSE 0 END) AS combles "
                        + "FROM horaire_quarts q JOIN horaire_periodes p ON p.id = q.periodeId AND p.tenantId = q.tenantId "
                        + "WHERE q.tenantId = ? AND q.date >= ? AND q.date <= ? AND p.statut = 'publiee'")
                .params(tenantId, du, au).query().singleRow();
        return new Listes(Utilisation.usageDesQuarts(entier(l.get("publies")), entier(l.get("combles"))), comptes(tenantId, maintenant));
    }

    private static long entier(Object v) {
        return v instanceof Number n ? n.longValue() : 0;
    }

    private static String lisible(String username) {
        String u = username == null ? "" : username;
        int i = u.indexOf(SUFFIXE_ETABLISSEMENT);
        return i < 0 ? u : u.substring(0, i);
    }

    private List<Utilisation.CompteObserve> comptes(String tenantId, String maintenant) {
        Map<String, String> activites = activites();
        List<Utilisation.CompteObserve> observes = new ArrayList<>(jdbc.sql("SELECT id, username, role, active FROM users WHERE tenantId = ?")
                .param(tenantId)
                .query((rs, i) -> new Utilisation.CompteObserve(lisible(rs.getString("username")), rs.getString("role"), rs.getInt("active") == 1,
                        false, activites.get(rs.getString("id"))))
                .list());
        Set<String> connus = new HashSet<>(observes.stream().map(Utilisation.CompteObserve::identifiant).toList());
        // Une invitation sans compte est une personne invitée qui n'est pas encore là : la première ligne de la question posée.
        for (String username : invitations(tenantId, maintenant)) {
            if (connus.add(lisible(username))) {
                observes.add(new Utilisation.CompteObserve(lisible(username), ROLE_INCONNU, true, true, null));
            }
        }
        return observes;
    }

    /** Une seule requête d'agrégat pour tout le journal : une par compte relirait la table la plus volumineuse autant de fois. */
    private Map<String, String> activites() {
        Map<String, String> activites = new HashMap<>();
        try {
            jdbc.sql("SELECT userId, MAX(at) AS dernier FROM audit_log WHERE userId IS NOT NULL GROUP BY userId")
                    .query((rs, i) -> activites.put(rs.getString("userId"), rs.getString("dernier"))).list();
        } catch (DataAccessException e) {
            // Journal absent d'une base réduite : aucune activité connue, jamais une panne.
            return Map.of();
        }
        return activites;
    }

    private List<String> invitations(String tenantId, String maintenant) {
        try {
            return jdbc.sql("SELECT username FROM auth_invitations WHERE tenantId = ? AND accepteeLe IS NULL AND revoqueeLe IS NULL AND expireLe > ? ORDER BY username")
                    .params(tenantId, maintenant).query(String.class).list();
        } catch (DataAccessException e) {
            // Table absente : aucune invitation en cours.
            return List.of();
        }
    }
}
