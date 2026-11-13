package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Milieu;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.List;
import java.util.Set;

/** Les lectures des milieux — jumeau de la partie lecture de {@code serviceMilieux.ts}. */
public class LectureMilieux {

    private final DepotMilieuxPort depot;
    private final LecturePerimetres perimetres;

    public LectureMilieux(DepotMilieuxPort depot, LecturePerimetres perimetres) {
        this.depot = depot;
        this.perimetres = perimetres;
    }

    /** Le milieu, sans contrôle de périmètre — réservé aux services qui n'en renvoient que le verdict (pointage). */
    public Milieu lire(String tenantId, String id) {
        return depot.lire(tenantId, id).orElseThrow(() -> new IntrouvableException("milieu « " + id + " » introuvable"));
    }

    /** Le milieu, s'il est visible par l'acteur. Utilisé par les autres lots (quarts, pointage). */
    public Milieu exiger(Contexte ctx, String id) {
        Milieu m = lire(ctx.tenantId(), id);
        perimetres.exiger(ctx, m.uniteCode(), Perimetres.Niveau.LECTURE);
        return m;
    }

    public List<Milieu> lister(Contexte ctx) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId()).stream().filter(m -> visibles == null || visibles.contains(m.uniteCode())).toList();
    }
}
