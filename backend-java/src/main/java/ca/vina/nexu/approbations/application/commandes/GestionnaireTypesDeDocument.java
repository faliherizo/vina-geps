package ca.vina.nexu.approbations.application.commandes;

import ca.vina.nexu.approbations.domaine.TypesDeDocument;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.temps.domaine.Horloge;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Écrire le catalogue des types de document : créer, modifier, supprimer (ajoutés par l'organisme seulement). */
public final class GestionnaireTypesDeDocument {

    static final String INTROUVABLE = "document type not found";

    private final TypesDeDocument.Depot depot;
    private final Horloge horloge;
    private final Supplier<String> identifiants;

    public GestionnaireTypesDeDocument(TypesDeDocument.Depot depot, Horloge horloge) {
        this(depot, horloge, () -> UUID.randomUUID().toString());
    }

    GestionnaireTypesDeDocument(TypesDeDocument.Depot depot, Horloge horloge, Supplier<String> identifiants) {
        this.depot = depot;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /**
     * <strong>Semer d'abord</strong>, pour que l'index unique tranche contre les types système :
     * sinon un {@code contract} « ajouté par l'organisme » — donc supprimable — porterait le flux
     * des contrats.
     */
    public TypesDeDocument.Type creer(String tenantId, Map<String, Object> entree) {
        depot.semer(tenantId, TypesDeDocument.TYPES_SYSTEME, horloge.maintenant());
        TypesDeDocument.Nouveau n = TypesDeDocument.nouveau(entree);
        String id = identifiants.get();
        if (!depot.inserer(tenantId, id, n, horloge.maintenant())) {
            throw new IllegalArgumentException("a document type with this code already exists");
        }
        return depot.lire(tenantId, id).orElseThrow();
    }

    public TypesDeDocument.Type modifier(String tenantId, String id, Map<String, Object> patch) {
        depot.lire(tenantId, id).orElseThrow(() -> new IntrouvableException(INTROUVABLE));
        depot.modifier(tenantId, id, TypesDeDocument.modification(patch));
        return depot.lire(tenantId, id).orElseThrow();
    }

    /** Faux quand le type n'existe pas ; un type système est refusé (on le désactive). */
    public boolean supprimer(String tenantId, String id) {
        var existant = depot.lire(tenantId, id);
        if (existant.isEmpty()) {
            return false;
        }
        TypesDeDocument.verifierSuppression(existant.get());
        return depot.supprimer(tenantId, id);
    }
}
