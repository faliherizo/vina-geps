package ca.vina.nexu.effectifs.domaine;

import java.util.List;

/**
 * Un attribut d'un type de référentiel.
 *
 * @param reference pour {@link TypeAttribut#REFERENCE} et {@link TypeAttribut#REFERENCES} : le type visé
 * @param valeurs   pour {@link TypeAttribut#CHOIX} : les valeurs admises
 */
public record DescripteurAttribut(String nom, String libelle, TypeAttribut type, boolean obligatoire,
                                  TypeReferentiel reference, List<String> valeurs, Double min, Double max) {

    static DescripteurAttribut simple(String nom, String libelle, TypeAttribut type) {
        return new DescripteurAttribut(nom, libelle, type, false, null, List.of(), null, null);
    }

    static DescripteurAttribut borne(String nom, String libelle, boolean obligatoire, double min, double max) {
        return new DescripteurAttribut(nom, libelle, TypeAttribut.NOMBRE, obligatoire, null, List.of(), min, max);
    }

    static DescripteurAttribut reference(String nom, String libelle, TypeAttribut type, TypeReferentiel vers, boolean obligatoire) {
        return new DescripteurAttribut(nom, libelle, type, obligatoire, vers, List.of(), null, null);
    }

    static DescripteurAttribut choix(String nom, String libelle, List<String> valeurs, boolean obligatoire) {
        return new DescripteurAttribut(nom, libelle, TypeAttribut.CHOIX, obligatoire, null, valeurs, null, null);
    }
}
