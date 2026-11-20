package ca.vina.nexu.alertes.domaine;

import java.text.Collator;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <strong>Les utilisateurs actifs facturables</strong> (ST-G08, bordereau) — la part pure. Une personne compte une fois
 * par mois civil, à l'heure de Toronto, dès qu'une tentative de transmission la vise : remise d'une alerte (directe,
 * par groupe, par liste, ou par escalade), relance d'une étape de cascade, appel automatique. On retient sa première
 * tentative du mois et l'alerte en cause : c'est la pièce justificative.
 */
public final class UtilisateursActifs {

    /** Le fuseau du bordereau : le mois civil se découpe à Toronto, pas en UTC. */
    public static final ZoneId FUSEAU = ZoneId.of("America/Toronto");

    private UtilisateursActifs() {
    }

    /** Une tentative de transmission vers une personne, à un instant, pour une alerte. */
    public record Tentative(String personId, String nom, String instant, String alerteId, String titre) { }

    /**
     * Le minimum facturable par mois (FX-AO-20260810, contrat 3.03 : « sous réserve d'un minimum facturable de cent (100)
     * utilisateurs par mois »). Au-dessus, seuls les actifs comptent.
     */
    public record MinimumFacturable(int valeur) {
        public MinimumFacturable {
            if (valeur < 0) {
                throw new IllegalArgumentException("le minimum facturable ne peut pas être négatif : " + valeur);
            }
        }

        public int facturables(int actifs) {
            return Math.max(valeur, actifs);
        }
    }

    /** Une personne active du mois : sa première tentative et l'alerte en cause. */
    public record Actif(String personId, String nom, String premiereTentative, String alerteId, String titre) { }

    /** Le mois, et ses bornes en instants : du 1er à 0 h à Toronto, inclus, au 1er du mois suivant, exclu. */
    public record Mois(YearMonth valeur) {

        public static Mois de(String code) {
            try {
                return new Mois(YearMonth.parse(code == null ? "" : code.trim()));
            } catch (DateTimeParseException e) {
                throw RefusAlerte.invalide("« mois » : AAAA-MM, par exemple 2026-11");
            }
        }

        public Instant debut() {
            return valeur.atDay(1).atStartOfDay(FUSEAU).toInstant();
        }

        public Instant fin() {
            return valeur.plusMonths(1).atDay(1).atStartOfDay(FUSEAU).toInstant();
        }

        public boolean contient(String instant) {
            Instant i = Instant.parse(instant);
            return !i.isBefore(debut()) && i.isBefore(fin());
        }

        public String code() {
            return valeur.toString();
        }
    }

    /** Une personne, une ligne : la première tentative du mois ; triées par nom, à la française. */
    public static List<Actif> actifs(Mois mois, List<Tentative> tentatives) {
        Map<String, Tentative> premieres = new LinkedHashMap<>();
        tentatives.stream().filter(t -> mois.contient(t.instant()))
                .sorted(Comparator.comparing((Tentative t) -> Instant.parse(t.instant())))
                .forEach(t -> premieres.putIfAbsent(t.personId(), t));
        Collator francais = Collator.getInstance(Locale.CANADA_FRENCH);
        return premieres.values().stream().map(t -> new Actif(t.personId(), t.nom(), t.instant(), t.alerteId(), t.titre()))
                .sorted(Comparator.comparing(Actif::nom, francais).thenComparing(Actif::personId)).toList();
    }
}
