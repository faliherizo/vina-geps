package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.ApercuImpact;
import ca.vina.nexu.effectifs.domaine.ContenuRegle;
import ca.vina.nexu.effectifs.domaine.ContexteRegle;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotReglesPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.EvaluationRegle;
import ca.vina.nexu.effectifs.domaine.FicheRegle;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Regle;

import java.util.List;
import java.util.Map;

/**
 * Les lectures du moteur de règles (lot R2) : le catalogue de faits, les règles et leurs versions, les
 * décisions conservées, et l'aperçu d'impact d'un brouillon — qui n'écrit rien.
 */
public class LectureRegles {

    private static final String CONTEXTE = "contexte";
    private static final String DOSSIERS = "dossiers";

    private final DepotReglesPort depot;

    public LectureRegles(DepotReglesPort depot) {
        this.depot = depot;
    }

    public Map<String, Object> faits() {
        return VuesRegles.faits();
    }

    /** Une règle et ses versions, ou 404. */
    public FicheRegle fiche(Contexte ctx, String id) {
        Regle r = depot.regle(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("règle « " + id + " » introuvable"));
        return new FicheRegle(r, depot.versions(ctx.tenantId(), id));
    }

    public Map<String, Object> lire(Contexte ctx, String id) {
        return VuesRegles.fiche(fiche(ctx, id), ctx.aujourdhui());
    }

    /** La règle seule, avec sa version courante : ce que rendent la création, la publication et le retrait. */
    public Map<String, Object> regle(Contexte ctx, String id) {
        return VuesRegles.regle(fiche(ctx, id), ctx.aujourdhui());
    }

    /** @param contexte nul = tous ; sinon un contexte connu, ou 422 */
    public List<Map<String, Object>> lister(Contexte ctx, String contexte) {
        ContexteRegle c = contexte == null ? null : ContexteRegle.de(contexte);
        List<FicheRegle> fiches = depot.regles(ctx.tenantId(), c).stream().map(r -> new FicheRegle(r, depot.versions(ctx.tenantId(), r.id()))).toList();
        return VuesRegles.regles(fiches, ctx.aujourdhui());
    }

    /** Les décisions conservées ; un filtre nul ne filtre pas. */
    public List<EvaluationRegle> evaluations(Contexte ctx, String objetType, String objetId, String contexte) {
        ContexteRegle c = contexte == null ? null : ContexteRegle.de(contexte);
        return depot.evaluations(ctx.tenantId(), new EvaluationRegle.Filtre(objetType, objetId, c));
    }

    /** L'impact d'un brouillon {@code {contexte, condition, action, message, priorite, dossiers}} contre des dossiers saisis. */
    public ApercuImpact apercu(Map<String, Object> entree) {
        ContexteRegle contexte = ContexteRegle.de(entree.get(CONTEXTE));
        ContenuRegle contenu = ContenuRegle.valider(contexte, entree);
        if (!(entree.get(DOSSIERS) instanceof List<?> dossiers)) {
            throw new EffectifsInvalideException("« dossiers » : une liste de { objetId, faits } est attendue");
        }
        return ApercuImpact.calculer(contexte, contenu, dossiers);
    }
}
