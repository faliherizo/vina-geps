package ca.vina.nexu.effectifs.domaine;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * <strong>La restriction de la modification des heures</strong> (E-GP-3) — le pur de {@code faitsDeModification}
 * ({@code temps/domaine.ts}) et du refus de {@code serviceModification.ts}. La seule restriction codée reste le verrou d'une
 * feuille soumise ; tout le reste (délai après le quart, période fermée, rôle, niveau) se compose dans le moteur de règles,
 * contexte {@link ContexteRegle#MODIFICATION_HEURES}.
 */
public final class ModificationHeures {

    /** L'état d'une journée hors de toute période de l'horaire. */
    public static final String AUCUNE_PERIODE = "AUCUNE";
    /** Le type d'objet sous lequel chaque jugement est tracé. */
    public static final String OBJET = "modification-heures";

    private static final double HEURE_MS = 3_600_000.0;
    private static final double CENTIEMES = 100.0;

    private ModificationHeures() {
    }

    public enum OperationModification { AJOUT, CORRECTION, RETRAIT, SAISIE_A_POSTERIORI }

    /** Le niveau de qui modifie sur l'unité, du moins au plus étendu ; l'administration porte tout l'établissement. */
    public enum NiveauActeur {
        AUCUN, LECTURE, PLANIFICATION, GESTION, ETABLISSEMENT;

        /** Le plus haut niveau de périmètre dont l'acteur voit l'unité. */
        public static NiveauActeur de(boolean administrateur, Predicate<Perimetres.Niveau> voitLUnite) {
            if (administrateur) {
                return ETABLISSEMENT;
            }
            NiveauActeur n = AUCUN;
            for (Perimetres.Niveau p : Perimetres.Niveau.values()) {
                n = voitLUnite.test(p) ? valueOf(p.name()) : n;
            }
            return n;
        }
    }

    /**
     * Une modification soumise aux règles.
     *
     * @param finDuTemps la fin du temps touché : c'est d'elle que court le délai après le quart
     */
    public record Modification(OperationModification operation, String finDuTemps, String maintenant, String periodeEtat, boolean parAutrui,
                               String roleActeur, NiveauActeur niveauActeur, TempsEffectifs.Source sourceSegment) { }

    /**
     * Les faits du contexte MODIFICATION_HEURES, dans l'ordre du catalogue. Le délai ne descend pas sous zéro : un quart pas
     * encore fini n'est « dépassé » d'aucune heure, et une règle de délai ne doit pas le voir négatif.
     */
    public static Map<String, Object> faits(Modification m) {
        double heures = (Instant.parse(m.maintenant()).toEpochMilli() - Instant.parse(m.finDuTemps()).toEpochMilli()) / HEURE_MS;
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("operation", m.operation().name());
        f.put("heuresDepuisLeQuart", nombre(Math.max(0, Math.round(heures * CENTIEMES) / CENTIEMES)));
        f.put("periodeEtat", m.periodeEtat());
        f.put("parAutrui", m.parAutrui());
        f.put("roleActeur", m.roleActeur());
        f.put("niveauActeur", m.niveauActeur().name());
        f.put("sourceSegment", m.sourceSegment().name());
        return f;
    }

    /** Un nombre comme le JSON du Node : 96, pas 96.0. */
    private static Number nombre(double v) {
        return v == Math.rint(v) ? (Number) (long) v : (Number) v;
    }

    /** Le message d'un refus : les motifs bloquants, dans l'ordre du verdict. */
    public static String messageDeRefus(List<Regles.Motif> motifs) {
        return "modification des heures refusée : " + motifs.stream().filter(m -> m.action().estBloquant()).map(Regles.Motif::message)
                .collect(Collectors.joining(" ; "));
    }
}
