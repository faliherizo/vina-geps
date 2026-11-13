package ca.vina.nexu.effectifs.domaine;

/** Un rang figé d'une campagne : l'ancienneté est celle de l'ouverture, pas celle de la fiche du jour. */
public record AgphRang(String ressourceId, String nom, String matricule, int rang, double anciennete, Agph.EtatRang etat, String tourDebut,
                       String tourFin) {
}
