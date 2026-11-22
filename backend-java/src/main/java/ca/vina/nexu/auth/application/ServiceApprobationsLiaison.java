package ca.vina.nexu.auth.application;

import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DemandesLiaison;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.IdentitesExternes.LiaisonRefuseeException;
import ca.vina.nexu.auth.domaine.IdentitesExternes.Motif;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * <strong>L'administration des demandes de liaison</strong> (ADR 0093) : l'administrateur de l'organisme voit les
 * demandes en attente de <em>son</em> organisme, et les approuve — l'identité est alors reliée au compte candidat —
 * ou les refuse. Une demande d'un autre organisme est introuvable, pas interdite : rien n'en est révélé.
 */
public class ServiceApprobationsLiaison {

    private final ServiceResolutionIdentite.Ports ports;
    private final Clock horloge;

    public ServiceApprobationsLiaison(ServiceResolutionIdentite.Ports ports, Clock horloge) {
        this.ports = ports;
        this.horloge = horloge;
    }

    public List<DemandesLiaison.Demande> enAttente(String tenantId) {
        return ports.demandes().enAttente(tenantId);
    }

    private DemandesLiaison.Demande enAttente(String tenantId, String id) {
        return ports.demandes().parId(id)
                .filter(d -> d.tenantId().equals(tenantId) && d.statut() == DemandesLiaison.Statut.EN_ATTENTE)
                .orElseThrow(() -> new LiaisonRefuseeException(Motif.DEMANDE_INTROUVABLE));
    }

    /** Relie l'identité au compte candidat, s'il est toujours actif et de l'organisme, et si l'identité est encore libre. */
    public DemandesLiaison.Demande approuver(String tenantId, String id, String adminId) {
        DemandesLiaison.Demande d = enAttente(tenantId, id);
        Compte c = ports.comptes().parId(d.compteId()).orElseThrow(() -> new LiaisonRefuseeException(Motif.COMPTE_INTROUVABLE));
        IdentitesExternes.Provenance provenance = new IdentitesExternes.Provenance(d.annuaireId(), Optional.of(d.tenantId()));
        IdentitesExternes.autoriser(c, provenance);
        if (ports.registre().trouver(d.cle()).isPresent()) {
            throw new LiaisonRefuseeException(Motif.DEJA_LIEE);
        }
        ports.ecriture().lier(new IdentitesExternes.Identite(d.cle(), c.id(), provenance, horloge.instant(), adminId,
                IdentitesExternes.Preuve.APPROBATION, Optional.empty()));
        DemandesLiaison.Demande decidee = d.decider(DemandesLiaison.Statut.APPROUVEE, horloge.instant(), adminId);
        ports.ecritureDemandes().decider(decidee);
        return decidee;
    }

    public DemandesLiaison.Demande refuser(String tenantId, String id, String adminId) {
        DemandesLiaison.Demande decidee = enAttente(tenantId, id).decider(DemandesLiaison.Statut.REFUSEE, horloge.instant(), adminId);
        ports.ecritureDemandes().decider(decidee);
        return decidee;
    }
}
