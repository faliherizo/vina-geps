package ca.vina.nexu.partage.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Document PDF — texte replié, pages, placements, images, glyphes encodables")
class DocumentPdfTest {

    private static String texteDe(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(d);
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument d = Loader.loadPDF(pdf)) {
            return d.getNumberOfPages();
        }
    }

    @Test
    void glyphes() {
        assertThat(DocumentPdf.encodable("a → b ≥ c ≤ d … • e · f — g – h «i»")).isEqualTo("a -> b >= c <= d ... - e - f - g - h \"i\"");
        assertThat(DocumentPdf.encodable("Élève\tà\nl'école")).isEqualTo("Élève à l'école");
        assertThat(DocumentPdf.encodable("日本")).isEqualTo("??");
        assertThat(DocumentPdf.encodable(null)).isEmpty();
    }

    @Test
    void repliEtPages() throws Exception {
        try (DocumentPdf doc = new DocumentPdf(false, 50)) {
            doc.police(DocumentPdf.GRAS, 18).texte("Titre", DocumentPdf.Alignement.CENTRE);
            doc.police(DocumentPdf.NORMAL, 11).couleur(Color.GRAY).texte("À droite", DocumentPdf.Alignement.DROITE);
            List<String> lignes = doc.replier("un mot puis un très long " + "x".repeat(300) + " puis la suite", 200);
            assertThat(lignes.size()).isGreaterThan(3);
            assertThat(lignes.get(0)).isEqualTo("un mot puis un très long");
            assertThat(String.join("", lignes).replace(" ", "")).contains("x".repeat(300));
            assertThat(doc.replier("", 100)).containsExactly("");
            for (int i = 0; i < 120; i++) {
                doc.texte("Ligne " + i + " — " + "mot ".repeat(30));
            }
            doc.sauter(2000);   // plus haut qu'une page : passe à la suivante
            doc.trait(50, 100, 500);
            assertThat(doc.texteA(50, 120, 200, "Placé à gauche puis replié sur deux lignes au moins " + "y".repeat(40), DocumentPdf.Alignement.GAUCHE)).isGreaterThan(20);
            doc.texteA(50, 160, 200, "Centre", DocumentPdf.Alignement.CENTRE);
            doc.texteA(50, 180, 200, "Droite", DocumentPdf.Alignement.DROITE);
            assertThat(doc.largeurUtile()).isEqualTo(595.27563f - 100, org.assertj.core.data.Offset.offset(0.01f));
            assertThat(doc.y()).isPositive();
            byte[] pdf = doc.octets();
            assertThat(pages(pdf)).isGreaterThan(3);
            String texte = texteDe(pdf);
            assertThat(texte).contains("Titre").contains("À droite").contains("Ligne 119").contains("Centre").contains("Droite");
        }
    }

    @Test
    void images() throws Exception {
        BufferedImage img = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", png);
        try (DocumentPdf doc = new DocumentPdf(true, 36)) {
            float avant = doc.y();
            doc.image(png.toByteArray(), 200, 70);
            assertThat(doc.y()).isLessThan(avant);
            float apres = doc.y();
            doc.image("pas une image".getBytes(), 200, 70);   // ignorée
            assertThat(doc.y()).isEqualTo(apres);
            byte[] pdf = doc.octets();
            try (PDDocument d = Loader.loadPDF(pdf)) {
                assertThat(d.getPage(0).getMediaBox().getWidth()).isGreaterThan(d.getPage(0).getMediaBox().getHeight());
                assertThat(d.getPage(0).getResources().getXObjectNames()).isNotEmpty();
            }
            // Après octets(), le flux est clos : écrire encore est une erreur nommée, et fermer reste possible.
            assertThatThrownBy(() -> doc.texte("trop tard")).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("écriture PDF : ");
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("lu deux fois, le document reste complet ; fermé sans avoir été lu, il libère sa page ; le lire fermé est refusé nommément")
    void fermeture() {
        try (DocumentPdf lu = new DocumentPdf(false, 50)) {
            lu.texte("deux lectures", DocumentPdf.Alignement.GAUCHE);
            lu.octets();
            // La seconde lecture ne lève plus : elle rend un document complet (la numérotation interne peut différer).
            org.assertj.core.api.Assertions.assertThat(new String(lu.octets(), java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-").endsWith("%%EOF\n");
        }
        DocumentPdf doc = new DocumentPdf(false, 50);
        doc.texte("brouillon", DocumentPdf.Alignement.GAUCHE);
        doc.close();
        org.assertj.core.api.Assertions.assertThatThrownBy(doc::octets).isInstanceOf(IllegalStateException.class).hasMessageStartingWith("écriture PDF");
    }
}
