package ca.vina.nexu.organisme.domaine;

/** Les deux couleurs qu'un organisme choisit ; le code est le nom du champ du contrat HTTP, cité dans les refus. */
public enum CouleurCharte {
    PRINCIPALE("couleurPrincipale"), LATERALE("couleurLaterale");

    private final String code;

    CouleurCharte(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
