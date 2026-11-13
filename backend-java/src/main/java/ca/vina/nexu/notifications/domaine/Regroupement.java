package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * <strong>Le regroupement des avis</strong> (BF-124) — jumeau de
 * {@code backend/src/notifications/regroupement.ts}. Quand un lot touche plusieurs objets pour la
 * même personne (douze quarts publiés), elle reçoit un avis groupé ou un avis par objet. La
 * décision se prend à deux niveaux : le choix de la personne l'emporte, puis le défaut de
 * l'établissement, puis celui du produit.
 */
public final class Regroupement {

    private static final int MAX_LIGNES_DETAILLEES = 12;
    private static final String DETAIL_TROP_LONG = "Le détail se lit dans l'application : la liste est trop longue pour un message.";

    private Regroupement() {
    }

    public enum Mode {
        UN_AVIS_GROUPE("un_avis_groupe", "un seul avis pour tout le lot"),
        UN_AVIS_PAR_OBJET("un_avis_par_objet", "un avis par quart");

        private final String code;
        private final String libelle;

        Mode(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        public static Mode de(Object brut) {
            String v = brut == null ? "" : String.valueOf(brut).trim();
            return Arrays.stream(values()).filter(m -> m.code.equals(v)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("mode de regroupement inconnu « " + v + " » — attendu : "
                            + String.join(", ", Arrays.stream(values()).map(Mode::code).toList())));
        }
    }

    public static final Mode PAR_DEFAUT = Mode.UN_AVIS_GROUPE;

    public record ObjetAvis(String personId, String ligne) {
    }

    public record MessageConsolide(String personId, int objets, String corps) {
    }

    public record Compte(int avis, int personnes, int objets) {
    }

    public static Mode effectif(Mode personnel, Mode etablissement) {
        return personnel != null ? personnel : etablissement != null ? etablissement : PAR_DEFAUT;
    }

    public static List<MessageConsolide> consolider(List<ObjetAvis> objets, Function<String, Mode> modeDe, IntFunction<String> entete) {
        Map<String, List<String>> parPersonne = new LinkedHashMap<>();
        objets.forEach(o -> parPersonne.computeIfAbsent(o.personId(), k -> new ArrayList<>()).add(o.ligne()));
        List<MessageConsolide> messages = new ArrayList<>();
        parPersonne.forEach((personId, lignes) -> {
            if (modeDe.apply(personId) == Mode.UN_AVIS_PAR_OBJET) {
                lignes.forEach(l -> messages.add(new MessageConsolide(personId, 1, entete.apply(1) + "\n\n" + l)));
                return;
            }
            String detail = lignes.size() <= MAX_LIGNES_DETAILLEES ? "\n\n" + String.join("\n", lignes) : "\n\n" + DETAIL_TROP_LONG;
            messages.add(new MessageConsolide(personId, lignes.size(), entete.apply(lignes.size()) + detail));
        });
        return messages;
    }

    public static Compte compter(List<MessageConsolide> messages) {
        return new Compte(messages.size(), (int) messages.stream().map(MessageConsolide::personId).distinct().count(),
                messages.stream().mapToInt(MessageConsolide::objets).sum());
    }
}
