package ca.vina.nexu.partage.pdf;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDMetadata;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.color.PDOutputIntent;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Un document PDF écrit de haut en bas, comme PDFKit le fait côté Node : une police, une marge, un curseur vertical, le passage à la page suivante quand le bas est
 * atteint, le retour à la ligne des textes trop longs, des placements absolus pour les tableaux.
 * Les glyphes hors WinAnsi (flèches, « ≥ », points de suspension typographiques) sont remplacés :
 * une police standard ne les a pas, et un PDF qui refuse de s'écrire pour un caractère ne sert à rien.
 *
 * <p><strong>PDF/A-2b</strong> (FX-AO-20260810, ST-G10, critère 39) : un PDF d'archive ne dépend de rien d'extérieur.
 * Les polices sont donc <em>incorporées</em> — Liberation Sans, métriquement compatible avec Helvetica, sous licence
 * libre avec exception pour l'incorporation (voir {@code polices/LICENCE-LIBERATION.txt}) ; {@link #NORMAL} et
 * {@link #GRAS} ne sont que des repères, remplacés dans chaque document. S'y ajoutent le profil de couleur sRGB, les
 * métadonnées XMP qui déclarent la conformité, et l'identifiant du fichier.
 */
public final class DocumentPdf implements AutoCloseable {

    public static final PDFont NORMAL = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    public static final PDFont GRAS = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final Map<String, String> REMPLACEMENTS = Map.of("→", "->", "≥", ">=", "≤", "<=", "…", "...", "•", "-", "·", "-", "—", "-", "–", "-", "«", "\"", "»", "\"");

    private static final String POLICE_NORMALE = "/polices/LiberationSans-Regular.ttf";
    private static final String POLICE_GRASSE = "/polices/LiberationSans-Bold.ttf";
    private static final String PRODUCTEUR = "VINA GePS";
    private static final String SRGB = "sRGB IEC61966-2.1";
    /** Les métadonnées d'un PDF/A-2b : partie 2, conformité B, et le producteur. */
    static final String XMP = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
            + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">"
            + "<rdf:Description rdf:about=\"\" xmlns:pdfaid=\"http://www.aiim.org/pdfa/ns/id/\" xmlns:pdf=\"http://ns.adobe.com/pdf/1.3/\">"
            + "<pdfaid:part>2</pdfaid:part><pdfaid:conformance>B</pdfaid:conformance><pdf:Producer>" + PRODUCTEUR + "</pdf:Producer>"
            + "</rdf:Description></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";

    private final PDDocument document = new PDDocument();
    private final PDFont normale = incorporer(POLICE_NORMALE);
    private final PDFont grasse = incorporer(POLICE_GRASSE);
    private final PDRectangle format;
    private final float marge;
    private PDPage page;
    private PDPageContentStream flux;
    private float y;
    private float taille = 11;
    private PDFont police = normale;
    private Color couleur = Color.BLACK;
    private boolean conforme;

    public DocumentPdf(boolean paysage, float marge) {
        this(paysage ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()) : PDRectangle.A4, marge);
    }

    public DocumentPdf(PDRectangle format, float marge) {
        this.format = format;
        this.marge = marge;
        nouvellePage();
    }

    /** Le texte tel que la police standard sait l'écrire : substitutions connues, puis « ? » pour le reste. */
    public static String encodable(String texte) {
        String t = texte == null ? "" : texte;
        for (Map.Entry<String, String> e : REMPLACEMENTS.entrySet()) {
            t = t.replace(e.getKey(), e.getValue());
        }
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            sb.append(c == '\n' || c == '\r' || c == '\t' ? ' ' : c);
        }
        String propre = sb.toString();
        StringBuilder sortie = new StringBuilder(propre.length());
        for (int i = 0; i < propre.length(); i++) {
            String c = propre.substring(i, i + 1);
            try {
                NORMAL.encode(c);
                sortie.append(c);
            } catch (IOException | IllegalArgumentException e) {
                sortie.append('?');
            }
        }
        return sortie.toString();
    }

    private static <T> T surement(Calcul<T> calcul) {
        try {
            return calcul.faire();
        } catch (IOException e) {
            throw new IllegalStateException("écriture PDF : " + e.getMessage(), e);
        }
    }

    private interface Calcul<T> {
        T faire() throws IOException;
    }

    /** Une police incorporée (sous-ensemble) dans ce document : PDF/A interdit de compter sur celles du lecteur. */
    private PDFont incorporer(String ressource) {
        return surement(() -> charger(document, ressource));
    }

    /** La police d'une ressource, incorporée dans ce document ; absente, l'erreur la nomme. */
    static PDFont charger(PDDocument doc, String ressource) throws IOException {
        try (InputStream flux = DocumentPdf.class.getResourceAsStream(ressource)) {
            if (flux == null) {
                throw new IOException("police absente du classpath : " + ressource);
            }
            return PDType0Font.load(doc, flux);
        }
    }

    private void nouvellePage() {
        surement(() -> {
            if (flux != null) {
                flux.close();
            }
            page = new PDPage(format);
            document.addPage(page);
            flux = new PDPageContentStream(document, page);
            y = format.getHeight() - marge;
            return null;
        });
    }

    public float largeurUtile() {
        return format.getWidth() - 2 * marge;
    }

    public float y() {
        return y;
    }

    public DocumentPdf police(PDFont p, float t) {
        this.police = p == NORMAL ? normale : p == GRAS ? grasse : p;
        this.taille = t;
        return this;
    }

    public DocumentPdf couleur(Color c) {
        this.couleur = c;
        return this;
    }

    private float largeurDe(String texte) {
        return surement(() -> police.getStringWidth(texte)) / 1000f * taille;
    }

    /** Découpe un texte en lignes qui tiennent dans {@code largeur} ; un mot trop long est coupé. */
    public List<String> replier(String texte, float largeur) {
        List<String> lignes = new ArrayList<>();
        StringBuilder courante = new StringBuilder();
        for (String mot : encodable(texte).split(" ")) {
            String essai = courante.isEmpty() ? mot : courante + " " + mot;
            if (largeurDe(essai) <= largeur || courante.isEmpty() && largeurDe(mot) <= largeur) {
                courante = new StringBuilder(essai);
                continue;
            }
            if (!courante.isEmpty()) {
                lignes.add(courante.toString());
                courante = new StringBuilder();
            }
            String reste = mot;
            while (largeurDe(reste) > largeur && reste.length() > 1) {
                int n = reste.length();
                while (n > 1 && largeurDe(reste.substring(0, n)) > largeur) {
                    n--;
                }
                lignes.add(reste.substring(0, n));
                reste = reste.substring(n);
            }
            courante = new StringBuilder(reste);
        }
        lignes.add(courante.toString());
        return lignes;
    }

    private void ecrireA(float x, float yBase, String texte) {
        surement(() -> {
            flux.beginText();
            flux.setFont(police, taille);
            flux.setNonStrokingColor(couleur);
            flux.newLineAtOffset(x, yBase);
            flux.showText(texte);
            flux.endText();
            return null;
        });
    }

    private void assurerPlace(float hauteur) {
        if (flux == null) {
            throw new IllegalStateException("écriture PDF : le document est déjà produit");
        }
        if (y - hauteur < marge) {
            nouvellePage();
        }
    }

    /** Un paragraphe au curseur, replié à la largeur utile, aligné à gauche, au centre ou à droite. */
    public DocumentPdf texte(String texte, Alignement alignement) {
        float interligne = taille * 1.25f;
        for (String ligne : replier(texte, largeurUtile())) {
            assurerPlace(interligne);
            float x = switch (alignement) {
                case GAUCHE -> marge;
                case CENTRE -> marge + (largeurUtile() - largeurDe(ligne)) / 2;
                case DROITE -> marge + largeurUtile() - largeurDe(ligne);
            };
            ecrireA(x, y - taille, ligne);
            y -= interligne;
        }
        return this;
    }

    public DocumentPdf texte(String texte) {
        return texte(texte, Alignement.GAUCHE);
    }

    public enum Alignement { GAUCHE, CENTRE, DROITE }

    /** Un texte placé (coordonnées depuis la gauche et le haut, comme PDFKit), replié à {@code largeur} ; rend la hauteur occupée. */
    public float texteA(float x, float depuisLeHaut, float largeur, String texte, Alignement alignement) {
        float interligne = taille * 1.25f;
        float yCourant = format.getHeight() - depuisLeHaut;
        for (String ligne : replier(texte, largeur)) {
            float dx = switch (alignement) {
                case GAUCHE -> 0;
                case CENTRE -> (largeur - largeurDe(ligne)) / 2;
                case DROITE -> largeur - largeurDe(ligne);
            };
            ecrireA(x + dx, yCourant - taille, ligne);
            yCourant -= interligne;
        }
        return format.getHeight() - depuisLeHaut - yCourant;
    }

    /** Un trait horizontal placé depuis le haut. */
    public DocumentPdf trait(float x1, float depuisLeHaut, float x2) {
        return trait(x1, depuisLeHaut, x2, Color.BLACK);
    }

    public DocumentPdf trait(float x1, float depuisLeHaut, float x2, Color c) {
        surement(() -> {
            flux.setStrokingColor(c);
            flux.moveTo(x1, format.getHeight() - depuisLeHaut);
            flux.lineTo(x2, format.getHeight() - depuisLeHaut);
            flux.stroke();
            return null;
        });
        return this;
    }

    /** Un espace vertical ; s'il ne tient pas sur la page, on repart en haut de la suivante. */
    public DocumentPdf sauter(float hauteur) {
        boolean deborde = y - hauteur < marge;
        assurerPlace(hauteur);
        if (!deborde) {
            y -= hauteur;
        }
        return this;
    }

    /** Une image (PNG ou JPEG) au curseur, contenue dans {@code maxLargeur × maxHauteur} ; une image illisible est ignorée, comme côté Node. */
    public DocumentPdf image(byte[] octets, float maxLargeur, float maxHauteur) {
        try {
            PDImageXObject img = PDImageXObject.createFromByteArray(document, octets, "image");
            float echelle = Math.min(maxLargeur / img.getWidth(), maxHauteur / img.getHeight());
            float l = img.getWidth() * echelle;
            float h = img.getHeight() * echelle;
            assurerPlace(h);
            flux.drawImage(img, marge, y - h, l, h);
            y -= h + 4;
        } catch (IOException | RuntimeException e) {
            // image invalide : ignorée
        }
        return this;
    }

    /** Les octets du document ; une seconde lecture rend un document complet, une lecture après fermeture est refusée. */
    public byte[] octets() {
        return surement(() -> {
            if (flux != null) {
                flux.close();
                flux = null;
            }
            conformer();
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            document.save(sortie);
            return sortie.toByteArray();
        });
    }

    /**
     * PDF/A-2b, une fois : le XMP qui déclare la conformité (et le même producteur que le dictionnaire d'information,
     * que les validateurs comparent), le profil sRGB des couleurs, l'identifiant du fichier.
     */
    private void conformer() throws IOException {
        if (conforme) {
            return;
        }
        conforme = true;
        PDDocumentCatalog catalogue = document.getDocumentCatalog();
        PDMetadata metadonnees = new PDMetadata(document);
        metadonnees.importXMPMetadata(XMP.getBytes(StandardCharsets.UTF_8));
        catalogue.setMetadata(metadonnees);
        document.getDocumentInformation().setProducer(PRODUCTEUR);
        PDOutputIntent intention = new PDOutputIntent(document, new ByteArrayInputStream(ICC_Profile.getInstance(ColorSpace.CS_sRGB).getData()));
        intention.setInfo(SRGB);
        intention.setOutputCondition(SRGB);
        intention.setOutputConditionIdentifier(SRGB);
        intention.setRegistryName("http://www.color.org");
        catalogue.addOutputIntent(intention);
        byte[] id = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        COSArray identifiant = new COSArray();
        identifiant.add(new COSString(id));
        identifiant.add(new COSString(id));
        document.getDocument().getTrailer().setItem(COSName.ID, identifiant);
    }

    @Override
    public void close() {
        surement(() -> {
            if (flux != null) {
                flux.close();
                flux = null;
            }
            document.close();
            return null;
        });
    }
}
