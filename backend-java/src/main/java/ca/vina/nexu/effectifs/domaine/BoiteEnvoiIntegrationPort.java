package ca.vina.nexu.effectifs.domaine;

import java.util.Map;
import java.util.Optional;

/** Port de la <strong>boîte d'envoi</strong> ({@code eff_messages_sortants}) : un message par livraison, unique par clé et par connecteur. */
public interface BoiteEnvoiIntegrationPort {

    /** Le filtre d'une liste : connecteur et état facultatifs (nuls : tous). */
    record Filtre(IntegrationsEffectifs.CodeConnecteur connecteur, IntegrationsEffectifs.EtatMessage etat, int limite) { }

    Map<IntegrationsEffectifs.EtatMessage, Integer> compter(String tenantId, IntegrationsEffectifs.CodeConnecteur code);

    Optional<String> idParCle(String tenantId, IntegrationsEffectifs.CodeConnecteur code, String cle);

    void inserer(String tenantId, IntegrationsEffectifs.MessageSortant m);

    Optional<IntegrationsEffectifs.MessageSortant> lire(String tenantId, String id);

    java.util.List<IntegrationsEffectifs.MessageSortant> lister(String tenantId, Filtre filtre);

    /** L'issue d'une livraison : envoyé (avec la référence) ou rejeté (avec le motif) ; un essai de plus. */
    void consigner(String tenantId, String id, IntegrationsEffectifs.Issue issue, String traiteLe);

    /** Une panne passagère : un essai de plus, son motif, et l'état qui en découle. */
    void consignerPanne(String tenantId, String id, String motif, IntegrationsEffectifs.EtatMessage etat);

    void remettreEnAttente(String tenantId, String id);
}
