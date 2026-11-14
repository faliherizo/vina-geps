package ca.vina.nexu.communication.infrastructure.persistance;

import ca.vina.nexu.communication.domaine.AppelsGroupe;
import ca.vina.nexu.communication.domaine.EcritureAppelsGroupePort;
import ca.vina.nexu.communication.domaine.LectureAppelsGroupePort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;

/** Les appels de groupe dans {@code msg_appels_groupe} et leurs invités dans {@code msg_appels_groupe_participants}. */
public final class DepotAppelsGroupe implements LectureAppelsGroupePort, EcritureAppelsGroupePort {

    private static final String TERMINAUX = "('termine', 'manque')";

    private final JdbcClient jdbc;

    public DepotAppelsGroupe(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** L'en-tête d'un appel, lu seul : les invités se lisent après, la première lecture refermée. */
    private record Entete(String id, String conversationId, String lanceParId, String etat, String lanceLe, String debuteLe, String termineLe) { }

    private AppelsGroupe.Appel complet(String tenantId, Entete e) {
        return new AppelsGroupe.Appel(e.id(), e.conversationId(), e.lanceParId(), AppelsGroupe.EtatAppel.de(e.etat()), e.lanceLe(), e.debuteLe(),
                e.termineLe(), invites(tenantId, e.id()));
    }

    private List<AppelsGroupe.Invite> invites(String tenantId, String appelId) {
        return jdbc.sql("SELECT personId, etat, rejointLe, partiLe FROM msg_appels_groupe_participants WHERE tenantId = ? AND appelId = ? ORDER BY rowid")
                .params(tenantId, appelId)
                .query((rs, i) -> new AppelsGroupe.Invite(rs.getString("personId"), AppelsGroupe.EtatInvite.de(rs.getString("etat")),
                        rs.getString("rejointLe"), rs.getString("partiLe")))
                .list();
    }

    private List<Entete> entetes(String condition, Object... params) {
        return jdbc.sql("SELECT a.id, a.conversationId, a.lanceParId, a.etat, a.lanceLe, a.debuteLe, a.termineLe FROM msg_appels_groupe a WHERE " + condition
                        + " ORDER BY a.lanceLe DESC")
                .params(params)
                .query((rs, i) -> new Entete(rs.getString("id"), rs.getString("conversationId"), rs.getString("lanceParId"), rs.getString("etat"),
                        rs.getString("lanceLe"), rs.getString("debuteLe"), rs.getString("termineLe")))
                .list();
    }

    @Override
    public Optional<AppelsGroupe.Appel> lire(String tenantId, String appelId) {
        return entetes("a.tenantId = ? AND a.id = ?", tenantId, appelId).stream().findFirst().map(e -> complet(tenantId, e));
    }

    @Override
    public Optional<AppelsGroupe.Appel> actifDuFil(String tenantId, String conversationId) {
        return entetes("a.tenantId = ? AND a.conversationId = ? AND a.etat NOT IN " + TERMINAUX, tenantId, conversationId).stream().findFirst()
                .map(e -> complet(tenantId, e));
    }

    @Override
    public List<AppelsGroupe.Appel> actifsDe(String tenantId, String personId) {
        return entetes("a.tenantId = ? AND a.etat NOT IN " + TERMINAUX + " AND EXISTS (SELECT 1 FROM msg_appels_groupe_participants p "
                + "WHERE p.tenantId = a.tenantId AND p.appelId = a.id AND p.personId = ?)", tenantId, personId).stream()
                .map(e -> complet(tenantId, e)).toList();
    }

    @Override
    public void inserer(String tenantId, AppelsGroupe.Appel a) {
        jdbc.sql("INSERT INTO msg_appels_groupe (id, tenantId, conversationId, lanceParId, etat, lanceLe, debuteLe, termineLe) VALUES (?,?,?,?,?,?,?,?)")
                .params(a.id(), tenantId, a.conversationId(), a.lanceParId(), a.etat().code(), a.lanceLe(), a.debuteLe(), a.termineLe()).update();
        for (AppelsGroupe.Invite i : a.invites()) {
            jdbc.sql("INSERT INTO msg_appels_groupe_participants (tenantId, appelId, personId, etat, rejointLe, partiLe) VALUES (?,?,?,?,?,?)")
                    .params(tenantId, a.id(), i.personId(), i.etat().code(), i.rejointLe(), i.partiLe()).update();
        }
    }

    @Override
    public void enregistrer(String tenantId, AppelsGroupe.Appel a) {
        jdbc.sql("UPDATE msg_appels_groupe SET etat = ?, debuteLe = ?, termineLe = ? WHERE tenantId = ? AND id = ?")
                .params(a.etat().code(), a.debuteLe(), a.termineLe(), tenantId, a.id()).update();
        for (AppelsGroupe.Invite i : a.invites()) {
            jdbc.sql("UPDATE msg_appels_groupe_participants SET etat = ?, rejointLe = ?, partiLe = ? WHERE tenantId = ? AND appelId = ? AND personId = ?")
                    .params(i.etat().code(), i.rejointLe(), i.partiLe(), tenantId, a.id(), i.personId()).update();
        }
    }
}
