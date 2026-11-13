package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Répercussion d'un poste modifié sur ses quarts déjà générés</strong> (E-CH-3) — le pur de la section
 * correspondante de {@code backend/src/effectifs/planification/domaine.ts}.
 *
 * <p>Une place <strong>pointée</strong> est un fait accompli : on ne la touche pas. Un jour devenu congé, ou un
 * poste passé à une autre unité, retire la place ; une autre plage, un autre milieu ou une autre nature la déplacent
 * vers l'équipage correspondant ; un autre corps d'emploi la requalifie sur place.
 */
public final class RepercussionPoste {

    private RepercussionPoste() {
    }

    /**
     * Les champs d'un poste qu'une modification <strong>en lot</strong> peut changer. Le code et le libellé n'en
     * sont pas : un lot les rendrait tous identiques.
     */
    public enum ChampLot {
        UNITE("uniteCode"), STATUT_EMPLOI("statutEmploiCode"), CORPS_EMPLOI("corpsEmploiCode"), MILIEU("milieuId"), NATURE("natureCode"),
        CYCLE("cycle"), DEBUT_CYCLE("debutCycle"), PARAMETRES("parametres");

        private final String code;

        ChampLot(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        static boolean connu(String code) {
            return Arrays.stream(values()).anyMatch(c -> c.code.equals(code));
        }
    }

    /** Ce que la répercussion décide pour une place. */
    public enum Action { IGNORER_POINTEE, RETIRER, DEPLACER, AJUSTER_CORPS, AUCUNE }

    public record Decision(Action action, String plageCode) {
        static Decision de(Action action) {
            return new Decision(action, null);
        }
    }

    /** Une place future d'un poste, telle que la répercussion la lit. */
    public record PlaceGeneree(String date, String uniteCode, String plageCode, String milieuId, String natureCode, String corpsEmploiCode, boolean pointee) { }

    /** Ce que le poste est devenu : ce qui décide de la forme de ses quarts. */
    public record FormePoste(String uniteCode, String milieuId, String natureCode, String corpsEmploiCode, List<List<String>> cycle, String debutCycle) {
        public static FormePoste de(DepotPostesPort.Champs c) {
            return new FormePoste(c.uniteCode(), c.milieuId(), c.natureCode(), c.corpsEmploiCode(), c.cycle(), c.debutCycle());
        }
    }

    public record JourAAjouter(String date, String plageCode) { }

    /** Les changements d'un lot de postes : au moins un, et seulement des champs connus. */
    public static Map<String, Object> changementsDe(Object v) {
        Map<String, Object> brut = new LinkedHashMap<>();
        if (v instanceof Map<?, ?> m) {
            m.forEach((k, x) -> brut.put(String.valueOf(k), x));
        }
        List<String> inconnus = brut.keySet().stream().filter(k -> !ChampLot.connu(k)).toList();
        if (!inconnus.isEmpty()) {
            throw new EffectifsInvalideException("changement de poste inconnu : " + String.join(", ", inconnus) + " (admis : "
                    + Arrays.stream(ChampLot.values()).map(ChampLot::code).collect(Collectors.joining(", ")) + ")");
        }
        if (brut.isEmpty()) {
            throw new EffectifsInvalideException("aucun changement demandé");
        }
        return brut;
    }

    /** Le sort d'une place déjà générée quand son poste change. */
    public static Decision decision(PlaceGeneree p, FormePoste poste, Set<String> plagesConnues) {
        if (p.pointee()) {
            return Decision.de(Action.IGNORER_POINTEE);
        }
        String plage = Planification.plageDuJour(poste.cycle(), poste.debutCycle(), p.date());
        if (plage == null || !plagesConnues.contains(plage) || !p.uniteCode().equals(poste.uniteCode())) {
            return Decision.de(Action.RETIRER);
        }
        if (!plage.equals(p.plageCode()) || !p.milieuId().equals(poste.milieuId()) || !p.natureCode().equals(poste.natureCode())) {
            return new Decision(Action.DEPLACER, plage);
        }
        return Decision.de(p.corpsEmploiCode().equals(poste.corpsEmploiCode()) ? Action.AUCUNE : Action.AJUSTER_CORPS);
    }

    /** Les jours (après {@code apres}) où le cycle donne désormais une plage connue sans que le poste ait de place. */
    public static List<JourAAjouter> joursAAjouter(FormePoste poste, List<String> jours, String apres, Set<String> dejaTenus, Set<String> plagesConnues) {
        List<JourAAjouter> out = new ArrayList<>();
        for (String date : jours) {
            String plage = Planification.plageDuJour(poste.cycle(), poste.debutCycle(), date);
            if (date.compareTo(apres) > 0 && plage != null && plagesConnues.contains(plage) && !dejaTenus.contains(date)) {
                out.add(new JourAAjouter(date, plage));
            }
        }
        return out;
    }

    /** Un équipage où toutes les places étaient requises le reste ; sinon le minimum choisi ne bouge pas. */
    public static int minimumApresAjout(int minimum, int placesAvant) {
        return minimum >= placesAvant ? placesAvant + 1 : minimum;
    }

    /** Le minimum après un départ : jamais plus que les places restantes. */
    public static int minimumApresRetrait(int minimum, int placesRestantes) {
        return Math.min(minimum, placesRestantes);
    }
}
