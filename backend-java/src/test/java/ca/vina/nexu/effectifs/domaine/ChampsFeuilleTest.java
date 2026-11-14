package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.effectifs.domaine.ChampsFeuille.ChampFeuille;
import ca.vina.nexu.effectifs.domaine.ChampsFeuille.TypeChamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** <strong>Les champs de la feuille propres à l'organisme</strong> — définition, vérification des valeurs, description. */
@DisplayName("ChampsFeuille — une feuille de temps qui s'adapte à l'emploi")
class ChampsFeuilleTest {

    private static final ChampFeuille VEHICULE = new ChampFeuille("vehicule", "Véhicule", TypeChamp.LISTE, List.of("A-12", "B-07"), true, true, 1);
    private static final ChampFeuille KM = new ChampFeuille("km", "Kilomètres", TypeChamp.NOMBRE, List.of(), false, true, 2);
    private static final ChampFeuille GARDE = new ChampFeuille("garde", "De garde", TypeChamp.BOOLEEN, List.of(), false, true, 3);
    private static final ChampFeuille NOTE = new ChampFeuille("note", "Note", TypeChamp.TEXTE, List.of(), false, true, 4);
    private static final ChampFeuille ANCIEN = new ChampFeuille("ancien", "Ancien", TypeChamp.TEXTE, List.of(), true, false, 5);
    private static final List<ChampFeuille> CHAMPS = List.of(VEHICULE, KM, GARDE, NOTE, ANCIEN);

    private static Map<String, Object> def(Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("code", "projet", "libelle", " Projet ", "type", "texte"));
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("le type se convertit une fois à la frontière")
    void types() {
        assertThat(Arrays.stream(TypeChamp.values()).map(t -> TypeChamp.de(t.code())).toList())
                .containsExactly(Optional.of(TypeChamp.TEXTE), Optional.of(TypeChamp.NOMBRE), Optional.of(TypeChamp.BOOLEEN), Optional.of(TypeChamp.LISTE));
        assertThat(TypeChamp.de("date")).isEmpty();
        assertThat(TypeChamp.de(null)).isEmpty();
    }

    @Test
    @DisplayName("une définition : code, libellé nettoyé, type ; une liste dédoublonnée ; actif d'emblée")
    void definir() {
        assertThat(ChampsFeuille.definir(def(), 3)).isEqualTo(new ChampFeuille("projet", "Projet", TypeChamp.TEXTE, List.of(), false, true, 3));
        ChampFeuille liste = ChampsFeuille.definir(def("type", "liste", "valeurs", List.of("A", " B ", "A"), "requis", true), 1);
        assertThat(List.of(liste.valeurs(), liste.requis())).containsExactly(List.of("A", "B"), true);
        assertThat(ChampsFeuille.definir(def("type", "nombre", "valeurs", List.of("ignorée")), 1).valeurs()).isEmpty();
        assertThat(liste.avecActif(false).actif()).isFalse();
        assertThat(liste.avecActif(false).avecActif(true)).isEqualTo(liste);
    }

    @Test
    @DisplayName("une définition invalide est refusée avec ce qu'il faut corriger")
    void definitionInvalide() {
        assertThatThrownBy(() -> ChampsFeuille.definir(def("code", "Vehicule"), 1)).hasMessageStartingWith("« code » : 2 à 40 caractères");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("code", 12), 1)).hasMessageStartingWith("« code »");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("libelle", "  "), 1)).hasMessage("« libelle » est requis");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("libelle", 4), 1)).hasMessage("« libelle » : un texte");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("libelle", "x".repeat(61)), 1)).hasMessage("« libelle » : 60 caractères au plus");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("type", "date"), 1)).hasMessage("« type » : texte, nombre, booleen ou liste");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("type", "liste"), 1)).hasMessage("« valeurs » : une liste de 1 à 30 choix");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("type", "liste", "valeurs", List.of()), 1)).hasMessageStartingWith("« valeurs »");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("type", "liste", "valeurs", java.util.Collections.nCopies(31, "a")), 1))
                .hasMessageStartingWith("« valeurs »");
        assertThatThrownBy(() -> ChampsFeuille.definir(def("type", "liste", "valeurs", List.of(" ")), 1)).hasMessage("« valeurs » est requis");
    }

    @Test
    @DisplayName("les valeurs d'un segment : canoniques, seulement les données, contre les champs actifs")
    void valeurs() {
        Map<String, Object> v = ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "km", 42.5, "garde", true, "note", "  ras  "));
        assertThat(v).containsExactly(Map.entry("vehicule", "A-12"), Map.entry("km", 42.5), Map.entry("garde", true), Map.entry("note", "ras"));
        Map<String, Object> avecVide = new HashMap<>(Map.of("vehicule", "B-07", "note", " "));
        assertThat(ChampsFeuille.valeurs(CHAMPS, avecVide)).containsOnlyKeys("vehicule");
        assertThat(ChampsFeuille.valeurs(List.of(KM), null)).isEmpty();
        assertThat(ChampsFeuille.valeurs(List.of(), Map.of())).isEmpty();
    }

    @Test
    @DisplayName("une valeur refusée dit le champ et la forme attendue")
    void valeursInvalides() {
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "inconnu", 1))).hasMessage("« attributs.inconnu » : champ inconnu ou désactivé");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "ancien", "x"))).hasMessage("« attributs.ancien » : champ inconnu ou désactivé");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of())).hasMessage("« Véhicule » est requis");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "Z-99"))).hasMessage("« Véhicule » : l'un de A-12, B-07");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", 12))).hasMessage("« Véhicule » : l'un de A-12, B-07");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "km", "dix"))).hasMessage("« Kilomètres » : un nombre");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "km", Double.NaN))).hasMessage("« Kilomètres » : un nombre");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "garde", "oui"))).hasMessage("« De garde » : oui ou non");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "note", 3))).hasMessage("« Note » : un texte");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, Map.of("vehicule", "A-12", "note", "x".repeat(201)))).hasMessage("« Note » : 200 caractères au plus");
        assertThatThrownBy(() -> ChampsFeuille.valeurs(CHAMPS, "vehicule=A-12")).hasMessage("« attributs » : un objet { code: valeur }");
    }

    @Test
    @DisplayName("la description des tâches : libre, nettoyée, bornée à 500, nulle si vide")
    void description() {
        assertThat(ChampsFeuille.description(null)).isNull();
        assertThat(ChampsFeuille.description("   ")).isNull();
        assertThat(ChampsFeuille.description(" Transport inter-hospitalier ")).isEqualTo("Transport inter-hospitalier");
        assertThat(ChampsFeuille.description("x".repeat(500))).hasSize(500);
        assertThatThrownBy(() -> ChampsFeuille.description("x".repeat(501))).hasMessage("« description » : 500 caractères au plus");
        assertThatThrownBy(() -> ChampsFeuille.description(7)).hasMessage("« description » : un texte");
    }
}
