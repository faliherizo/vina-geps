package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port de lecture des profils de pointage (E-GP-1, E-GP-2, P-GP-1) : la lecture seule, que le pointage emprunte. */
public interface LectureProfilsPointagePort {

    /** Les profils de l'organisme, par code. */
    List<ProfilsPointage.ProfilPointage> lister(String tenantId);
}
