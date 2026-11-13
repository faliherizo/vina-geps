package ca.vina.nexu.approbations.domaine;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <strong>Le catalogue des types de document</strong> du moteur d'approbation — jumeau de
 * {@code backend/src/approvals/documentTypes.ts}.
 *
 * <p>Le champ « Type de document » des règles était une saisie libre : une faute de frappe rendait
 * une règle <i>muette</i>, et deux orthographes du même métier créaient deux flux. Un ensemble fermé
 * appartient à un catalogue, isolé par organisme et trilingue. Ce domaine ne connaît que le
 * catalogue ; les règles vivent dans {@link Approbations}.
 */
public final class TypesDeDocument {

    /** Le point est admis : les types posés par le code en portent ({@code effectifs.feuille}). */
    private static final Pattern CODE_VALIDE = Pattern.compile("^[a-z][a-z0-9_.]{1,40}$");
    private static final Pattern DIACRITIQUES = Pattern.compile("\\p{M}+");
    private static final Pattern HORS_CODE = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SOULIGNES_EXTREMES = Pattern.compile("^_+|_+$");
    private static final int LONGUEUR_SLUG = 40;
    private static final int LIBELLE_MIN = 2;
    private static final int ORDRE_PAR_DEFAUT = 99;
    private static final String PREFIXE_SLUG = "t_";

    private TypesDeDocument() {
    }

    public record Type(String id, String tenantId, String code, String labelFr, String labelEn, String labelMg,
                       boolean systeme, int sortOrder, boolean active, String createdAt) {
    }

    /** Un type que le code émet : il doit exister dans tout organisme. */
    public record Systeme(String code, String labelFr, String labelEn, String labelMg, int sortOrder) {
    }

    public record Nouveau(String code, String labelFr, String labelEn, String labelMg, int sortOrder) {
    }

    /** Ce qu'une modification touche ; {@code null} = laissé tel quel. */
    public record Modification(String labelFr, String labelEn, String labelMg, Integer sortOrder, Boolean active) {
    }

    /**
     * Les types que <strong>le code émet</strong>. Toute nouvelle émission d'une demande
     * d'approbation ailleurs dans le code s'ajoute ici : c'est la liste qui fait foi.
     */
    public static final List<Systeme> TYPES_SYSTEME = List.of(
            new Systeme("contract", "Contrat", "Contract", "Fifanarahana", 10),
            new Systeme("purchase_order", "Bon de commande", "Purchase order", "Taratasy fanafarana", 20),
            new Systeme("expense", "Dépense", "Expense", "Fandaniana", 30),
            new Systeme("invoice", "Facture", "Invoice", "Faktiora", 40),
            new Systeme("requisition", "Réquisition", "Requisition", "Fangatahana", 50),
            new Systeme("effectifs.feuille", "Feuille de temps (effectifs)", "Timesheet (workforce)", "Taratasin'ny ora (mpiasa)", 60),
            new Systeme("effectifs.pointage", "Pointage (effectifs)", "Time clocking (workforce)", "Fanamarihana ora (mpiasa)", 70));

    /** Le port du catalogue : semé des types système à chaque lecture, isolé par organisme. */
    public interface Depot {
        void semer(String tenantId, List<Systeme> types, String le);

        List<Type> lister(String tenantId, boolean actifsSeulement);

        Optional<Type> lire(String tenantId, String id);

        boolean actif(String tenantId, String code);

        /** Faux si le code existe déjà dans l'organisme (l'index unique tranche). */
        boolean inserer(String tenantId, String id, Nouveau n, String le);

        void modifier(String tenantId, String id, Modification m);

        boolean supprimer(String tenantId, String id);
    }

    /** Valide une création ; le code se déduit du libellé quand il est absent. */
    public static Nouveau nouveau(Map<String, Object> entree) {
        String labelFr = texte(entree.get("labelFr"));
        if (labelFr.length() < LIBELLE_MIN) {
            throw new IllegalArgumentException("labelFr must be at least 2 characters");
        }
        String brut = texte(entree.get("code"));
        String code = (brut.isEmpty() ? slug(labelFr) : brut).toLowerCase(Locale.ROOT);
        if (!CODE_VALIDE.matcher(code).matches()) {
            throw new IllegalArgumentException("code must be lowercase alphanumeric (letter first, dots and underscores allowed, 2-41 chars)");
        }
        String en = texte(entree.get("labelEn"));
        String mg = texte(entree.get("labelMg"));
        int ordre = entree.get("sortOrder") instanceof Number n && Double.isFinite(n.doubleValue()) ? n.intValue() : ORDRE_PAR_DEFAUT;
        return new Nouveau(code, labelFr, en.isEmpty() ? labelFr : en, mg.isEmpty() ? labelFr : mg, ordre);
    }

    /** Une clé présente est appliquée, même nulle — comme {@code patch.x !== undefined} du Node. */
    public static Modification modification(Map<String, Object> patch) {
        String labelFr = patch.containsKey("labelFr") ? String.valueOf(patch.get("labelFr")).trim() : null;
        if (labelFr != null && labelFr.length() < LIBELLE_MIN) {
            throw new IllegalArgumentException("labelFr must be at least 2 characters");
        }
        return new Modification(labelFr,
                patch.containsKey("labelEn") ? String.valueOf(patch.get("labelEn")).trim() : null,
                patch.containsKey("labelMg") ? String.valueOf(patch.get("labelMg")).trim() : null,
                patch.containsKey("sortOrder") ? entier(patch.get("sortOrder")) : null,
                patch.containsKey("active") ? vrai(patch.get("active")) : null);
    }

    /** Un type système ne se supprime pas : le code en émet des demandes ; on le désactive. */
    public static void verifierSuppression(Type t) {
        if (t.systeme()) {
            throw new IllegalArgumentException("a system document type cannot be deleted; deactivate it instead");
        }
    }

    /** Code technique dérivé d'un libellé : accents retirés, non-alphanumérique → « _ ». */
    static String slug(String libelle) {
        String sans = DIACRITIQUES.matcher(Normalizer.normalize(libelle.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)).replaceAll("");
        String base = SOULIGNES_EXTREMES.matcher(HORS_CODE.matcher(sans).replaceAll("_")).replaceAll("");
        base = base.substring(0, Math.min(base.length(), LONGUEUR_SLUG));
        if (!base.isEmpty() && Character.isLetter(base.charAt(0)) && base.charAt(0) <= 'z') {
            return base;
        }
        String prefixe = PREFIXE_SLUG + base;
        return prefixe.substring(0, Math.min(prefixe.length(), LONGUEUR_SLUG + 1));
    }

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    /** {@code Number(v)} ramené à l'entier ; {@code NaN} devient 0 comme l'écrirait SQLite. */
    private static int entier(Object v) {
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return (int) Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** La vérité JavaScript d'une valeur JSON. */
    private static boolean vrai(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return v != null && !String.valueOf(v).isEmpty();
    }
}
