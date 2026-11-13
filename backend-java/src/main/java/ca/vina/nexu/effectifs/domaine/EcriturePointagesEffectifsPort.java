package ca.vina.nexu.effectifs.domaine;

/** Port d'écriture de la présence (lot R8). */
public interface EcriturePointagesEffectifsPort {

    /** Un pointage à inscrire ; la saisie (instant, auteur) vient du contexte. */
    record NouveauPointage(String id, String ressourceId, PointageEffectifs.Lieu lieu, PresenceEffectifs.PointageCourt pointage,
                           PresenceEffectifs.OriginePointage origine, String saisieId, PointageEffectifs.Constat constat,
                           PresenceEffectifs.StatutPointage statut, String justification) { }

    /** Un horodatage importé d'une borne ou d'un système tiers ; la clé rend l'import rejouable. */
    record HorodatageExterne(String id, String source, String cle, String ressourceId, PresenceEffectifs.PointageCourt pointage) { }

    void inserer(Contexte ctx, NouveauPointage p);

    void inscrirePreuve(String tenantId, String pointageId, PresenceEffectifs.VerdictPreuve v);

    void decider(Contexte ctx, String id, PresenceEffectifs.StatutPointage vers, String motif);

    /** Vrai si l'horodatage est nouveau ; faux si sa clé existait déjà pour cette source (doublon ignoré). */
    boolean importer(Contexte ctx, HorodatageExterne h);
}
