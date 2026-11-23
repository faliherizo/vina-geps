package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.NotesDestinataires.Note;
import ca.vina.nexu.alertes.domaine.NotesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** <strong>Tenir la note d'un destinataire</strong> (ST-G12) : l'écrire, la remplacer, l'effacer ; la lire pour plusieurs. */
public final class GestionnaireNotes {

    private final NotesPort notes;
    private final AnnuaireAlertes annuaire;
    private final Clock horloge;

    public GestionnaireNotes(NotesPort notes, AnnuaireAlertes annuaire, Clock horloge) {
        this.notes = notes;
        this.annuaire = annuaire;
        this.horloge = horloge;
    }

    /** Une note ne se pose que sur une personne de l'organisme ; elle remplace la précédente. */
    public Note ecrire(Acteur acteur, String personId, String texte) {
        annuaire.personne(acteur.tenantId(), personId).orElseThrow(() -> RefusAlerte.introuvable("personne introuvable"));
        Note n = Note.ecrire(personId, texte, Instant.now(horloge).toString(), acteur.personId());
        notes.enregistrer(acteur.tenantId(), n);
        return n;
    }

    public void effacer(Acteur acteur, String personId) {
        if (!notes.effacer(acteur.tenantId(), personId)) {
            throw RefusAlerte.introuvable("aucune note sur cette personne");
        }
    }

    public List<Note> lire(Acteur acteur, List<String> personIds) {
        return notes.notes(acteur.tenantId(), personIds);
    }
}
