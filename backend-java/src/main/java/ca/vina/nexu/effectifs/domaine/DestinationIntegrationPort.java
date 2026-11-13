package ca.vina.nexu.effectifs.domaine;

/** Port d'un connecteur sortant : le destinataire d'un message de la boîte d'envoi. */
@FunctionalInterface
public interface DestinationIntegrationPort {

    /**
     * Livre un message ; la clé est la clé d'idempotence — le même message livré deux fois n'est traité qu'une fois. Rend
     * l'issue (acceptée ou rejetée) ; <strong>lève</strong> {@link DestinataireIndisponibleException} sur une panne
     * passagère, que l'ordonnanceur retentera.
     */
    IntegrationsEffectifs.Issue livrer(IntegrationsEffectifs.MessageALivrer m);
}
