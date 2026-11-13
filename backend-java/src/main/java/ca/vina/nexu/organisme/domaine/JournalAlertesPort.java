package ca.vina.nexu.organisme.domaine;

import java.util.List;

/** Port du journal des alertes : chaque déclenchement s'y consigne, courriel parti ou non. */
public interface JournalAlertesPort {

    void consigner(String tenantId, AlerteConsignee alerte);

    /** Les dernières alertes de l'organisme, les plus récentes d'abord. */
    List<AlerteConsignee> lire(String tenantId, int limite);
}
