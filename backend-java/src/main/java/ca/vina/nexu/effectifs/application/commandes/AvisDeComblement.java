package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import ca.vina.nexu.effectifs.domaine.Ressource;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;

import java.util.List;

/**
 * <strong>L'avis d'une place à combler</strong>, rattaché à son comblement (lot U5, AD-07) : ce que chaque canal a
 * rendu est gardé pour l'écran du comblement. Partagé par l'offre simultanée, l'attribution d'office et l'offre en
 * cascade, qui avise un rang à la fois : l'encadrement lit tous les avis d'une place au même endroit.
 */
public class AvisDeComblement {

    private static final String COURRIEL = "courriel";
    private static final String ECHEC = "failed";

    /** Ce qu'on dit, sous quel événement du catalogue d'avis, et l'écran où agir. */
    public record Message(String evenement, String sujet, String corps, String lien) { }

    private final LectureRessources ressources;
    private final DepotComblementsAbsencesPort depot;
    private final PublicationAvis avis;

    public AvisDeComblement(LectureRessources ressources, DepotComblementsAbsencesPort depot, PublicationAvis avis) {
        this.ressources = ressources;
        this.depot = depot;
        this.avis = avis;
    }

    /** Avise une ressource ; un avis n'échoue jamais le geste qui l'envoie. */
    public void aviser(Contexte sys, String comblementId, String ressourceId, Message m) {
        Ressource r = ressources.exiger(sys, ressourceId);
        for (PublicationAvis.Envoi e : envois(sys, r, m)) {
            depot.aviser(sys.tenantId(), new DepotComblementsAbsencesPort.Avis(comblementId, ressourceId, e.canal(), e.statut(), e.erreur(), sys.maintenant()));
        }
    }

    private List<PublicationAvis.Envoi> envois(Contexte sys, Ressource r, Message m) {
        if (r.utilisateurId() == null || r.utilisateurId().isEmpty()) {
            return List.of(new PublicationAvis.Envoi("", COURRIEL, ECHEC, "aucun compte relié à cette ressource : personne à aviser"));
        }
        String destinataire = depot.employeDuCompte(r.utilisateurId()).filter(s -> !s.isEmpty()).orElse(r.utilisateurId());
        try {
            return avis.publier(PublicationAvis.Avis.de(sys.tenantId(), m.evenement(), List.of(destinataire), m.sujet(), m.corps(), m.lien()));
        } catch (RuntimeException panne) {
            return List.of(new PublicationAvis.Envoi("", COURRIEL, ECHEC, String.valueOf(panne.getMessage())));
        }
    }
}
