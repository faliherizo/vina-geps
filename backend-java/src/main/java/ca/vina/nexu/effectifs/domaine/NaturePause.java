package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * La <b>nature d'un temps non payé</b> (E-GP-2) : la pause courte, la pause-repas, la période de repos.
 *
 * <p>Les trois se comptent séparément parce que la convention les plafonne séparément : un repas non
 * payé de trente minutes n'est pas une pause de quinze, et les confondre dans un seul « PAUSE »
 * empêche de vérifier la convention. Jumeau de {@code NATURES_NON_PAYEES} du Node.</p>
 */
public enum NaturePause {
    PAUSE("pause"), REPAS("pause-repas"), REPOS("période de repos");

    private static final String ATTENDUES = Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));

    private final String libelle;

    NaturePause(String libelle) {
        this.libelle = libelle;
    }

    public String code() {
        return name();
    }

    /** Le mot qu'une personne lit dans un refus : « la pause-repas », pas « REPAS ». */
    public String libelle() {
        return libelle;
    }

    /** Converti <b>une fois, à la frontière</b> ; inconnu, le refus nomme les valeurs admises. */
    public static NaturePause de(Object valeur, String sujet) {
        return Arrays.stream(values()).filter(n -> n.name().equals(valeur)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException(sujet + " " + ATTENDUES));
    }

    /** Les natures admises, dans l'ordre de déclaration, telles que l'écran les liste. */
    public static String attendues() {
        return ATTENDUES;
    }
}
