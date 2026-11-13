package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Le <strong>relais du journal</strong> vers le bus (ESB) et l'entrepôt (jumeau de {@code relayerJournal}), à partir du
 * curseur de chacun. Rien n'appelle le bus directement : si le connecteur est coupé, le curseur attend, et rien ne se perd.
 */
public class RelaisJournalIntegration {

    /** Un relais lit au plus ce nombre d'entrées : le suivant reprend au curseur. */
    public static final int JOURNAL_MAX = 5_000;

    /** Ce qu'un relais a déposé, et où il s'est arrêté. */
    public record Relais(int deposes, String curseur) { }

    private final LectureIntegrations lecture;
    /** Ce que le relais écrit : la boîte d'envoi, le curseur du connecteur, et la trace du geste. */
    public record Ecritures(BoiteEnvoiIntegration boite, DepotConnecteursIntegrationPort connecteurs, JournalEffectifsPort journal) { }

    private final BoiteEnvoiIntegration boite;
    private final DonneesIntegrationPort donnees;
    private final DepotConnecteursIntegrationPort connecteurs;
    private final JournalEffectifsPort journal;

    public RelaisJournalIntegration(LectureIntegrations lecture, DonneesIntegrationPort donnees, Ecritures ecrire) {
        this.lecture = lecture;
        this.boite = ecrire.boite();
        this.donnees = donnees;
        this.connecteurs = ecrire.connecteurs();
        this.journal = ecrire.journal();
    }

    /** {@code code} : ESB ou ENTREPOT (voir {@link IntegrationsEffectifs#relaisDe}). */
    public Relais relayer(Contexte ctx, CodeConnecteur code) {
        ConnecteurEnregistre c = lecture.exigerActif(ctx.tenantId(), code);
        List<DonneesIntegrationPort.EntreeJournal> entrees = donnees.journalApres(ctx.tenantId(), c.curseur(), JOURNAL_MAX);
        if (entrees.isEmpty()) {
            return tracer(ctx, code, new Relais(0, c.curseur()));
        }
        int deposes = code == CodeConnecteur.ESB ? evenements(ctx, entrees) : versements(ctx, entrees);
        String curseur = IntegrationsEffectifs.curseurDe(entrees.getLast());
        connecteurs.poserCurseur(ctx.tenantId(), code, curseur);
        return tracer(ctx, code, new Relais(deposes, curseur));
    }

    /** E-GN-9 : le relais est une interaction ; il se trace, même quand il n'avait rien à relayer. */
    private Relais tracer(Contexte ctx, CodeConnecteur code, Relais relais) {
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("deposes", relais.deposes());
        apres.put("curseur", relais.curseur());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.relais", "connecteur", code.name(), null, null, apres));
        return relais;
    }

    /** Une entrée dont l'action n'est pas un événement publié est lue, et passée : le curseur avance quand même. */
    private int evenements(Contexte ctx, List<DonneesIntegrationPort.EntreeJournal> entrees) {
        int deposes = 0;
        for (DonneesIntegrationPort.EntreeJournal e : entrees) {
            Optional<String> evenement = IntegrationsEffectifs.evenementDe(e.action());
            if (evenement.isPresent()) {
                Map<String, Object> charge = new LinkedHashMap<>();
                charge.put("evenement", evenement.get());
                charge.put("horodatage", e.horodatage());
                charge.put("objetType", e.objetType());
                charge.put("objetId", e.objetId());
                charge.put("uniteCode", e.uniteCode());
                boite.deposer(ctx, new BoiteEnvoiIntegration.Depot(CodeConnecteur.ESB, TypeMessage.EVENEMENT, e.id(), charge));
                deposes++;
            }
        }
        return deposes;
    }

    private int versements(Contexte ctx, List<DonneesIntegrationPort.EntreeJournal> entrees) {
        List<List<DonneesIntegrationPort.EntreeJournal>> lots = IntegrationsEffectifs.lots(entrees, IntegrationsEffectifs.TAILLE_VERSEMENT);
        for (List<DonneesIntegrationPort.EntreeJournal> l : lots) {
            boite.deposer(ctx, new BoiteEnvoiIntegration.Depot(CodeConnecteur.ENTREPOT, TypeMessage.VERSEMENT_JOURNAL,
                    l.getFirst().id() + ".." + l.getLast().id(), Map.of("entrees", l)));
        }
        return lots.size();
    }
}
