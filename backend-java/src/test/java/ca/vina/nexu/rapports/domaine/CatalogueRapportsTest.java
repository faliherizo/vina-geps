package ca.vina.nexu.rapports.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le catalogue des rapports (STO FIN191) — ce qu'un usager a le droit de lire.
 *
 * <p>La règle décisive est de conception plutôt que d'arithmétique : <b>ce qui n'est pas déclaré
 * n'est pas interrogeable</b>. Un constructeur de rapports qui exposerait les tables serait le
 * chemin le plus court vers les données d'un autre organisme.
 */
@DisplayName("Catalogue des rapports — ce qui n'est pas déclaré n'est pas interrogeable")
class CatalogueRapportsTest {

    private static CatalogueRapports.Definition definition(List<CatalogueRapports.Colonne> colonnes,
                                                           List<String> groupes,
                                                           List<CatalogueRapports.Filtre> filtres,
                                                           String triChamp, Integer limite) {
        List<CatalogueRapports.Filtre> bornes = new java.util.ArrayList<>(List.of(PERIODE));
        bornes.addAll(filtres == null ? List.of() : filtres);
        return new CatalogueRapports.Definition("effectifs_places", colonnes, groupes, bornes,
                triChamp, false, limite);
    }

    /** Une source de faits exige sa période : chaque définition de l'essai la porte. */
    private static final CatalogueRapports.Filtre PERIODE = new CatalogueRapports.Filtre("jour",
            CatalogueRapports.Operateur.ENTRE, List.of("2031-01-01", "2031-01-31"));

    private static CatalogueRapports.Colonne colonne(String champ) {
        return new CatalogueRapports.Colonne(champ, null);
    }

    private static CatalogueRapports.Colonne agregee(String champ, CatalogueRapports.Agregat a) {
        return new CatalogueRapports.Colonne(champ, a);
    }

    // ── Les ensembles fermés ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un code hors de l'ensemble est refusé en énumérant ceux qui existent")
    void codesInconnus() {
        assertThatThrownBy(() -> CatalogueRapports.Agregat.de("mediane"))
                .hasMessageContaining("somme, moyenne, compte, minimum, maximum");
        assertThatThrownBy(() -> CatalogueRapports.Operateur.de("commence_par"))
                .hasMessageContaining("egal, different, contient");
        assertThatThrownBy(() -> CatalogueRapports.FormatExport.de("docx"))
                .hasMessageContaining("csv, xlsx, pdf, json");
        assertThatThrownBy(() -> CatalogueRapports.sourceDe("users"))
                .hasMessageContaining("effectifs_heures, effectifs_journees, effectifs_pointages, effectifs_places, effectifs_absences");
    }

    @Test
    @DisplayName("les sources se filtrent par module de l'organisme")
    void sourcesDisponibles() {
        assertThat(CatalogueRapports.sourcesDisponibles(List.of("effectifs_temps")))
                .extracting(CatalogueRapports.Source::code).containsExactly("effectifs_heures", "effectifs_journees");
        assertThat(CatalogueRapports.sourcesDisponibles(List.of())).isEmpty();
        assertThat(CatalogueRapports.sourcesDisponibles(List.of("effectifs_temps", "effectifs_presence", "effectifs_planification", "effectifs_absences")))
                .hasSize(5);
    }

    // ── La planification ─────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("un rapport sans colonne, ou sur un champ inconnu, est refusé **nommément**")
    void refusNommes() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(definition(List.of(), null, null, null, null)))
                .hasMessageContaining("au moins une colonne");
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("salaire")), null, null, null, null)))
                .hasMessageContaining("« salaire »").hasMessageContaining("effectifs_places");
    }

    /** Additionner des dates rendrait un résultat qu'on ne peut ni lire ni contester. */
    @Test
    @DisplayName("un agrégat numérique ne s'applique pas à un champ texte ; « compte » s'applique à tout")
    void agregatsTypes() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(agregee("plageHoraire", CatalogueRapports.Agregat.SOMME),
                        colonne("unite")), List.of("unite"), null, null, null)))
                .hasMessageContaining("ne s'applique pas");

        assertThat(CatalogueRapports.planifier(definition(
                List.of(agregee("plageHoraire", CatalogueRapports.Agregat.COMPTE)), null, null, null, null))
                .colonnes()).hasSize(1);
    }

    /**
     * <b>La règle qui rend un rapport lisible.</b> Sans elle, le moteur rendrait une valeur
     * arbitraire prise dans le groupe — un montant plausible et faux, le pire des deux.
     */
    @Test
    @DisplayName("une colonne libre dans un rapport agrégé est refusée, et l'erreur la nomme")
    void colonneLibre() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(definition(
                List.of(colonne("plageHoraire"), agregee("places", CatalogueRapports.Agregat.SOMME)),
                List.of(), null, null, null)))
                .hasMessageContaining("« plageHoraire »");
    }

    @Test
    @DisplayName("un regroupement sans agrégat ne fait que dédoubler les lignes : il est refusé")
    void regroupementSansAgregat() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(definition(
                List.of(colonne("plageHoraire")), List.of("unite"), null, null, null)))
                .hasMessageContaining("ajoutez une colonne agrégée");
    }

    @Test
    @DisplayName("un champ marqué non groupable ne se regroupe pas")
    void champNonGroupable() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(definition(
                List.of(agregee("pourvues", CatalogueRapports.Agregat.SOMME)), List.of("places"), null, null, null)))
                .hasMessageContaining("ne se regroupe pas");
    }

    @Test
    @DisplayName("chaque opérateur reçoit le nombre de valeurs qu'il attend, et pas un autre")
    void ariteDesFiltres() {
        var entre = new CatalogueRapports.Filtre("places", CatalogueRapports.Operateur.ENTRE, List.of(1));
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, List.of(entre), null, null)))
                .hasMessageContaining("deux bornes");

        var egal = new CatalogueRapports.Filtre("unite", CatalogueRapports.Operateur.EGAL, List.of("a", "b"));
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, List.of(egal), null, null)))
                .hasMessageContaining("une seule valeur");

        var vide = new CatalogueRapports.Filtre("unite", CatalogueRapports.Operateur.EGAL, List.of());
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, List.of(vide), null, null)))
                .hasMessageContaining("aucune valeur");
    }

    @Test
    @DisplayName("« contient » ne s'applique qu'à un champ texte")
    void contientSurTexte() {
        var f = new CatalogueRapports.Filtre("places", CatalogueRapports.Operateur.CONTIENT, List.of("5"));
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, List.of(f), null, null)))
                .hasMessageContaining("champ texte");
    }

    @Test
    @DisplayName("la limite a un défaut et un plafond, tous deux explicites")
    void limite() {
        assertThat(CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, null, null, null)).limite()).isEqualTo(1000);
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, null, null, 0)))
                .hasMessageContaining("entre 1 et 10000");
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, null, null, 20000)))
                .hasMessageContaining("entre 1 et 10000");
    }

    @Test
    @DisplayName("un plan valide porte le libellé et le type de chaque colonne")
    void planComplet() {
        CatalogueRapports.PlanRapport plan = CatalogueRapports.planifier(definition(
                List.of(colonne("unite"), agregee("places", CatalogueRapports.Agregat.SOMME)),
                List.of("unite"),
                List.of(new CatalogueRapports.Filtre("plageHoraire", CatalogueRapports.Operateur.PARMI,
                        List.of("Jour", "Nuit"))),
                "unite", 50));

        assertThat(plan.colonnes()).extracting(CatalogueRapports.ColonnePlanifiee::libelle)
                .containsExactly("Unité", "Places");
        assertThat(plan.groupes()).containsExactly("unite");
        assertThat(plan.filtres()).hasSize(2);
        assertThat(CatalogueRapports.bornesDe(plan)).isEqualTo(new CatalogueRapports.Periode("2031-01-01", "2031-01-31"));
        assertThat(plan.tri().champ()).isEqualTo("unite");
        assertThat(plan.limite()).isEqualTo(50);
    }

    @Test
    @DisplayName("un tri sur un champ inconnu est refusé, et un tri absent rend un plan sans tri")
    void tri() {
        assertThatThrownBy(() -> CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, null, "inexistant", null)))
                .hasMessageContaining("le tri");
        assertThat(CatalogueRapports.planifier(
                definition(List.of(colonne("places")), null, null, "  ", null)).tri()).isNull();
    }
}
