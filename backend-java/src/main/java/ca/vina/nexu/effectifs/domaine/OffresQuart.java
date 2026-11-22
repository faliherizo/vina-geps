package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.temps.Iso;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <strong>L'offre d'un quart par ancienneté et à tour de rôle</strong> (AD-07 ; convention SP-CSN, art. 15.10, 16.02,
 * 16.03 ; ADR 0089). Le pur : le mode d'offre de l'organisme, ses délais, l'ordre des admissibles et les instants qui
 * en découlent. Aucune E/S, aucune horloge : l'instant est toujours reçu.
 *
 * <p>« Par ancienneté et à tour de rôle, de façon à les répartir équitablement » (16.02), et « chaque fois que la
 * personne salariée refuse, elle est considérée avoir effectué les heures offertes » (16.03) : l'ordre se tient donc
 * par un <strong>compte d'heures</strong> — acceptées, refusées ou laissées sans réponse — sur une fenêtre glissante ;
 * celui qui en compte le moins passe d'abord, l'ancienneté départage. Un pointeur de rotation ne saurait pas qu'un
 * quart de 11,25 h n'en vaut pas un de 7,75 h.
 */
public final class OffresQuart {

    /** Le nom sous lequel le mécanisme offre, fait expirer et journalise — jamais l'employé ni le planificateur. */
    public static final String MECANISME = "offre-tour-de-role";
    /** Le type de tâche de l'ordonnanceur qui fait passer l'offre au rang suivant à l'échéance. */
    public static final String TACHE_ECHEANCE = "effectifs_offre_echeance";
    /** Art. 15.10 et 16.02 : dix minutes pour répondre. */
    public static final int DELAI_CONVENTION_MINUTES = 10;
    /** Art. 16.02 : le délai ne s'applique pas à un quart qui commence dans les deux prochaines heures. */
    public static final int URGENCE_CONVENTION_MINUTES = 120;
    /** Art. 16.04 : la compilation se remet tous les trois mois ; la répartition se juge sur la même durée. */
    public static final int FENETRE_CONVENTION_JOURS = 91;
    static final int MINUTES_JOUR_MAX = 1440;
    static final int JOURS_FENETRE_MAX = 366;
    private static final double MINUTES_HEURE = 60.0;
    private static final DateTimeFormatter HEURE_LOCALE = DateTimeFormatter.ofPattern("uuuu-MM-dd 'à' HH:mm");

    private OffresQuart() {
    }

    /** {@code SIMULTANEE} : tous les admissibles sont avisés à la fois (le comportement d'avant). */
    public enum Mode {
        SIMULTANEE, CASCADE;

        public static Mode de(Object v) {
            return Arrays.stream(values()).filter(m -> m.name().equals(v)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« mode » : " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))));
        }
    }

    /** {@code EPUISEE} : plus personne à qui offrir, la place va à la gestion régulière ; {@code RETIREE} : pourvue autrement. */
    public enum EtatOffre { OUVERTE, POURVUE, EPUISEE, RETIREE }

    /** L'état d'une personne dans l'ordre d'une offre. */
    public enum EtatRang {
        EN_ATTENTE("ce n'est pas encore votre tour"),
        OFFERT(""),
        ACCEPTE("vous avez déjà accepté ce quart"),
        REFUSE("vous avez déjà refusé ce quart"),
        EXPIRE("le délai de réponse est échu : le quart est réputé refusé"),
        ECARTE("les règles d'attribution vous ont écarté de ce quart"),
        CLOS("l'offre de ce quart est close");

        private final String pourquoiPasOffert;

        EtatRang(String pourquoiPasOffert) {
            this.pourquoiPasOffert = pourquoiPasOffert;
        }

        /** Ce qui compte dans la répartition (16.03) : la réponse, le refus et le silence ; pas l'écart ni la clôture. */
        public boolean estCompte() {
            return this == ACCEPTE || this == REFUSE || this == EXPIRE;
        }

        public String pourquoiPasOffert() {
            return pourquoiPasOffert;
        }

        public static List<EtatRang> comptes() {
            return Arrays.stream(values()).filter(EtatRang::estCompte).toList();
        }
    }

    /** Les gestes tracés au journal des effectifs, chacun sous son code. */
    public enum Evenement {
        OUVERTE("offre.ouverte"), PROPOSEE("offre.proposee"), ACCEPTEE("offre.acceptee"), REFUSEE("offre.refusee"), EXPIREE("offre.expiree"),
        ECARTEE("offre.ecartee"), EPUISEE("offre.epuisee"), RETIREE("offre.retiree"), PARAMETRES("offres.parametres");

        private final String code;

        Evenement(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Le réglage de l'organisme ; ses défauts sont ceux de la convention, et le mode celui d'avant. */
    public record Parametres(Mode mode, int delaiReponseMinutes, int seuilUrgenceMinutes, int fenetreRepartitionJours) {

        public static final Parametres PAR_DEFAUT = new Parametres(Mode.SIMULTANEE, DELAI_CONVENTION_MINUTES, URGENCE_CONVENTION_MINUTES,
                FENETRE_CONVENTION_JOURS);

        public boolean estEnCascade() {
            return mode == Mode.CASCADE;
        }

        /** Les champs reçus remplacent ceux d'aujourd'hui ; un champ absent garde sa valeur, un champ hors borne est refusé en le nommant. */
        public Parametres modifiesPar(Map<String, Object> entree) {
            return new Parametres(entree.containsKey("mode") ? Mode.de(entree.get("mode")) : mode,
                    entier(entree, "delaiReponseMinutes", 1, MINUTES_JOUR_MAX, delaiReponseMinutes),
                    entier(entree, "seuilUrgenceMinutes", 0, MINUTES_JOUR_MAX, seuilUrgenceMinutes),
                    entier(entree, "fenetreRepartitionJours", 1, JOURS_FENETRE_MAX, fenetreRepartitionJours));
        }

        private static int entier(Map<String, Object> entree, String champ, int min, int max, int actuel) {
            if (!entree.containsKey(champ)) {
                return actuel;
            }
            if (entree.get(champ) instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= min && n.intValue() <= max) {
                return n.intValue();
            }
            throw new EffectifsInvalideException("« " + champ + " » : un nombre entier de " + min + " à " + max + " est attendu");
        }
    }

    /** Un admissible, et ce qui le place dans l'ordre. */
    public record Candidat(String ressourceId, String nom, double anciennete, double heuresComptees) { }

    /** Le tour de rôle (16.02) : le moins d'heures comptées d'abord, puis la plus grande ancienneté, puis l'identifiant. */
    public static List<Candidat> ordonner(Collection<Candidat> candidats) {
        return candidats.stream().sorted(Comparator.comparingDouble(Candidat::heuresComptees)
                .thenComparing(Comparator.comparingDouble(Candidat::anciennete).reversed()).thenComparing(Candidat::ressourceId)).toList();
    }

    /** Les heures d'un quart, comptées à qui l'accepte, le refuse ou le laisse expirer. */
    public static double heuresDuQuart(int debut, int fin) {
        return Planification.dureeMinutes(debut, fin) / MINUTES_HEURE;
    }

    /** Le quart commence-t-il dans le seuil d'urgence ? Alors le délai de réponse ne s'applique pas (16.02). */
    public static boolean estUrgente(String maintenant, String debutDuQuart, int seuilUrgenceMinutes) {
        return !Instant.parse(maintenant).plus(Duration.ofMinutes(seuilUrgenceMinutes)).isBefore(Instant.parse(debutDuQuart));
    }

    /**
     * L'échéance d'une proposition : le délai de réponse ; pour une offre urgente, le début du quart — ou le délai, si
     * le quart a déjà commencé : une échéance passée ferait expirer l'offre avant qu'on puisse la lire.
     */
    public static String echeance(String maintenant, String debutDuQuart, boolean urgente, int delaiReponseMinutes) {
        String apresDelai = Agph.ajouterMinutes(maintenant, delaiReponseMinutes);
        return urgente && Instant.parse(debutDuQuart).isAfter(Instant.parse(maintenant)) ? Iso.de(Instant.parse(debutDuQuart)) : apresDelai;
    }

    /** L'instant d'une échéance, lu à l'heure de l'établissement : « 2030-03-04 à 06:10 ». */
    public static String heureLocale(String instant) {
        return HEURE_LOCALE.format(Instant.parse(instant).atZone(ZoneId.of(PresenceEffectifs.FUSEAU)));
    }

    /** Le début de la fenêtre de répartition. */
    public static String debutFenetre(String maintenant, int jours) {
        return Iso.de(Instant.parse(maintenant).minus(Duration.ofDays(jours)));
    }
}
