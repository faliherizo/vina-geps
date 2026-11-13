package ca.vina.nexu.effectifs.application.commandes;

import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.CodeConnecteur;
import ca.vina.nexu.effectifs.domaine.IntegrationsEffectifs.ConnecteurEnregistre;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configurer un connecteur (jumeau de {@code configurer}) : le mode, l'adresse et le <strong>nom</strong> de la variable du
 * jeton, par connecteur et par organisme ({@code effectifs.configure}). Le changement est journalisé avant / après.
 */
public class GestionnaireConnecteursIntegration {

    /** Un jeu d'essai de simulateur reste une démonstration : au-delà, c'est une base de données déguisée. */
    public static final int JEU_ESSAI_MAX = 1_000;

    private final LectureIntegrations lecture;
    private final DepotConnecteursIntegrationPort depot;
    private final JournalEffectifsPort journal;

    public GestionnaireConnecteursIntegration(LectureIntegrations lecture, DepotConnecteursIntegrationPort depot, JournalEffectifsPort journal) {
        this.lecture = lecture;
        this.depot = depot;
        this.journal = journal;
    }

    public LectureIntegrations.VueConnecteur configurer(Contexte ctx, CodeConnecteur code, Map<String, Object> entree) {
        ConnecteurEnregistre avant = lecture.connecteur(ctx.tenantId(), code);
        IntegrationsEffectifs.ConfigConnecteur c = IntegrationsEffectifs.configDe(entree, avant.config());
        Object jeu = entree.containsKey("jeuEssai") ? entree.get("jeuEssai") : avant.jeuEssai();
        if (!(jeu instanceof List<?> l) || l.size() > JEU_ESSAI_MAX) {
            throw new EffectifsInvalideException("« jeuEssai » : une liste de 1 000 éléments au plus");
        }
        depot.enregistrer(ctx.tenantId(), new ConnecteurEnregistre(code, c, new ArrayList<Object>(l), avant.curseur()), ctx.maintenant(), ctx.acteurId());
        journal.ecrire(ctx, JournalEffectifs.Consignation.de("integration.configuree", "connecteur", code.name(), null, avant.config(), c));
        return lecture.vue(ctx, code);
    }
}
