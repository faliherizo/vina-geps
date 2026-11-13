package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.AppelsDirects;
import ca.vina.nexu.communication.domaine.EcritureAppelsDirectsPort;
import ca.vina.nexu.communication.domaine.LectureAppelsDirectsPort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Les appels entre collègues dans {@code msg_appels} : lire, écrire, rien de la décision. */
public final class DepotAppelsDirects implements LectureAppelsDirectsPort, EcritureAppelsDirectsPort {

    private static final String COLONNES = "SELECT id, appelantId, appeleId, conversationId, etat, lanceLe, decrocheLe, termineLe, termineParId FROM msg_appels WHERE tenantId = ?";
    private static final String DE_LA_PERSONNE = " AND (appeleId = ? OR appelantId = ?)";
    private static final RowMapper<AppelsDirects.Appel> LIGNE = (rs, i) -> new AppelsDirects.Appel(rs.getString("id"), rs.getString("appelantId"),
            rs.getString("appeleId"), rs.getString("conversationId"), AppelsDirects.Etat.de(rs.getString("etat")), rs.getString("lanceLe"),
            rs.getString("decrocheLe"), rs.getString("termineLe"), rs.getString("termineParId"));

    private final JdbcClient jdbc;

    public DepotAppelsDirects(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AppelsDirects.Appel> lire(String tenantId, String appelId) {
        return jdbc.sql(COLONNES + " AND id = ?").params(tenantId, appelId).query(LIGNE).optional();
    }

    @Override
    public List<AppelsDirects.Appel> sonnants(String tenantId, String personId) {
        return jdbc.sql(COLONNES + " AND etat = ?" + DE_LA_PERSONNE).params(tenantId, AppelsDirects.Etat.SONNE.code(), personId, personId).query(LIGNE).list();
    }

    @Override
    public Optional<AppelsDirects.Appel> actifEntre(String tenantId, String appelantId, String appeleId) {
        return jdbc.sql(COLONNES + " AND appelantId = ? AND appeleId = ? AND etat IN (?, ?)")
                .params(tenantId, appelantId, appeleId, AppelsDirects.Etat.SONNE.code(), AppelsDirects.Etat.EN_COURS.code()).query(LIGNE).optional();
    }

    @Override
    public List<AppelsDirects.Appel> enCours(String tenantId, String personId) {
        return jdbc.sql(COLONNES + DE_LA_PERSONNE + " AND etat IN (?, ?) ORDER BY lanceLe")
                .params(tenantId, personId, personId, AppelsDirects.Etat.SONNE.code(), AppelsDirects.Etat.EN_COURS.code()).query(LIGNE).list();
    }

    @Override
    public List<AppelsDirects.Appel> historique(String tenantId, String personId, int limite) {
        return jdbc.sql(COLONNES + DE_LA_PERSONNE + " ORDER BY lanceLe DESC LIMIT ?").params(tenantId, personId, personId, limite).query(LIGNE).list();
    }

    @Override
    public void inserer(String tenantId, AppelsDirects.Appel a) {
        jdbc.sql("INSERT INTO msg_appels (id, tenantId, appelantId, appeleId, conversationId, etat, lanceLe, decrocheLe, termineLe, termineParId) VALUES (?,?,?,?,?,?,?,?,?,?)")
                .params(a.id(), tenantId, a.appelantId(), a.appeleId(), a.conversationId(), a.etat().code(), a.lanceLe(), a.decrocheLe(), a.termineLe(), a.termineParId())
                .update();
    }

    @Override
    public void changerEtat(String tenantId, AppelsDirects.Appel a) {
        jdbc.sql("UPDATE msg_appels SET etat = ?, decrocheLe = ?, termineLe = ?, termineParId = ? WHERE tenantId = ? AND id = ?")
                .params(a.etat().code(), a.decrocheLe(), a.termineLe(), a.termineParId(), tenantId, a.id()).update();
    }
}
