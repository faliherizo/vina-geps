package ca.vina.nexu.auth.domaine;

import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * Port : la restriction de connexion par pays d'un organisme (AO 26-015-DO, P-TN-11). L'authentification
 * la consulte sans la connaître — c'est le contexte {@code organisme} qui l'implémente, ce qui garde
 * {@code auth} sans dépendance vers lui.
 */
@FunctionalInterface
public interface RestrictionPaysConnexion {

    /** Aucune restriction : le déploiement sans contexte {@code organisme}, et les essais qui ne la visent pas. */
    RestrictionPaysConnexion AUCUNE = (tenantId, entete, qui) -> Optional.empty();

    /**
     * Le motif du refus, ou vide si la connexion peut se poursuivre.
     *
     * @param tenantId l'organisme visé par la connexion ; {@code null} pour un compte global
     * @param entete   la lecture d'un en-tête de la requête, par son nom
     * @param qui      le nom de connexion tenté
     */
    Optional<String> refus(String tenantId, UnaryOperator<String> entete, String qui);
}
