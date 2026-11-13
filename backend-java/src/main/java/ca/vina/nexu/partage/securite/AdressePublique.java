package ca.vina.nexu.partage.securite;

/**
 * L'adresse publique d'un établissement pour les liens sortants (courriels, textos, poussée,
 * signature, retour du SSO) : en déploiement par sous-domaine, {@code https://{slug}.{racine}} ;
 * sinon l'adresse commune ({@code PUBLIC_APP_URL}), vide s'il n'y en a pas (ADR 0033).
 */
@FunctionalInterface
public interface AdressePublique {

    /** Sans barre oblique finale ; vide quand le déploiement n'a pas d'adresse publique. */
    String pour(String tenantId);

    /** La même adresse pour tous — les déploiements sans sous-domaine, et les essais. */
    static AdressePublique fixe(String base) {
        String propre = base == null ? "" : base.trim().replaceAll("/+$", "");
        return tenantId -> propre;
    }
}
