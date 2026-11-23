package ca.vina.nexu.alertes.domaine;

import java.util.Collection;
import java.util.List;

/** Port : les notes sur les destinataires d'un organisme. */
public interface NotesPort {

    /** Les notes de ces personnes ; celles qui n'en ont pas sont absentes. */
    List<NotesDestinataires.Note> notes(String tenantId, Collection<String> personIds);

    void enregistrer(String tenantId, NotesDestinataires.Note note);

    boolean effacer(String tenantId, String personId);
}
