package ca.vina.nexu.organisme.domaine;

import java.util.Arrays;
import java.util.Optional;

/**
 * Ce que l'état d'un établissement permet (jumeau de {@code requireTenant}) : un établissement inconnu,
 * suspendu ou résilié n'a plus d'options à lire ni à poser. Le code est celui de {@code tenants.status}.
 */
public enum AccesEtablissement {
    OUVERT(null), INTROUVABLE(null), SUSPENDU("suspended"), RESILIE("terminated");

    private final String statut;

    AccesEtablissement(String statut) {
        this.statut = statut;
    }

    /** {@code statut} vide : l'établissement n'existe pas. Un statut sans restriction (essai, actif) ouvre. */
    public static AccesEtablissement de(Optional<String> statut) {
        if (statut.isEmpty()) {
            return INTROUVABLE;
        }
        return Arrays.stream(values()).filter(a -> a.statut != null && a.statut.equals(statut.get())).findFirst().orElse(OUVERT);
    }
}
