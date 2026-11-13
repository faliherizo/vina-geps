package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.Periode;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.partage.securite.Role;

import java.util.Optional;

/**
 * <strong>La fin de collecte, sans geste du planificateur</strong> (P-GH-4) — jumeau de {@code finDeCollecte} de
 * {@code disponibilites/service.ts}. À l'échéance planifiée à l'ouverture de la collecte, la période passe en confection et
 * ses places vacantes s'attribuent depuis les disponibilités.
 *
 * <p><strong>Idempotente</strong> : rejouée, elle ne trouve plus que les places restées vacantes. Une période publiée ou
 * close n'est plus touchée. Le contexte est celui du mécanisme : c'est lui qu'on lit comme acteur au journal, et l'instant
 * est celui du tic, jamais l'horloge.
 */
public class FinDeCollecteAutomatique {

    private final GestionnairePeriodes periodes;
    private final GestionnaireAttributionFinDeCollecte attribution;

    public FinDeCollecteAutomatique(GestionnairePeriodes periodes, GestionnaireAttributionFinDeCollecte attribution) {
        this.periodes = periodes;
        this.attribution = attribution;
    }

    /** Ferme la collecte et attribue ; vide quand la période n'est pas (ou plus) en confection. */
    public Optional<GestionnaireAttributionFinDeCollecte.Bilan> executer(String tenantId, String periodeId, String maintenant) {
        Contexte ctx = new Contexte(tenantId, GestionnaireAttributionFinDeCollecte.MECANISME, Role.ADMIN.code(), maintenant);
        Periode p = periodes.fermerCollecte(ctx, periodeId, GestionnaireAttributionFinDeCollecte.MECANISME);
        if (p.etat() != Planification.EtatPeriode.CONFECTION) {
            return Optional.empty();
        }
        return Optional.of(attribution.attribuer(ctx, periodeId));
    }
}
