package ca.vina.nexu.approbations.application.requetes;

import ca.vina.nexu.approbations.domaine.Delegataires;
import ca.vina.nexu.approbations.domaine.TypesDeDocument;
import ca.vina.nexu.partage.temps.domaine.Horloge;

import java.util.List;

/** Lire le catalogue des types de document — semé à chaque lecture — et suggérer des délégataires. */
public final class LectureTypesDeDocument {

    private final TypesDeDocument.Depot depot;
    private final Delegataires.Comptes comptes;
    private final Horloge horloge;

    public LectureTypesDeDocument(TypesDeDocument.Depot depot, Delegataires.Comptes comptes, Horloge horloge) {
        this.depot = depot;
        this.comptes = comptes;
        this.horloge = horloge;
    }

    public List<TypesDeDocument.Type> types(String tenantId, boolean actifsSeulement) {
        depot.semer(tenantId, TypesDeDocument.TYPES_SYSTEME, horloge.maintenant());
        return depot.lister(tenantId, actifsSeulement);
    }

    /**
     * <strong>Le code demandé existe-t-il et est-il actif ?</strong> La question qu'une règle pose
     * avant d'être enregistrée : la liste déroulante ne protège de rien si le serveur accepte encore
     * n'importe quelle chaîne.
     */
    public boolean actif(String tenantId, String code) {
        depot.semer(tenantId, TypesDeDocument.TYPES_SYSTEME, horloge.maintenant());
        return depot.actif(tenantId, code == null ? "" : code.trim());
    }

    public List<Delegataires.Suggestion> delegataires(String tenantId, String q, String exclureUserId) {
        if (q == null || q.trim().length() < Delegataires.SEUIL_SUGGESTION) {
            return List.of();
        }
        return Delegataires.suggerer(comptes.deLOrganisme(tenantId), q, exclureUserId);
    }
}
