package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotPostesPort;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Poste;

import java.util.List;
import java.util.Set;

/** Les lectures des postes — jumeau de la partie lecture de {@code servicePostes.ts}. */
public class LecturePostes {

    private final DepotPostesPort depot;
    private final LecturePerimetres perimetres;

    public LecturePostes(DepotPostesPort depot, LecturePerimetres perimetres) {
        this.depot = depot;
        this.perimetres = perimetres;
    }

    public Poste exiger(Contexte ctx, String id) {
        return depot.lire(ctx.tenantId(), id).orElseThrow(() -> new IntrouvableException("poste « " + id + " » introuvable"));
    }

    /** {@code etat} et {@code uniteCode} facultatifs (nuls ou vides : sans filtre). */
    public List<Poste> lister(Contexte ctx, String etat, String uniteCode) {
        Set<String> visibles = ctx.estAdministrateur() ? null : perimetres.unitesVisibles(ctx, Perimetres.Niveau.LECTURE);
        return depot.lister(ctx.tenantId()).stream()
                .filter(p -> visibles == null || visibles.contains(p.uniteCode()))
                .filter(p -> etat == null || etat.isEmpty() || p.etat().name().equals(etat))
                .filter(p -> uniteCode == null || uniteCode.isEmpty() || p.uniteCode().equals(uniteCode)).toList();
    }
}
