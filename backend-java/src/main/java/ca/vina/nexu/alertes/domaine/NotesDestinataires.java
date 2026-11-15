package ca.vina.nexu.alertes.domaine;

/**
 * <strong>La note sur la fiche du destinataire</strong> (ST-G12, critère 2, dans sa lecture « note interne » de Q-DOS) :
 * une note courte et datée sur une personne — « joindre sur le cellulaire de garde », « pas d'appel avant 7 h » —
 * visible des expéditeurs autorisés. Elle ne passe jamais en clair au journal : on y consigne qu'elle a changé, pas ce
 * qu'elle dit.
 */
public final class NotesDestinataires {

    public static final int LONGUEUR_MAX = 280;

    private NotesDestinataires() {
    }

    /** La note courante d'une personne : son texte, quand et par qui elle a été écrite. */
    public record Note(String personId, String texte, String ecriteLe, String ecritePar) {

        public static Note ecrire(String personId, String texte, String instant, String auteur) {
            return new Note(Alertes.texte("personId", personId, Alertes.TITRE_MAX), Alertes.texte("texte", texte, LONGUEUR_MAX), instant, auteur);
        }
    }
}
