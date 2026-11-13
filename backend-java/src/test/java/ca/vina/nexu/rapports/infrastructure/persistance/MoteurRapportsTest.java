package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le moteur de rapports : les faits d'une source passent par <b>son</b> fournisseur, qui borne au
 * périmètre du demandeur ; le moteur les filtre, regroupe, trie, borne et exporte.
 */
@DisplayName("Moteur de rapports — faits du fournisseur, agrégation, troncature, exports")
class MoteurRapportsTest {

    private static final FournisseurFaits.Demandeur DEMANDEUR = new FournisseurFaits.Demandeur("t-1", "u-1", "manager", "2031-01-20T12:00:00Z");

    /** Un fournisseur qui sert le comblement des quarts et retient ce qu'on lui a demandé. */
    private static final class FournisseurTemoin implements FournisseurFaits {
        final List<Demandeur> demandes = new ArrayList<>();

        @Override
        public Set<String> sources() {
            return Set.of("effectifs_places");
        }

        @Override
        public List<Map<String, Object>> faits(String source, Demandeur demandeur, CatalogueRapports.Periode periode) {
            demandes.add(demandeur);
            return List.of(
                    Map.of("jour", "2031-01-06", "unite", "EST", "plageHoraire", "Jour", "places", 4, "pourvues", 3),
                    Map.of("jour", "2031-01-06", "unite", "EST", "plageHoraire", "Nuit", "places", 2, "pourvues", 2),
                    Map.of("jour", "2031-01-07", "unite", "OUEST ; nord", "plageHoraire", "Jour", "places", 6, "pourvues", 1));
        }
    }

    private final FournisseurTemoin fournisseur = new FournisseurTemoin();
    private final MoteurRapports moteur = new MoteurRapports(List.of(fournisseur));

    private static CatalogueRapports.Definition definition(List<CatalogueRapports.Colonne> colonnes, List<String> groupes,
                                                           List<CatalogueRapports.Filtre> filtres, String tri, boolean descendant, Integer limite) {
        List<CatalogueRapports.Filtre> bornes = new ArrayList<>(List.of(PERIODE));
        bornes.addAll(filtres == null ? List.of() : filtres);
        return new CatalogueRapports.Definition("effectifs_places", colonnes, groupes, bornes, tri, descendant, limite);
    }

    private static final CatalogueRapports.Filtre PERIODE = new CatalogueRapports.Filtre("jour",
            CatalogueRapports.Operateur.ENTRE, List.of("2031-01-01", "2031-01-31"));

    private static CatalogueRapports.Colonne colonne(String champ) {
        return new CatalogueRapports.Colonne(champ, null);
    }

    private MoteurRapports.Resultat executer(CatalogueRapports.Definition d) {
        return moteur.executer(d, DEMANDEUR);
    }

    @Test
    @DisplayName("les faits viennent du fournisseur de la source, qui reçoit le demandeur pour borner son périmètre")
    void parLeFournisseur() {
        MoteurRapports.Resultat r = executer(definition(List.of(colonne("unite")), null, null, null, false, null));
        assertThat(r.lignes()).hasSize(3);
        assertThat(fournisseur.demandes).containsExactly(DEMANDEUR);
        assertThat(r.colonnes()).extracting(c -> c.get("libelle")).containsExactly("Unité");
    }

    @Test
    @DisplayName("sans fournisseur branché, ou sans demandeur, le rapport est refusé nommément")
    void refus() {
        assertThatThrownBy(() -> new MoteurRapports(List.of()).executer(definition(List.of(colonne("unite")), null, null, null, false, null), DEMANDEUR))
                .hasMessageContaining("n'a pas de fournisseur de faits branché");
        assertThatThrownBy(() -> moteur.executer(definition(List.of(colonne("unite")), null, null, null, false, null), null))
                .hasMessageContaining("exige l'identité du demandeur");
    }

    @Test
    @DisplayName("un rapport regroupé rend les totaux par groupe")
    void agregation() {
        MoteurRapports.Resultat r = executer(definition(
                List.of(colonne("unite"), new CatalogueRapports.Colonne("places", CatalogueRapports.Agregat.SOMME)),
                List.of("unite"), null, "unite", false, null));
        assertThat(r.lignes()).containsExactly(Map.of("unite", "EST", "places", 6L), Map.of("unite", "OUEST ; nord", "places", 6L));
    }

    @Test
    @DisplayName("les filtres s'appliquent aux faits, et le tri descendant aussi")
    void filtresEtTri() {
        var nuit = new CatalogueRapports.Filtre("plageHoraire", CatalogueRapports.Operateur.EGAL, List.of("Nuit"));
        assertThat(executer(definition(List.of(colonne("places")), null, List.of(nuit), null, false, null)).lignes()).hasSize(1);
        assertThat(executer(definition(List.of(colonne("places")), null, null, "places", true, null)).lignes())
                .extracting(l -> ((Number) l.get("places")).intValue()).containsExactly(6, 4, 2);
    }

    /** Rendre mille lignes d'un million protège l'écran ; annoncer la coupe dit au lecteur qu'il n'a pas tout. */
    @Test
    @DisplayName("la limite est appliquée, et la troncature est **annoncée**")
    void troncature() {
        MoteurRapports.Resultat r = executer(definition(List.of(colonne("unite")), null, null, null, false, 2));
        assertThat(r.tronque()).isTrue();
        assertThat(r.total()).isEqualTo(2);
        assertThat(r.lignes()).hasSize(2);
    }

    @Test
    @DisplayName("le CSV porte le BOM et le point-virgule qu'Excel en français attend ; une cellule à point-virgule est échappée")
    void exportCsv() {
        MoteurRapports.Resultat r = executer(definition(List.of(colonne("unite"), colonne("places")), null, null, "unite", false, null));
        MoteurRapports.Fichier f = MoteurRapports.exporter(r, "csv");
        assertThat(f.contenu()).startsWith("﻿").contains("Unité;Places").contains("EST;4").contains("\"OUEST ; nord\";6");
        assertThat(f.type()).contains("charset=utf-8");
    }

    @Test
    @DisplayName("le JSON rend les lignes ; Excel et PDF sont binaires ; un format inconnu est refusé nommément")
    void exports() {
        MoteurRapports.Resultat r = executer(definition(List.of(colonne("unite")), null, null, null, false, null));
        assertThat(MoteurRapports.exporter(r, "json").contenu()).contains("EST");
        assertThatThrownBy(() -> MoteurRapports.exporter(r, "xlsx")).hasMessageContaining("est binaire : il se produit par exporterFichier");
        assertThatThrownBy(() -> MoteurRapports.exporter(r, "pdf")).hasMessageContaining("est binaire : il se produit par exporterFichier");
        MoteurRapports.Entete entete = new MoteurRapports.Entete("Comblement", "Comblement des quarts — produit le 2031-01-20");
        assertThat(new String(MoteurRapports.exporterFichier(r, "xlsx", entete).contenu(), 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        assertThat(new String(MoteurRapports.exporterFichier(r, "pdf", entete).contenu(), 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        MoteurRapports.Resultat vide = new MoteurRapports.Resultat(r.colonnes(), List.of(), 0, false);
        assertThat(MoteurRapports.exporterFichier(vide, "pdf", new MoteurRapports.Entete("Vide", "x")).type()).isEqualTo("application/pdf");
        assertThat(MoteurRapports.exporterFichier(vide, "xlsx", new MoteurRapports.Entete(":/", "x")).contenu()).isNotEmpty();
        assertThat(new String(MoteurRapports.exporterFichier(r, "csv", entete).contenu(), java.nio.charset.StandardCharsets.UTF_8)).contains("EST");
        assertThatThrownBy(() -> MoteurRapports.exporter(r, "docx")).hasMessageContaining("csv, xlsx, pdf, json");
    }

    @Test
    @DisplayName("les formats annoncés sont ceux qui sont **branchés**")
    void formatsDisponibles() {
        assertThat(MoteurRapports.FORMATS_DISPONIBLES).containsExactly(CatalogueRapports.FormatExport.CSV,
                CatalogueRapports.FormatExport.XLSX, CatalogueRapports.FormatExport.PDF, CatalogueRapports.FormatExport.JSON);
    }
}
