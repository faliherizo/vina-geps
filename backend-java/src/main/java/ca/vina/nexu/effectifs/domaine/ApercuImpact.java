package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <strong>L'aperçu d'impact</strong> d'une règle en brouillon (« 14 disponibilités deviendraient non
 * conformes »), calculé sans rien écrire. La règle est considérée en vigueur à la date de chaque dossier et
 * les vise tous : on éprouve la condition, pas la portée.
 *
 * @param parAction le compte de dossiers par action, toutes les actions présentes (à zéro au besoin)
 * @param exemples  cinq au plus, pour que l'auteur voie ses messages rendus
 */
public record ApercuImpact(int total, Map<String, Integer> parAction, List<Exemple> exemples) {

    public static final String OBJET_ID = "objetId";
    public static final String FAITS = "faits";
    private static final int EXEMPLES_MAX = 5;
    /** Le {@code String(undefined)} du Node : un dossier sans identifiant se nomme ainsi dans le refus. */
    private static final String SANS_ID = "undefined";

    public record Exemple(String objetId, String message) { }

    public static ApercuImpact calculer(ContexteRegle contexte, ContenuRegle contenu, List<?> dossiers) {
        VersionRegle brouillon = VersionRegle.brouillon(contexte, contenu);
        Set<String> lus = ConditionsRegles.faitsLus(contenu.condition());
        Map<String, Integer> parAction = new LinkedHashMap<>();
        for (ActionRegle a : ActionRegle.values()) {
            parAction.put(a.code(), 0);
        }
        List<Exemple> exemples = new ArrayList<>();
        for (Object brut : dossiers) {
            Map<?, ?> dossier = brut instanceof Map<?, ?> m ? m : Map.of();
            Map<String, Object> faits = faitsDe(dossier);
            lus.stream().filter(f -> !faits.containsKey(f)).findFirst().ifPresent(f -> {
                throw new EffectifsInvalideException("dossier « " + nomDe(dossier) + " » : fait « " + f + " » manquant");
            });
            Regles.Verdict v = Regles.evaluer(List.of(brouillon), faits);
            parAction.merge(v.action().code(), 1, Integer::sum);
            if (!v.motifs().isEmpty() && exemples.size() < EXEMPLES_MAX) {
                exemples.add(new Exemple(identifiant(dossier), v.motifs().get(0).message()));
            }
        }
        return new ApercuImpact(dossiers.size(), parAction, List.copyOf(exemples));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> faitsDe(Map<?, ?> dossier) {
        return dossier.get(FAITS) instanceof Map<?, ?> f ? (Map<String, Object>) f : Map.of();
    }

    private static String nomDe(Map<?, ?> dossier) {
        return dossier.containsKey(OBJET_ID) ? String.valueOf(dossier.get(OBJET_ID)) : SANS_ID;
    }

    private static String identifiant(Map<?, ?> dossier) {
        Object id = dossier.get(OBJET_ID);
        return id == null ? "" : String.valueOf(id);
    }
}
