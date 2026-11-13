package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.domaine.BoiteEnvoiIntegrationPort;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EtatMessage;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageSortant;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.TypeMessage;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OrdonnanceurIntegrationPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La <strong>boîte d'envoi</strong> des effectifs (jumeau de {@code deposer} et {@code rejouer}). Un dépôt se fait
 * <strong>dans</strong> la transaction du geste qui le produit ; une clé d'idempotence par connecteur rend le dépôt rejouable
 * sans doublon ; la livraison est planifiée à l'ordonnanceur, dans la même transaction.
 */
public class BoiteEnvoiIntegration {

    /** Ce qu'on dépose : le connecteur, le type de message, sa clé d'idempotence et sa charge. */
    public record Depot(CodeConnecteur code, TypeMessage type, String cle, Map<String, Object> charge) { }

    /** Ce que la boîte écrit. */
    public record Ecritures(BoiteEnvoiIntegrationPort boite, OrdonnanceurIntegrationPort ordonnanceur, JournalEffectifsPort journal, Sequence sequence) { }

    private final LectureIntegrations lecture;
    private final Ecritures ecrire;

    public BoiteEnvoiIntegration(LectureIntegrations lecture, Ecritures ecrire) {
        this.lecture = lecture;
        this.ecrire = ecrire;
    }

    /** Un dépôt de même clé pour le même connecteur rend le message existant : rien n'est livré deux fois. */
    public MessageSortant deposer(Contexte ctx, Depot d) {
        IntegrationsEffectifs.verifierType(d.code(), d.type());
        lecture.exigerActif(ctx.tenantId(), d.code());
        var existant = ecrire.boite().idParCle(ctx.tenantId(), d.code(), d.cle());
        if (existant.isPresent()) {
            return lecture.message(ctx.tenantId(), existant.get());
        }
        String id = ecrire.sequence().suivant("emsg");
        ecrire.boite().inserer(ctx.tenantId(), new MessageSortant(id, d.code(), d.type(), d.cle(), d.charge(), EtatMessage.EN_ATTENTE, 0, null, null,
                ctx.maintenant(), ctx.acteurId(), null));
        ecrire.ordonnanceur().planifierLivraison(ctx.tenantId(), id, ctx.maintenant(), ctx.acteurId());
        return lecture.message(ctx.tenantId(), id);
    }

    /** Un message rejeté ou en échec repart, ses essais remis à zéro ; jamais un message accepté ni un message qui attend. */
    public MessageSortant rejouer(Contexte ctx, String id) {
        MessageSortant m = lecture.message(ctx.tenantId(), id);
        if (!m.etat().rejouable()) {
            throw new ConflitException(m.etat() == EtatMessage.ENVOYE ? "ce message a été accepté : le rejouer le livrerait deux fois"
                    : "ce message attend déjà sa livraison");
        }
        ecrire.boite().remettreEnAttente(ctx.tenantId(), id);
        ecrire.ordonnanceur().planifierLivraison(ctx.tenantId(), id, ctx.maintenant(), ctx.acteurId());
        Map<String, Object> avant = new LinkedHashMap<>();
        avant.put("etat", m.etat());
        avant.put("motif", m.motif());
        ecrire.journal().ecrire(ctx, JournalEffectifs.Consignation.de("integration.rejeu", "message", id, null, avant, null));
        return lecture.message(ctx.tenantId(), id);
    }
}
