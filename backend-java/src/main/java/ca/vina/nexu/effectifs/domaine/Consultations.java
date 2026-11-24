package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <strong>Ce qui est une consultation</strong> (AO 26-015-DO, addenda 8, M-10) : la lecture d'un dossier de ressource ou
 * d'un horaire. Toute modification est déjà journalisée ; la consultation « doit pouvoir » l'être, de façon paramétrable.
 * Ce domaine dit seulement quel chemin lu désigne quoi ; l'organisme décide s'il le journalise. Pur.
 */
public final class Consultations {

    /** L'action consignée au journal. */
    public static final String ACTION = "consultation";

    private Consultations() {
    }

    /** Ce qu'on a consulté : un dossier de personne, ou un horaire. */
    public enum TypeObjet {
        DOSSIER("dossier"), HORAIRE("horaire");

        private final String code;

        TypeObjet(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** L'objet consulté et son identifiant (la personne, la période ; « liste » pour la liste des ressources). */
    public record Objet(TypeObjet type, String id) { }

    private record Motif(Pattern chemin, TypeObjet type) { }

    private static final List<Motif> MOTIFS = List.of(
            new Motif(Pattern.compile("^/api/effectifs/planification/ressources$"), TypeObjet.DOSSIER),
            new Motif(Pattern.compile("^/api/effectifs/planification/ressources/([^/]+)/(?:competences|rattachements)$"), TypeObjet.DOSSIER),
            new Motif(Pattern.compile("^/api/effectifs/ressources/([^/]+)/(?:quarts|pointages)$"), TypeObjet.DOSSIER),
            new Motif(Pattern.compile("^/api/effectifs/planification/periodes/([^/]+)/equipages$"), TypeObjet.HORAIRE),
            new Motif(Pattern.compile("^/api/effectifs/disponibilites/periodes/([^/]+)$"), TypeObjet.HORAIRE));

    /** L'objet que désigne un chemin lu, ou rien si ce n'est pas une consultation journalisable. */
    public static Optional<Objet> objetDe(String chemin) {
        for (Motif m : MOTIFS) {
            Matcher x = m.chemin().matcher(chemin == null ? "" : chemin);
            if (x.matches()) {
                return Optional.of(new Objet(m.type(), x.groupCount() == 0 ? "liste" : x.group(1)));
            }
        }
        return Optional.empty();
    }
}
