package ca.vina.nexu.rapports.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * <b>Constructeur de rapports</b> (STO DS2026-120-03, FIN191) — jumeau de
 * {@code backend/src/rapports/domaine.ts}.
 *
 * <h2>La conception qu'il ne faut pas faire</h2>
 *
 * <p>Le réflexe est d'exposer les <b>tables</b> : l'usager choisit {@code gl_entry_lines}, coche
 * des colonnes, et l'on assemble le SQL. Cela paraît souple, et c'est une faute de sécurité — un
 * usager d'écran obtiendrait le droit de lire n'importe quelle table, dont celle d'un autre
 * organisme, et l'isolation reposerait sur la discipline de l'assembleur de SQL, c'est-à-dire sur
 * rien.
 *
 * <h2>La conception retenue : des sources déclarées</h2>
 *
 * <p>Un rapport se bâtit sur une <b>source</b> — une vue métier déclarée dans le code, avec ses
 * champs et leur libellé français. Ce qui n'est pas déclaré n'est pas interrogeable ; le filtre de
 * locataire est porté par la source, et non par la requête de l'usager.
 */
public final class CatalogueRapports {

    private CatalogueRapports() {
    }

    public static class RapportInvalideException extends RuntimeException {
        public RapportInvalideException(String message) {
            super(message);
        }
    }

    public enum TypeChamp {
        TEXTE("texte"), NOMBRE("nombre"), MONTANT("montant"), DATE("date"), BOOLEEN("booleen");

        private final String code;

        TypeChamp(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /**
     * {@code taux} rapporte la somme d'un champ à la somme d'un autre, son <b>dénominateur</b>, en pour
     * cent : un taux d'écarts de pointage, un taux de comblement. Il n'est pas une moyenne de taux — la
     * moyenne de deux taux de 50 % et 100 % n'est pas le taux de l'ensemble quand les dénominateurs
     * diffèrent (P-RF-1). Sa fonction SQL est donc la somme, appliquée aux deux termes.
     */
    public enum Agregat {
        SOMME("somme"), MOYENNE("moyenne"), COMPTE("compte"), MINIMUM("minimum"), MAXIMUM("maximum"), TAUX("taux");

        private final String code;

        Agregat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        /** Le nom SQL vit <b>avec</b> le code : séparés, ils finiraient par ne plus se correspondre. */

        public static List<String> codes() {
            return Arrays.stream(values()).map(Agregat::code).toList();
        }

        public static Agregat de(String code) {
            return Arrays.stream(values()).filter(a -> a.code.equals(code)).findFirst()
                    .orElseThrow(() -> new RapportInvalideException(
                            "agrégat inconnu : « " + code + " » (attendu : " + String.join(", ", codes()) + ")"));
        }
    }

    public enum Operateur {
        EGAL("egal"), DIFFERENT("different"), CONTIENT("contient"), SUPERIEUR("superieur"),
        INFERIEUR("inferieur"), ENTRE("entre"), PARMI("parmi");

        private final String code;

        Operateur(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(Operateur::code).toList();
        }

        public static Operateur de(String code) {
            return Arrays.stream(values()).filter(o -> o.code.equals(code)).findFirst()
                    .orElseThrow(() -> new RapportInvalideException(
                            "opérateur inconnu : « " + code + " » (attendu : " + String.join(", ", codes()) + ")"));
        }
    }

    public enum FormatExport {
        CSV("csv"), XLSX("xlsx"), PDF("pdf"), JSON("json");

        private final String code;

        FormatExport(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static List<String> codes() {
            return Arrays.stream(values()).map(FormatExport::code).toList();
        }

        public static FormatExport de(String code) {
            return Arrays.stream(values()).filter(f -> f.code.equals(code)).findFirst()
                    .orElseThrow(() -> new RapportInvalideException(
                            "format d'export inconnu : « " + code + " » (attendu : "
                                    + String.join(", ", codes()) + ")"));
        }
    }

    /** {@code groupable} faux pour un champ qu'on affiche mais qu'on ne regroupe pas. */
    public record Champ(String code, String libelle, TypeChamp type, boolean groupable) {
    }

    /**
     * Une source <b>de faits calculés</b> (les effectifs, P-RF-1) : ses lignes viennent d'un
     * fournisseur, et un filtre « entre » sur son champ de période est obligatoire. {@code capacite}
     * est la capacité qu'exige sa lecture quand elle expose des personnes ; absente, la source ne
     * suit que la garde de son module.
     */
    public record Source(String code, String libelle, String module, List<Champ> champs, Faits faits, String capacite) {
    }

    /** Le champ de date qui borne une source de faits calculés. */
    public record Faits(String periode) {
    }

    /**
     * Le <b>catalogue</b>, seul point d'entrée des rapports.
     *
     * <p>Ajouter une source est une décision de conception — on déclare ce qu'un usager a le droit
     * de lire — et non une conséquence d'une migration.
     */
    // Les relevés statistiques des effectifs (AO 26-015-DO, P-RF-1) : des sources de faits calculés.
    public static final List<Source> SOURCES = SourcesEffectifs.SOURCES;

    public static Source sourceDe(String code) {
        return SOURCES.stream().filter(s -> s.code().equals(code)).findFirst()
                .orElseThrow(() -> new RapportInvalideException("source inconnue : « " + code
                        + " » (déclarées : " + String.join(", ",
                        SOURCES.stream().map(Source::code).toList()) + ")"));
    }

    /**
     * Les sources offertes à un organisme, selon ses modules actifs.
     *
     * <p>Offrir la source d'un module coupé laisserait bâtir un rapport qui ne rendrait jamais
     * rien — et l'usager croirait à des données vides plutôt qu'à un module absent.
     */
    public static List<Source> sourcesDisponibles(List<String> modulesActifs) {
        return SOURCES.stream().filter(s -> modulesActifs.contains(s.module())).toList();
    }

    // ── La définition d'un rapport ───────────────────────────────────────────────────────────

    public record Filtre(String champ, Operateur operateur, List<Object> valeurs) {
    }

    /** {@code agregat} nul pour une colonne affichée telle quelle ; {@code denominateur} pour un taux seulement. */
    public record Colonne(String champ, Agregat agregat, String denominateur) {

        public Colonne(String champ, Agregat agregat) {
            this(champ, agregat, null);
        }
    }

    public record Definition(String source, List<Colonne> colonnes, List<String> groupes,
                             List<Filtre> filtres, String triChamp, boolean triDescendant, Integer limite) {
    }

    public record ColonnePlanifiee(String champ, Agregat agregat, String libelle, TypeChamp type, String denominateur) {

        public ColonnePlanifiee(String champ, Agregat agregat, String libelle, TypeChamp type) {
            this(champ, agregat, libelle, type, null);
        }
    }

    public record Tri(String champ, boolean descendant) {
    }

    public record PlanRapport(Source source, List<ColonnePlanifiee> colonnes, List<String> groupes,
                              List<Filtre> filtres, Tri tri, int limite) {
    }

    /** Au-delà, un rapport d'écran devient un export : la limite protège la mémoire du serveur. */
    public static final int LIMITE_MAXIMALE = 10000;

    /** Une source de faits calculés couvre au plus une année : au-delà, le calcul ne tient plus dans une requête d'écran. */
    public static final int PERIODE_MAXIMALE_JOURS = 366;

    private static final int LIMITE_PAR_DEFAUT = 1000;

    private static final List<TypeChamp> NOMBRES = List.of(TypeChamp.NOMBRE, TypeChamp.MONTANT);

    private static Champ champDe(Source source, String code, String role) {
        return source.champs().stream().filter(c -> c.code().equals(code)).findFirst()
                .orElseThrow(() -> new RapportInvalideException(role + " « " + code
                        + " » n'existe pas dans la source « " + source.code() + " » (champs : "
                        + String.join(", ", source.champs().stream().map(Champ::code).toList()) + ")"));
    }

    /**
     * <b>Valider une définition contre le catalogue</b>, et produire le plan.
     *
     * <p>Tout ce qui n'est pas déclaré est refusé, <b>nommément</b>. Une erreur qui dirait « champ
     * invalide » obligerait l'usager à deviner lequel ; ici chaque refus cite le champ et la source.
     */
    public static PlanRapport planifier(Definition d) {
        Source source = sourceDe(d.source());
        if (d.colonnes() == null || d.colonnes().isEmpty()) {
            throw new RapportInvalideException("un rapport porte au moins une colonne");
        }

        List<ColonnePlanifiee> colonnes = new ArrayList<>();
        for (Colonne c : d.colonnes()) {
            colonnes.add(planifierColonne(source, c));
        }

        List<String> groupes = new ArrayList<>();
        for (String g : d.groupes() == null ? List.<String>of() : d.groupes()) {
            Champ champ = champDe(source, g, "le regroupement");
            if (!champ.groupable()) {
                throw new RapportInvalideException("le champ « " + champ.code() + " » ne se regroupe pas");
            }
            groupes.add(champ.code());
        }

        verifierCoherence(colonnes, groupes);

        List<Filtre> filtres = new ArrayList<>();
        for (Filtre f : d.filtres() == null ? List.<Filtre>of() : d.filtres()) {
            filtres.add(verifierFiltre(source, f));
        }
        verifierPeriode(source, filtres);

        Tri tri = d.triChamp() == null || d.triChamp().isBlank() ? null
                : new Tri(champDe(source, d.triChamp(), "le tri").code(), d.triDescendant());

        int limite = d.limite() == null ? LIMITE_PAR_DEFAUT : d.limite();
        if (limite <= 0 || limite > LIMITE_MAXIMALE) {
            throw new RapportInvalideException("la limite est un entier entre 1 et " + LIMITE_MAXIMALE);
        }
        return new PlanRapport(source, List.copyOf(colonnes), List.copyOf(groupes),
                List.copyOf(filtres), tri, limite);
    }

    private static ColonnePlanifiee planifierColonne(Source source, Colonne c) {
        Champ champ = champDe(source, c.champ(), "la colonne");
        // `compte` s'applique à tout ; les autres exigent un nombre. Additionner des dates
        // produit un résultat que l'on ne peut ni lire ni contester.
        if (c.agregat() != null && c.agregat() != Agregat.COMPTE && !NOMBRES.contains(champ.type())) {
            throw new RapportInvalideException("l'agrégat « " + c.agregat().code()
                    + " » ne s'applique pas au champ « " + champ.code() + " », qui est de type "
                    + champ.type().code());
        }
        return c.agregat() == Agregat.TAUX ? colonneDeTaux(source, c, champ)
                : new ColonnePlanifiee(c.champ(), c.agregat(), champ.libelle(), champ.type());
    }

    /**
     * <b>Le taux</b> : un numérateur et un dénominateur, tous deux numériques, et distincts — un champ
     * rapporté à lui-même vaudrait 100 % partout, ce qui ne renseigne sur rien.
     */
    private static ColonnePlanifiee colonneDeTaux(Source source, Colonne c, Champ champ) {
        if (c.denominateur() == null || c.denominateur().isBlank()) {
            throw new RapportInvalideException("le taux de « " + champ.code()
                    + " » exige un dénominateur : le champ dont la somme divise la sienne");
        }
        Champ denominateur = champDe(source, c.denominateur(), "le dénominateur");
        if (!NOMBRES.contains(denominateur.type())) {
            throw new RapportInvalideException("le dénominateur « " + denominateur.code() + " » est de type "
                    + denominateur.type().code() + " : un taux divise par un nombre");
        }
        if (denominateur.code().equals(champ.code())) {
            throw new RapportInvalideException("le taux de « " + champ.code()
                    + " » sur lui-même vaudrait 100 % partout : choisissez un autre dénominateur");
        }
        return new ColonnePlanifiee(champ.code(), Agregat.TAUX, champ.libelle() + " / " + denominateur.libelle() + " (%)",
                TypeChamp.NOMBRE, denominateur.code());
    }

    private static final java.util.regex.Pattern DATE = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    /**
     * <b>Une source calculée exige sa période</b> : un filtre « entre » sur son champ de date, de deux
     * dates valides, ordonnées, distantes d'au plus {@link #PERIODE_MAXIMALE_JOURS} jours.
     */
    private static void verifierPeriode(Source source, List<Filtre> filtres) {
        String champ = source.faits().periode();
        Filtre f = filtres.stream().filter(x -> x.champ().equals(champ) && x.operateur() == Operateur.ENTRE).findFirst()
                .orElseThrow(() -> new RapportInvalideException("la source « " + source.code() + " » exige sa période : un filtre « entre » sur « "
                        + champ + " » (deux dates AAAA-MM-JJ)"));
        long jours = joursEntre(String.valueOf(f.valeurs().get(0)), String.valueOf(f.valeurs().get(1)), champ);
        if (jours < 0 || jours >= PERIODE_MAXIMALE_JOURS) {
            throw new RapportInvalideException("la période de « " + champ + " » va du plus ancien au plus récent, sur "
                    + PERIODE_MAXIMALE_JOURS + " jours au plus");
        }
    }

    private static long joursEntre(String du, String au, String champ) {
        if (DATE.matcher(du).matches() && DATE.matcher(au).matches()) {
            try {
                return java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(du), java.time.LocalDate.parse(au));
            } catch (java.time.format.DateTimeParseException e) {
                // Une date au bon format mais inexistante (le 30 février) est refusée comme les autres.
            }
        }
        throw new RapportInvalideException("la période de « " + champ + " » attend deux dates AAAA-MM-JJ");
    }

    /** Les bornes de la période d'un plan de source calculée — déjà vérifiées par {@link #planifier}. */
    public static Periode bornesDe(PlanRapport plan) {
        String champ = plan.source().faits().periode();
        return plan.filtres().stream().filter(f -> f.champ().equals(champ) && f.operateur() == Operateur.ENTRE).findFirst()
                .map(f -> new Periode(String.valueOf(f.valeurs().get(0)), String.valueOf(f.valeurs().get(1))))
                .orElseThrow();
    }

    /** Une période de relevé, bornes comprises (AAAA-MM-JJ). */
    public record Periode(String du, String au) {
    }

    /**
     * <b>La règle qui rend un rapport lisible</b> : dès qu'une colonne est agrégée, toute colonne
     * non agrégée doit être un regroupement.
     *
     * <p>Sans elle, le moteur rendrait une valeur arbitraire prise dans le groupe — un montant
     * plausible et faux, le pire des deux.
     */
    private static void verifierCoherence(List<ColonnePlanifiee> colonnes, List<String> groupes) {
        boolean agregee = colonnes.stream().anyMatch(c -> c.agregat() != null);
        if (agregee) {
            List<String> libres = colonnes.stream()
                    .filter(c -> c.agregat() == null && !groupes.contains(c.champ()))
                    .map(c -> "« " + c.champ() + " »").toList();
            if (!libres.isEmpty()) {
                throw new RapportInvalideException("regroupez ou agrégez " + String.join(", ", libres)
                        + " : une colonne libre dans un rapport agrégé rendrait une valeur arbitraire du groupe");
            }
        } else if (!groupes.isEmpty()) {
            throw new RapportInvalideException(
                    "un regroupement sans agrégat ne fait que dédoubler les lignes : ajoutez une colonne agrégée");
        }
    }

    private static Filtre verifierFiltre(Source source, Filtre f) {
        Champ champ = champDe(source, f.champ(), "le filtre");
        List<Object> valeurs = f.valeurs() == null ? List.of() : f.valeurs();
        if (valeurs.isEmpty()) {
            throw new RapportInvalideException("le filtre sur « " + champ.code() + " » n'a aucune valeur");
        }
        if (f.operateur() == Operateur.ENTRE && valeurs.size() != 2) {
            throw new RapportInvalideException("le filtre « entre » sur « " + champ.code()
                    + " » attend exactement deux bornes");
        }
        if (f.operateur() != Operateur.ENTRE && f.operateur() != Operateur.PARMI && valeurs.size() != 1) {
            throw new RapportInvalideException("le filtre « " + f.operateur().code() + " » sur « "
                    + champ.code() + " » attend une seule valeur");
        }
        if (f.operateur() == Operateur.CONTIENT && champ.type() != TypeChamp.TEXTE) {
            throw new RapportInvalideException("« contient » ne s'applique qu'à un champ texte, et « "
                    + champ.code() + " » est de type " + champ.type().code());
        }
        return new Filtre(champ.code(), f.operateur(), List.copyOf(valeurs));
    }
}
