package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Competences;
import ca.vina.nexu.effectifs.domaine.CompetencesPort;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.Map;

/**
 * <strong>Poser ou retirer une compétence</strong> (addenda 8, M-04) : au niveau GESTION de l'unité de la personne, pour
 * un code du référentiel en vigueur ; chaque geste est consigné au journal, avec l'avant et l'après.
 */
public class GestionnaireCompetences {

    private static final String OBJET = "competence";

    /** Ce que le gestionnaire lit : la personne, le code au référentiel, le périmètre de l'acteur. */
    public record Lectures(LectureRessources ressources, LectureReferentiels referentiels, LecturePerimetres perimetres) { }

    private final CompetencesPort depot;
    private final Lectures lire;
    private final JournalEffectifsPort journal;

    public GestionnaireCompetences(CompetencesPort depot, Lectures lire, JournalEffectifsPort journal) {
        this.depot = depot;
        this.lire = lire;
        this.journal = journal;
    }

    /** Pose, ou renouvelle, la compétence {@code code} de la personne : {@code obtenueLe} exigée, {@code echeance} facultative. */
    public Competences.Detenue poser(Contexte ctx, String ressourceId, String code, Map<String, Object> entree) {
        Ressource r = gerable(ctx, ressourceId);
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.COMPETENCES, code, ctx.aujourdhui());
        Competences.Detenue avant = actuelle(ctx, r.id(), code);
        Competences.Detenue c = Competences.saisie(r.id(), code, entree.get("obtenueLe"), entree.get("echeance"));
        depot.poser(ctx.tenantId(), c, ctx.acteurId(), ctx.maintenant());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de(avant == null ? "competence.posee" : "competence.renouvelee", OBJET, r.id() + ":" + code,
                r.uniteCode(), avant, c));
        return c;
    }

    public void retirer(Contexte ctx, String ressourceId, String code) {
        Ressource r = gerable(ctx, ressourceId);
        Competences.Detenue avant = actuelle(ctx, r.id(), code);
        if (avant == null || !depot.retirer(ctx.tenantId(), r.id(), code)) {
            throw new IntrouvableException("« " + r.nom() + " » ne détient pas la compétence « " + code + " »");
        }
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("competence.retiree", OBJET, r.id() + ":" + code, r.uniteCode(), avant, null));
    }

    /** Une compétence s'atteste par l'encadrement de l'unité, jamais par la personne elle-même. */
    private Ressource gerable(Contexte ctx, String ressourceId) {
        Ressource r = lire.ressources().exiger(ctx, ressourceId);
        lire.perimetres().exiger(ctx, r.uniteCode(), Perimetres.Niveau.GESTION);
        return r;
    }

    private Competences.Detenue actuelle(Contexte ctx, String ressourceId, String code) {
        return depot.deLaRessource(ctx.tenantId(), ressourceId).stream().filter(d -> d.code().equals(code)).findFirst().orElse(null);
    }
}
