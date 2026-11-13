package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPeriodesPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.List;
import java.util.Set;

/** Les lectures des périodes de planification — jumeau de la partie lecture de {@code servicePeriodes.ts}. */
public class LecturePeriodes {

    private final DepotPeriodesPort depot;
    private final LecturePerimetres perimetres;

    public LecturePeriodes(DepotPeriodesPort depot, LecturePerimetres perimetres) {
        this.depot = depot;
        this.perimetres = perimetres;
    }

    public Periode lire(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("période « " + id + " » introuvable"));
    }

    /** La période, si l'acteur a ce niveau sur son unité. */
    public Periode exiger(Contexte ctx, String id, Perimetres.Niveau niveau) {
        Periode p = lire(ctx, id);
        perimetres.exiger(ctx, p.uniteCode(), niveau);
        return p;
    }

    public List<Periode> lister(Contexte ctx) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId()).stream().filter(p -> visibles == null || visibles.contains(p.uniteCode())).toList();
    }
}
