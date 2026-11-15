package ca.vina.nexu.paie.domaine;

import java.util.List;

/** Port d'écriture de la paie. */
public interface EcriturePaiePort {

    void enregistrerDossier(Trace trace, DossierRemuneration dossier);

    void enregistrerReglages(Trace trace, ReglagesPaie reglages);

    void creerCycle(String tenantId, CyclePaie cycle);

    /** Les bulletins d'un cycle préparé, remplacés en bloc : recalculer ne laisse pas de reste. */
    void remplacerBulletins(String tenantId, String cycleId, List<Bulletin> bulletins);

    void changerEtat(Trace trace, String cycleId, EtatCycle etat);
}
