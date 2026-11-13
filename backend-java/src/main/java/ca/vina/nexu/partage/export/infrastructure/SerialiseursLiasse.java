package ca.vina.nexu.partage.export.infrastructure;

import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.pdf.DocumentPdf;
import ca.vina.nexu.partage.tableur.Classeur;

import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Le hub d'export ({@code services/exportHub.ts}) : une liasse — titre, sections de lignes — en
 * JSON, CSV (BOM, une section par bloc), XML (une balise par section), XLSX (une feuille par
 * section) ou PDF (titre puis une table simple par section). Formats ouverts, réversibilité des
 * données (AO-5338 NF-06 / NF-16).
 */
public final class SerialiseursLiasse {

    private SerialiseursLiasse() {
    }

    public static final List<String> FORMATS = List.of("json", "csv", "xml", "xlsx", "pdf");
    public static final Map<String, String> TYPES_MIME = Map.of("json", "application/json; charset=utf-8", "csv", "text/csv; charset=utf-8", "xml", "application/xml; charset=utf-8",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "pdf", "application/pdf");

    public record Section(String name, List<Map<String, Object>> rows) {
    }

    public record Liasse(String title, String generatedAt, Map<String, Object> meta, List<Section> sections) {
    }

    private static List<String> cles(Section s) {
        Set<String> cles = new LinkedHashSet<>();
        s.rows().forEach(r -> cles.addAll(r.keySet()));
        return new ArrayList<>(cles);
    }

    static String celluleCsv(Object v) {
        String s = v == null ? "" : String.valueOf(v);
        return s.matches("(?s).*[\",\r\n].*") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    public static String json(Liasse l) {
        return Json.textePresente(l);
    }

    public static String csv(Liasse l) {
        List<String> blocs = new ArrayList<>();
        for (Section s : l.sections()) {
            List<String> cles = cles(s);
            List<String> lignes = new ArrayList<>();
            lignes.add("# " + s.name());
            lignes.add(String.join(",", cles.stream().map(SerialiseursLiasse::celluleCsv).toList()));
            for (Map<String, Object> r : s.rows()) {
                lignes.add(String.join(",", cles.stream().map(k -> celluleCsv(r.get(k))).toList()));
            }
            blocs.add(String.join("\r\n", lignes));
        }
        return "﻿" + String.join("\r\n\r\n", blocs) + "\r\n";
    }

    static String echapperXml(Object v) {
        return v == null ? "" : String.valueOf(v).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    /** Comme {@code toXml} du Node, section par section — déclaration comprise, pour rendre le même document. */
    public static String xml(Liasse l) {
        List<String> parties = new ArrayList<>();
        for (Section s : l.sections()) {
            String balise = s.name().replaceAll("[^A-Za-z0-9_]", "_");
            List<String> items = new ArrayList<>();
            for (Map<String, Object> r : s.rows()) {
                List<String> champs = new ArrayList<>();
                r.forEach((k, v) -> champs.add("    <" + k + ">" + echapperXml(v) + "</" + k + ">"));
                items.add("  <row>\n" + String.join("\n", champs) + "\n  </row>");
            }
            String interne = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<" + (balise.isEmpty() ? "section" : balise) + ">\n" + String.join("\n", items) + "\n</" + (balise.isEmpty() ? "section" : balise) + ">\n";
            parties.add("  <section name=\"" + s.name() + "\">\n" + interne + "\n  </section>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<export title=\"" + l.title() + "\" generatedAt=\"" + l.generatedAt() + "\">\n" + String.join("\n", parties) + "\n</export>";
    }

    public static byte[] xlsx(Liasse l) {
        try (Classeur classeur = new Classeur()) {
            for (Section s : l.sections()) {
                List<String> cles = cles(s);
                Classeur.Feuille feuille = classeur.feuille(s.name().isEmpty() ? "Section" : s.name(), java.util.Collections.nCopies(Math.max(1, cles.size()), 22));
                if (cles.isEmpty()) {
                    feuille.ligne(List.of("(aucune donnée)"), false);
                    continue;
                }
                feuille.ligne(cles, true);
                for (Map<String, Object> r : s.rows()) {
                    feuille.ligne(cles.stream().map(k -> r.get(k) == null ? "" : r.get(k)).toList(), false);
                }
            }
            return classeur.octets();
        }
    }

    public static byte[] pdf(Liasse l) {
        try (DocumentPdf pdf = new DocumentPdf(false, 40)) {
            pdf.police(DocumentPdf.GRAS, 18).texte(l.title()).sauter(4);
            pdf.police(DocumentPdf.NORMAL, 9).couleur(new Color(0x55, 0x55, 0x55)).texte("Généré le " + l.generatedAt() + " · Nexu VINA").couleur(Color.BLACK).sauter(12);
            for (Section s : l.sections()) {
                pdf.police(DocumentPdf.GRAS, 13).couleur(new Color(0x0b, 0x5a, 0x54)).texte(s.name()).couleur(Color.BLACK).sauter(3).police(DocumentPdf.NORMAL, 9);
                if (s.rows().isEmpty()) {
                    pdf.texte("(aucune donnée)").sauter(10);
                    continue;
                }
                List<String> cles = cles(s);
                pdf.police(DocumentPdf.GRAS, 9).texte(String.join("  |  ", cles)).police(DocumentPdf.NORMAL, 9);
                for (Map<String, Object> r : s.rows()) {
                    pdf.texte(String.join("  |  ", cles.stream().map(k -> r.get(k) == null ? "" : String.valueOf(r.get(k))).toList()));
                }
                pdf.sauter(10);
            }
            return pdf.octets();
        }
    }

    /** La liasse dans le format demandé ; un format inconnu est refusé par le nom. */
    public static byte[] serialiser(Liasse l, String format) {
        return switch (format) {
            case "json" -> json(l).getBytes(StandardCharsets.UTF_8);
            case "csv" -> csv(l).getBytes(StandardCharsets.UTF_8);
            case "xml" -> xml(l).getBytes(StandardCharsets.UTF_8);
            case "xlsx" -> xlsx(l);
            case "pdf" -> pdf(l);
            default -> throw new IllegalArgumentException("unsupported export format: " + format);
        };
    }
}
