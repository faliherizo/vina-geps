package ca.vina.nexu.notifications.domaine;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * <b>Ce que le fournisseur facture</b> : des segments, pas des messages.
 *
 * <p>Un texto n'est pas une unité de coût. Twilio, Telnyx et tout fournisseur A2P découpent le
 * message selon son <b>encodage</b> :
 *
 * <ul>
 *   <li><b>GSM-7</b> — l'alphabet des textos : 160 caractères par segment, 153 dès qu'il y en a
 *       plusieurs (7 octets partent dans l'en-tête de réassemblage) ;</li>
 *   <li><b>UCS-2</b> — tout le reste : 70 caractères par segment, 67 en multipart.</li>
 * </ul>
 *
 * <p>Le piège est <b>français</b>. {@code é}, {@code à}, {@code è}, {@code ù} appartiennent à
 * GSM-7 ; <b>{@code ê}, {@code ô}, {@code û}, {@code î}, {@code â} non</b>, ni {@code ç}
 * minuscule, ni {@code À} majuscule — la table contient {@code Ç} et {@code à}, mais pas leurs
 * symétriques. « être », « coût », « août », « ça » font donc basculer le message entier en
 * UCS-2 : 70 caractères par segment au lieu de 160. Un avis de 480 caractères contenant « ça »
 * coûte <b>huit</b> segments là où le même avis sans accent en coûte trois.
 *
 * <p>C'est pourquoi le quota se compte ici et non en nombre de lignes du journal : compter les
 * messages laisse un organisme consommer huit fois son enveloppe sans jamais la « dépasser ».
 *
 * <p>Jumeau exact de {@code backend/src/notifications/segments.ts} — les deux piles doivent
 * facturer le même chiffre, sans quoi la bascule de l'une à l'autre changerait la facture.
 */
public final class Segments {

    /**
     * L'alphabet GSM 03.38 par défaut. Écrit en clair plutôt que par plages : les plages seraient
     * fausses (l'alphabet n'est pas contigu en Unicode) et invérifiables à la lecture.
     */
    private static final String GSM7_BASE =
            "@£$¥èéùìòÇ\nØø\rÅå_ÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?"
            + "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§"
            + "¿abcdefghijklmnopqrstuvwxyzäöñüà"
            + "ΔΦΓΛΩΠΨΣΘΞ";

    /**
     * Les caractères de la <b>table d'extension</b> : admis en GSM-7, mais ils comptent
     * <b>double</b> (un caractère d'échappement les précède). {@code €} est le cas courant, et il
     * surprend.
     */
    private static final String GSM7_EXTENSION = "^{}\\[~]|€";

    private static final Set<Integer> GSM7 = (GSM7_BASE + GSM7_EXTENSION).codePoints().boxed().collect(Collectors.toUnmodifiableSet());
    private static final Set<Integer> EXTENSION = GSM7_EXTENSION.codePoints().boxed().collect(Collectors.toUnmodifiableSet());

    private static final int GSM7_SEUL = 160;
    private static final int GSM7_MULTIPLE = 153;
    private static final int UCS2_SEUL = 70;
    private static final int UCS2_MULTIPLE = 67;

    private Segments() {
    }

    /** L'encodage qu'un texte impose au fournisseur. */
    public enum Encodage {
        GSM7("gsm7"),
        UCS2("ucs2");

        private final String code;

        Encodage(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * Le découpage facturé d'un texte.
     *
     * @param segments             le nombre de segments <b>facturés</b> — jamais zéro, même pour un texte vide
     * @param encodage             GSM-7 ou UCS-2
     * @param unites               les unités occupées : caractères GSM-7 (extension comptée double) ou unités UTF-16
     * @param caractereDeclencheur le premier caractère qui a forcé UCS-2, ou {@code null}. L'écran
     *                             s'en sert pour dire <i>pourquoi</i> un avis coûte cher — « le ô de
     *                             bientôt » est actionnable, « UCS-2 » ne l'est pas
     */
    public record Decoupage(int segments, Encodage encodage, int unites, String caractereDeclencheur) {
    }

    /**
     * Le découpage facturé d'un texte.
     *
     * <p>Un texte vide ou absent compte <b>un</b> segment : le fournisseur facture la tentative,
     * et un quota qui laisserait passer des messages vides gratuitement serait une porte ouverte.
     */
    public static Decoupage decouper(String texte) {
        String t = texte == null ? "" : texte;
        String declencheur = premierHorsGsm7(t);
        boolean ucs2 = declencheur != null;
        int unites = ucs2 ? t.length() : unitesGsm7(t);
        int seul = ucs2 ? UCS2_SEUL : GSM7_SEUL;
        int multiple = ucs2 ? UCS2_MULTIPLE : GSM7_MULTIPLE;
        int segments = unites <= seul ? 1 : (int) Math.ceil((double) unites / multiple);
        return new Decoupage(Math.max(1, segments), ucs2 ? Encodage.UCS2 : Encodage.GSM7, unites, declencheur);
    }

    /** Le nombre de segments facturés — le seul chiffre qui compte pour le quota et la facture. */
    public static int compter(String texte) {
        return decouper(texte).segments();
    }

    /** Le premier caractère hors de l'alphabet des textos, ou {@code null} si le texte y tient. */
    private static String premierHorsGsm7(String texte) {
        for (int i = 0; i < texte.length(); ) {
            int point = texte.codePointAt(i);
            if (!GSM7.contains(point)) {
                return new String(Character.toChars(point));
            }
            i += Character.charCount(point);
        }
        return null;
    }

    /** Les unités occupées en GSM-7 : un caractère d'extension en occupe <b>deux</b>. */
    private static int unitesGsm7(String texte) {
        int n = 0;
        for (int i = 0; i < texte.length(); ) {
            int point = texte.codePointAt(i);
            n += EXTENSION.contains(point) ? 2 : 1;
            i += Character.charCount(point);
        }
        return n;
    }
}
