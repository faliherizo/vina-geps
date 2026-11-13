package ca.vina.nexu.organisme.domaine;

/**
 * Port du signalement d'un événement aux règles d'alerte de l'organisme. Il ne rend rien et ne lève rien :
 * l'événement signalé (un téléchargement, une connexion refusée) est déjà acquis, et une alerte qui échoue
 * ne doit ni retenir la réponse ni la changer.
 */
public interface SignalementPort {

    void signaler(String tenantId, EvenementAlerte evenement, Circonstances circonstances);
}
