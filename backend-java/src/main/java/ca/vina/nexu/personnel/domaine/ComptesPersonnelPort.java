package ca.vina.nexu.personnel.domaine;

import java.util.Optional;

/** Port : le compte d'une personne, vu du répertoire — son établissement, sa fiche, son courriel — et son rattachement à une fiche. */
public interface ComptesPersonnelPort {

    record CompteLie(String tenantId, String ficheId, String courriel) { }

    Optional<CompteLie> compte(String utilisateurId);

    void rattacher(String utilisateurId, String ficheId);
}
