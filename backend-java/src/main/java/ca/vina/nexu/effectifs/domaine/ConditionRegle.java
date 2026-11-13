package ca.vina.nexu.effectifs.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Une condition de règle : un arbre de {@code tous}, {@code un_de}, {@code non} et de comparaisons sur des
 * faits. {@link #enDonnees()} rend la forme JSON du Node, celle qu'on stocke et qu'on publie : une règle
 * écrite par l'un se relit par l'autre.
 */
public sealed interface ConditionRegle permits ConditionRegle.Tous, ConditionRegle.UnDe, ConditionRegle.Non, ConditionRegle.Comparaison {

    String TOUS = "tous";
    String UN_DE = "un_de";
    String NON = "non";
    String FAIT = "fait";
    String OP = "op";
    String VALEUR = "valeur";

    Map<String, Object> enDonnees();

    record Tous(List<ConditionRegle> enfants) implements ConditionRegle {
        public Tous {
            enfants = List.copyOf(enfants);
        }

        @Override
        public Map<String, Object> enDonnees() {
            return Map.of(TOUS, enfants.stream().map(ConditionRegle::enDonnees).toList());
        }
    }

    record UnDe(List<ConditionRegle> enfants) implements ConditionRegle {
        public UnDe {
            enfants = List.copyOf(enfants);
        }

        @Override
        public Map<String, Object> enDonnees() {
            return Map.of(UN_DE, enfants.stream().map(ConditionRegle::enDonnees).toList());
        }
    }

    record Non(ConditionRegle enfant) implements ConditionRegle {
        @Override
        public Map<String, Object> enDonnees() {
            return Map.of(NON, enfant.enDonnees());
        }
    }

    /** {@code valeur} : un nombre, un texte, un booléen, une date AAAA-MM-JJ — ou leur liste pour {@code dans} / {@code hors}. */
    record Comparaison(String fait, OperateurRegle op, Object valeur) implements ConditionRegle {
        @Override
        public Map<String, Object> enDonnees() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put(FAIT, fait);
            m.put(OP, op.code());
            m.put(VALEUR, valeur);
            return m;
        }
    }
}
