package ca.vina.nexu.personnel.application.commandes;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Ajouter une personne au répertoire, ou corriger sa fiche. Sans ce geste, le répertoire d'un
 * organisme neuf restait vide : aucun avis ne partait « par le nom », aucun champ « personne » ne
 * trouvait quiconque.
 */
public final class GestionnairePersonnes {

    static final String INTROUVABLE = "employé introuvable : ";

    private final FichesPersonnes.Ecriture ecriture;
    private final RepertoirePersonnes lecture;
    private final Sequence sequence;
    private final Clock horloge;

    public GestionnairePersonnes(FichesPersonnes.Ecriture ecriture, RepertoirePersonnes lecture, Sequence sequence, Clock horloge) {
        this.ecriture = ecriture;
        this.lecture = lecture;
        this.sequence = sequence;
        this.horloge = horloge;
    }

    public RepertoirePersonnes.Personne creer(String tenantId, FichesPersonnes.Demande demande) {
        FichesPersonnes.Fiche f = FichesPersonnes.fiche(sequence.uuid(), demande, LocalDate.now(horloge).toString());
        ecriture.creer(tenantId, f);
        return lecture.lire(tenantId, f.id()).orElseThrow();
    }

    public RepertoirePersonnes.Personne modifier(String tenantId, String id, FichesPersonnes.Demande demande) {
        FichesPersonnes.Fiche f = FichesPersonnes.fiche(id, demande, LocalDate.now(horloge).toString());
        if (!ecriture.modifier(tenantId, f)) {
            throw new IntrouvableException(INTROUVABLE + id);
        }
        return lecture.lire(tenantId, id).orElseThrow();
    }
}
