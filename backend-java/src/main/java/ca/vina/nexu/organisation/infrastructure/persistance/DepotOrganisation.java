package ca.vina.nexu.organisation.infrastructure.persistance;

import ca.vina.nexu.organisation.domaine.DepotOrganisationPort;
import ca.vina.nexu.organisation.domaine.Organisation;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.util.List;

/**
 * La persistance du contexte {@code organisation} (migration 0119).
 *
 * <p>{@code issueDe} est stocké en JSON : c'est une liste de longueur variable, et une table de
 * liaison pour trois identifiants coûterait plus qu'elle ne rapporte. La conversion se fait ici,
 * à un seul endroit.
 */
@Repository
public class DepotOrganisation implements DepotOrganisationPort {

    private static final ObjectMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<List<String>> FILIATION = new TypeReference<>() { };

    private final JdbcClient jdbc;

    public DepotOrganisation(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Organisation.VersionUnite> lister(String tenantId) {
        return jdbc.sql("""
                SELECT id, uniteId, code, libelle, parentVersionId, valideDu, valideAu, operation, issueDe, motif
                FROM org_versions WHERE tenantId = ? ORDER BY valideDu, code""")
                .param(tenantId)
                .query((ResultSet rs, int i) -> new Organisation.VersionUnite(rs.getString("id"),
                        rs.getString("uniteId"), rs.getString("code"), rs.getString("libelle"),
                        rs.getString("parentVersionId"), rs.getString("valideDu"), rs.getString("valideAu"),
                        Organisation.Operation.de(rs.getString("operation")),
                        filiation(rs.getString("issueDe")), rs.getString("motif")))
                .list();
    }

    private static List<String> filiation(String json) {
        return json == null || json.isBlank() ? List.of() : JSON.readValue(json, FILIATION);
    }

    @Override
    public void appliquer(String tenantId, Organisation.ResultatReorganisation resultat, String motif,
                          String auteurId, String creeLe) {
        for (Organisation.VersionTerminee t : resultat.terminees()) {
            jdbc.sql("UPDATE org_versions SET valideAu = ? WHERE tenantId = ? AND id = ?")
                    .params(t.valideAu(), tenantId, t.id()).update();
        }
        for (Organisation.VersionUnite c : resultat.creees()) {
            jdbc.sql("""
                    INSERT INTO org_versions (id, tenantId, uniteId, code, libelle, parentVersionId, valideDu,
                                              valideAu, operation, issueDe, motif, auteurId, creeLe)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(c.id(), tenantId, c.uniteId(), c.code(), c.libelle(), c.parentVersionId(),
                            c.valideDu(), c.valideAu(), c.operation().code(),
                            JSON.writeValueAsString(c.issueDe()), motif, auteurId, creeLe)
                    .update();
        }
    }
}
