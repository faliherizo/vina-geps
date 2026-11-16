package ca.vina.nexu.partage.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ST-G10 (critère 39) : chaque PDF du produit est un PDF/A-2b — polices incorporées, profil sRGB, XMP de conformité,
 * identifiant. L'échantillon est déposé dans {@code target/pdfa/} pour que veraPDF le valide là où il est installé
 * ({@code scripts/verifier-pdfa.sh}).
 */
@DisplayName("PDF/A-2b — polices incorporées, sRGB, XMP, identifiant ; le texte accentué reste extractible")
class PdfAConformiteTest {

    @Test
    @DisplayName("un document produit porte tout ce que PDF/A-2b exige, et deux lectures donnent le même document")
    void conformite() throws IOException {
        byte[] octets;
        try (DocumentPdf doc = new DocumentPdf(false, 40)) {
            doc.police(DocumentPdf.GRAS, 16).texte("Équipe de garde — été");
            doc.police(DocumentPdf.NORMAL, 11).texte("Prise en charge à 14 h : « Code bleu », 5e étage.");
            octets = doc.octets();
            assertThat(doc.octets()).isNotEmpty();
        }
        Path echantillon = Path.of("target", "pdfa", "echantillon.pdf");
        Files.createDirectories(echantillon.getParent());
        Files.write(echantillon, octets);
        try (PDDocument relu = Loader.loadPDF(octets)) {
            String xmp = new String(relu.getDocumentCatalog().getMetadata().toByteArray(), StandardCharsets.UTF_8);
            assertThat(xmp).contains("<pdfaid:part>2</pdfaid:part>").contains("<pdfaid:conformance>B</pdfaid:conformance>")
                    .contains("<pdf:Producer>VINA GePS</pdf:Producer>");
            assertThat(relu.getDocumentInformation().getProducer()).isEqualTo("VINA GePS");
            assertThat(relu.getDocumentCatalog().getOutputIntents()).singleElement()
                    .satisfies(i -> assertThat(i.getOutputConditionIdentifier()).isEqualTo("sRGB IEC61966-2.1"));
            assertThat(relu.getDocument().getTrailer().getCOSArray(COSName.ID)).hasSize(2);
            for (PDPage page : relu.getPages()) {
                PDResources res = page.getResources();
                for (COSName nom : res.getFontNames()) {
                    PDFont police = res.getFont(nom);
                    assertThat(police.isEmbedded()).as(police.getName()).isTrue();
                }
            }
            assertThat(new PDFTextStripper().getText(relu)).contains("Équipe de garde").contains("à 14 h").contains("étage");
        }
    }

    @Test
    @DisplayName("une police absente du classpath est nommée, au lieu d'un PDF sans police incorporée")
    void policeAbsente() {
        assertThatThrownBy(() -> DocumentPdf.charger(new PDDocument(), "/polices/absente.ttf")).isInstanceOf(IOException.class)
                .hasMessage("police absente du classpath : /polices/absente.ttf");
    }
}
