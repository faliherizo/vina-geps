package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/** Port des rattachements d'une personne à d'autres unités (addenda 8, M-09). */
public interface RattachementsPort {

    /** Aucun rattachement : chaque personne n'a que son unité principale. */
    RattachementsPort AUCUN = new RattachementsPort() {
        @Override
        public void ajouter(String tenantId, Rattachements.Rattachement r, String par, String le) {
            throw new UnsupportedOperationException("aucun dépôt de rattachements");
        }

        @Override
        public boolean retirer(String tenantId, String ressourceId, String uniteCode, String depuis) {
            return false;
        }

        @Override
        public List<Rattachements.Rattachement> deLaRessource(String tenantId, String ressourceId) {
            return List.of();
        }

        @Override
        public List<Rattachements.Rattachement> tous(String tenantId) {
            return List.of();
        }
    };

    void ajouter(String tenantId, Rattachements.Rattachement r, String par, String le);

    boolean retirer(String tenantId, String ressourceId, String uniteCode, String depuis);

    List<Rattachements.Rattachement> deLaRessource(String tenantId, String ressourceId);

    List<Rattachements.Rattachement> tous(String tenantId);
}
