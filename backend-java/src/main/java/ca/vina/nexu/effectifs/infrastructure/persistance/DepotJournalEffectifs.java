package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Adaptateur SQLite du journal des effectifs ({@code eff_journal}). */
@Repository("effectifsDepotJournalEffectifs")
public class DepotJournalEffectifs implements JournalEffectifsPort {

    private final JdbcClient jdbc;
    private final Sequence sequence;

    public DepotJournalEffectifs(JdbcClient jdbc, Sequence sequence, SchemaEffectifs schema) {
        this.jdbc = jdbc;
        this.sequence = sequence;
    }

    @Override
    public void ecrire(Contexte ctx, JournalEffectifs.Consignation c) {
        JournalEffectifs.Origine origine = c.systeme() == null ? JournalEffectifs.Origine.UTILISATEUR : JournalEffectifs.Origine.SYSTEME;
        jdbc.sql("INSERT INTO eff_journal (id, tenantId, horodatage, acteurId, origine, action, objetType, objetId, uniteCode, avant, apres) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(sequence.suivant("ejr"), ctx.tenantId(), ctx.maintenant(), c.systeme() == null ? ctx.acteurId() : c.systeme(), origine.name(), c.action(),
                        c.objetType(), c.objetId(), c.uniteCode(), JsonEffectifs.ecrire(c.avant()), JsonEffectifs.ecrire(c.apres())).update();
    }

    @Override
    public List<JournalEffectifs.Entree> lire(String tenantId, JournalEffectifs.Filtre f, Set<String> unites, int limite) {
        StringBuilder sql = new StringBuilder("SELECT * FROM eff_journal WHERE tenantId = ?");
        List<Object> params = new ArrayList<>(List.of(tenantId));
        egal(sql, params, "acteurId", f.acteurId());
        egal(sql, params, "action", f.action());
        egal(sql, params, "objetType", f.objetType());
        egal(sql, params, "objetId", f.objetId());
        egal(sql, params, "origine", f.origine() == null ? null : f.origine().name());
        if (f.du() != null) {
            sql.append(" AND horodatage >= ?");
            params.add(f.du());
        }
        if (f.au() != null) {
            sql.append(" AND horodatage < ?");
            params.add(f.au());
        }
        if (unites != null) {
            sql.append(" AND uniteCode IN (").append(String.join(",", Collections.nCopies(unites.size(), "?"))).append(')');
            params.addAll(unites);
        }
        sql.append(" ORDER BY horodatage DESC, rowid DESC LIMIT ?");
        params.add(limite);
        return jdbc.sql(sql.toString()).params(params).query((rs, i) -> new JournalEffectifs.Entree(rs.getString("id"), rs.getString("horodatage"),
                rs.getString("acteurId"), JournalEffectifs.Origine.valueOf(rs.getString("origine")), rs.getString("action"), rs.getString("objetType"),
                rs.getString("objetId"), rs.getString("uniteCode"), rs.getString("avant"), rs.getString("apres"))).list();
    }

    private static void egal(StringBuilder sql, List<Object> params, String colonne, String valeur) {
        if (valeur != null && !valeur.isEmpty()) {
            sql.append(" AND ").append(colonne).append(" = ?");
            params.add(valeur);
        }
    }
}
