package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureAlignementOrganigramme;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.HashMap;
import java.util.Map;

/**
 * L'application de l'alignement des unités sur l'organigramme (lot R12) — jumeau de {@code appliquerAlignement}.
 * Chaque changement passe par le référentiel : il garde son historique et son entrée au journal. Les parents
 * d'abord — le plan est déjà ordonné par profondeur.
 */
public class GestionnaireAlignementOrganigramme {

    private static final String PARENT = "parent";

    /** Ce que l'alignement a fait. */
    public record Resultat(String date, int creees, int modifiees, int ignorees) { }

    private final LectureAlignementOrganigramme lecture;
    private final GestionnaireReferentiels referentiels;

    public GestionnaireAlignementOrganigramme(LectureAlignementOrganigramme lecture, GestionnaireReferentiels referentiels) {
        this.lecture = lecture;
        this.referentiels = referentiels;
    }

    private static Map<String, Object> attributs(String parent) {
        return parent == null ? Map.of() : Map.of(PARENT, parent);
    }

    public Resultat appliquer(Contexte ctx, Object dateDemandee) {
        LectureAlignementOrganigramme.Apercu p = lecture.apercu(ctx, dateDemandee);
        String type = TypeReferentiel.UNITES.code();
        p.creer().forEach(u -> {
            Map<String, Object> e = new HashMap<>();
            e.put("code", u.code());
            e.put("libelleFr", u.libelle());
            e.put("effectifDepuis", p.date());
            e.put("attributs", attributs(u.parent()));
            referentiels.creer(ctx, type, e);
        });
        p.modifier().forEach(u -> referentiels.remplacer(ctx, type, u.code(),
                Map.of("libelleFr", u.libelle(), "depuis", p.date(), "attributs", attributs(u.parent()))));
        return new Resultat(p.date(), p.creer().size(), p.modifier().size(), p.ignorees().size());
    }
}
