package ca.vina.nexu.alertes.domaine;

import java.util.List;

/**
 * Port : les échéances durables d'une cascade. Une étape attend dans l'ordonnanceur partagé (ADR 0014), et l'envoi
 * d'une relance aussi : un redémarrage ne perd ni l'une ni l'autre.
 */
public interface EcheancierCascades {

    /** Une relance prête à partir : à qui, par quels canaux, quoi. */
    record Relance(String alerteId, int etape, List<String> comptes, List<String> canaux, String sujet, String corps) {
        public Relance {
            comptes = List.copyOf(comptes);
            canaux = List.copyOf(canaux);
        }
    }

    /** Remplace l'échéance qui attend déjà pour cette alerte : une cascade n'a qu'une étape en attente. */
    void planifierEtape(String tenantId, String alerteId, int etape, String echeanceLe);

    void annulerEtape(String tenantId, String alerteId);

    void planifierRelance(String tenantId, Relance relance, String maintenant);
}
