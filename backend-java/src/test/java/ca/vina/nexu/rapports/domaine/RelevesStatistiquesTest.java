package ca.vina.nexu.rapports.domaine;

import ca.vina.nexu.rapports.domaine.CatalogueRapports.Agregat;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Colonne;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Definition;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Filtre;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Operateur;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * <b>Relevés statistiques paramétrés par l'organisation</b> (AO 26-015-DO, P-RF-1) — jumeau de
 * {@code backend/tests/unit/rapports-releves-effectifs.test.ts} : le taux est une somme sur une somme,
 * une source de faits exige sa période, et l'agrégation en mémoire a la sémantique du SQL.
 */
@DisplayName("Relevés statistiques — taux, période obligatoire, agrégation en mémoire")
class RelevesStatistiquesTest {

    private static final Filtre SEMAINE = new Filtre("jour", Operateur.ENTRE, List.of("2031-01-01", "2031-01-31"));

    private static Definition def(List<Colonne> colonnes, List<String> groupes, List<Filtre> filtres, String tri, boolean desc, Integer limite) {
        return new Definition("effectifs_pointages", colonnes, groupes, filtres, tri, desc, limite);
    }

    private static Definition def(List<Colonne> colonnes, List<Filtre> filtres) {
        return def(colonnes, List.of(), filtres, null, false, null);
    }

    private static Colonne somme(String champ) {
        return new Colonne(champ, Agregat.SOMME);
    }

    private static Map<String, Object> fait(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private static final List<Map<String, Object>> FAITS = List.of(
            fait("jour", "2031-01-02", "unite", "EST", "sens", "ENTREE", "resultat", "ECART", "pointages", 1, "ecarts", 1, "corrobores", 0),
            fait("jour", "2031-01-02", "unite", "EST", "sens", "SORTIE", "resultat", "CONFORME", "pointages", 1, "ecarts", 0, "corrobores", 1),
            fait("jour", "2031-01-03", "unite", "OUEST", "sens", "ENTREE", "resultat", "CONFORME", "pointages", 1, "ecarts", 0, "corrobores", 1),
            fait("jour", "2031-01-03", "unite", "OUEST", "sens", "SORTIE", "resultat", "CONFORME", "pointages", 1, "ecarts", 0, "corrobores", null),
            fait("jour", "2031-02-03", "unite", "OUEST", "sens", "SORTIE", "resultat", "CONFORME", "pointages", 1, "ecarts", 0, "corrobores", 0));

    private static List<Map<String, Object>> agreger(Definition d, List<Map<String, Object>> faits) {
        return Agregation.agreger(CatalogueRapports.planifier(d), faits);
    }

    @Test
    @DisplayName("sept sources d'effectifs, chacune sous son module, toutes bornées par le jour et gardées par la capacité")
    void catalogue() {
        assertThat(SourcesEffectifs.SOURCES).extracting(CatalogueRapports.Source::code, CatalogueRapports.Source::module).containsExactly(
                org.assertj.core.groups.Tuple.tuple("effectifs_heures", "effectifs_temps"), org.assertj.core.groups.Tuple.tuple("effectifs_journees", "effectifs_temps"),
                org.assertj.core.groups.Tuple.tuple("effectifs_pointages", "effectifs_presence"),
                org.assertj.core.groups.Tuple.tuple("effectifs_places", "effectifs_planification"),
                org.assertj.core.groups.Tuple.tuple("effectifs_absences", "effectifs_absences"),
                org.assertj.core.groups.Tuple.tuple("effectifs_horaires", "effectifs_planification"),
                org.assertj.core.groups.Tuple.tuple("effectifs_budget", "effectifs_agph"));
        assertThat(SourcesEffectifs.SOURCES).allSatisfy(s -> {
            assertThat(s.faits().periode()).isEqualTo("jour");
            assertThat(s.capacite()).isEqualTo("effectifs.manage");
            assertThat(s.champs()).extracting(CatalogueRapports.Champ::code).contains("jour", "semaine", "mois", "unite", "uniteParente", "uniteNiveau1",
                    "ressource", "titreEmploi", "statutEmploi", "convention");
        });
        assertThat(CatalogueRapports.sourcesDisponibles(List.of("effectifs_presence"))).extracting(CatalogueRapports.Source::code)
                .containsExactly("effectifs_pointages");
        assertThat(CatalogueRapports.Agregat.codes()).endsWith("taux");
    }

    @Test
    @DisplayName("le taux se planifie avec son dénominateur ; sans lui, sur un texte ou sur lui-même, il est refusé")
    void taux() {
        CatalogueRapports.PlanRapport p = CatalogueRapports.planifier(def(List.of(new Colonne("ecarts", Agregat.TAUX, "pointages")), List.of(SEMAINE)));
        assertThat(p.colonnes().get(0)).isEqualTo(new CatalogueRapports.ColonnePlanifiee("ecarts", Agregat.TAUX,
                "Pointages en écart / Pointages (%)", CatalogueRapports.TypeChamp.NOMBRE, "pointages"));
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(List.of(new Colonne("ecarts", Agregat.TAUX)), List.of(SEMAINE))))
                .hasMessage("le taux de « ecarts » exige un dénominateur : le champ dont la somme divise la sienne");
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(List.of(new Colonne("ecarts", Agregat.TAUX, " ")), List.of(SEMAINE))))
                .hasMessageContaining("exige un dénominateur");
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(List.of(new Colonne("ecarts", Agregat.TAUX, "sens")), List.of(SEMAINE))))
                .hasMessage("le dénominateur « sens » est de type texte : un taux divise par un nombre");
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(List.of(new Colonne("ecarts", Agregat.TAUX, "ecarts")), List.of(SEMAINE))))
                .hasMessageContaining("vaudrait 100 % partout");
    }

    @Test
    @DisplayName("une source de faits exige sa période : présente, valide, ordonnée, d'une année au plus")
    void periode() {
        List<Colonne> c = List.of(somme("pointages"));
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(c, List.of())))
                .hasMessage("la source « effectifs_pointages » exige sa période : un filtre « entre » sur « jour » (deux dates AAAA-MM-JJ)");
        assertThatThrownBy(() -> CatalogueRapports.planifier(def(c, List.of(new Filtre("jour", Operateur.EGAL, List.of("2031-01-01"))))))
                .hasMessageContaining("exige sa période");
        for (List<Object> invalide : List.of(List.<Object>of("2031-01-01", "demain"), List.<Object>of("2031-02-30", "2031-03-01"))) {
            assertThatThrownBy(() -> CatalogueRapports.planifier(def(c, List.of(new Filtre("jour", Operateur.ENTRE, invalide)))))
                    .hasMessage("la période de « jour » attend deux dates AAAA-MM-JJ");
        }
        for (List<Object> horsBornes : List.of(List.<Object>of("2031-02-01", "2031-01-01"), List.<Object>of("2031-01-01", "2032-01-02"))) {
            assertThatThrownBy(() -> CatalogueRapports.planifier(def(c, List.of(new Filtre("jour", Operateur.ENTRE, horsBornes)))))
                    .hasMessage("la période de « jour » va du plus ancien au plus récent, sur 366 jours au plus");
        }
        assertThat(CatalogueRapports.bornesDe(CatalogueRapports.planifier(def(c, List.of(SEMAINE)))))
                .isEqualTo(new CatalogueRapports.Periode("2031-01-01", "2031-01-31"));
    }

    @Test
    @DisplayName("regrouper, sommer, compter, calculer le taux par groupe ; la période filtre")
    void regroupement() {
        List<Map<String, Object>> l = agreger(def(List.of(new Colonne("unite", null), somme("pointages"), new Colonne("ecarts", Agregat.TAUX, "pointages"),
                new Colonne("corrobores", Agregat.COMPTE)), List.of("unite"), List.of(SEMAINE), "unite", false, null), FAITS);
        assertThat(l).containsExactly(Map.of("unite", "EST", "pointages", 2L, "ecarts", 50L, "corrobores", 2L),
                Map.of("unite", "OUEST", "pointages", 2L, "ecarts", 0L, "corrobores", 1L));
    }

    @Test
    @DisplayName("moyenne, minimum, maximum ; un taux sur un dénominateur nul et une somme d'aucune valeur sont nuls")
    void extremes() {
        assertThat(agreger(def(List.of(new Colonne("corrobores", Agregat.MOYENNE), new Colonne("ecarts", Agregat.MAXIMUM),
                new Colonne("pointages", Agregat.MINIMUM)), List.of(SEMAINE)), FAITS))
                .containsExactly(Map.of("corrobores", 0.67, "ecarts", 1L, "pointages", 1L));
        Map<String, Object> vide = new HashMap<>();
        vide.put("ecarts", null);
        vide.put("corrobores", null);
        vide.put("pointages", null);
        assertThat(agreger(def(List.of(new Colonne("ecarts", Agregat.TAUX, "pointages"), new Colonne("corrobores", Agregat.MOYENNE), somme("pointages")),
                List.of(SEMAINE)), List.of())).containsExactly(vide);
    }

    private static Object compte(Filtre f) {
        return agreger(def(List.of(somme("pointages")), List.of(SEMAINE, f)), FAITS).get(0).get("pointages");
    }

    @Test
    @DisplayName("chaque opérateur filtre comme en SQL ; « contient » ignore la casse")
    void operateurs() {
        assertThat(compte(new Filtre("sens", Operateur.EGAL, List.of("ENTREE")))).isEqualTo(2L);
        assertThat(compte(new Filtre("sens", Operateur.DIFFERENT, List.of("ENTREE")))).isEqualTo(2L);
        assertThat(compte(new Filtre("resultat", Operateur.CONTIENT, List.of("cart")))).isEqualTo(1L);
        assertThat(compte(new Filtre("ecarts", Operateur.SUPERIEUR, List.of(0)))).isEqualTo(1L);
        assertThat(compte(new Filtre("ecarts", Operateur.INFERIEUR, List.of("1")))).isEqualTo(3L);
        assertThat(compte(new Filtre("unite", Operateur.PARMI, List.of("EST", "NORD")))).isEqualTo(2L);
        assertThat(compte(new Filtre("jour", Operateur.ENTRE, List.of("2031-01-03", "2031-01-03")))).isEqualTo(2L);
    }

    @Test
    @DisplayName("projeter sans agrégat, trier par un champ non affiché, décroissant ; une ligne de plus que la limite")
    void projectionEtTri() {
        assertThat(agreger(def(List.of(new Colonne("sens", null)), List.of(), List.of(SEMAINE), "jour", true, 2), FAITS))
                .containsExactly(Map.of("sens", "ENTREE"), Map.of("sens", "SORTIE"), Map.of("sens", "ENTREE"));
        assertThat(agreger(def(List.of(new Colonne("unite", null), somme("ecarts")), List.of("unite"), List.of(SEMAINE), "ecarts", true, null), FAITS))
                .extracting(l -> l.get("unite")).containsExactly("EST", "OUEST");
    }

    @Test
    @DisplayName("une valeur manquante, vide ou illisible ne compte pas, ne casse ni le tri ni le taux")
    void valeursSales() {
        List<Map<String, Object>> sales = new ArrayList<>(List.of(
                fait("jour", "2031-01-02", "unite", "EST", "pointages", "", "ecarts", "abc"),
                fait("jour", "2031-01-02", "pointages", 2, "ecarts", 1),
                fait("jour", "2031-01-03", "unite", "EST", "pointages", null, "ecarts", null)));
        assertThat(agreger(def(List.of(somme("pointages"), new Colonne("ecarts", Agregat.TAUX, "pointages")), List.of(SEMAINE)), sales))
                .containsExactly(Map.of("pointages", 2L, "ecarts", 50L));
        assertThat(agreger(def(List.of(new Colonne("pointages", null)), List.of(), List.of(SEMAINE), "pointages", false, null), sales))
                .extracting(l -> l.get("pointages")).containsExactly("", null, 2);
        assertThat(agreger(def(List.of(somme("pointages")), List.of(SEMAINE, new Filtre("unite", Operateur.CONTIENT, List.of("es")))), sales))
                .extracting(l -> l.get("pointages")).containsExactly((Object) null);
        assertThat(agreger(def(List.of(new Colonne("unite", null), new Colonne("pointages", Agregat.COMPTE)), List.of("unite"), List.of(SEMAINE), null, false, null), sales))
                .extracting(l -> l.get("unite"), l -> l.get("pointages"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("EST", 1L), org.assertj.core.groups.Tuple.tuple(null, 1L));
    }
}
