package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
