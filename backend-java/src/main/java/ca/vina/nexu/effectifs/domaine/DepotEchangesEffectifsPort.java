package ca.vina.nexu.effectifs.domaine;

/**
 * Port d'écriture des échanges de quarts. {@link #reaffecter} est l'écriture qu'un échange appliqué fait sur une
 * place : l'attribution de R3 rejugerait la place comme une attribution nouvelle, alors que l'échange a été jugé
 * dans les deux sens et que les deux places changent de titulaire ensemble.
 */
public interface DepotEchangesEffectifsPort {

    record NouvelEchange(String id, EchangeEffectifs.PlaceDetaillee cedee, EchangeEffectifs.PlaceDetaillee voulue,
                         EchangeEffectifs.VerdictCombine verdict, String proposeLe, String proposePar) { }

    /** {@code rejuge} nul : le verdict de la proposition reste. */
    record Decision(EchangeEffectifs.Statut vers, EchangeEffectifs.VerdictCombine rejuge, String decideLe, String decidePar, String motif) { }

    void inserer(String tenantId, NouvelEchange e);

    void decider(String tenantId, String id, Decision d);

    /** La place change de titulaire, par une personne, sans justification de forçage. */
    void reaffecter(String tenantId, String placeId, String ressourceId, String le);
}
