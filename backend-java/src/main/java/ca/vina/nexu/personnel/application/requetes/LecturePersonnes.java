package ca.vina.nexu.personnel.application.requetes;

import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes.Personne;

import java.util.List;
import java.util.Optional;

/** Les lectures du répertoire : suggérer à partir de trois caractères, résoudre un identifiant, tout lister. */
public final class LecturePersonnes {

    /** Une liste de suggestions se lit d'un regard : au-delà, on affine la saisie. */
    public static final int LIMITE_SUGGESTIONS = 10;

    private final RepertoirePersonnes repertoire;

    public LecturePersonnes(RepertoirePersonnes repertoire) {
        this.repertoire = repertoire;
    }

    public List<Personne> suggerer(String tenantId, String saisie) {
        String texte = saisie == null ? "" : saisie.trim();
        if (texte.length() < RepertoirePersonnes.SEUIL) {
            return List.of();
        }
        return repertoire.parTexte(tenantId, texte, LIMITE_SUGGESTIONS);
    }

    public Optional<Personne> lire(String tenantId, String id) {
        return repertoire.lire(tenantId, id);
    }

    public List<Personne> tous(String tenantId) {
        return repertoire.tous(tenantId);
    }
}
