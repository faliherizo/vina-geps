package ca.vina.nexu.securite.infrastructure.web;

import ca.vina.nexu.partage.pdf.DocumentPdf;
import ca.vina.nexu.securite.application.requetes.LectureIncidents;
import org.springframework.stereotype.Component;

import java.util.List;

/** La mise en page du registre des incidents (PDFBox). */
@Component
public class RenduPdfIncidents implements LectureIncidents.RenduPdf {

    @Override
    public byte[] registre(String titre, String sousTitre, String filtres, List<List<String>> incidents) {
        try (DocumentPdf doc = new DocumentPdf(true, 36)) {
            doc.police(DocumentPdf.GRAS, 16).texte(titre);
            doc.police(DocumentPdf.NORMAL, 9).texte(sousTitre);
            if (!filtres.isEmpty()) {
                doc.texte("Filtres : " + filtres);
            }
            doc.sauter(11);
            if (incidents.isEmpty()) {
                doc.texte("Aucun incident consigné.");
            }
            for (List<String> lignes : incidents) {
                doc.police(DocumentPdf.GRAS, 10).texte(lignes.get(0));
                doc.police(DocumentPdf.NORMAL, 9);
                lignes.subList(1, lignes.size()).forEach(doc::texte);
                doc.sauter(7);
            }
            return doc.octets();
        }
    }
}
