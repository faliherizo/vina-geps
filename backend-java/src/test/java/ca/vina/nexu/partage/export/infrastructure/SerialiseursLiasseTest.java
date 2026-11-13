package ca.vina.nexu.partage.export.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Hub d'export — une liasse en JSON, CSV, XML, XLSX, PDF")
class SerialiseursLiasseTest {

    private static SerialiseursLiasse.Liasse liasse() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("code", "PRJ-1");
        a.put("name", "Alpha, \"bêta\"");
        a.put("hours", 12.5);
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("code", "PRJ-2");
        b.put("note", "<x>");
        return new SerialiseursLiasse.Liasse("Export projet", "2026-03-12T12:00:00.000Z", Map.of("projectId", "p1"), List.of(new SerialiseursLiasse.Section("Projet & plus", List.of(a, b)), new SerialiseursLiasse.Section("Vide", List.of())));
    }

    @Test
    void formatsTexte() {
        String csv = SerialiseursLiasse.csv(liasse());
        assertThat(csv).startsWith("﻿# Projet & plus\r\ncode,name,hours,note\r\nPRJ-1,\"Alpha, \"\"bêta\"\"\",12.5,\r\nPRJ-2,,,<x>\r\n\r\n# Vide\r\n\r\n");
        String xml = SerialiseursLiasse.xml(liasse());
        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<export title=\"Export projet\" generatedAt=\"2026-03-12T12:00:00.000Z\">");
        assertThat(xml).contains("<section name=\"Projet & plus\">").contains("<Projet___plus>").contains("<note>&lt;x&gt;</note>").contains("<name>Alpha, &quot;bêta&quot;</name>");
        String json = SerialiseursLiasse.json(liasse());
        assertThat(json).contains("\"title\" : \"Export projet\"").contains("\"hours\" : 12.5");
        assertThat(SerialiseursLiasse.serialiser(liasse(), "json")).isEqualTo(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(new String(SerialiseursLiasse.serialiser(liasse(), "csv"), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(csv);
        assertThat(new String(SerialiseursLiasse.serialiser(liasse(), "xml"), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(xml);
        assertThatThrownBy(() -> SerialiseursLiasse.serialiser(liasse(), "docx")).hasMessage("unsupported export format: docx");
        assertThat(SerialiseursLiasse.celluleCsv(null)).isEmpty();
        assertThat(SerialiseursLiasse.echapperXml(null)).isEmpty();
    }

    @Test
    void formatsBinaires() throws Exception {
        byte[] xlsx = SerialiseursLiasse.serialiser(liasse(), "xlsx");
        assertThat(xlsx.length).isGreaterThan(1000);
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            assertThat(wb.getNumberOfSheets()).isEqualTo(2);
            assertThat(wb.getSheetAt(0).getRow(0).getCell(1).getStringCellValue()).isEqualTo("name");
            assertThat(wb.getSheetAt(0).getRow(1).getCell(2).getNumericCellValue()).isEqualTo(12.5);
            assertThat(wb.getSheetAt(1).getRow(0).getCell(0).getStringCellValue()).isEqualTo("(aucune donnée)");
        }
        byte[] pdf = SerialiseursLiasse.serialiser(liasse(), "pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument d = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String texte = new org.apache.pdfbox.text.PDFTextStripper().getText(d);
            assertThat(texte).contains("Export projet").contains("Projet & plus").contains("code  |  name  |  hours  |  note").contains("(aucune donnée)");
        }
    }
}
