package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Ce que le temps d'un quart <b>se répartit</b>, et ce qu'une nouvelle saisie ne doit pas doubler — jumeau des
 * fonctions {@code tempsParMilieu}, {@code tempsParActivite}, {@code deplacementsDe}, {@code plagesCouvertes} et
 * {@code verifierCumul} du domaine de présence du Node.
 *
 * <p>Ces fonctions manquaient au Java : le multi-site (E-GP-7), les activités (E-GP-15) et le cumul des saisies
 * (P-GP-4) n'existaient que côté Node, et l'écran « Mon pointage » s'effondrait sur la pile Java dès qu'un quart
 * était en cours, faute des trois champs qu'elles produisent (mesuré le 8 novembre 2026).</p>
 *
 * <p>Pures : elles ne lisent ni n'écrivent rien, et ne connaissent pas l'horloge.</p>
 */
public final class RepartitionPresence {

    private static final int DEBUT_HEURE = 11;
    private static final int FIN_HEURE = 16;
    private static final int FIN_DATE = 10;

    public record TempsParMilieu(String milieuId, long minutes) { }

    public record TempsParActivite(String activiteCode, long minutes) { }

    public record Deplacement(String quand, String de, String vers) { }

    public record PlageOccupee(String debut, String fin) { }

    private RepartitionPresence() {
    }

    /**
     * <b>Le temps payé réparti par site</b> (E-GP-7) : une personne passée de la caserne 12 à la caserne 7 a
     * travaillé dans les deux, et l'imputation des heures a besoin de la répartition. Les pauses n'y figurent pas :
     * elles ne sont pas payées, et les compter donnerait un total supérieur aux heures payées.
     */
    public static List<TempsParMilieu> tempsParMilieu(List<PresenceEffectifs.Segment> segments) {
        return repartir(segments, PresenceEffectifs.Segment::milieuId).entrySet().stream()
                .map(e -> new TempsParMilieu(e.getKey(), e.getValue())).toList();
    }

    /**
     * <b>Le temps payé réparti par activité</b> (E-GP-15) : une garde de douze heures dont deux en intervention ne
     * se dimensionne pas comme douze heures d'intervention.
     */
    public static List<TempsParActivite> tempsParActivite(List<PresenceEffectifs.Segment> segments) {
        return repartir(segments, PresenceEffectifs.Segment::activiteCode).entrySet().stream()
                .map(e -> new TempsParActivite(e.getKey(), e.getValue())).toList();
    }

    /** Les minutes payées par clé, du plus long au plus court, puis par clé — un ordre stable d'une lecture à l'autre. */
    private static Map<String, Long> repartir(List<PresenceEffectifs.Segment> segments, Function<PresenceEffectifs.Segment, String> cle) {
        Map<String, Long> par = new LinkedHashMap<>();
        segments.stream().filter(s -> s.type() != PresenceEffectifs.TypeSegment.PAUSE)
                // Une clé nulle — site ou activité non déclarés — est une ligne à part entière : le temps reste compté.
                .forEach(s -> par.merge(cle.apply(s), s.minutes(), Long::sum));
        Map<String, Long> trie = new LinkedHashMap<>();
        par.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed()
                        .thenComparing(e -> String.valueOf(e.getKey())))
                .forEach(e -> trie.put(e.getKey(), e.getValue()));
        return trie;
    }

    /**
     * <b>Les déplacements d'un quart</b> : les changements de site entre deux pointages consécutifs. Un déplacement
     * n'est pas un geste à part, c'est un pointage fait ailleurs — le déduire évite un « je me déplace » que
     * personne ne penserait à cliquer en partant.
     */
    public static List<Deplacement> deplacementsDe(List<PresenceEffectifs.PointageCourt> pointages) {
        List<PresenceEffectifs.PointageCourt> tries = pointages.stream().filter(p -> p.milieuId() != null)
                .sorted(Comparator.comparing(PresenceEffectifs.PointageCourt::horodatage)).toList();
        List<Deplacement> changements = new ArrayList<>();
        for (int i = 1; i < tries.size(); i++) {
            if (!tries.get(i).milieuId().equals(tries.get(i - 1).milieuId())) {
                changements.add(new Deplacement(tries.get(i).horodatage(), tries.get(i - 1).milieuId(), tries.get(i).milieuId()));
            }
        }
        return List.copyOf(changements);
    }

    /**
     * Les plages déjà couvertes par des pointages, pour juger un cumul. Les pointages se lisent par paires : une
     * entrée (ou une fin de pause) ouvre, la suivante ferme. Une ouverture restée sans fermeture ne couvre rien —
     * un quart en cours ne doit pas interdire de déclarer la veille.
     */
    public static List<PlageOccupee> plagesCouvertes(List<PresenceEffectifs.PointageCourt> pointages) {
        List<PlageOccupee> plages = new ArrayList<>();
        String debut = null;
        for (PresenceEffectifs.PointageCourt p : pointages.stream().sorted(Comparator.comparing(PresenceEffectifs.PointageCourt::horodatage)).toList()) {
            boolean ouvre = p.sens() == PresenceEffectifs.Sens.ENTREE || p.sens() == PresenceEffectifs.Sens.FIN_PAUSE;
            if (ouvre) {
                debut = debut == null ? p.horodatage() : debut;
            } else if (debut != null) {
                plages.add(new PlageOccupee(debut, p.horodatage()));
                debut = null;
            }
        }
        return List.copyOf(plages);
    }

    /**
     * <b>Le cumul de plusieurs saisies sur un même quart</b> (P-GP-4). Refuser la seconde saisie obligeait à faire
     * annuler la première pour ressaisir le tout ; ce qui reste refusé est le <b>chevauchement</b> — déclarer deux
     * fois le même temps doublerait le total sans que rien ne le signale. Le refus nomme la plage en conflit, sans
     * quoi la personne ne saurait pas laquelle corriger.
     */
    public static void verifierCumul(List<PresenceEffectifs.SegmentSaisi> nouveaux, List<PlageOccupee> deja) {
        for (PresenceEffectifs.SegmentSaisi n : nouveaux) {
            deja.stream().filter(d -> n.debut().compareTo(d.fin()) < 0 && d.debut().compareTo(n.fin()) < 0).findFirst()
                    .ifPresent(conflit -> {
                        throw new EffectifsInvalideException("le temps du %s de %s à %s chevauche du temps déjà déclaré (%s – %s) : corrigez l'un des deux plutôt que de compter deux fois"
                                .formatted(n.debut().substring(0, FIN_DATE), heure(n.debut()), heure(n.fin()), heure(conflit.debut()), heure(conflit.fin())));
                    });
        }
    }

    private static String heure(String iso) {
        return iso.substring(DEBUT_HEURE, FIN_HEURE);
    }
}
