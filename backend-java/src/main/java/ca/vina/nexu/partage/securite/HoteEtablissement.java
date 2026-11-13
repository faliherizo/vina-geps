package ca.vina.nexu.partage.securite;

import jakarta.servlet.http.HttpServletRequest;

/**
 * L'établissement que l'hôte d'une requête désigne — la seule autorité en déploiement par
 * sous-domaine (ADR 0033). Le port lit le dépôt des établissements ; la règle est dans {@link ResolutionHote}.
 */
public interface HoteEtablissement {

    sealed interface Resolution {
        /** Mode sélecteur : l'hôte ne dit rien, le client choisit à la connexion. */
        record Selecteur() implements Resolution { }
        /** La racine ou un sous-domaine réservé ({@code admin.}) : les comptes globaux. */
        record Global() implements Resolution { }
        /** Un slug qui ne désigne aucun établissement ouvert. */
        record Inconnu(String slug) implements Resolution { }
        /** Un hôte étranger à la racine. */
        record HorsDomaine(String hote) implements Resolution { }
        record Etablissement(String id, String nom, String slug) implements Resolution { }
    }

    ResolutionHote.Configuration configuration();

    Resolution de(HttpServletRequest requete);

    /** L'identifiant de l'établissement de l'hôte, s'il en désigne un. */
    default String tenantDeLHote(HttpServletRequest requete) {
        return de(requete) instanceof Resolution.Etablissement e ? e.id() : null;
    }

    /** Sans déploiement par sous-domaine : le sélecteur, partout (essais des filtres et des contrôleurs). */
    static HoteEtablissement selecteur() {
        return new HoteEtablissement() {
            private final ResolutionHote.Configuration c = ResolutionHote.Configuration.depuis(null, null, null);

            @Override
            public ResolutionHote.Configuration configuration() {
                return c;
            }

            @Override
            public Resolution de(HttpServletRequest requete) {
                return new Resolution.Selecteur();
            }
        };
    }
}
