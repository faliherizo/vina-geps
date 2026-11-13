package ca.vina.nexu.organisme.application.commandes;

import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.OptionInvalideException;
import ca.vina.nexu.organisme.domaine.PaysAutorises;
import ca.vina.nexu.organisme.domaine.PaysAutorisesPort;
import ca.vina.nexu.organisme.domaine.ReglagesPays;

import java.util.List;

/**
 * Poser la liste des pays autorisés à la connexion (P-TN-11). Sans en-tête de pays configuré, activer la
 * restriction est refusé <strong>à la configuration</strong> — jamais découvert à la première connexion, où
 * elle bloquerait tout le monde, administration comprise.
 */
public class GestionnairePaysAutorises {

    private final PaysAutorisesPort pays;
    private final LectureOptionsOrganisme lecture;
    private final ReglagesPays reglages;

    public GestionnairePaysAutorises(PaysAutorisesPort pays, LectureOptionsOrganisme lecture, ReglagesPays reglages) {
        this.pays = pays;
        this.lecture = lecture;
        this.reglages = reglages;
    }

    public LectureOptionsOrganisme.PaysVue enregistrer(String tenantId, Object brut, Modification modification) {
        List<String> codes = PaysAutorises.paysDe(brut);
        if (!codes.isEmpty() && !reglages.estConfigure()) {
            throw new OptionInvalideException("le serveur ne reçoit pas le pays d'origine des connexions (variable PAYS_ENTETE absente) : "
                    + "activer la restriction bloquerait tout le monde. Faites configurer la passerelle d'abord.");
        }
        pays.enregistrer(tenantId, codes, modification);
        return lecture.pays(tenantId);
    }
}
