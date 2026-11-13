package ca.vina.nexu.rapports.infrastructure.persistance;

import ca.vina.nexu.partage.pdf.DocumentPdf;
import ca.vina.nexu.partage.tableur.Classeur;

import java.util.List;
import java.util.Map;

/**
 * <b>Les exports binaires d'un rapport</b> : Excel et PDF (FIN191, P-RF-1) — jumeau de
 * {@code backend/src/rapports/exportsBinaires.ts}.
 *
 * <p>Le fichier reprend <b>exactement</b> le résultat affiché : mêmes colonnes, mêmes libellés, mêmes
 * lignes. Un export qui recalculerait finirait par contredire la page.
 */
final class ExportsRapports {

    private ExportsRapports() {
    }

    private static final int LARGEUR_MIN = 12;
    private static final int LARGEUR_MAX = 40;
    private static final float MARGE = 36;
    private static final float TITRE = 14;
    private static final float CORPS = 8;

    private static List<String> libelles(MoteurRapports.Resultat r) {
        return r.colonnes().stream().map(c -> String.valueOf(c.get("libelle"))).toList();
    }

    private static List<Object> valeurs(MoteurRapports.Resultat r, Map<String, Object> l) {
        return r.colonnes().stream().map(c -> l.get(String.valueOf(c.get("champ")))).toList();
    }

    /** Un classeur d'une feuille : l'en-tête en gras, les nombres restés nombres. */
    static byte[] xlsx(MoteurRapports.Resultat r, String titre) {
        try (Classeur classeur = new Classeur()) {
            List<String> entetes = libelles(r);
            // Excel refuse « \ / ? * [ ] : » dans le nom d'une feuille.
            String nom = titre.replaceAll("[\\\\/?*\\[\\]:]", " ");
            Classeur.Feuille feuille = classeur.feuille(nom.isBlank() ? "Rapport" : nom,
                    entetes.stream().map(e -> Math.max(LARGEUR_MIN, Math.min(LARGEUR_MAX, e.length() + 2))).toList());
            feuille.ligne(entetes, true);
            r.lignes().forEach(l -> feuille.ligne(valeurs(r, l), false));
            return classeur.octets();
        }
    }

    /** Un PDF A4 paysage : titre, sous-titre, puis une ligne par rangée du tableau, paginé. */
    static byte[] pdf(MoteurRapports.Resultat r, MoteurRapports.Entete entete) {
        try (DocumentPdf doc = new DocumentPdf(true, MARGE)) {
            doc.police(DocumentPdf.GRAS, TITRE).texte(entete.titre());
            doc.police(DocumentPdf.NORMAL, CORPS).texte(entete.sousTitre()).sauter(CORPS);
            doc.police(DocumentPdf.GRAS, CORPS).texte(String.join(" | ", libelles(r)));
            doc.police(DocumentPdf.NORMAL, CORPS);
            if (r.lignes().isEmpty()) {
                doc.texte("Aucune ligne ne répond à ces filtres.");
            }
            r.lignes().forEach(l -> doc.texte(String.join(" | ", valeurs(r, l).stream().map(v -> v == null ? "" : String.valueOf(v)).toList())));
            return doc.octets();
        }
    }
}
