package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.AlignementOrganigramme;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.OrganigrammePourEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.List;

/**
 * L'aperçu de l'alignement des unités sur l'organigramme (lot R12) — jumeau de {@code apercuAlignement}. L'organigramme
 * se lit par son port, jamais par ses tables ; le plan se calcule, rien ne s'écrit.
 */
public class LectureAlignementOrganigramme {

    /** Le plan à une date. */
    public record Apercu(String date, List<AlignementOrganigramme.UniteCible> creer, List<AlignementOrganigramme.Modification> modifier,
                         List<String> identiques, List<AlignementOrganigramme.Ignoree> ignorees, List<String> horsOrganigramme) {
        static Apercu de(String date, AlignementOrganigramme.Plan p) {
            return new Apercu(date, p.creer(), p.modifier(), p.identiques(), p.ignorees(), p.horsOrganigramme());
        }
    }

    private final OrganigrammePourEffectifsPort organigramme;
    private final LectureReferentiels referentiels;

    public LectureAlignementOrganigramme(OrganigrammePourEffectifsPort organigramme, LectureReferentiels referentiels) {
        this.organigramme = organigramme;
        this.referentiels = referentiels;
    }

    /** Sans date, aujourd'hui ; jamais dans le passé, parce que les référentiels ne sont pas rétroactifs. */
    static String dateDe(Contexte ctx, Object v) {
        String date = v == null || "".equals(v) ? ctx.aujourdhui() : Referentiels.dateValide(v, "date");
        if (date.compareTo(ctx.aujourdhui()) < 0) {
            throw new EffectifsInvalideException("« date » : un alignement ne se fait pas dans le passé (les référentiels ne sont pas rétroactifs)");
        }
        return date;
    }

    public Apercu apercu(Contexte ctx, Object dateDemandee) {
        String date = dateDe(ctx, dateDemandee);
        return Apercu.de(date, AlignementOrganigramme.planAlignement(organigramme.organigramme(ctx.tenantId(), date),
                referentiels.lister(ctx, TypeReferentiel.UNITES.code(), date, false)));
    }
}
