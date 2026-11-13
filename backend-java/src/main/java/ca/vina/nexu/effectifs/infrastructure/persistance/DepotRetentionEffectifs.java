package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EcritureRetentionEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureRetentionEffectifsPort;
import ca.vina.nexu.effectifs.domaine.RetentionEffectifs.Classe;
import ca.vina.nexu.effectifs.domaine.TempsEffectifs.StatutFeuille;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Adaptateur SQLite de la rétention ({@code eff_retention}) et de ses purges, jumeau du {@code SUPPRESSIONS} de
 * {@code backend/src/effectifs/retention/domaine.ts} : mêmes requêtes, mêmes paramètres nommés, dans l'ordre où
 * elles doivent se faire (les enfants avant leurs parents). Le décompte d'une suppression est la même requête en
 * {@code SELECT COUNT(*)} : l'aperçu ne peut pas annoncer autre chose que ce que la purge fera. Le statut d'une
 * feuille approuvée se passe en paramètre — une feuille en cours n'est pas une archive.
 */
@Repository("effectifsDepotRetention")
public class DepotRetentionEffectifs implements LectureRetentionEffectifsPort, EcritureRetentionEffectifsPort {

    private static final Pattern SUPPRESSION = Pattern.compile("^DELETE FROM ");
    private static final String FEUILLES_ECHUES = "SELECT id FROM eff_feuilles WHERE tenantId = :tenantId AND statut = :approuvee AND fin < :limite";

    private final JdbcClient jdbc;

    public DepotRetentionEffectifs(JdbcClient jdbc, SchemaRetentionEffectifs schema) {
        this.jdbc = jdbc;
    }

    /** Les suppressions d'une classe ; chacune reçoit {@code tenantId}, {@code limite} et, au besoin, {@code approuvee}. */
    static List<String> suppressions(Classe classe) {
        return switch (classe) {
            case HORAIRES -> List.of(
                    "DELETE FROM eff_echanges WHERE tenantId = :tenantId AND proposeLe < :limite",
                    "DELETE FROM eff_places WHERE tenantId = :tenantId AND equipageId IN (SELECT id FROM eff_equipages WHERE tenantId = :tenantId AND date < :limite)",
                    "DELETE FROM eff_equipages WHERE tenantId = :tenantId AND date < :limite");
            case PRESENCE -> List.of(
                    "DELETE FROM eff_pointage_preuves WHERE tenantId = :tenantId AND pointageId IN (SELECT id FROM eff_pointages WHERE tenantId = :tenantId AND horodatage < :limite)",
                    "DELETE FROM eff_pointages WHERE tenantId = :tenantId AND horodatage < :limite",
                    "DELETE FROM eff_horodatages_externes WHERE tenantId = :tenantId AND horodatage < :limite");
            case TEMPS -> List.of(
                    "DELETE FROM eff_segments_temps WHERE tenantId = :tenantId AND feuilleId IN (" + FEUILLES_ECHUES + ")",
                    "DELETE FROM eff_feuille_decisions WHERE tenantId = :tenantId AND feuilleId IN (" + FEUILLES_ECHUES + ")",
                    "DELETE FROM eff_feuilles WHERE tenantId = :tenantId AND statut = :approuvee AND fin < :limite");
            case ABSENCES -> List.of("DELETE FROM eff_absences WHERE tenantId = :tenantId AND au < :limite");
            case JOURNAUX -> List.of(
                    "DELETE FROM eff_regle_evaluations WHERE tenantId = :tenantId AND horodatage < :limite",
                    "DELETE FROM eff_journal WHERE tenantId = :tenantId AND horodatage < :limite");
        };
    }

    /** Le décompte d'une suppression : la même requête, en {@code SELECT COUNT(*)}. */
    static String comptage(String suppression) {
        return SUPPRESSION.matcher(suppression).replaceFirst("SELECT COUNT(*) AS n FROM ");
    }

    private JdbcClient.StatementSpec requete(String sql, String tenantId, String limite) {
        JdbcClient.StatementSpec s = jdbc.sql(sql).param("tenantId", tenantId).param("limite", limite);
        return sql.contains(":approuvee") ? s.param("approuvee", StatutFeuille.APPROUVEE.name()) : s;
    }

    @Override
    public Map<Classe, Integer> durees(String tenantId) {
        Map<Classe, Integer> durees = new EnumMap<>(Classe.class);
        jdbc.sql("SELECT classe, annees FROM eff_retention WHERE tenantId = ?").param(tenantId)
                .query((rs, i) -> Map.entry(Classe.valueOf(rs.getString("classe")), rs.getInt("annees"))).list()
                .forEach(e -> durees.put(e.getKey(), e.getValue()));
        return durees;
    }

    @Override
    public long compter(String tenantId, Classe classe, String limite) {
        return suppressions(classe).stream().mapToLong(sql -> requete(comptage(sql), tenantId, limite).query(Long.class).single()).sum();
    }

    @Override
    public void poser(Contexte ctx, Classe classe, int annees) {
        jdbc.sql("INSERT INTO eff_retention (tenantId, classe, annees, modifieLe, modifiePar) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, classe) DO UPDATE SET annees = excluded.annees, modifieLe = excluded.modifieLe, modifiePar = excluded.modifiePar")
                .params(ctx.tenantId(), classe.name(), annees, ctx.maintenant(), ctx.acteurId()).update();
    }

    @Override
    public void retirer(String tenantId, Classe classe) {
        jdbc.sql("DELETE FROM eff_retention WHERE tenantId = ? AND classe = ?").params(tenantId, classe.name()).update();
    }

    @Override
    public long purger(String tenantId, Classe classe, String limite) {
        return suppressions(classe).stream().mapToLong(sql -> requete(sql, tenantId, limite).update()).sum();
    }
}
