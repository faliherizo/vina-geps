package ca.vina.nexu.soutien.domaine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * <strong>Les jours ouvrables au Québec</strong>, pour compter un préavis (FX-AO-20260810, grille 99 et 99.1) : du lundi
 * au vendredi, à Montréal, hors jours fériés de la <em>Loi sur les normes du travail</em> (art. 60).
 *
 * <p>La loi laisse à l'employeur le choix entre le Vendredi saint et le lundi de Pâques ; on retire les deux. Compter
 * large ne peut qu'allonger le préavis, jamais l'écourter. Fonction pure : ni horloge, ni base.
 */
public final class JoursOuvrablesDuQuebec {

    public static final ZoneId FUSEAU = ZoneId.of("America/Montreal");
    private static final int VEILLE_DES_PATRIOTES = 24;
    private static final int SAINT_JEAN = 24;

    private JoursOuvrablesDuQuebec() {
    }

    public static boolean estOuvrable(LocalDate jour) {
        DayOfWeek j = jour.getDayOfWeek();
        return j != DayOfWeek.SATURDAY && j != DayOfWeek.SUNDAY && !feries(jour.getYear()).contains(jour);
    }

    /**
     * Le premier instant où peut commencer un changement annoncé à {@code annonce} avec {@code jours} jours ouvrables de
     * préavis : le jour de l'annonce ne compte pas ; il faut {@code jours} jours ouvrables pleins, puis le jour suivant
     * à minuit, heure de Montréal.
     */
    public static Instant premierDebutAdmis(Instant annonce, int jours) {
        LocalDate jour = annonce.atZone(FUSEAU).toLocalDate();
        for (int comptes = 0; comptes < jours; ) {
            jour = jour.plusDays(1);
            if (estOuvrable(jour)) {
                comptes++;
            }
        }
        return jour.plusDays(1).atStartOfDay(FUSEAU).toInstant();
    }

    /** Les jours fériés d'une année ; la Saint-Jean et la fête du Canada tombant un dimanche passent au lundi. */
    static Set<LocalDate> feries(int annee) {
        LocalDate paques = paques(annee);
        return Set.of(
                LocalDate.of(annee, Month.JANUARY, 1),
                paques.minusDays(2),
                paques.plusDays(1),
                LocalDate.of(annee, Month.MAY, VEILLE_DES_PATRIOTES).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                reporteDuDimanche(LocalDate.of(annee, Month.JUNE, SAINT_JEAN)),
                reporteDuDimanche(LocalDate.of(annee, Month.JULY, 1)),
                LocalDate.of(annee, Month.SEPTEMBER, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY)),
                LocalDate.of(annee, Month.OCTOBER, 1).with(TemporalAdjusters.dayOfWeekInMonth(2, DayOfWeek.MONDAY)),
                LocalDate.of(annee, Month.DECEMBER, 25));
    }

    private static LocalDate reporteDuDimanche(LocalDate jour) {
        return jour.getDayOfWeek() == DayOfWeek.SUNDAY ? jour.plusDays(1) : jour;
    }

    /** Le dimanche de Pâques grégorien (algorithme de Meeus, Jones et Butcher). */
    static LocalDate paques(int annee) {
        int a = annee % 19;
        int b = annee / 100;
        int c = annee % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mois = (h + l - 7 * m + 114) / 31;
        int jour = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(annee, mois, jour);
    }
}
