package ca.vina.nexu.partage.tableur;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import ca.vina.nexu.partage.crypto.Primitives;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Un classeur Excel écrit feuille par feuille, comme le fait {@code exceljs} côté Node : une
 * première ligne en gras, des largeurs de colonne, des cellules texte ou nombre. Rien de plus —
 * un export reprend ce que l'écran affiche, il ne calcule rien.
 */
public final class Classeur implements AutoCloseable {

    private final XSSFWorkbook classeur = new XSSFWorkbook();
    private final CellStyle gras;

    public Classeur() {
        Font police = classeur.createFont();
        police.setBold(true);
        gras = classeur.createCellStyle();
        gras.setFont(police);
        classeur.getProperties().getCoreProperties().setCreator("Nexu VINA");
    }

    /** Une feuille : son nom (31 caractères au plus, la limite d'Excel) et les largeurs de ses colonnes en caractères. */
    public Feuille feuille(String nom, List<Integer> largeurs) {
        Sheet feuille = classeur.createSheet(nom.length() > 31 ? nom.substring(0, 31) : nom);
        for (int i = 0; i < largeurs.size(); i++) {
            feuille.setColumnWidth(i, largeurs.get(i) * 256);
        }
        return new Feuille(feuille);
    }

    public final class Feuille {
        private final Sheet feuille;
        private int suivante;

        private Feuille(Sheet feuille) {
            this.feuille = feuille;
        }

        public Feuille ligne(List<?> valeurs, boolean enGras) {
            Row ligne = feuille.createRow(suivante++);
            for (int i = 0; i < valeurs.size(); i++) {
                Cell cellule = ligne.createCell(i);
                Object v = valeurs.get(i);
                if (v instanceof Number n) {
                    cellule.setCellValue(n.doubleValue());
                } else {
                    cellule.setCellValue(v == null ? "" : String.valueOf(v));
                }
                if (enGras) {
                    cellule.setCellStyle(gras);
                }
            }
            return this;
        }

    }

    public byte[] octets() {
        return Primitives.surement("écriture du classeur", () -> {
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            classeur.write(sortie);
            return sortie.toByteArray();
        });
    }

    @Override
    public void close() {
        Primitives.surement("fermeture du classeur", () -> {
            classeur.close();
            return null;
        });
    }
}
