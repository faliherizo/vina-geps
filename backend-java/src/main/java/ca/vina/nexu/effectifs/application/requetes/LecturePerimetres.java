package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPerimetresPort;
import ca.vina.nexu.effectifs.domaine.HorsPerimetreException;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Les périmètres et la <strong>garde de périmètre</strong> — jumeau de {@code servicePerimetres.ts}.
 * {@link #exiger} est ce que chaque écriture des lots R3 à R10 appelle : le rôle dit ce qu'on sait faire,
 * le périmètre dit <strong>où</strong>. Refus par défaut.
 */
public class LecturePerimetres {

    private final DepotPerimetresPort depot;
    private final LectureReferentiels referentiels;

    public LecturePerimetres(DepotPerimetresPort depot, LectureReferentiels referentiels) {
        this.depot = depot;
        this.referentiels = referentiels;
    }

    public List<Perimetres.Perimetre> duCompte(Contexte ctx, String utilisateurId) {
        return depot.duCompte(ctx.tenantId(), utilisateurId);
    }

    /** Les unités visibles au niveau voulu, aujourd'hui. Un administrateur les voit toutes. */
    public Set<String> unitesVisibles(Contexte ctx, Perimetres.Niveau niveau) {
        List<Perimetres.Unite> unites = referentiels.unitesEnVigueur(ctx.tenantId(), ctx.aujourdhui());
        if (ctx.estAdministrateur()) {
            Set<String> toutes = new LinkedHashSet<>();
            unites.forEach(u -> toutes.add(u.code()));
            return toutes;
        }
        return Perimetres.unitesAccessibles(depot.duCompte(ctx.tenantId(), ctx.acteurId()), unites, niveau, ctx.aujourdhui());
    }

    /** La garde : 403 si l'acteur n'a pas ce niveau sur cette unité. */
    public void exiger(Contexte ctx, String uniteCode, Perimetres.Niveau niveau) {
        if (!ctx.estAdministrateur() && !unitesVisibles(ctx, niveau).contains(uniteCode)) {
            throw new HorsPerimetreException("unité « " + uniteCode + " » hors de votre périmètre (niveau " + niveau + " requis)");
        }
    }

    public Map<String, Object> mesUnites(Contexte ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("toutes", ctx.estAdministrateur());
        m.put("lecture", new TreeSet<>(unitesVisibles(ctx, Perimetres.Niveau.LECTURE)));
        m.put("planification", new TreeSet<>(unitesVisibles(ctx, Perimetres.Niveau.PLANIFICATION)));
        m.put("gestion", new TreeSet<>(unitesVisibles(ctx, Perimetres.Niveau.GESTION)));
        return m;
    }
}
