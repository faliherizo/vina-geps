package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.FormatResultats;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("P-TN-8 — un résultat de recherche des effectifs emporté en CSV, Excel, PDF ou JSON")
class ResultatsTelechargesTest {

    private static final ResultatsTelecharges.Document DOC = new ResultatsTelecharges.Document("Relevé", "releve", "2026-11-10T12:00:00.000Z");

    private static Map<String, Object> ligne(Object... cv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < cv.length; i += 2) {
            m.put((String) cv[i], cv[i + 1]);
        }
        return m;
    }

    private static String texte(ResponseEntity<Object> r) {
        return new String((byte[]) r.getBody(), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("le CSV réunit les colonnes de toutes les lignes et neutralise une formule saisie par un usager")
    void csv() {
        ResponseEntity<Object> r = ResultatsTelecharges.fichier(FormatResultats.CSV, DOC,
                List.of(ligne("Groupe", "=HYPERLINK(\"x\")", "Heures", 7.5), ligne("Groupe", "OUEST", "Note", null)));
        assertThat(r.getHeaders().getFirst("Content-Type")).isEqualTo("text/csv; charset=utf-8");
        assertThat(r.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment; filename=\"releve.csv\"");
        assertThat(texte(r)).isEqualTo("Groupe,Heures,Note\n\"'=HYPERLINK(\"\"x\"\")\",7.5,\nOUEST,,\n");
    }

    @Test
    @DisplayName("le JSON en fichier est une pièce titrée et datée, pas la réponse nue de l'écran")
    void json() {
        ResponseEntity<Object> r = ResultatsTelecharges.fichier(FormatResultats.JSON, DOC, List.of(ligne("Groupe", "EST")));
        assertThat(r.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment; filename=\"releve.json\"");
        assertThat(texte(r)).contains("\"title\"").contains("Relevé").contains("2026-11-10T12:00:00.000Z").contains("EST");
    }

    @Test
    @DisplayName("Excel et PDF sont de vrais fichiers de leur format, même sans aucune ligne")
    void tableurEtPdf() {
        for (List<Map<String, Object>> lignes : List.of(List.<Map<String, Object>>of(), List.of(ligne("Groupe", "EST", "Heures", 36.0)))) {
            byte[] xlsx = (byte[]) ResultatsTelecharges.fichier(FormatResultats.XLSX, DOC, lignes).getBody();
            assertThat(new String(xlsx, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");
            ResponseEntity<Object> pdf = ResultatsTelecharges.fichier(FormatResultats.PDF, DOC, lignes);
            assertThat(pdf.getHeaders().getFirst("Content-Type")).isEqualTo("application/pdf");
            assertThat(new String((byte[]) pdf.getBody(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        }
    }
}
