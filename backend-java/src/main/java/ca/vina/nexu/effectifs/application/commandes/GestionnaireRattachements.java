package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Rattachements;
import ca.vina.nexu.effectifs.domaine.RattachementsPort;
import ca.vina.nexu.effectifs.domaine.Referentiels;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.effectifs.domaine.TypeReferentiel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>Rattacher une personne à une autre unité</strong> (addenda 8, M-09) : l'encadrement de son unité principale
 * (périmètre Gestion) la rattache, pour une période, à une unité en vigueur ; le rattachement se lit et se retire. Chaque
 * geste est au journal.
 */
public class GestionnaireRattachements {

    private static final String OBJET = "rattachement";

    /** Ce que le gestionnaire lit. */
    public record Lectures(LectureRessources ressources, LectureReferentiels referentiels, LecturePerimetres perimetres) { }

    private final RattachementsPort depot;
    private final Lectures lire;
    private final JournalEffectifsPort journal;

    public GestionnaireRattachements(RattachementsPort depot, Lectures lire, JournalEffectifsPort journal) {
        this.depot = depot;
        this.lire = lire;
        this.journal = journal;
    }

    public List<Map<String, Object>> deLaRessource(Contexte ctx, String ressourceId) {
        Ressource r = lire.ressources().exiger(ctx, ressourceId);
        lire.perimetres().exiger(ctx, r.uniteCode(), Perimetres.Niveau.LECTURE);
        return depot.deLaRessource(ctx.tenantId(), r.id()).stream().map(x -> vue(x, ctx.aujourdhui())).toList();
    }

    public Map<String, Object> ajouter(Contexte ctx, String ressourceId, Map<String, Object> entree) {
        Ressource r = gerable(ctx, ressourceId);
        Rattachements.Rattachement x = Rattachements.saisie(r, entree.get("uniteCode"), entree.get("depuis"), entree.get("jusqua"),
                depot.deLaRessource(ctx.tenantId(), r.id()));
        lire.referentiels().exigerValeur(ctx.tenantId(), TypeReferentiel.UNITES, x.uniteCode(), x.depuis());
        depot.ajouter(ctx.tenantId(), x, ctx.acteurId(), ctx.maintenant());
        Map<String, Object> v = vue(x, ctx.aujourdhui());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("rattachement.ajoute", OBJET, r.id() + ":" + x.uniteCode(), r.uniteCode(), null, v));
        return v;
    }

    public Map<String, Object> retirer(Contexte ctx, String ressourceId, String uniteCode, String depuis) {
        Ressource r = gerable(ctx, ressourceId);
        String jour = Referentiels.dateValide(depuis, "depuis");
        if (!depot.retirer(ctx.tenantId(), r.id(), uniteCode, jour)) {
            throw new IntrouvableException("« " + r.nom() + " » n'est pas rattachée à « " + uniteCode + " » depuis le " + jour);
        }
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("rattachement.retire", OBJET, r.id() + ":" + uniteCode, r.uniteCode(),
                Map.of("uniteCode", uniteCode, "depuis", jour), null));
        return Map.of("retire", uniteCode);
    }

    private Ressource gerable(Contexte ctx, String ressourceId) {
        Ressource r = lire.ressources().exiger(ctx, ressourceId);
        lire.perimetres().exiger(ctx, r.uniteCode(), Perimetres.Niveau.GESTION);
        return r;
    }

    private static Map<String, Object> vue(Rattachements.Rattachement x, String jour) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("uniteCode", x.uniteCode());
        m.put("depuis", x.depuis());
        m.put("jusqua", x.jusqua());
        m.put("actif", x.enVigueurLe(jour));
        return m;
    }
}
