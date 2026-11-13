package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.application.requetes.LectureListesDistribution;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.domaine.EcritureListesDistributionPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.communication.domaine.Messagerie;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Créer, modifier, supprimer une liste de distribution, et ouvrir une conversation sur elle (BF-033). */
public final class GestionnaireListesDistribution {

    /** Ce qu'une demande porte ; {@code partagee} et {@code membres} nuls quand ils sont absents. */
    public record Demande(String nom, Boolean partagee, List<ListesDistribution.MembreDemande> membres) { }

    /** Les dépendances, groupées. */
    public record Dependances(LectureListesDistributionPort lecture, EcritureListesDistributionPort ecriture,
                              ConversationsPourListesPort conversations, JournalCommunicationPort journal) { }

    private final Dependances d;
    private final LectureListesDistribution listes;
    private final Sequence sequence;
    private final Clock horloge;

    public GestionnaireListesDistribution(Dependances dependances, LectureListesDistribution listes, Sequence sequence, Clock horloge) {
        this.d = dependances;
        this.listes = listes;
        this.sequence = sequence;
        this.horloge = horloge;
    }

    private static void valider(String nom, List<ListesDistribution.MembreDemande> membres) {
        List<String> erreurs = ListesDistribution.valider(nom, membres);
        if (!erreurs.isEmpty()) {
            throw RefusCommunication.invalide(String.join(" · ", erreurs));
        }
    }

    private void tracer(Appelant a, String action, String listeId, Object avant, Object apres) {
        d.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), action, listeId, avant, apres));
    }

    /** Deux listes du même nom pour la même personne sont un conflit ; pour deux personnes, non. */
    public ListesDistribution.Liste creer(Appelant a, Demande e) {
        List<ListesDistribution.MembreDemande> demandes = e.membres() == null ? List.of() : e.membres();
        valider(e.nom(), demandes);
        String nom = e.nom().trim();
        if (d.lecture().nomPris(a.tenantId(), a.acteurId(), nom)) {
            throw RefusCommunication.conflit("vous avez déjà une liste nommée « " + nom + " »");
        }
        ListesDistribution.Liste l = new ListesDistribution.Liste(sequence.uuid(), nom, Boolean.TRUE.equals(e.partagee()), a.acteurId(),
                Iso.de(horloge.instant()), ListesDistribution.membres(demandes));
        d.ecriture().creer(a.tenantId(), l);
        tracer(a, "liste_distribution.creation", l.id(), null, Map.of("nom", nom, "membres", demandes.size()));
        return listes.lire(a, l.id());
    }

    private ListesDistribution.Liste deLAuteur(Appelant a, String listeId, String verbe) {
        ListesDistribution.Liste l = d.lecture().lire(a.tenantId(), listeId)
                .orElseThrow(() -> RefusCommunication.introuvable("liste de distribution introuvable"));
        if (!l.creeePar().equals(a.acteurId())) {
            // Partager, c'est donner à lire : une liste partagée reste à son auteur seul.
            throw RefusCommunication.interdit("seul l'auteur d'une liste la " + verbe);
        }
        return l;
    }

    public ListesDistribution.Liste modifier(Appelant a, String listeId, Demande e) {
        ListesDistribution.Liste actuelle = deLAuteur(a, listeId, "modifie");
        String nom = e.nom() != null ? e.nom() : actuelle.nom();
        List<ListesDistribution.MembreDemande> demandes = e.membres() != null ? e.membres()
                : actuelle.membres().stream().map(m -> new ListesDistribution.MembreDemande(m.nature().code(), m.cible())).toList();
        valider(nom, demandes);
        // Renommer vers un nom déjà pris est le même conflit qu'à la création : sans ce contrôle, la
        // contrainte d'unicité de la base répondait 500 (balayage JMeter du 13 novembre).
        if (!nom.trim().equals(actuelle.nom()) && d.lecture().nomPris(a.tenantId(), a.acteurId(), nom.trim())) {
            throw RefusCommunication.conflit("vous avez déjà une liste nommée « " + nom.trim() + " »");
        }
        boolean partagee = e.partagee() != null ? e.partagee() : actuelle.partagee();
        ListesDistribution.Liste projet = new ListesDistribution.Liste(listeId, nom.trim(), partagee, actuelle.creeePar(), actuelle.creeeLe(), List.of());
        d.ecriture().modifier(a.tenantId(), projet, e.membres() == null ? null : ListesDistribution.membres(e.membres()));
        tracer(a, "liste_distribution.modification", listeId, Map.of("nom", actuelle.nom()), Map.of("nom", nom.trim()));
        return listes.lire(a, listeId);
    }

    public Map<String, Object> supprimer(Appelant a, String listeId) {
        ListesDistribution.Liste actuelle = deLAuteur(a, listeId, "supprime");
        d.ecriture().supprimer(a.tenantId(), listeId);
        tracer(a, "liste_distribution.suppression", listeId, Map.of("nom", actuelle.nom()), null);
        return Map.of("supprime", true);
    }

    /** L'auteur est joint même hors de la liste : sans lui, il écrirait dans un fil qu'il ne relirait pas. */
    public Messagerie.Conversation ouvrirConversation(Appelant a, String listeId, String sujet) {
        Map<String, Object> resolus = listes.destinataires(a, listeId);
        String nom = (String) resolus.get("nom");
        LinkedHashSet<String> participants = new LinkedHashSet<>();
        participants.add(a.acteurId());
        ((List<?>) resolus.get("destinataires")).forEach(p -> participants.add((String) p));
        if (participants.size() < 2) {
            throw RefusCommunication.conflit("la liste « " + nom + " » ne désigne personne d'autre que vous : rien à diffuser");
        }
        String s = sujet == null ? "" : sujet.trim();
        return d.conversations().ouvrirGroupe(a.tenantId(), a.acteurId(), s.isEmpty() ? nom : s, new ArrayList<>(participants));
    }
}
