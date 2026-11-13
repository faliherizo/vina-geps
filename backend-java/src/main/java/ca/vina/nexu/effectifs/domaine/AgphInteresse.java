package ca.vina.nexu.effectifs.domaine;

/** Une manifestation d'intérêt telle que l'encadrement la lit : qui, depuis quand. */
public record AgphInteresse(String ressourceId, String nom, String matricule, double anciennete, String deposeLe) {

    public Agph.Interesse pourClassement() {
        return new Agph.Interesse(ressourceId, anciennete, deposeLe);
    }
}
