package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageSortant;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;
import ca.vina.nexu.effectifs.domaine.Perimetres;
import ca.vina.nexu.effectifs.domaine.Planification;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.effectifs.domaine.Ressource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Les gestes sortants déclenchés par l'encadrement (jumeaux de {@code transmettrePaie}, {@code publierEquipages},
 * {@code publierCalendrier}) : chacun dépose dans la boîte d'envoi, dans la transaction de l'appel, et l'ordonnanceur livre.
 */
public class GestesSortantsIntegration {

    /** Ce que les gestes empruntent aux autres lots : le lot de paie (R9), les périmètres (R1), les ressources (R3). */
    public record Appuis(ExportPaieTemps paie, LecturePerimetres perimetres, LectureRessources ressources, DonneesIntegrationPort donnees) { }

    /** Le compte des quarts nouvellement déposés : un quart inchangé ne se duplique pas. */
    public record Publication(int deposes) { }

    private final Appuis appuis;
    private final BoiteEnvoiIntegration boite;
    private final LectureIntegrations lecture;
    private final JournalEffectifsPort journal;

    public GestesSortantsIntegration(Appuis appuis, BoiteEnvoiIntegration boite, LectureIntegrations lecture, JournalEffectifsPort journal) {
        this.appuis = appuis;
        this.boite = boite;
        this.lecture = lecture;
        this.journal = journal;
    }

    /** PFT : le lot des feuilles approuvées d'une période ; son empreinte est la clé — un lot identique ne part qu'une fois. */
    public MessageSortant transmettrePaie(Contexte ctx, Map<String, Object> entree) {
        ExportPaieTemps.LotPaie lot = appuis.paie().exporter(ctx, entree.get("debut"), entree.get("uniteCode") instanceof String u ? u : null);
        if (lot.lignes().isEmpty()) {
            throw new EffectifsInvalideException("aucune feuille approuvée à transmettre pour la période du " + lot.debut());
        }
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("debut", lot.debut());
        charge.put("empreinte", lot.empreinte());
        charge.put("lignes", lot.lignes());
        return boite.deposer(ctx, new BoiteEnvoiIntegration.Depot(CodeConnecteur.PFT, TypeMessage.LOT_PAIE, lot.empreinte(), charge));
    }

    /** SAAV : les équipages publiés d'une date, avec leurs matricules ; la clé change quand la composition change. */
    public MessageSortant publierEquipages(Contexte ctx, Map<String, Object> entree) {
        String date = IntegrationsEffectifs.dateDe(entree.get("date"));
        String unite = IntegrationsEffectifs.uniteDe(entree.get("uniteCode"));
        appuis.perimetres().exiger(ctx, unite, Perimetres.Niveau.PLANIFICATION);
        List<DonneesIntegrationPort.LigneEquipage> lignes = appuis.donnees().equipagesPublies(ctx.tenantId(), date, unite);
        if (lignes.isEmpty()) {
            throw new EffectifsInvalideException("aucun équipage publié le " + date + " pour l'unité " + unite);
        }
        List<IntegrationsEffectifs.EquipageSaav> equipages = IntegrationsEffectifs.equipagesDe(lignes);
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("date", date);
        charge.put("uniteCode", unite);
        charge.put("equipages", equipages);
        MessageSortant m = boite.deposer(ctx, new BoiteEnvoiIntegration.Depot(CodeConnecteur.SAAV, TypeMessage.EQUIPAGE,
                IntegrationsEffectifs.cleEquipages(unite, date, equipages), charge));
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("date", date);
        apres.put("messageId", m.id());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.equipages", "connecteur", CodeConnecteur.SAAV.name(), unite, null, apres));
        return m;
    }

    /** Graph : les quarts publiés à venir d'une personne vers son calendrier, un message par quart. */
    public Publication publierCalendrier(Contexte ctx, String ressourceId) {
        Ressource r = appuis.ressources().exiger(ctx, ressourceId);
        appuis.ressources().exigerDroitSur(ctx, r, Perimetres.Niveau.PLANIFICATION);
        lecture.exigerActif(ctx.tenantId(), CodeConnecteur.GRAPH);
        int deposes = 0;
        List<DonneesIntegrationPort.QuartPublie> quarts = appuis.donnees().quartsPublies(ctx.tenantId(), r.id(), ctx.aujourdhui());
        for (DonneesIntegrationPort.QuartPublie q : quarts) {
            String cle = IntegrationsEffectifs.cleQuart(q, r.id());
            boolean nouveau = !lecture.dejaDepose(ctx.tenantId(), CodeConnecteur.GRAPH, cle);
            boite.deposer(ctx, new BoiteEnvoiIntegration.Depot(CodeConnecteur.GRAPH, TypeMessage.QUART_CALENDRIER, cle, chargeQuart(r, q)));
            deposes += nouveau ? 1 : 0;
        }
        Map<String, Object> apres = new LinkedHashMap<>();
        apres.put("quarts", quarts.size());
        apres.put("deposes", deposes);
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.calendrier", "ressource", r.id(), r.uniteCode(), null, apres));
        return new Publication(deposes);
    }

    private static Map<String, Object> chargeQuart(Ressource r, DonneesIntegrationPort.QuartPublie q) {
        Map<String, Object> charge = new LinkedHashMap<>();
        charge.put("matricule", r.matricule());
        charge.put("sujet", "Quart — " + q.milieu());
        charge.put("debut", PresenceEffectifs.instantLocal(q.date(), q.debut()));
        charge.put("fin", PresenceEffectifs.instantLocal(q.date(), q.debut() + Planification.dureeMinutes(q.debut(), q.fin())));
        return charge;
    }
}
