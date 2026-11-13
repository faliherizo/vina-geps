package ca.vina.nexu.partage.demonstration;

import java.util.Map;
import java.util.Optional;

/**
 * Ce que les modules ont besoin de savoir du registre des démonstrations, sans dépendre de lui :
 * une intégration est-elle branchée sur son tiers embarqué, et où le navigateur doit-il revenir.
 * Le registre lui-même — brancher, débrancher, essayer — vit dans le module de démonstration.
 */
public interface RegistreDemonstrations {

    boolean estBranchee(String code);

    /** L'origine de l'application web, apprise au branchement ; vide tant que rien n'est branché. */
    Optional<String> urlApplication();

    /** L'adresse à laquelle ce serveur se joint lui-même, et à laquelle un navigateur atteint ses tiers embarqués. */
    String urlServeur();

    /**
     * Ce qu'un adaptateur doit savoir du tiers embarqué pour lui parler — identifiants du client
     * Entra, émetteur et clé publique de l'IdP SAML — sans dépendre du module de démonstration.
     */
    Map<String, String> reglages(String code);

    /** Le registre d'une instance sans démonstration : rien n'est branché, rien ne peut l'être. */
    static RegistreDemonstrations aucune(String urlServeur) {
        return new RegistreDemonstrations() {
            @Override
            public boolean estBranchee(String code) {
                return false;
            }

            @Override
            public Optional<String> urlApplication() {
                return Optional.empty();
            }

            @Override
            public String urlServeur() {
                return urlServeur;
            }

            @Override
            public Map<String, String> reglages(String code) {
                return Map.of();
            }
        };
    }
}
