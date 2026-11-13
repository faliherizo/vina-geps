package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.auth.domaine.QuestionsSecretes;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le catalogue des questions secrètes ({@code security_question_catalog}) — port de
 * {@code backend/src/auth/securityQuestionsCatalog.ts}. Deux portées : globale
 * ({@code tenantId} nul, au superadmin) et par établissement (à son admin).
 *
 * <p>Les questions de secours sont semées dans la portée globale à la première lecture ou
 * écriture, si elle est vide — le geste idempotent du Node à l'initialisation du schéma.
 */
@Repository
public class DepotCatalogueQuestions {

    public record Question(String id, String tenantId, String question, boolean active, int sortOrder, String createdAt) {
        public Map<String, Object> vue() {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", id);
            v.put("tenantId", tenantId);
            v.put("question", question);
            v.put("active", active);
            v.put("sortOrder", sortOrder);
            v.put("createdAt", createdAt);
            return v;
        }
    }

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotCatalogueQuestions(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    private static Question lire(ResultSet rs, int i) throws SQLException {
        return new Question(rs.getString("id"), rs.getString("tenantId"), rs.getString("question"), rs.getInt("active") == 1,
                rs.getInt("sortOrder"), rs.getString("createdAt"));
    }

    private void semer() {
        if (jdbc.sql("SELECT COUNT(*) FROM security_question_catalog WHERE tenantId IS NULL").query(Integer.class).single() > 0) {
            return;
        }
        String maintenant = Iso.de(horloge.instant());
        for (int i = 0; i < QuestionsSecretes.DEFAUT.size(); i++) {
            jdbc.sql("INSERT INTO security_question_catalog (id, tenantId, question, active, sortOrder, createdAt) VALUES (?, NULL, ?, 1, ?, ?)")
                    .params(UUID.randomUUID().toString(), QuestionsSecretes.DEFAUT.get(i), i, maintenant).update();
        }
    }

    /** Liste effective pour un établissement : globales actives puis les siennes, dédupliquées. */
    public List<String> effectives(String tenantId) {
        semer();
        List<String> actives = jdbc.sql("SELECT question FROM security_question_catalog WHERE active = 1 AND (tenantId IS NULL OR tenantId = ?) "
                        + "ORDER BY (tenantId IS NULL) DESC, sortOrder ASC")
                .params(tenantId == null ? " __none__" : tenantId).query((rs, i) -> rs.getString("question")).list();
        return QuestionsSecretes.effectives(actives);
    }

    /** Liste de gestion d'une portée, actives ou non ; {@code null} = globale. */
    public List<Question> lister(String portee) {
        semer();
        return portee == null
                ? jdbc.sql("SELECT * FROM security_question_catalog WHERE tenantId IS NULL ORDER BY sortOrder ASC, createdAt ASC").query(DepotCatalogueQuestions::lire).list()
                : jdbc.sql("SELECT * FROM security_question_catalog WHERE tenantId = ? ORDER BY sortOrder ASC, createdAt ASC").params(portee).query(DepotCatalogueQuestions::lire).list();
    }

    public Optional<Question> parId(String id) {
        return jdbc.sql("SELECT * FROM security_question_catalog WHERE id = ?").params(id).query(DepotCatalogueQuestions::lire).optional();
    }

    public Question ajouter(String portee, String brut) {
        semer();
        String q = QuestionsSecretes.questionDeCatalogue(brut);
        String filtre = portee == null ? "tenantId IS NULL" : "tenantId = ?";
        var doublons = jdbc.sql("SELECT COUNT(*) FROM security_question_catalog WHERE " + filtre + " AND lower(question) = lower(?)");
        int n = (portee == null ? doublons.params(q) : doublons.params(portee, q)).query(Integer.class).single();
        if (n > 0) {
            throw new IllegalArgumentException("cette question existe déjà dans ce catalogue");
        }
        var rang = jdbc.sql("SELECT COALESCE(MAX(sortOrder), -1) FROM security_question_catalog WHERE " + filtre);
        int suivant = (portee == null ? rang : rang.params(portee)).query(Integer.class).single() + 1;
        String id = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO security_question_catalog (id, tenantId, question, active, sortOrder, createdAt) VALUES (?, ?, ?, 1, ?, ?)")
                .params(id, portee, q, suivant, Iso.de(horloge.instant())).update();
        return parId(id).orElseThrow();
    }

    /** {@code null} = inchangé. */
    public Question modifier(Question actuelle, String question, Boolean active) {
        String q = question != null ? QuestionsSecretes.questionDeCatalogue(question) : actuelle.question();
        boolean a = active != null ? active : actuelle.active();
        jdbc.sql("UPDATE security_question_catalog SET question = ?, active = ? WHERE id = ?").params(q, a ? 1 : 0, actuelle.id()).update();
        return parId(actuelle.id()).orElseThrow();
    }

    public boolean supprimer(String id) {
        return jdbc.sql("DELETE FROM security_question_catalog WHERE id = ?").params(id).update() > 0;
    }
}
