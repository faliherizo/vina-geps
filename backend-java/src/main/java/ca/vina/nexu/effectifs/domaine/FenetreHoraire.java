package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * La <b>fenêtre de dates</b> d'une lecture d'horaire — validée à la frontière, une seule fois.
 * Jumeau de {@code backend/src/effectifs/planification/fenetre.ts}.
 *
 * <p>Trois refus nommés plutôt qu'un comportement deviné :</p>
 *
 * <ul>
 *   <li><b>une borne seule</b> ne dit pas ce qu'on veut lire. Compléter l'autre en silence rendrait
 *       un horaire que personne n'a demandé, et l'employée conclurait que ses quarts ont disparu ;</li>
 *   <li><b>un ordre inversé</b> rend toujours zéro ligne : un tableau vide qui a l'air d'un horaire
 *       vide est le pire des deux, parce qu'il ne se corrige pas ;</li>
 *   <li><b>une fenêtre sans plafond</b> laisse demander dix ans de quarts à une base qui balaie
 *       alors tout l'historique. Le plafond est un trimestre : assez pour un mois affiché et sa marge.</li>
 * </ul>
 */
public record FenetreHoraire(String du, String au) {

    /** Un trimestre : la plus longue fenêtre qu'un écran d'horaire ait à montrer d'un coup. */
    public static final int JOURS_MAX = 92;

    /**
     * La fenêtre demandée, ou vide quand aucune borne n'est donnée — auquel cas la lecture garde son
     * sens d'origine (les quarts à venir).
     */
    public static Optional<FenetreHoraire> demandee(String du, String au) {
        boolean sansDu = du == null || du.isBlank();
        boolean sansAu = au == null || au.isBlank();
        if (sansDu && sansAu) {
            return Optional.empty();
        }
        if (sansDu || sansAu) {
            throw new EffectifsInvalideException("« du » et « au » vont de pair : une fenêtre a deux bornes");
        }
        LocalDate debut = date(du, "du");
        LocalDate fin = date(au, "au");
        if (debut.isAfter(fin)) {
            throw new EffectifsInvalideException("« du » (%s) ne peut suivre « au » (%s)".formatted(debut, fin));
        }
        long jours = ChronoUnit.DAYS.between(debut, fin) + 1;
        if (jours > JOURS_MAX) {
            throw new EffectifsInvalideException("la fenêtre ne peut dépasser %d jours (demandé : %d)".formatted(JOURS_MAX, jours));
        }
        return Optional.of(new FenetreHoraire(debut.toString(), fin.toString()));
    }

    private static LocalDate date(String valeur, String champ) {
        try {
            return LocalDate.parse(valeur.strip());
        } catch (DateTimeParseException e) {
            throw new EffectifsInvalideException("« %s » : date AAAA-MM-JJ attendue".formatted(champ));
        }
    }
}
