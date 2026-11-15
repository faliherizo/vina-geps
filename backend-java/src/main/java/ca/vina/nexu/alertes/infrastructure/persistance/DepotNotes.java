package ca.vina.nexu.alertes.infrastructure.persistance;

import ca.vina.nexu.alertes.domaine.NotesDestinataires.Note;
import ca.vina.nexu.alertes.domaine.NotesPort;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Les notes sur les destinataires (changeset 0017) : une note courante par personne. */
public class DepotNotes implements NotesPort {

    private static final String COLONNES = "SELECT personId, texte, ecriteLe, ecritePar FROM alr_notes WHERE tenantId = ? ";

    private final JdbcClient jdbc;

    public DepotNotes(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Un paramètre par personne, jamais une liste collée dans le SQL. */
    @Override
    public List<Note> notes(String tenantId, Collection<String> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>(List.of(tenantId));
        params.addAll(personIds);
        String marques = String.join(", ", java.util.Collections.nCopies(personIds.size(), "?"));
        return jdbc.sql(COLONNES + "AND personId IN (" + marques + ") ORDER BY personId").params(params).query((rs, i) -> lire(rs)).list();
    }

    @Override
    public void enregistrer(String tenantId, Note n) {
        jdbc.sql("INSERT INTO alr_notes (tenantId, personId, texte, ecriteLe, ecritePar) VALUES (?, ?, ?, ?, ?) ON CONFLICT (tenantId, personId) "
                        + "DO UPDATE SET texte = excluded.texte, ecriteLe = excluded.ecriteLe, ecritePar = excluded.ecritePar")
                .params(tenantId, n.personId(), n.texte(), n.ecriteLe(), n.ecritePar()).update();
    }

    @Override
    public boolean effacer(String tenantId, String personId) {
        return jdbc.sql("DELETE FROM alr_notes WHERE tenantId = ? AND personId = ?").params(tenantId, personId).update() > 0;
    }

    private static Note lire(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Note(rs.getString("personId"), rs.getString("texte"), rs.getString("ecriteLe"), rs.getString("ecritePar"));
    }
}
