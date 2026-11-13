package ca.vina.nexu.organisme.domaine;

import java.util.List;

/** Une règle d'alerte : un événement, ses destinataires, et qui l'a posée. */
public record RegleAlerte(String id, EvenementAlerte evenement, List<String> destinataires, String creeLe, String creePar) {
}
