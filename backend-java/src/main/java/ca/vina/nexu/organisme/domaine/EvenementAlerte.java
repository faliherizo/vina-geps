package ca.vina.nexu.organisme.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Ce qui peut déclencher une alerte de l'organisme (P-TN-10). Le code est le nom de la constante, comme au
 * Node ({@code EXPORT_TELECHARGE}) : il circule tel quel dans le contrat HTTP et dans la base.
 */
public enum EvenementAlerte {
    EXPORT_TELECHARGE("Téléchargement d'un export (CSV, PDF, tableur)"),
    CONNEXION_REFUSEE_PAYS("Connexion refusée par la restriction de pays");

    private final String libelle;

    EvenementAlerte(String libelle) {
        this.libelle = libelle;
    }

    public String code() {
        return name();
    }

    public String libelle() {
        return libelle;
    }

    /** La conversion à la frontière : un événement inconnu est refusé en énumérant ceux qui existent. */
    public static EvenementAlerte de(Object v) {
        return Arrays.stream(values()).filter(e -> e.code().equals(v)).findFirst().orElseThrow(() -> new OptionInvalideException(
                "« evenement » : " + Arrays.stream(values()).map(EvenementAlerte::code).collect(Collectors.joining(", "))));
    }
}
