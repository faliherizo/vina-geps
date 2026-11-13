package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <strong>Le paquet de configuration</strong> — AO 26-015-DO P-TN-1 (« configurer les modules dans
 * les environnements de formation, de tests et de production »).
 *
 * <p>La configuration fonctionnelle (référentiels, règles) se monte et s'éprouve dans
 * l'environnement de tests, puis se <em>promeut</em> : exportée en un document, rejouée dans
 * l'environnement suivant. Rejouée, et non copiée : chaque valeur repasse par les validations, la
 * mise en version datée et le journal, comme une saisie. Le domaine ne fait ici que la part pure :
 * l'en-tête du document, la comparaison, l'ordre et la date de prise d'effet.
 */
public final class PaquetConfiguration {

    public static final String FORMAT = "nexu-effectifs-configuration";
    public static final int VERSION = 1;
    private static final String PARENT = "parent";
    private static final String CODE = "code";
    private static final String LIBELLE_FR = "libelleFr";
    private static final String LIBELLE_EN = "libelleEn";
    private static final String COMPORTEMENT = "comportement";
    private static final String ORDRE = "ordre";
    private static final String ATTRIBUTS = "attributs";
    /** Les champs d'une valeur de référentiel qu'une saisie accepte, et sur lesquels deux valeurs se comparent. */
    private static final List<String> CHAMPS_VALEUR = List.of(CODE, LIBELLE_FR, LIBELLE_EN, COMPORTEMENT, ORDRE, ATTRIBUTS);
    private static final String CONTEXTE = "contexte";
    private static final String CLE = "cle";
    private static final String PORTEE_TYPE = "porteeType";
    private static final String PORTEE_CODE = "porteeCode";
    /** L'identité d'une règle d'un environnement à l'autre. */
    private static final List<String> CHAMPS_IDENTITE_REGLE = List.of(CONTEXTE, CLE, PORTEE_TYPE, PORTEE_CODE);
    private static final List<String> CHAMPS_CONTENU_REGLE = List.of(ContenuRegle.CONDITION, ContenuRegle.ACTION, ContenuRegle.MESSAGE, ContenuRegle.PRIORITE);

    private PaquetConfiguration() {
    }

    /** Ce que l'import fera d'un élément du paquet. */
    public enum Action {
        CREER("creer"), REMPLACER("remplacer"), INCHANGE("inchange");

        private final String code;

        Action(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Une ligne du bilan : l'objet (« referentiel.unites », « regle »), sa clé, et l'action. */
    public record Ligne(String objet, String cle, Action action) {
        public Map<String, Object> enCarte() {
            return Map.of("objet", objet, "cle", cle, "action", action.code());
        }
    }

    /** Refuse un document qui n'est pas un paquet de cette version : un autre JSON ne s'importe pas « à peu près ». */
    public static void verifierEntete(Map<String, Object> paquet) {
        if (paquet == null || !FORMAT.equals(paquet.get("format"))) {
            throw new EffectifsInvalideException("ce document n'est pas un paquet de configuration des effectifs (format attendu : " + FORMAT + ")");
        }
        if (!(paquet.get("version") instanceof Number n) || n.intValue() != VERSION) {
            throw new EffectifsInvalideException("version de paquet non prise en charge (attendue : " + VERSION + ")");
        }
    }

    /** Une valeur de référentiel, sous la forme qu'une saisie accepte. */
    public static Map<String, Object> valeur(ValeurReferentiel v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(CODE, v.code());
        m.put(LIBELLE_FR, v.libelleFr());
        m.put(LIBELLE_EN, v.libelleEn());
        m.put(COMPORTEMENT, v.comportement());
        m.put(ORDRE, v.ordre());
        m.put(ATTRIBUTS, v.attributs());
        return m;
    }

    /** Les unités, parentes d'abord : une unité ne se crée pas avant celle qui la contient. */
    public static List<ValeurReferentiel> parentsDabord(List<ValeurReferentiel> unites) {
        List<ValeurReferentiel> ordre = new ArrayList<>();
        Set<String> placees = new HashSet<>();
        List<ValeurReferentiel> restantes = new ArrayList<>(unites);
        Set<String> codes = new HashSet<>(unites.stream().map(ValeurReferentiel::code).toList());
        while (!restantes.isEmpty()) {
            List<ValeurReferentiel> pretes = restantes.stream().filter(u -> {
                Object p = u.attributs().get(PARENT);
                return !(p instanceof String parent) || placees.contains(parent) || !codes.contains(parent);
            }).toList();
            // Un cycle ne se résout pas : le reste part tel quel, et la validation de la saisie le refusera.
            List<ValeurReferentiel> lot = pretes.isEmpty() ? List.copyOf(restantes) : pretes;
            lot.forEach(u -> placees.add(u.code()));
            ordre.addAll(lot);
            restantes.removeAll(lot);
        }
        return ordre;
    }

    public static Action comparerValeur(Map<String, Object> existante, Map<String, Object> recue) {
        return comparer(existante, recue, CHAMPS_VALEUR);
    }

    public static Action comparerRegle(Map<String, Object> existante, Map<String, Object> recue) {
        return comparer(existante, recue, CHAMPS_CONTENU_REGLE);
    }

    private static Action comparer(Map<String, Object> existante, Map<String, Object> recue, List<String> champs) {
        if (existante == null) {
            return Action.CREER;
        }
        return champs.stream().allMatch(c -> egaux(existante.get(c), recue.get(c))) ? Action.INCHANGE : Action.REMPLACER;
    }

    /** Un nombre lu du JSON peut être un entier ou un réel selon sa provenance : 3 et 3.0 sont la même priorité. */
    private static boolean egaux(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return x.doubleValue() == y.doubleValue();
        }
        return Objects.equals(a, b);
    }

    /** L'identité d'une règle d'un environnement à l'autre : ses identifiants techniques diffèrent, pas ceci. */
    public static String cleRegle(Map<String, Object> regle) {
        return String.join("|", CHAMPS_IDENTITE_REGLE.stream().map(c -> String.valueOf(regle.get(c))).toList());
    }

    /** Le contenu d'une règle (condition, action, message, priorité), extrait d'une vue ou d'une entrée. */
    public static Map<String, Object> contenuRegle(Map<String, Object> source) {
        Map<String, Object> m = new LinkedHashMap<>();
        CHAMPS_CONTENU_REGLE.forEach(c -> m.put(c, source.get(c)));
        return m;
    }

    /** L'identité et le contenu d'une règle : ce que le paquet transporte. */
    public static Map<String, Object> regle(Map<String, Object> source) {
        Map<String, Object> m = new LinkedHashMap<>();
        CHAMPS_IDENTITE_REGLE.forEach(c -> m.put(c, source.get(c)));
        m.putAll(contenuRegle(source));
        return m;
    }

    /**
     * La date à laquelle un remplacement prend effet : celle demandée, sinon le lendemain. Jamais
     * dans le passé, et par défaut jamais sous les pieds d'une journée commencée.
     */
    public static String priseDEffet(String demandee, String aujourdhui) {
        if (demandee == null || demandee.isBlank()) {
            return LocalDate.parse(aujourdhui).plusDays(1).toString();
        }
        String d = Referentiels.dateValide(demandee, "depuis");
        if (d.compareTo(aujourdhui) < 0) {
            throw new EffectifsInvalideException("une configuration promue ne prend pas effet dans le passé (" + d + ")");
        }
        return d;
    }
}
