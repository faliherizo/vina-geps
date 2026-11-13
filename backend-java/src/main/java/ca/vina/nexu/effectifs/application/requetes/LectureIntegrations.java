package ca.vina.nexu.effectifs.application.requetes;

import ca.vina.nexu.effectifs.domaine.BoiteEnvoiIntegrationPort;
import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.EtatMessage;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.MessageSortant;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Les lectures des intégrations des effectifs (lot R10) — connecteurs et boîte d'envoi, jumeau de la partie lecture de {@code service.ts}. */
public class LectureIntegrations {

    /** Une liste de la boîte d'envoi rend au plus ce nombre de messages : l'écran suit l'actualité, pas l'archive. */
    public static final int MESSAGES_MAX = 500;

    /** Un connecteur tel que l'écran le voit ; le jeu d'essai n'est pas rendu (il peut porter des matricules). */
    public record VueConnecteur(CodeConnecteur code, IntegrationsEffectifs.Mode mode, String url, String jetonRef, boolean actif, List<Object> jeuEssai,
                                String curseur, String libelle, IntegrationsEffectifs.Sens sens, int enAttente, int enEchec) { }

    private final DepotConnecteursIntegrationPort connecteurs;
    private final BoiteEnvoiIntegrationPort boite;

    public LectureIntegrations(DepotConnecteursIntegrationPort connecteurs, BoiteEnvoiIntegrationPort boite) {
        this.connecteurs = connecteurs;
        this.boite = boite;
    }

    /** Un connecteur jamais configuré existe quand même : coupé, en simulateur. */
    public ConnecteurEnregistre connecteur(String tenantId, CodeConnecteur code) {
        return connecteurs.lire(tenantId, code).orElseGet(() -> ConnecteurEnregistre.parDefaut(code));
    }

    public List<VueConnecteur> connecteurs(Contexte ctx) {
        return Arrays.stream(CodeConnecteur.values()).map(c -> vue(ctx, c)).toList();
    }

    public VueConnecteur vue(Contexte ctx, CodeConnecteur code) {
        ConnecteurEnregistre c = connecteur(ctx.tenantId(), code);
        Map<EtatMessage, Integer> n = boite.compter(ctx.tenantId(), code);
        return new VueConnecteur(code, c.config().mode(), c.config().url(), c.config().jetonRef(), c.config().actif(), List.of(), c.curseur(), code.libelle(),
                code.sens(), n.getOrDefault(EtatMessage.EN_ATTENTE, 0), n.getOrDefault(EtatMessage.ECHEC, 0) + n.getOrDefault(EtatMessage.REJETE, 0));
    }

    public ConnecteurEnregistre exigerActif(String tenantId, CodeConnecteur code) {
        ConnecteurEnregistre c = connecteur(tenantId, code);
        if (!c.config().actif()) {
            throw new ConflitException("le connecteur " + code + " n'est pas activé pour cet organisme : activez-le dans Intégrations");
        }
        return c;
    }

    public MessageSortant message(String tenantId, String id) {
        return boite.lire(tenantId, id).orElseThrow(() -> new IntrouvableException("message « " + id + " » introuvable"));
    }

    /** Un message déjà déposé sous cette clé pour ce connecteur ? */
    public boolean dejaDepose(String tenantId, CodeConnecteur code, String cle) {
        return boite.idParCle(tenantId, code, cle).isPresent();
    }

    /** {@code connecteur} et {@code etat} nuls : sans filtre ; sinon validés (422 nommé). */
    public List<MessageSortant> messages(Contexte ctx, String connecteur, String etat) {
        return boite.lister(ctx.tenantId(), new BoiteEnvoiIntegrationPort.Filtre(connecteur == null ? null : IntegrationsEffectifs.connecteurDe(connecteur),
                etat == null ? null : IntegrationsEffectifs.etatDe(etat), MESSAGES_MAX));
    }
}
