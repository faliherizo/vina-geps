package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.domaine.AcheminementUnites;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.CanalReception;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.domaine.ModeReception;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.communication.domaine.ReglagesAcheminementPort;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.List;

/**
 * Régler l'acheminement d'une unité, ou la rendre au défaut. Seule une unité en vigueur se règle : un
 * réglage sur une unité fermée ne joindrait personne et ne se verrait nulle part.
 */
public final class GestionnaireReglagesAcheminement {

    static final String UNITE_INTROUVABLE = "unité introuvable";

    /** Ce dont le gestionnaire a besoin : les unités, le rangement et la trace. */
    public record Dependances(GroupesDeTravailPort groupes, ReglagesAcheminementPort.Ecriture ecriture, JournalCommunicationPort journal) { }

    /** Une étape telle que le corps HTTP la décrit, avant conversion. */
    public record EtapeDemandee(String canal, String mode) { }

    private final Dependances d;
    private final Clock horloge;

    public GestionnaireReglagesAcheminement(Dependances dependances, Clock horloge) {
        this.d = dependances;
        this.horloge = horloge;
    }

    private void exigerUnite(Appelant a, String uniteId) {
        if (d.groupes().groupes(a.tenantId()).stream().noneMatch(g -> g.id().equals(uniteId))) {
            throw RefusCommunication.introuvable(UNITE_INTROUVABLE);
        }
    }

    /** Une étape sans mode sonne : c'est le défaut du répertoire. */
    static AcheminementUnites.Etape etape(EtapeDemandee e) {
        return new AcheminementUnites.Etape(CanalReception.de(e.canal()), e.mode() == null ? ModeReception.SONNERIE : ModeReception.de(e.mode()));
    }

    public AcheminementUnites.Reglage regler(Appelant a, String uniteId, List<EtapeDemandee> demandees) {
        exigerUnite(a, uniteId);
        List<AcheminementUnites.Etape> etapes = demandees.stream().map(GestionnaireReglagesAcheminement::etape).toList();
        List<String> erreurs = AcheminementUnites.valider(etapes);
        if (!erreurs.isEmpty()) {
            throw RefusCommunication.invalide(String.join(" · ", erreurs));
        }
        AcheminementUnites.Reglage r = new AcheminementUnites.Reglage(uniteId, etapes, Iso.de(horloge.instant()), a.acteurId());
        d.ecriture().enregistrer(a.tenantId(), r);
        d.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "acheminement.reglage", uniteId, null, r.enCarte()));
        return r;
    }

    /** Rendre l'unité au défaut ; une unité déjà au défaut le dit plutôt que de feindre un geste. */
    public void retablirDefaut(Appelant a, String uniteId) {
        exigerUnite(a, uniteId);
        if (!d.ecriture().retirer(a.tenantId(), uniteId)) {
            throw RefusCommunication.conflit("cette unité suit déjà l'acheminement par défaut");
        }
        d.journal().consigner(a.tenantId(), new JournalCommunicationPort.Trace(a.acteurId(), "acheminement.defaut", uniteId, null, null));
    }
}
