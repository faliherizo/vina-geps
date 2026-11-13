package ca.vina.nexu.partage.personnel;

/**
 * Port : inscrire au répertoire du personnel la personne qu'une ressource d'effectifs désigne. Une
 * ressource reliée à un compte est quelqu'un qu'on doit pouvoir aviser par son nom et retrouver dans
 * un champ « personne » ; sans fiche, il n'existait pour le reste du produit que comme un identifiant.
 * L'inscription est idempotente : un compte déjà rattaché à une fiche ne change pas.
 */
@FunctionalInterface
public interface FichesPersonnelPort {

    record Inscription(String ressourceId, String nomComplet, String utilisateurId) { }

    void inscrire(String tenantId, Inscription inscription);
}
