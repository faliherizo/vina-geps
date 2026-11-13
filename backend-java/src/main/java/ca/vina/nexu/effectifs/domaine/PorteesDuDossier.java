package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Map;

/**
 * Ce que le dossier dit de la personne ou de l'objet, pour choisir les règles qui le visent.
 *
 * @param unites l'unité du dossier et tous ses ancêtres
 */
public record PorteesDuDossier(List<String> unites, String convention, String statutEmploi, String corpsEmploi, String titreEmploi) {

    public static final String CONVENTION = "convention";
    public static final String STATUT_EMPLOI = "statutEmploi";
    public static final String CORPS_EMPLOI = "corpsEmploi";
    public static final String TITRE_EMPLOI = "titreEmploi";

    public PorteesDuDossier {
        unites = List.copyOf(unites);
    }

    /** Les portées d'un dossier telles que les lots appelants les donnent : une carte nommée, nulle au besoin. */
    public static PorteesDuDossier de(List<String> unites, Map<String, String> portees) {
        Map<String, String> p = portees == null ? Map.of() : portees;
        return new PorteesDuDossier(unites, p.get(CONVENTION), p.get(STATUT_EMPLOI), p.get(CORPS_EMPLOI), p.get(TITRE_EMPLOI));
    }
}
