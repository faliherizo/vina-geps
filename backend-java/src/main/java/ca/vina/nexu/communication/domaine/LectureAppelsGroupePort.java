package ca.vina.nexu.communication.domaine;

import java.util.List;
import java.util.Optional;

/** Port de lecture des appels de groupe : ce qui est rangé, sans règle. */
public interface LectureAppelsGroupePort {

    Optional<AppelsGroupe.Appel> lire(String tenantId, String appelId);

    /** L'appel non terminé d'un fil, s'il y en a un : un fil n'en porte qu'un à la fois. */
    Optional<AppelsGroupe.Appel> actifDuFil(String tenantId, String conversationId);

    /** Les appels non terminés où la personne est invitée. */
    List<AppelsGroupe.Appel> actifsDe(String tenantId, String personId);
}
