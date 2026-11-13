package ca.vina.nexu.auth.domaine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Règles des questions secrètes (second facteur par questions) — port de
 * {@code backend/src/auth/securityQuestionsStore.ts}, sans le stockage.
 *
 * <p>Au moins trois questions distinctes ; une question fait cinq caractères ou plus, une
 * réponse deux ou plus. Les réponses se comparent normalisées (rognées, en minuscules) :
 * « Montréal » et « montréal » sont la même réponse.
 */
public final class QuestionsSecretes {

    public static final int MINIMUM = 3;

    /** Les questions de secours, offertes tant qu'aucun catalogue n'est renseigné ({@code SECURITY_QUESTIONS}). */
    public static final List<String> DEFAUT = List.of(
            "Quel est le nom de votre premier animal de compagnie ?",
            "Quel est le nom de jeune fille de votre mère ?",
            "Dans quelle ville êtes-vous né(e) ?",
            "Quel est le nom de votre école primaire ?",
            "Quel est le prénom de votre meilleur(e) ami(e) d'enfance ?",
            "Quel est le modèle de votre première voiture ?");

    private QuestionsSecretes() {
    }

    /** Une question et sa réponse en clair, telles que reçues. */
    public record Paire(String question, String reponse) {
    }

    /** Une question et sa réponse normalisée, prêtes à être hachées. */
    public record PaireNettoyee(String question, String reponseNormalisee) {
    }

    /** Liste effective : dédupliquée sans égard à la casse, dans l'ordre reçu ; les questions de secours si elle est vide. */
    public static List<String> effectives(List<String> actives) {
        Set<String> vues = new HashSet<>();
        List<String> sortie = new ArrayList<>();
        for (String q : actives) {
            String cle = q.trim().toLowerCase(Locale.ROOT);
            if (!cle.isEmpty() && vues.add(cle)) {
                sortie.add(q);
            }
        }
        return sortie.isEmpty() ? DEFAUT : sortie;
    }

    /** Une question du catalogue fait cinq caractères ou plus — message du Node. */
    public static String questionDeCatalogue(String brut) {
        String q = (brut == null ? "" : brut).trim();
        if (q.length() < 5) {
            throw new IllegalArgumentException("la question doit comporter au moins 5 caractères");
        }
        return q;
    }

    public static String normaliser(String reponse) {
        return (reponse == null ? "" : reponse).trim().toLowerCase(Locale.ROOT);
    }

    /** Valide et normalise, dans l'ordre, avec les messages du Node. */
    public static List<PaireNettoyee> nettoyer(List<Paire> paires) {
        if (paires == null || paires.size() < MINIMUM) {
            throw new IllegalArgumentException("at least " + MINIMUM + " security questions are required");
        }
        Set<String> vues = new HashSet<>();
        List<PaireNettoyee> propres = new ArrayList<>();
        for (int i = 0; i < paires.size(); i++) {
            Paire p = paires.get(i);
            String question = (p == null || p.question() == null ? "" : p.question()).trim();
            String reponse = p == null || p.reponse() == null ? "" : p.reponse();
            if (question.length() < 5) {
                throw new IllegalArgumentException("question #" + (i + 1) + " must be at least 5 characters");
            }
            if (reponse.trim().length() < 2) {
                throw new IllegalArgumentException("answer #" + (i + 1) + " must be at least 2 characters");
            }
            if (!vues.add(question.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("security questions must be distinct");
            }
            propres.add(new PaireNettoyee(question, normaliser(reponse)));
        }
        return propres;
    }
}
