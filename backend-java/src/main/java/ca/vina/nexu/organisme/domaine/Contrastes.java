package ca.vina.nexu.organisme.domaine;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * <strong>Couleurs et contrastes</strong> (WCAG 2.2, 1.4.3 et 1.4.11) — jumeau des fonctions de couleur de
 * {@code backend/src/organisme/domaine.ts}. Un contraste se <em>calcule</em>, il ne se juge pas à l'œil : une
 * charte qui rendrait un texte illisible doit être refusée avec le chiffre, et ce chiffre doit être le même
 * que celui du Node, au centième près.
 */
public final class Contrastes {

    private static final Pattern HEX = Pattern.compile("^#([0-9a-f]{6})$", Pattern.CASE_INSENSITIVE);
    private static final double SEUIL_LINEAIRE = 0.03928;
    private static final double DIVISEUR_LINEAIRE = 12.92;
    private static final double DECALAGE_GAMMA = 0.055;
    private static final double DIVISEUR_GAMMA = 1.055;
    private static final double EXPOSANT_GAMMA = 2.4;
    private static final double POIDS_ROUGE = 0.2126;
    private static final double POIDS_VERT = 0.7152;
    private static final double POIDS_BLEU = 0.0722;
    private static final double LUMIERE_AMBIANTE = 0.05;
    private static final double CENTIEMES = 100;
    private static final int CANAL_MAX = 255;
    private static final int OCTET = 8;
    private static final int BASE_HEX = 16;

    private Contrastes() {
    }

    /** Une couleur {@code #RRGGBB}, ramenée en minuscules ; tout le reste est refusé en nommant le champ. */
    public static String couleurDe(Object v, String champ) {
        if (!(v instanceof String s) || !HEX.matcher(s.strip()).matches()) {
            throw new OptionInvalideException("« " + champ + " » : une couleur #RRGGBB");
        }
        return s.strip().toLowerCase(Locale.ROOT);
    }

    private static double canal(int c) {
        double s = c / (double) CANAL_MAX;
        return s <= SEUIL_LINEAIRE ? s / DIVISEUR_LINEAIRE : Math.pow((s + DECALAGE_GAMMA) / DIVISEUR_GAMMA, EXPOSANT_GAMMA);
    }

    private static int[] canaux(String hex) {
        int n = Integer.parseInt(hex.substring(1), BASE_HEX);
        return new int[]{(n >> (2 * OCTET)) & CANAL_MAX, (n >> OCTET) & CANAL_MAX, n & CANAL_MAX};
    }

    /** Luminance relative (WCAG). */
    public static double luminance(String hex) {
        int[] c = canaux(hex);
        return POIDS_ROUGE * canal(c[0]) + POIDS_VERT * canal(c[1]) + POIDS_BLEU * canal(c[2]);
    }

    /** Le rapport de contraste entre deux couleurs, arrondi au centième <strong>vers le bas</strong> : jamais flatté. */
    public static double contraste(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        double clair = Math.max(la, lb);
        double sombre = Math.min(la, lb);
        return Math.floor(((clair + LUMIERE_AMBIANTE) / (sombre + LUMIERE_AMBIANTE)) * CENTIEMES) / CENTIEMES;
    }

    /** Assombrit (facteur ≤ 1) ou éclaircit vers le blanc (facteur > 1) une couleur. */
    public static String nuance(String hex, double facteur) {
        StringBuilder b = new StringBuilder("#");
        for (int c : canaux(hex)) {
            long v = Math.round(facteur <= 1 ? c * facteur : c + (CANAL_MAX - c) * (facteur - 1));
            b.append(deuxChiffres(Math.min(CANAL_MAX, v)));
        }
        return b.toString();
    }

    /** La couleur vue à travers un voile blanc d'opacité donnée : ce que le lecteur a réellement sous les yeux. */
    public static String sousVoile(String hex, double opacite) {
        StringBuilder b = new StringBuilder("#");
        for (int c : canaux(hex)) {
            b.append(deuxChiffres(Math.round(c * (1 - opacite) + CANAL_MAX * opacite)));
        }
        return b.toString();
    }

    /** Les trois canaux décimaux d'une couleur, séparés comme dans {@code rgba(r, g, b, …)}. */
    static String canauxDecimaux(String hex) {
        int[] c = canaux(hex);
        return c[0] + ", " + c[1] + ", " + c[2];
    }

    private static String deuxChiffres(long v) {
        String h = Long.toHexString(v);
        return h.length() == 1 ? "0" + h : h;
    }

    /**
     * Un nombre écrit comme JavaScript l'écrit dans un gabarit : {@code 21} et non {@code 21.0}. Les messages
     * de refus citent le chiffre mesuré ; ils doivent être identiques à ceux du Node, que le front affiche.
     */
    static String nombre(double d) {
        return d == Math.rint(d) ? Long.toString((long) d) : Double.toString(d);
    }
}
