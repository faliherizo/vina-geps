package ca.vina.nexu.paie.application.commandes;

import ca.vina.nexu.paie.domaine.DossierRemuneration;
import ca.vina.nexu.paie.domaine.EcriturePaiePort;
import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.ReglagesPaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;

/** Tenir les dossiers de rémunération et les réglages de paie de l'organisme. */
public class GestionnaireDossiersPaie {

    private final EcriturePaiePort ecriture;
    private final PersonnesPourPaiePort personnes;

    public GestionnaireDossiersPaie(EcriturePaiePort ecriture, PersonnesPourPaiePort personnes) {
        this.ecriture = ecriture;
        this.personnes = personnes;
    }

    /** Un dossier se tient pour une ressource d'effectifs de l'organisme, et pour nulle autre. */
    public DossierRemuneration enregistrer(Trace trace, DossierRemuneration dossier) {
        boolean connue = personnes.personnes(trace.tenantId()).stream().anyMatch(p -> p.ressourceId().equals(dossier.ressourceId()));
        if (!connue) {
            throw RefusPaie.introuvable("personne « " + dossier.ressourceId() + " » introuvable dans les effectifs de l'organisme");
        }
        ecriture.enregistrerDossier(trace, dossier);
        return dossier;
    }

    public ReglagesPaie regler(Trace trace, ReglagesPaie reglages) {
        ecriture.enregistrerReglages(trace, reglages);
        return reglages;
    }
}
