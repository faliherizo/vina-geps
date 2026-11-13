package ca.vina.nexu.approbations.infrastructure.persistance;

import ca.vina.nexu.approbations.domaine.TypesDeDocument;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Le catalogue des types de document ({@code approval_document_types}, migration 0132). Il lit et
 * écrit ; il ne décide rien. Le semis insère <strong>chaque code manquant</strong> séparément : un
 * organisme créé avant l'ajout d'un type système en serait privé pour toujours.
 */
@Repository
public class DepotTypesDeDocument implements TypesDeDocument.Depot {

    private static final String UNIQUE = "UNIQUE";

    private final JdbcClient jdbc;

    public DepotTypesDeDocument(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void semer(String tenantId, List<TypesDeDocument.Systeme> types, String le) {
        Set<String> connus = new HashSet<>(jdbc.sql("SELECT code FROM approval_document_types WHERE tenantId = ?")
                .param(tenantId).query(String.class).list());
        for (TypesDeDocument.Systeme t : types) {
            if (!connus.contains(t.code())) {
                jdbc.sql("INSERT OR IGNORE INTO approval_document_types (id, tenantId, code, labelFr, labelEn, labelMg, systeme, sortOrder, active, createdAt)"
                                + " VALUES (?, ?, ?, ?, ?, ?, 1, ?, 1, ?)")
                        .params(UUID.randomUUID().toString(), tenantId, t.code(), t.labelFr(), t.labelEn(), t.labelMg(), t.sortOrder(), le)
                        .update();
            }
        }
    }

    @Override
    public List<TypesDeDocument.Type> lister(String tenantId, boolean actifsSeulement) {
        return jdbc.sql("SELECT * FROM approval_document_types WHERE tenantId = ?" + (actifsSeulement ? " AND active = 1" : "")
                        + " ORDER BY sortOrder ASC, labelFr ASC")
                .param(tenantId).query(DepotTypesDeDocument::type).list();
    }

    @Override
    public Optional<TypesDeDocument.Type> lire(String tenantId, String id) {
        return jdbc.sql("SELECT * FROM approval_document_types WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query(DepotTypesDeDocument::type).optional();
    }

    @Override
    public boolean actif(String tenantId, String code) {
        return jdbc.sql("SELECT active FROM approval_document_types WHERE tenantId = ? AND code = ?").params(tenantId, code)
                .query(Integer.class).optional().map(a -> a != 0).orElse(false);
    }

    @Override
    public boolean inserer(String tenantId, String id, TypesDeDocument.Nouveau n, String le) {
        try {
            jdbc.sql("INSERT INTO approval_document_types (id, tenantId, code, labelFr, labelEn, labelMg, systeme, sortOrder, active, createdAt)"
                            + " VALUES (?, ?, ?, ?, ?, ?, 0, ?, 1, ?)")
                    .params(id, tenantId, n.code(), n.labelFr(), n.labelEn(), n.labelMg(), n.sortOrder(), le).update();
            return true;
        } catch (org.springframework.dao.DataAccessException e) {
            // SQLite ne lève pas DataIntegrityViolationException : on lit le motif, comme le Node.
            if (String.valueOf(e.getMessage()).contains(UNIQUE)) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void modifier(String tenantId, String id, TypesDeDocument.Modification m) {
        champ(tenantId, id, "labelFr", m.labelFr());
        champ(tenantId, id, "labelEn", m.labelEn());
        champ(tenantId, id, "labelMg", m.labelMg());
        champ(tenantId, id, "sortOrder", m.sortOrder());
        champ(tenantId, id, "active", m.active() == null ? null : (m.active() ? 1 : 0));
    }

    /** {@code colonne} vient de cette classe, jamais de l'appelant : aucune concaténation de saisie. */
    private void champ(String tenantId, String id, String colonne, Object valeur) {
        if (valeur != null) {
            jdbc.sql("UPDATE approval_document_types SET " + colonne + " = ? WHERE tenantId = ? AND id = ?")
                    .params(valeur, tenantId, id).update();
        }
    }

    @Override
    public boolean supprimer(String tenantId, String id) {
        return jdbc.sql("DELETE FROM approval_document_types WHERE tenantId = ? AND id = ?").params(tenantId, id).update() > 0;
    }

    private static TypesDeDocument.Type type(ResultSet rs, int i) throws SQLException {
        return new TypesDeDocument.Type(rs.getString("id"), rs.getString("tenantId"), rs.getString("code"), rs.getString("labelFr"),
                rs.getString("labelEn"), rs.getString("labelMg"), rs.getInt("systeme") != 0, rs.getInt("sortOrder"),
                rs.getInt("active") != 0, rs.getString("createdAt"));
    }
}
