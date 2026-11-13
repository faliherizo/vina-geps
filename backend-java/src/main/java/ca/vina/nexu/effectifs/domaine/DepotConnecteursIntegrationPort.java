package ca.vina.nexu.effectifs.domaine;

import java.util.Optional;

/** Port des connecteurs configurés ({@code eff_connecteurs}) : configuration, jeu d'essai, curseur. */
public interface DepotConnecteursIntegrationPort {

    Optional<IntegrationsEffectifs.ConnecteurEnregistre> lire(String tenantId, IntegrationsEffectifs.CodeConnecteur code);

    /** Écrit la configuration et le jeu d'essai ; le curseur n'est jamais touché ici (il n'appartient qu'aux lectures). */
    void enregistrer(String tenantId, IntegrationsEffectifs.ConnecteurEnregistre c, String modifieLe, String modifiePar);

    void poserCurseur(String tenantId, IntegrationsEffectifs.CodeConnecteur code, String curseur);
}
