package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.RapportsEnregistres;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Le dépôt des rapports enregistrés</b> — la table {@code rap_enregistres}, identique au Node
 * ({@code backend/src/rapports/enregistres.ts}), posée à la construction comme les autres schémas
 * paresseux. Une lecture rend la forme du contrat HTTP : la définition redevient un objet.
 */
@Repository
public class DepotRapportsEnregistres {

    private static final String COLONNES = "id, nom, description, definition, creeLe, creePar, modifieLe, modifiePar";

    private final JdbcClient jdbc;

    public DepotRapportsEnregistres(JdbcClient jdbc) {
        this.jdbc = jdbc;
        jdbc.sql("CREATE TABLE IF NOT EXISTS rap_enregistres (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, nom TEXT NOT NULL, "
                + "description TEXT, definition TEXT NOT NULL, creeLe TEXT NOT NULL, creePar TEXT NOT NULL, modifieLe TEXT NOT NULL, "
                + "modifiePar TEXT NOT NULL, UNIQUE (tenantId, nom))").update();
    }

    /** L'auteur d'une écriture : l'organisme, l'acteur, l'instant (injecté à la frontière). */
    public record Auteur(String tenantId, String acteurId, String maintenant) {
    }

    private static Map<String, Object> vue(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rs.getString("id"));
        m.put("nom", rs.getString("nom"));
        m.put("description", rs.getString("description"));
        m.put("definition", Json.carte(rs.getString("definition")));
        for (String c : List.of("creeLe", "creePar", "modifieLe", "modifiePar")) {
            m.put(c, rs.getString(c));
        }
        return m;
    }

    public List<Map<String, Object>> lister(String tenantId) {
        return jdbc.sql("SELECT " + COLONNES + " FROM rap_enregistres WHERE tenantId = ? ORDER BY nom").param(tenantId)
                .query((rs, i) -> vue(rs)).list();
    }

    public Map<String, Object> lire(String tenantId, String id) {
        return jdbc.sql("SELECT " + COLONNES + " FROM rap_enregistres WHERE tenantId = ? AND id = ?").params(tenantId, id)
                .query((rs, i) -> vue(rs)).optional().orElseThrow(() -> new RapportsEnregistres.RapportIntrouvableException(id));
    }

    private void exigerNomLibre(String tenantId, String nom, String saufId) {
        boolean pris = jdbc.sql("SELECT id FROM rap_enregistres WHERE tenantId = ? AND nom = ? AND id <> ?").params(tenantId, nom, saufId)
                .query(String.class).optional().isPresent();
        if (pris) {
            throw new CatalogueRapports.RapportInvalideException("un rapport « " + nom + " » est déjà enregistré dans cet organisme");
        }
    }

    public Map<String, Object> creer(Auteur a, String id, RapportsEnregistres.Saisie s) {
        exigerNomLibre(a.tenantId(), s.nom(), "");
        jdbc.sql("INSERT INTO rap_enregistres (id, tenantId, nom, description, definition, creeLe, creePar, modifieLe, modifiePar) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")
                .params(id, a.tenantId(), s.nom(), s.description(), Json.texte(s.definition()), a.maintenant(), a.acteurId(), a.maintenant(), a.acteurId())
                .update();
        return lire(a.tenantId(), id);
    }

    public Map<String, Object> modifier(Auteur a, String id, RapportsEnregistres.Saisie s) {
        lire(a.tenantId(), id);
        exigerNomLibre(a.tenantId(), s.nom(), id);
        jdbc.sql("UPDATE rap_enregistres SET nom = ?, description = ?, definition = ?, modifieLe = ?, modifiePar = ? WHERE tenantId = ? AND id = ?")
                .params(s.nom(), s.description(), Json.texte(s.definition()), a.maintenant(), a.acteurId(), a.tenantId(), id).update();
        return lire(a.tenantId(), id);
    }

    public Map<String, Object> retirer(String tenantId, String id) {
        Map<String, Object> r = lire(tenantId, id);
        jdbc.sql("DELETE FROM rap_enregistres WHERE tenantId = ? AND id = ?").params(tenantId, id).update();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nom", r.get("nom"));
        return m;
    }
}
