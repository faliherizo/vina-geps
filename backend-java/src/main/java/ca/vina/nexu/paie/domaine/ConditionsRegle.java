package ca.vina.nexu.paie.domaine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Les conditions d'une règle de paie variable, toutes facultatives et combinées par « et » : une condition absente ne
 * filtre rien (ADR 0084).
 *
 * @param jours           vide : tous les jours
 * @param feriesSeulement seulement les jours fériés du calendrier de l'organisme
 * @param codesPaie       vide : tous les codes de paie
 */
public record ConditionsRegle(String titreEmploi, String convention, Integer echelon, PlageHoraire plage, Set<DayOfWeek> jours,
                              boolean feriesSeulement, Set<String> codesPaie) {

    static final int ECHELON_MAXIMUM = 30;
    private static final int POIDS_ECHELON = 4;
    private static final int POIDS_CONVENTION = 2;
    private static final int POIDS_TITRE = 1;

    public ConditionsRegle {
        jours = Set.copyOf(jours);
        codesPaie = Set.copyOf(codesPaie);
        if (echelon != null && (echelon < 1 || echelon > ECHELON_MAXIMUM)) {
            throw RefusPaie.invalide("« echelon » : de 1 à " + ECHELON_MAXIMUM);
        }
    }

    public static final ConditionsRegle AUCUNE = new ConditionsRegle(null, null, null, null, Set.of(), false, Set.of());

    /** Une condition sur les heures : sans objet pour un taux de base ou un forfait, qui ne dépendent pas des heures. */
    boolean visentLesHeures() {
        return plage != null || !jours.isEmpty() || feriesSeulement || !codesPaie.isEmpty();
    }

    boolean visent(ProfilPaie p) {
        return egalOuLibre(titreEmploi, p.titreEmploi()) && egalOuLibre(convention, p.convention()) && egalOuLibre(echelon, p.echelon());
    }

    boolean admettent(LocalDate jour, Set<LocalDate> feries) {
        return (jours.isEmpty() || jours.contains(jour.getDayOfWeek())) && (!feriesSeulement || feries.contains(jour));
    }

    boolean admettentLeCode(String codePaie) {
        return codesPaie.isEmpty() || codesPaie.contains(codePaie);
    }

    /** Ce qui rend une règle plus précise qu'une autre : l'échelon, puis la convention, puis le titre d'emploi. */
    int precision() {
        return (echelon != null ? POIDS_ECHELON : 0) + (convention != null ? POIDS_CONVENTION : 0) + (titreEmploi != null ? POIDS_TITRE : 0);
    }

    private static boolean egalOuLibre(Object voulu, Object reel) {
        return voulu == null || Objects.equals(voulu, reel);
    }
}
