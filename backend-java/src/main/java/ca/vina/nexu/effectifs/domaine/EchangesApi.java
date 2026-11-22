package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <strong>L'API d'échange</strong> (26-015-DO, addenda 5, AD-06) : ce que l'organisme lit lui-même pour alimenter ses
 * systèmes internes (RAO, SAAV, PFT, BD Corpo) — les quarts planifiés et leurs correctifs, puis les relevés de présence
 * validés. Le domaine dit les formes et les bornes ; l'adaptateur lit, le contrôleur traduit.
 *
 * <p>Seuls les quarts d'une période <em>publiée</em> ou <em>close</em> sortent : une période en confection n'est pas un
 * horaire, et la publier est précisément ce qui la rend opposable.
 */
public final class EchangesApi {

    /** Une fenêtre de quarts tient en deux mois : au-delà, on pagine par fenêtres. */
    public static final int JOURS_MAX_FENETRE = 62;
    public static final int LIMITE_DEFAUT = 100;
    public static final int LIMITE_MAX = 500;
    /** Les objets du journal qui sont des quarts : l'équipage, et chacune de ses places. */
    public static final String OBJET_EQUIPAGE = "equipage";
    public static final String OBJET_PLACE = "place";
    public static final Set<String> OBJETS_QUART = Set.of(OBJET_EQUIPAGE, OBJET_PLACE);
    /**
     * Les actions qui changent des places sans les nommer en objet (AD-06c) : l'absence qui les rend vacantes
     * ({@code apres.places}) et l'échange qui permute deux titulaires ({@code apres} : place → nouveau titulaire).
     */
    public static final String ACTION_PLACES_VACANTES = "places.vacantes";
    public static final String ACTION_ECHANGE_APPLIQUE = "echange.applique";
    public static final Set<String> ACTIONS_PLACES = Set.of(ACTION_PLACES_VACANTES, ACTION_ECHANGE_APPLIQUE);
    /** Les périodes dont les quarts sont un horaire opposable. */
    public static final Set<Planification.EtatPeriode> ETATS_EXPOSES = Set.of(Planification.EtatPeriode.PUBLIEE, Planification.EtatPeriode.CLOSE);
    /** Les feuilles dont la présence est validée : approuvées, ou déjà prises dans une paie. */
    public static final Set<TempsEffectifs.StatutFeuille> STATUTS_VALIDES = Set.of(TempsEffectifs.StatutFeuille.APPROUVEE, TempsEffectifs.StatutFeuille.PAYEE);

    private EchangesApi() {
    }

    /** Du … au … inclus, convertis une fois à la frontière ; le refus nomme le paramètre fautif. */
    public record Fenetre(LocalDate du, LocalDate au) {

        public static Fenetre de(String du, String au) {
            LocalDate d = date("du", du);
            LocalDate a = date("au", au);
            if (a.isBefore(d)) {
                throw new EffectifsInvalideException("« au » : ne précède pas « du »");
            }
            if (ChronoUnit.DAYS.between(d, a) >= JOURS_MAX_FENETRE) {
                throw new EffectifsInvalideException("fenêtre : " + JOURS_MAX_FENETRE + " jours au plus ; paginez par fenêtres");
            }
            return new Fenetre(d, a);
        }

        private static LocalDate date(String nom, String saisie) {
            if (saisie == null || saisie.isBlank()) {
                throw new EffectifsInvalideException("« " + nom + " » : date AAAA-MM-JJ exigée");
            }
            try {
                return LocalDate.parse(saisie.strip());
            } catch (DateTimeParseException e) {
                throw new EffectifsInvalideException("« " + nom + " » : « " + saisie + " » n'est pas une date AAAA-MM-JJ");
            }
        }
    }

    /** Le nombre d'éléments d'une page : défaut {@value #LIMITE_DEFAUT}, de 1 à {@value #LIMITE_MAX}. */
    public static int limite(String saisie) {
        if (saisie == null || saisie.isBlank()) {
            return LIMITE_DEFAUT;
        }
        try {
            int n = Integer.parseInt(saisie.strip());
            if (n >= 1 && n <= LIMITE_MAX) {
                return n;
            }
        } catch (NumberFormatException e) {
            // Le refus ci-dessous nomme le paramètre ; l'exception de conversion n'apprend rien de plus.
        }
        throw new EffectifsInvalideException("« limite » : entier de 1 à " + LIMITE_MAX);
    }

    /** Un curseur vide repart du début ; sinon il est rendu tel quel par la page précédente. */
    public static String curseur(String saisie) {
        return saisie == null || saisie.isBlank() ? null : saisie.strip();
    }

    /** Un quart touché par une entrée du journal : l'équipage lui-même, ou l'une de ses places. */
    public record Cible(String objetType, String objetId) { }

    /**
     * Les quarts que touche une entrée du journal. Une absence ou un échange consignent leurs places sous leur propre objet :
     * sans cette lecture, un quart rendu vacant par une absence n'apparaîtrait jamais parmi les correctifs.
     */
    public static List<Cible> cibles(String action, String objetType, String objetId, Object apres) {
        if (OBJETS_QUART.contains(objetType)) {
            return List.of(new Cible(objetType, objetId));
        }
        if (ACTION_PLACES_VACANTES.equals(action) && apres instanceof Map<?, ?> m && m.get("places") instanceof List<?> places) {
            return places.stream().map(p -> new Cible(OBJET_PLACE, String.valueOf(p))).toList();
        }
        if (ACTION_ECHANGE_APPLIQUE.equals(action) && apres instanceof Map<?, ?> m) {
            return m.keySet().stream().map(p -> new Cible(OBJET_PLACE, String.valueOf(p))).toList();
        }
        return List.of();
    }

    /**
     * Une absence déclarée aux opérations (addenda 5 d) : sur un quart réel ({@code quartId}), ou sur des dates
     * ({@code du}, {@code au}) ; l'un ou l'autre, jamais les deux. Les dates elles-mêmes se valident à la déclaration.
     */
    public record AbsenceOperations(String codeAbsence, String quartId, String du, String au, Object motif) {

        public static AbsenceOperations de(Map<String, Object> corps) {
            String code = texte(corps.get("codeAbsence"));
            if (code == null) {
                throw new EffectifsInvalideException("« codeAbsence » : exigé");
            }
            String quart = texte(corps.get("quartId"));
            String du = texte(corps.get("du"));
            if ((quart == null) == (du == null)) {
                throw new EffectifsInvalideException("nommez le quart réel (« quartId ») ou les dates (« du », « au ») : l'un ou l'autre");
            }
            if (quart != null && corps.get("au") != null) {
                throw new EffectifsInvalideException("« au » : sans objet sur un quart, qui tient en une date");
            }
            return new AbsenceOperations(code, quart, du, texte(corps.get("au")), corps.get("motif"));
        }

        private static String texte(Object v) {
            return v instanceof String s && !s.isBlank() ? s.strip() : null;
        }
    }

    /** Une place du quart ; {@code ressourceId} et {@code matricule} sont vides tant qu'elle est vacante. */
    public record Place(String id, int rang, String corpsEmploiCode, String ressourceId, String matricule) { }

    /** Un quart planifié : heures en minutes depuis minuit ; une fin inférieure au début finit le lendemain. */
    public record Quart(String id, String periodeId, String uniteCode, String date, int debutMinutes, int finMinutes, String natureCode,
                        String milieuId, List<Place> places) {

        public Quart {
            places = List.copyOf(places);
        }
    }

    /**
     * Un correctif : l'entrée du journal et l'<em>état présent</em> du quart qu'elle touche. L'entrée d'un quart non
     * exposé (période en confection) n'est pas rendue : il sortira avec la publication, dans {@code quarts}.
     */
    public record Modification(String curseur, String horodatage, String action, String objetType, String objetId, Quart quart) { }

    /** Un segment de temps actif d'une feuille validée. */
    public record Segment(String date, String debut, String fin, String typeHeureCode, String placeId) {
    }

    /** Un relevé de présence : une feuille validée, avec ses segments actifs. */
    public record Releve(String curseur, String feuilleId, String ressourceId, String matricule, String uniteCode, String debut, String fin,
                         String statut, String approuveeLe, List<Segment> segments) {

        public Releve {
            segments = List.copyOf(segments);
        }
    }

    /** Une page et le curseur d'où reprendre ; sur une page vide, le curseur reçu est rendu, pour qu'on rappelle plus tard. */
    public record Page<T>(List<T> elements, String curseur) {

        public Page {
            elements = List.copyOf(elements);
        }
    }
}
