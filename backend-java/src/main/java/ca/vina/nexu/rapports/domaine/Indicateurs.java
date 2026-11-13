package ca.vina.nexu.rapports.domaine;

import ca.vina.nexu.rapports.domaine.CatalogueRapports.Filtre;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Operateur;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.TypeChamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * <b>Les indicateurs définis par l'établissement</b> (BF-046) — jumeau de {@code rapports/indicateurs.ts}.
 *
 * <p>Un indicateur est une mesure nommée sur une source du catalogue : il ne réinvente ni les
 * sources, ni les filtres, ni l'agrégation. Un taux exige son dénominateur, et l'évaluation rend
 * toujours le dénombrement à côté de la valeur — 95 % de 62 et 95 % de 4 n'appellent pas la même
 * décision. Le seuil porte sa direction, et l'état est <b>nommé</b>, jamais une couleur (WCAG 1.4.1).
 */
public final class Indicateurs {

    private Indicateurs() {
    }

    /** Une définition d'indicateur refusée : distincte d'une définition de rapport mal formée, mais un 400 aussi. */
    public static final class IndicateurInvalideException extends CatalogueRapports.RapportInvalideException {
        public IndicateurInvalideException(String message) {
            super(message);
        }
    }

    public enum Format {
        TAUX("taux"), NOMBRE("nombre"), HEURES("heures");

        private final String code;

        Format(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Format::code).toList();
        }

        public static Format de(Object brut) {
            return Arrays.stream(values()).filter(f -> f.code.equals(texte(brut))).findFirst()
                    .orElseThrow(() -> new IndicateurInvalideException("format d'indicateur inconnu « " + texte(brut) + " » — attendu : " + String.join(", ", codes())));
        }
    }

    /** De quel côté du seuil se trouve le <b>bon</b> côté. */
    public enum Direction {
        AU_MOINS("au_moins"), AU_PLUS("au_plus");

        private final String code;

        Direction(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Direction::code).toList();
        }

        public static Direction de(Object brut) {
            return Arrays.stream(values()).filter(d -> d.code.equals(texte(brut))).findFirst()
                    .orElseThrow(() -> new IndicateurInvalideException("sens du seuil inconnu « " + texte(brut) + " » — attendu : " + String.join(", ", codes())));
        }
    }

    public enum Etat {
        ATTEINT("atteint"), VIGILANCE("vigilance"), MANQUE("manque"), INDETERMINE("indetermine");

        private final String code;

        Etat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Etat::code).toList();
        }
    }

    /** Une définition validée, sans identifiant ni état d'activité. */
    public record Definition(String code, String libelle, String source, Format format, String numerateur, String denominateur,
                             List<Filtre> filtres, String axe, Double seuil, Direction direction, double marge) {
    }

    /** {@code decoupage} nul pour la valeur globale ; {@code valeur} nulle quand il n'y a rien à mesurer. */
    public record Valeur(String decoupage, Double valeur, double numerateur, Double denominateur, String etat, int faits) {
    }

    private static final int LONGUEUR_MAX = 80;
    private static final double MARGE_PAR_DEFAUT = 5;
    private static final double CENT = 100;

    private static String texte(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static boolean vide(Object v) {
        return v == null || texte(v).isEmpty();
    }

    /** Les mesures d'une source : ce qu'un indicateur peut compter. */
    public static List<String> mesuresDe(String source) {
        return CatalogueRapports.sourceDe(source).champs().stream().filter(c -> !c.groupable() && c.type() == TypeChamp.NOMBRE)
                .map(CatalogueRapports.Champ::code).toList();
    }

    /** Les axes d'une source : ce par quoi un indicateur peut se découper. */
    public static List<String> axesDe(String source) {
        return CatalogueRapports.sourceDe(source).champs().stream().filter(CatalogueRapports.Champ::groupable)
                .map(CatalogueRapports.Champ::code).toList();
    }

    /** Valide une définition <b>contre sa source</b> : un numérateur mal orthographié vaudrait zéro partout. */
    public static Definition valider(Map<String, Object> e) {
        String code = texte(e.get("code"));
        if (code.isEmpty()) {
            throw new IndicateurInvalideException("le code d'un indicateur est obligatoire");
        }
        if (code.length() > LONGUEUR_MAX) {
            throw new IndicateurInvalideException("le code dépasse " + LONGUEUR_MAX + " caractères");
        }
        String libelle = texte(e.get("libelle")).isEmpty() ? code : texte(e.get("libelle"));
        String source = CatalogueRapports.sourceDe(texte(e.get("source"))).code();
        Format format = Format.de(e.get("format"));
        Direction direction = Direction.de(e.get("direction") == null ? Direction.AU_MOINS.code() : e.get("direction"));
        String numerateur = mesure(source, "le numérateur", texte(e.get("numerateur")));
        String denominateur = denominateur(source, format, numerateur, e.get("denominateur"));
        String axe = axe(source, e.get("axe"));
        Double seuil = seuil(format, e.get("seuil"));
        double marge = marge(e.get("marge"));
        return new Definition(code, libelle, source, format, numerateur, denominateur, filtres(source, e.get("filtres")), axe, seuil, direction, marge);
    }

    private static String mesure(String source, String role, String valeur) {
        List<String> mesures = mesuresDe(source);
        if (!mesures.contains(valeur)) {
            throw new IndicateurInvalideException(role + " « " + valeur + " » n'est pas une mesure de « " + source + " » — attendu : " + String.join(", ", mesures));
        }
        return valeur;
    }

    private static String denominateur(String source, Format format, String numerateur, Object brut) {
        String d = texte(brut);
        if (format == Format.TAUX && d.isEmpty()) {
            throw new IndicateurInvalideException("un taux exige son dénominateur : « 95 % » ne dit rien sans « sur quoi »");
        }
        if (d.isEmpty()) {
            return null;
        }
        mesure(source, "le dénominateur", d);
        if (d.equals(numerateur)) {
            throw new IndicateurInvalideException("« " + numerateur + " » rapporté à lui-même vaudrait 100 % partout");
        }
        return d;
    }

    private static String axe(String source, Object brut) {
        String a = texte(brut);
        if (a.isEmpty()) {
            return null;
        }
        List<String> axes = axesDe(source);
        if (!axes.contains(a)) {
            throw new IndicateurInvalideException("l'axe « " + a + " » n'est pas un découpage de « " + source + " » — attendu : " + String.join(", ", axes));
        }
        return a;
    }

    private static Double nombre(Object brut) {
        if (brut instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(texte(brut));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Double seuil(Format format, Object brut) {
        if (vide(brut)) {
            return null;
        }
        double s = nombre(brut);
        if (!Double.isFinite(s)) {
            throw new IndicateurInvalideException("le seuil doit être un nombre");
        }
        if (format == Format.TAUX && (s < 0 || s > CENT)) {
            throw new IndicateurInvalideException("le seuil d'un taux va de 0 à 100");
        }
        return s;
    }

    private static double marge(Object brut) {
        double m = vide(brut) ? MARGE_PAR_DEFAUT : nombre(brut);
        if (!Double.isFinite(m) || m < 0) {
            throw new IndicateurInvalideException("la marge de vigilance doit être un nombre positif");
        }
        return m;
    }

    /** Un filtre d'indicateur porte sur un champ de la source, avec un opérateur du catalogue. */
    private static List<Filtre> filtres(String source, Object brut) {
        if (brut == null) {
            return List.of();
        }
        if (!(brut instanceof List<?> liste)) {
            throw new IndicateurInvalideException("les filtres doivent être une liste");
        }
        List<String> champs = CatalogueRapports.sourceDe(source).champs().stream().map(CatalogueRapports.Champ::code).toList();
        List<Filtre> filtres = new ArrayList<>();
        for (Object o : liste) {
            filtres.add(filtre(source, champs, o instanceof Map<?, ?> m ? m : Map.of()));
        }
        return List.copyOf(filtres);
    }

    private static Filtre filtre(String source, List<String> champs, Map<?, ?> f) {
        String champ = texte(f.get("champ"));
        if (!champs.contains(champ)) {
            throw new IndicateurInvalideException("le filtre porte sur « " + champ + " », qui n'existe pas dans « " + source + " »");
        }
        String brut = texte(f.get("operateur"));
        Operateur operateur = Arrays.stream(Operateur.values()).filter(o -> o.code().equals(brut)).findFirst()
                .orElseThrow(() -> new IndicateurInvalideException("opérateur de filtre inconnu « " + brut + " » — attendu : " + String.join(", ", Operateur.codes())));
        List<Object> valeurs = f.get("valeurs") instanceof List<?> l ? List.copyOf(l) : List.of();
        if (valeurs.isEmpty()) {
            throw new IndicateurInvalideException("le filtre sur « " + champ + " » n'a aucune valeur");
        }
        if (operateur == Operateur.ENTRE && valeurs.size() != 2) {
            throw new IndicateurInvalideException("le filtre « entre » sur « " + champ + " » exige deux bornes");
        }
        return new Filtre(champ, operateur, valeurs);
    }

    /**
     * <b>Évalue un indicateur sur des faits.</b> Les filtres passent avant le découpage ; un découpage
     * dont aucun fait ne reste n'apparaît pas, plutôt qu'une ligne à zéro qui accuserait un service non concerné.
     */
    public static List<Valeur> evaluer(Definition d, List<Map<String, Object>> faits) {
        List<Map<String, Object>> retenus = faits.stream().filter(f -> d.filtres().stream().allMatch(filtre -> satisfait(d.source(), filtre, f))).toList();
        if (d.axe() == null) {
            return List.of(mesurer(d, retenus, null));
        }
        Map<String, List<Map<String, Object>>> parValeur = new TreeMap<>();
        for (Map<String, Object> f : retenus) {
            Object v = f.get(d.axe());
            parValeur.computeIfAbsent(v == null ? "" : String.valueOf(v), k -> new ArrayList<>()).add(f);
        }
        return parValeur.entrySet().stream().map(e -> mesurer(d, e.getValue(), e.getKey())).toList();
    }

    private static double arrondir(double v) {
        return Math.round(v * CENT) / CENT;
    }

    private static double somme(List<Map<String, Object>> lignes, String champ) {
        return arrondir(lignes.stream().mapToDouble(l -> {
            Double n = nombre(l.get(champ));
            return Double.isFinite(n) ? n : 0;
        }).sum());
    }

    private static Valeur mesurer(Definition d, List<Map<String, Object>> lignes, String decoupage) {
        double numerateur = somme(lignes, d.numerateur());
        Double denominateur = d.denominateur() == null ? null : somme(lignes, d.denominateur());
        // Un taux sur un dénominateur nul est indéterminé, non pas zéro.
        Double valeur = d.format() != Format.TAUX ? Double.valueOf(numerateur)
                : denominateur == null || denominateur == 0 ? null : arrondir(CENT * numerateur / denominateur);
        return new Valeur(decoupage, valeur, numerateur, denominateur, etatDe(d, valeur).code(), lignes.size());
    }

    /** Sans seuil ou sans valeur, l'indicateur est une mesure et non une alerte : {@code indetermine}. */
    public static Etat etatDe(Definition d, Double valeur) {
        if (d.seuil() == null || valeur == null) {
            return Etat.INDETERMINE;
        }
        boolean bon = d.direction() == Direction.AU_MOINS ? valeur >= d.seuil() : valeur <= d.seuil();
        if (bon) {
            return Etat.ATTEINT;
        }
        return Math.abs(valeur - d.seuil()) <= d.marge() ? Etat.VIGILANCE : Etat.MANQUE;
    }

    /** Le prédicat de filtre <b>de l'agrégation des rapports</b>, et non une seconde copie. */
    private static boolean satisfait(String source, Filtre filtre, Map<String, Object> fait) {
        TypeChamp type = CatalogueRapports.sourceDe(source).champs().stream().filter(c -> c.code().equals(filtre.champ()))
                .map(CatalogueRapports.Champ::type).findFirst().orElse(TypeChamp.TEXTE);
        return Agregation.retenu(filtre, fait.get(filtre.champ()), type == TypeChamp.NOMBRE || type == TypeChamp.MONTANT);
    }
}
