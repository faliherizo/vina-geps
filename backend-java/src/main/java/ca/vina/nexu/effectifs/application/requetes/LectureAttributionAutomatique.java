package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.FinDeCollectePort;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Perimetres;

import java.util.Optional;

/**
 * Où en est l'attribution automatique d'une période (P-GH-4) — jumeau de {@code attributionAutomatique} de
 * {@code disponibilites/service.ts} : quand elle part, où en est sa tâche, et ce qu'a donné son dernier passage.
 */
public class LectureAttributionAutomatique {

    /** L'état lu ; {@code echeanceLe}, {@code statut} et {@code derniere} sont nuls tant que rien n'a été planifié ou fait. */
    public record Etat(String periodeId, String finCollecte, String echeanceLe, String statut, FinDeCollectePort.Bilan derniere) { }

    private final LecturePeriodes periodes;
    private final FinDeCollectePort ordonnanceur;

    public LectureAttributionAutomatique(LecturePeriodes periodes, FinDeCollectePort ordonnanceur) {
        this.periodes = periodes;
        this.ordonnanceur = ordonnanceur;
    }

    public Etat lire(Contexte ctx, String periodeId) {
        Periode p = periodes.exiger(ctx, periodeId, Perimetres.Niveau.LECTURE);
        Optional<FinDeCollectePort.Tache> tache = ordonnanceur.derniereTache(ctx.tenantId(), periodeId);
        return new Etat(periodeId, p.finCollecte(), tache.map(FinDeCollectePort.Tache::echeanceLe).orElse(null),
                tache.map(FinDeCollectePort.Tache::statut).orElse(null),
                ordonnanceur.dernierBilan(ctx.tenantId(), periodeId, GestionnaireAttributionFinDeCollecte.MECANISME).orElse(null));
    }
}
