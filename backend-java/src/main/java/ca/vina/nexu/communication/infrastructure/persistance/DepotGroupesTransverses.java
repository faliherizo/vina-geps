package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.GroupesTransverses;
import ca.vina.nexu.communication.domaine.GroupesTransversesPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Les groupes transverses en base (changeset {@code 0010}). Une nature inconnue en base est une base corrompue. */
public class DepotGroupesTransverses implements GroupesTransversesPort.Lecture, GroupesTransversesPort.Ecriture {

    private static final String GROUPES = "SELECT id, nom, nature, actif FROM msg_groupes_transverses WHERE tenantId = ?";
    /** Le nom d'une personne : sa ressource d'effectifs si elle en a une, sinon son nom de connexion. */
    private static final String NOM = "SELECT COALESCE(MAX(r.nom), COALESCE(u.loginName, u.username)) FROM users u "
            + "LEFT JOIN eff_ressources r ON r.utilisateurId = u.id AND r.tenantId = u.tenantId AND r.actif = 1 "
            + "WHERE u.tenantId = ? AND u.active = 1 AND COALESCE(u.loginName, u.username) = ? GROUP BY u.id";

    private final JdbcClient jdbc;

    public DepotGroupesTransverses(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static GroupesTransverses.Groupe groupe(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new GroupesTransverses.Groupe(rs.getString("id"), rs.getString("nom"), GroupesTransverses.Nature.de(rs.getString("nature"))
                .orElseThrow(() -> new IllegalStateException("nature de groupe inconnue en base")), rs.getInt("actif") == 1);
    }

    @Override
    public List<GroupesTransverses.Groupe> groupes(String tenantId) {
        return jdbc.sql(GROUPES + " ORDER BY nom").param(tenantId).query(DepotGroupesTransverses::groupe).list();
    }

    @Override
    public Optional<GroupesTransverses.Groupe> groupe(String tenantId, String id) {
        return jdbc.sql(GROUPES + " AND id = ?").params(tenantId, id).query(DepotGroupesTransverses::groupe).optional();
    }

    @Override
    public List<GroupesTransverses.Appartenance> appartenances(String tenantId, String groupeId) {
        return jdbc.sql("SELECT personId, nom, depuis, jusqua, moderateur FROM msg_groupe_membres WHERE tenantId = ? AND groupeId = ? ORDER BY nom")
                .params(tenantId, groupeId).query((rs, i) -> new GroupesTransverses.Appartenance(rs.getString("personId"), rs.getString("nom"),
                        rs.getString("depuis"), rs.getString("jusqua"), rs.getInt("moderateur") == 1)).list();
    }

    @Override
    public Optional<String> nomDe(String tenantId, String personId) {
        return jdbc.sql(NOM).params(tenantId, personId).query(String.class).optional();
    }

    /** Le texte est un paramètre lié du {@code LIKE}, ses jokers neutralisés : jamais concaténé au SQL. */
    @Override
    public List<GroupesTransversesPort.Personne> personnes(String tenantId, String texte, int limite) {
        String motif = "%" + texte.toLowerCase(java.util.Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.sql("SELECT COALESCE(u.loginName, u.username) AS personId, COALESCE(MAX(r.nom), COALESCE(u.loginName, u.username)) AS nom FROM users u "
                        + "LEFT JOIN eff_ressources r ON r.utilisateurId = u.id AND r.tenantId = u.tenantId AND r.actif = 1 "
                        + "WHERE u.tenantId = ? AND u.active = 1 GROUP BY u.id "
                        + "HAVING lower(nom) LIKE ? ESCAPE '\\' OR lower(personId) LIKE ? ESCAPE '\\' ORDER BY nom LIMIT ?")
                .params(tenantId, motif, motif, limite)
                .query((rs, i) -> new GroupesTransversesPort.Personne(rs.getString("personId"), rs.getString("nom"))).list();
    }

    @Override
    public void enregistrer(String tenantId, GroupesTransverses.Groupe g) {
        jdbc.sql("INSERT INTO msg_groupes_transverses (id, tenantId, nom, nature, actif) VALUES (?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, id) DO UPDATE SET nom = excluded.nom, actif = excluded.actif")
                .params(g.id(), tenantId, g.nom(), g.nature().code(), g.actif() ? 1 : 0).update();
    }

    @Override
    public void enregistrer(String tenantId, String groupeId, GroupesTransverses.Appartenance a) {
        jdbc.sql("INSERT INTO msg_groupe_membres (tenantId, groupeId, personId, nom, depuis, jusqua, moderateur) VALUES (?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (tenantId, groupeId, personId) DO UPDATE SET nom = excluded.nom, depuis = excluded.depuis, jusqua = excluded.jusqua, "
                        + "moderateur = excluded.moderateur")
                .params(tenantId, groupeId, a.personId(), a.nom(), a.depuis(), a.jusqua(), a.moderateur() ? 1 : 0).update();
    }

    @Override
    public boolean retirer(String tenantId, String groupeId, String personId) {
        return jdbc.sql("DELETE FROM msg_groupe_membres WHERE tenantId = ? AND groupeId = ? AND personId = ?").params(tenantId, groupeId, personId).update() > 0;
    }
}
