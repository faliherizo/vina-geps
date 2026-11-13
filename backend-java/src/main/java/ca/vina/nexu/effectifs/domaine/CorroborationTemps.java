package ca.vina.nexu.effectifs.domaine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <strong>La corrélation des horodatages tiers</strong> (E-RF-13) — le pur de {@code corroborationDe},
 * {@code concordanceDesSources}, {@code plagesDUneSource} ({@code presence/domaine.ts}) et des anomalies de corroboration
 * ({@code temps/domaine.ts}) : ce qui confirme un pointage, ce que deux sources disent l'une de l'autre, le temps qu'une
 * source propose quand personne n'a pointé, et ce que la feuille en signale.
 */
public final class CorroborationTemps {

    private static final long MINUTE_MS = 60_000L;
    private static final String SOURCE_INCONNUE = "source externe";

    private CorroborationTemps() {
    }

    /** Un horodatage venu d'un logiciel tiers : il porte <strong>sa source</strong>, sans quoi on ne peut pas croiser. */
    public record HorodatageExterne(PresenceEffectifs.Sens sens, String horodatage, String source) { }

    /** Les sources qui confirment un pointage, et le plus grand écart (minutes) parmi les confirmations retenues. */
    public record Corroboration(boolean corrobore, List<String> sources, Long ecartMinutesMax) { }

    /** Ce que deux sources disent l'une de l'autre. */
    public record ConcordanceSources(List<String> sources, int concordants, int divergents, List<String> motifs) { }

    /** Une plage couverte : de l'ouverture à la fermeture. */
    public record Plage(String debut, String fin) { }

    /** Les plages qu'une source couvre dans un quart. */
    public record PlagesExternes(String source, List<Plage> plages) { }

    /** Ce que deux sources tierces disent l'une de l'autre, un jour donné. */
    public record CorrelationSources(String date, List<String> sources, int concordants, int divergents) { }

    /** Ce que les anomalies de corroboration lisent d'un segment de la feuille. */
    public interface SegmentProuve {
        String date();

        TempsEffectifs.Source source();

        /** Nul quand la question ne se pose pas : un temps déduit de l'horaire n'a rien à corroborer. */
        Boolean corrobore();

        String sourceExterne();

        String confirmeLe();
    }

    private static long ms(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    private static boolean proches(PresenceEffectifs.Sens sens, String horodatage, HorodatageExterne e, long toleranceMs) {
        return e.sens() == sens && Math.abs(ms(e.horodatage()) - ms(horodatage)) <= toleranceMs;
    }

    /**
     * <strong>Ce qui confirme un pointage, et de combien.</strong> Quelles sources confirment importe : un pointage confirmé
     * par la borne <em>et</em> par le répartiteur ne se discute pas, un pointage confirmé par la seule borne se discute encore.
     */
    public static Corroboration corroborationDe(PresenceEffectifs.PointageCourt p, List<HorodatageExterne> externes, long toleranceMs) {
        List<HorodatageExterne> retenus = externes.stream().filter(e -> proches(p.sens(), p.horodatage(), e, toleranceMs)).toList();
        List<String> sources = sourcesDe(retenus);
        Long ecart = retenus.stream().mapToLong(e -> Math.abs(ms(e.horodatage()) - ms(p.horodatage()))).max().stream()
                .mapToObj(m -> Math.round(m / (double) MINUTE_MS)).findFirst().orElse(null);
        return new Corroboration(!sources.isEmpty(), sources, ecart);
    }

    private static List<String> sourcesDe(Collection<HorodatageExterne> externes) {
        return externes.stream().map(HorodatageExterne::source).collect(Collectors.toCollection(TreeSet::new)).stream().toList();
    }

    /**
     * <strong>La concordance de deux sources externes entre elles</strong>, paire par paire dans l'ordre alphabétique. Deux
     * sources qui divergent invalident le pointage qu'elles prétendent toutes deux confirmer : comparer chaque source au
     * pointage seulement laisserait passer le cas où chacune est hors tolérance de l'autre.
     */
    public static List<ConcordanceSources> concordanceDesSources(List<HorodatageExterne> externes, long toleranceMs) {
        List<String> noms = sourcesDe(externes);
        List<ConcordanceSources> resultats = new ArrayList<>();
        for (int i = 0; i < noms.size(); i++) {
            for (int j = i + 1; j < noms.size(); j++) {
                resultats.add(concordance(externes, List.of(noms.get(i), noms.get(j)), toleranceMs));
            }
        }
        return resultats;
    }

    private static ConcordanceSources concordance(List<HorodatageExterne> externes, List<String> paire, long toleranceMs) {
        List<HorodatageExterne> a = deLaSource(externes, paire.get(0));
        List<HorodatageExterne> b = deLaSource(externes, paire.get(1));
        int concordants = (int) a.stream().filter(x -> confirme(x, b, toleranceMs)).count();
        int divergents = a.size() - concordants + (int) b.stream().filter(y -> !confirme(y, a, toleranceMs)).count();
        List<String> motifs = new ArrayList<>(List.of(concordants + " horodatage(s) concordant(s) entre « " + paire.get(0) + " » et « " + paire.get(1) + " »"));
        if (divergents > 0) {
            motifs.add(divergents + " horodatage(s) qu'une source porte et que l'autre ne confirme pas : à régler avant de valider la feuille");
        }
        return new ConcordanceSources(paire, concordants, divergents, motifs);
    }

    private static boolean confirme(HorodatageExterne x, List<HorodatageExterne> liste, long toleranceMs) {
        return liste.stream().anyMatch(y -> proches(x.sens(), x.horodatage(), y, toleranceMs));
    }

    private static List<HorodatageExterne> deLaSource(List<HorodatageExterne> externes, String source) {
        return externes.stream().filter(e -> e.source().equals(source)).toList();
    }

    /**
     * <strong>Les plages qu'une source externe couvre à elle seule</strong>, lues par paires dans l'ordre : une entrée (ou une
     * fin de pause) ouvre, le pointage suivant ferme. Une entrée restée sans sortie <strong>ne produit rien</strong> : la clore à
     * une heure inventée créerait du temps que personne n'a constaté.
     */
    public static List<Plage> plagesDUneSource(List<HorodatageExterne> externes, String source) {
        List<Plage> plages = new ArrayList<>();
        String debut = null;
        for (HorodatageExterne e : deLaSource(externes, source).stream().sorted(Comparator.comparing(HorodatageExterne::horodatage)).toList()) {
            if (ouvre(e.sens())) {
                debut = debut == null ? e.horodatage() : debut;
            } else if (debut != null) {
                plages.add(new Plage(debut, e.horodatage()));
                debut = null;
            }
        }
        return plages;
    }

    private static boolean ouvre(PresenceEffectifs.Sens sens) {
        return sens == PresenceEffectifs.Sens.ENTREE || sens == PresenceEffectifs.Sens.FIN_PAUSE;
    }

    /**
     * Les plages qu'une source tierce couvre <strong>à l'intérieur d'un quart</strong>, bornées au quart. Chaque source est lue
     * séparément (en mêler deux produirait des paires artificielles) ; quand plusieurs couvrent le quart, <strong>la première par
     * ordre alphabétique fait foi</strong> — un ordre arbitraire mais stable, plutôt qu'un cumul qui doublerait le temps.
     */
    public static Optional<PlagesExternes> plagesDuQuart(List<HorodatageExterne> externes, String debutPrevu, String finPrevue) {
        for (String source : sourcesDe(externes)) {
            List<Plage> plages = plagesDUneSource(externes, source).stream()
                    .map(p -> new Plage(p.debut().compareTo(debutPrevu) > 0 ? p.debut() : debutPrevu, p.fin().compareTo(finPrevue) < 0 ? p.fin() : finPrevue))
                    .filter(p -> p.debut().compareTo(p.fin()) < 0).toList();
            if (!plages.isEmpty()) {
                return Optional.of(new PlagesExternes(source, plages));
            }
        }
        return Optional.empty();
    }

    /** Les jours (locaux) où au moins une source tierce a été importée. */
    public static Set<String> joursAvecSource(List<HorodatageExterne> externes) {
        return externes.stream().map(e -> TempsEffectifs.dateLocaleDe(e.horodatage())).collect(Collectors.toSet());
    }

    /**
     * Les concordances entre sources, <strong>par jour</strong> : deux sources en désaccord le mardi ne disqualifient pas le
     * jeudi, et une anomalie datée se règle, une anomalie globale se subit.
     */
    public static List<CorrelationSources> correlationParJour(List<HorodatageExterne> externes, long toleranceMs) {
        Map<String, List<HorodatageExterne>> parJour = externes.stream()
                .collect(Collectors.groupingBy(e -> TempsEffectifs.dateLocaleDe(e.horodatage()), TreeMap::new, Collectors.toList()));
        return parJour.entrySet().stream().flatMap(j -> concordanceDesSources(j.getValue(), toleranceMs).stream()
                .map(c -> new CorrelationSources(j.getKey(), c.sources(), c.concordants(), c.divergents()))).toList();
    }

    /**
     * <strong>Les anomalies de corroboration.</strong> Un temps pointé qu'aucune source ne confirme n'est pas une faute — c'est
     * la situation ordinaire sans borne ; mais dès qu'une source existe ce jour-là et qu'un pointage n'y figure pas, cela se
     * dit, avec le sort que l'organisme a choisi.
     */
    public static List<TempsEffectifs.Anomalie> anomaliesDeCorroboration(List<? extends SegmentProuve> segments, Predicate<String> avecSource,
                                                                        ActionCorroboration action) {
        if (action == ActionCorroboration.IGNORER) {
            return List.of();
        }
        Map<String, Long> parDate = segments.stream().filter(s -> s.source() == TempsEffectifs.Source.POINTAGE && Boolean.FALSE.equals(s.corrobore()))
                .collect(Collectors.groupingBy(SegmentProuve::date, TreeMap::new, Collectors.counting()));
        return parDate.entrySet().stream().filter(e -> avecSource.test(e.getKey())).map(e -> new TempsEffectifs.Anomalie(
                TempsEffectifs.CodeAnomalie.NON_CORROBORE, e.getKey(), "le " + e.getKey() + " : " + e.getValue()
                + " segment(s) pointé(s) qu'aucune source tierce ne confirme, alors qu'une source a été importée ce jour-là", action.bloque())).toList();
    }

    /**
     * <strong>Deux sources tierces qui se contredisent.</strong> Contrairement à l'absence de confirmation — qui n'est le fait
     * de personne —, une divergence est un problème d'équipement ou de paramétrage, et il se règle.
     */
    public static List<TempsEffectifs.Anomalie> anomaliesDeDivergence(List<CorrelationSources> correlation, ActionCorroboration action) {
        if (action == ActionCorroboration.IGNORER) {
            return List.of();
        }
        return correlation.stream().filter(c -> c.divergents() > 0).map(c -> new TempsEffectifs.Anomalie(TempsEffectifs.CodeAnomalie.SOURCES_DIVERGENTES,
                c.date(), "le " + c.date() + " : « " + c.sources().get(0) + " » et « " + c.sources().get(1) + " » divergent sur " + c.divergents()
                + " horodatage(s) — le temps validé sur leur foi ne vaudrait rien", action.bloque())).toList();
    }

    /**
     * <strong>Un temps proposé par une source tierce seule attend une confirmation</strong> : il vaut mieux que l'horaire, mais
     * reste la parole d'une machine. Bloquante, l'anomalie empêche qu'un temps que personne n'a relu parte à la paie.
     */
    public static List<TempsEffectifs.Anomalie> anomaliesAConfirmer(List<? extends SegmentProuve> segments) {
        Map<String, Set<String>> parDate = segments.stream().filter(s -> s.source() == TempsEffectifs.Source.EXTERNE && s.confirmeLe() == null)
                .collect(Collectors.groupingBy(SegmentProuve::date, TreeMap::new,
                        Collectors.mapping(s -> s.sourceExterne() == null ? SOURCE_INCONNUE : s.sourceExterne(), Collectors.toCollection(TreeSet::new))));
        return parDate.entrySet().stream().map(e -> new TempsEffectifs.Anomalie(TempsEffectifs.CodeAnomalie.EXTERNE_A_CONFIRMER, e.getKey(),
                "le " + e.getKey() + " : temps proposé depuis l'horodatage de « " + String.join(" », « ", e.getValue())
                + " » seul, sans pointage — confirmez-le ou corrigez-le avant de soumettre", true)).toList();
    }
}
