package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.application.requetes.DocumentsPaie;
import ca.vina.nexu.paie.domaine.Bulletin;
import ca.vina.nexu.paie.domaine.Cumuls;
import ca.vina.nexu.paie.domaine.CyclePaie;
import ca.vina.nexu.paie.domaine.EtatCycle;
import ca.vina.nexu.paie.domaine.Gain;
import ca.vina.nexu.paie.domaine.Retenues;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rendu PDF de la paie (ADR 0084) — la fiche dit chaque gain dans son unité ; un long registre passe à la page suivante")
class RenduPdfPaieTest {

    private static final RenduPdfPaie RENDU = new RenduPdfPaie();
    private static final CyclePaie TRAITE = new CyclePaie("c1", "2026-02-15", "2026-02-28", "2026-03-05", EtatCycle.TRAITE, "x", "rh", "y", "comptable");
    private static final CyclePaie PREPARE = new CyclePaie("c1", "2026-02-15", "2026-02-28", "2026-03-05", EtatCycle.PREPARE, "x", "rh", null, null);

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static Retenues retenues() {
        return new Retenues(d("1161.00"), d("80"), d("100"), d("60"), d("0"), d("15"), d("5"), d("1161"), d("1161"), d("1161"),
                new Retenues.Employeur(d("60"), d("0"), d("21"), d("7"), d("49"), d("1"), d("20")));
    }

    private static Bulletin bulletin(int n) {
        List<Gain> gains = List.of(
                new Gain("REG", "Heures régulières", Gain.Nature.HEURES_REGULIERES, d("35"), d("30"), d("1050.00")),
                new Gain("PRIME_SOIR", "Prime de soir", Gain.Nature.PRIME_POURCENTAGE, d("7"), d("0.1"), d("21.00")),
                new Gain("FDS", "Prime de fin de semaine", Gain.Nature.PRIME_HORAIRE, d("8"), d("1.5"), d("12.00")),
                new Gain("SAL", "Salaire", Gain.Nature.SALAIRE, BigDecimal.ONE, d("60"), d("60.00")),
                new Gain("ALLOC", "Allocation", Gain.Nature.FORFAIT, BigDecimal.ONE, d("18"), d("18.00")));
        return new Bulletin("b" + n, "c1", "r" + n, "P" + n, "Personne " + n, "2026-03-05", d("35"), d("2"), retenues(), List.of("f" + n), gains);
    }

    private static String texte(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private static int pages(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    @Test
    @DisplayName("la fiche : chaque gain dans son unité, le net, le cumul de l'année, et « projet » tant que le cycle n'est pas traité")
    void fiche() throws IOException {
        Cumuls cumul = Cumuls.AUCUN.plus(retenues()).plus(retenues());
        String traitee = texte(RENDU.fiche(new DocumentsPaie.Fiche(bulletin(1), TRAITE, cumul)));
        assertThat(traitee).contains("Fiche de paie").doesNotContain("PROJET").contains("Personne 1").contains("matricule P1")
                .contains("10 %").contains("1,50 $").contains("1 050,00 $").contains("Net à payer").contains("901,00 $")
                .contains("Cumul de l'année 2026").contains("2 322,00 $").contains("Coût total pour l'employeur");
        assertThat(texte(RENDU.fiche(new DocumentsPaie.Fiche(bulletin(1), PREPARE, cumul)))).contains("PROJET (cycle non traité)");
    }

    @Test
    @DisplayName("le registre : une ligne par personne, les totaux ; au-delà d'une page, il continue sur la suivante")
    void registre() throws IOException {
        List<Bulletin> nombreux = IntStream.rangeClosed(1, 60).mapToObj(RenduPdfPaieTest::bulletin).toList();
        byte[] pdf = RENDU.registre(new DocumentsPaie.Registre(TRAITE, nombreux));
        assertThat(pages(pdf)).isGreaterThan(1);
        assertThat(texte(pdf)).contains("Registre de paie").doesNotContain("PROJET").contains("60 bulletin(s)").contains("Personne 60")
                .contains("Total").contains("69 660,00 $");
        assertThat(texte(RENDU.registre(new DocumentsPaie.Registre(PREPARE, List.of())))).contains("PROJET").contains("0 bulletin(s)");
    }

    @Test
    @DisplayName("les montants au format québécois, arrondis au cent ; les nombres sans zéros inutiles")
    void formats() {
        assertThat(RenduPdfPaie.argent(d("1234.567"))).isEqualTo("1 234,57 $");
        assertThat(RenduPdfPaie.nombre(d("7.50"))).isEqualTo("7,5");
    }
}
