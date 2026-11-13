package ca.vina.nexu.organisme.application.commandes;

import ca.vina.nexu.organisme.domaine.Alertes;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.OptionInvalideException;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglesAlertePort;
import ca.vina.nexu.organisme.domaine.Retrait;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.List;
import java.util.Map;

/** Poser et retirer les règles d'alerte de l'organisme (P-TN-10) : un événement, ses destinataires. */
public class GestionnaireReglesAlerte {

    private final ReglesAlertePort regles;
    private final Sequence sequence;

    public GestionnaireReglesAlerte(ReglesAlertePort regles, Sequence sequence) {
        this.regles = regles;
        this.sequence = sequence;
    }

    public RegleAlerte creer(String tenantId, Map<String, Object> brut, Modification modification) {
        EvenementAlerte evenement = EvenementAlerte.de(brut.get("evenement"));
        List<String> destinataires = Alertes.destinatairesDe(brut.get("destinataires"));
        RegleAlerte r = new RegleAlerte(sequence.uuid(), evenement, destinataires, modification.le(), modification.par());
        regles.inserer(tenantId, r);
        return r;
    }

    /** Une règle absente (ou d'un autre organisme) est un refus nommé, pas un succès silencieux. */
    public Retrait retirer(String tenantId, String regleId) {
        if (!regles.retirer(tenantId, regleId)) {
            throw new OptionInvalideException("règle introuvable");
        }
        return new Retrait(true);
    }
}
