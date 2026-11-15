package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.effectifs.domaine.DemandesFlux;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.DemandeApprobation;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.EtapeApprobation;
import ca.vina.nexu.effectifs.domaine.DemandesFlux.RefusEtape;
import ca.vina.nexu.effectifs.domaine.FluxApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.ObjetFlux;
import ca.vina.nexu.effectifs.domaine.FluxApprobation.RegleApprobation;
import ca.vina.nexu.effectifs.domaine.FluxApprobationsPort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Les flux d'approbation des heures branchés sur le <strong>moteur d'approbation générique</strong> (contexte
 * {@code approbations}) : ce dernier ne connaît pas les effectifs, l'adaptateur traduit dans les deux sens.
 */
@Repository("effectifsAdaptateurFluxApprobations")
public class AdaptateurFluxApprobations implements FluxApprobationsPort {

    private final Approbations.Depot moteur;

    public AdaptateurFluxApprobations(Approbations.Depot moteur) {
        this.moteur = moteur;
    }

    private static RegleApprobation regle(Approbations.Regle r) {
        return new RegleApprobation(r.id(), r.field(), FluxApprobation.OperateurFlux.de(r.operator()).orElseThrow(), r.value(),
                r.level(), r.approverRole(), r.approverId());
    }

    private static Approbations.Regle versMoteur(ObjetFlux objet, RegleApprobation r) {
        return new Approbations.Regle(r.id(), objet.typeDocument(), r.field(), r.operator().code(), r.value(), r.level(), r.approverRole(), r.approverId());
    }

    @Override
    public List<RegleApprobation> regles(String tenantId, ObjetFlux objet) {
        return moteur.regles(tenantId, objet.typeDocument()).stream().map(AdaptateurFluxApprobations::regle).toList();
    }

    @Override
    public String creerRegle(String tenantId, ObjetFlux objet, RegleApprobation r) {
        return (String) moteur.creerRegle(tenantId, versMoteur(objet, r), r.value()).get("id");
    }

    @Override
    public void retirerRegle(String tenantId, String id) {
        moteur.retirerRegle(tenantId, id);
    }

    @Override
    public boolean sAppliquent(List<RegleApprobation> regles, Map<String, Object> contexte) {
        return !Approbations.selectionner(regles.stream().map(r -> versMoteur(ObjetFlux.FEUILLE, r)).toList(), contexte).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static DemandeApprobation demande(Map<String, Object> d) {
        List<EtapeApprobation> etapes = ((List<Map<String, Object>>) d.get("steps")).stream().map(s -> new EtapeApprobation((String) s.get("id"),
                (Integer) s.get("level"), (String) s.get("approverRole"), (String) s.get("approverId"), DemandesFlux.StatutApprobation.de((String) s.get("status")),
                (String) s.get("decidedBy"), (String) s.get("decidedAt"), (String) s.get("onBehalfOf"))).toList();
        return new DemandeApprobation((String) d.get("id"), (String) d.get("documentId"), DemandesFlux.StatutApprobation.de((String) d.get("status")), etapes);
    }

    @Override
    public Optional<DemandeApprobation> demandeDuDocument(String tenantId, ObjetFlux objet, String documentId) {
        return moteur.demandeDuDocument(tenantId, objet.typeDocument(), documentId).map(AdaptateurFluxApprobations::demande);
    }

    @Override
    public List<DemandeApprobation> enCours(String tenantId, ObjetFlux objet) {
        return moteur.demandes(tenantId, DemandesFlux.StatutApprobation.EN_ATTENTE.code()).stream()
                .filter(d -> objet.typeDocument().equals(d.get("documentType"))).map(AdaptateurFluxApprobations::demande).toList();
    }

    @Override
    public boolean retirer(String tenantId, ObjetFlux objet, String documentId) {
        return moteur.demandeDuDocument(tenantId, objet.typeDocument(), documentId)
                .map(d -> moteur.retirer(tenantId, (String) d.get("id"))).orElse(false);
    }

    @Override
    public Set<String> suppleePour(String tenantId, String suppleantId) {
        return moteur.delegationsRecues(tenantId, suppleantId).stream().map(Approbations.Delegation::fromUserId).collect(Collectors.toSet());
    }

    @Override
    public Optional<DemandesFlux.Suppleance> suppleanceDe(String tenantId, String approbateurId) {
        return moteur.delegationActive(tenantId, approbateurId).map(d -> new DemandesFlux.Suppleance(d.toUserId(), d.toDate()));
    }

    @Override
    public DemandeApprobation ouvrir(String tenantId, NouvelleDemande d) {
        List<Approbations.Regle> regles = d.regles() == null ? null : d.regles().stream().map(r -> versMoteur(d.objet(), r)).toList();
        return demande(moteur.creerDemande(tenantId, new Approbations.NouvelleDemande(d.objet().typeDocument(), d.documentId(), d.contexte(),
                d.demandeurId(), regles)));
    }

    @Override
    public DemandeApprobation decider(String tenantId, String etapeId, DecisionEtape d) {
        Approbations.Decideur decideur = new Approbations.Decideur(d.acteurId(), d.role(),
                x -> d.porte().test(FluxApprobation.approbateurDe(x.approverRole(), x.approverId())));
        try {
            return demande(moteur.decider(tenantId, etapeId, d.decision().code(), decideur));
        } catch (Approbations.NonAutoriseException e) {
            throw new DemandesFlux.EtapeRefuseeException(switch (e.refus()) {
                case NON_AUTORISE -> RefusEtape.NON_PORTEE;
                case DEMANDEUR -> RefusEtape.DEMANDEUR;
                case PLUSIEURS_NIVEAUX -> RefusEtape.AUTRE;
            }, e.getMessage());
        } catch (IllegalStateException e) {
            throw new DemandesFlux.EtapeRefuseeException(RefusEtape.AUTRE, e.getMessage());
        }
    }
}
