package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.FormatResultats;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.partage.export.infrastructure.SerialiseursLiasse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Un résultat de recherche rendu en fichier — P-TN-8. Le tableur et le PDF sont ceux du sérialiseur
 * partagé ; le CSV protège chaque cellule contre l'injection de formule, comme le journal le faisait
 * déjà, parce qu'un code d'unité ou un nom saisi par un usager finit ouvert dans un tableur.
 */
final class ResultatsTelecharges {

    private static final String TYPE_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private ResultatsTelecharges() {
    }

    /** Le titre nomme le document et sa feuille ; le nom de fichier reçoit l'extension du format. */
    record Document(String titre, String nomFichier, String genereLe) {
    }

    /**
     * Le résultat en fichier téléchargé. Les écrans rendent d'ordinaire le JSON eux-mêmes, en réponse
     * de lecture ; ce rendu-ci en fait une pièce datée et titrée, comme les trois autres formats.
     */
    static ResponseEntity<Object> fichier(FormatResultats format, Document d, List<Map<String, Object>> lignes) {
        return switch (format) {
            case JSON -> telecharger("application/json; charset=utf-8", d, format, SerialiseursLiasse.json(liasse(d, lignes)).getBytes(StandardCharsets.UTF_8));
            case CSV -> telecharger("text/csv; charset=utf-8", d, format, csv(lignes).getBytes(StandardCharsets.UTF_8));
            case XLSX -> telecharger(TYPE_XLSX, d, format, SerialiseursLiasse.xlsx(liasse(d, lignes)));
            case PDF -> telecharger("application/pdf", d, format, SerialiseursLiasse.pdf(liasse(d, lignes)));
        };
    }

    private static ResponseEntity<Object> telecharger(String type, Document d, FormatResultats format, byte[] octets) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + d.nomFichier() + "." + format.code() + "\"")
                .body(octets);
    }

    private static SerialiseursLiasse.Liasse liasse(Document d, List<Map<String, Object>> lignes) {
        return new SerialiseursLiasse.Liasse(d.titre(), d.genereLe(), Map.of(), List.of(new SerialiseursLiasse.Section(d.titre(), lignes)));
    }

    static String csv(List<Map<String, Object>> lignes) {
        List<String> colonnes = colonnes(lignes);
        StringBuilder b = new StringBuilder(String.join(",", colonnes.stream().map(JournalEffectifs::celluleCsv).toList())).append('\n');
        for (Map<String, Object> l : lignes) {
            b.append(String.join(",", colonnes.stream().map(c -> JournalEffectifs.celluleCsv(texte(l.get(c)))).toList())).append('\n');
        }
        return b.toString();
    }

    private static List<String> colonnes(List<Map<String, Object>> lignes) {
        Set<String> c = new LinkedHashSet<>();
        lignes.forEach(l -> c.addAll(l.keySet()));
        return new ArrayList<>(c);
    }

    private static String texte(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
