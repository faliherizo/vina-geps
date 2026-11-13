package ca.vina.nexu.communication.domaine;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>L'appel entre collègues</strong> — inviter, faire sonner, décrocher, raccrocher (jumeau de
 * {@code appelDirect.ts}). La session seulement : qui appelle qui, qui a répondu, combien de temps.
 * Le transport de la voix se branche dessus et n'a pas à redécider qui a le droit de quoi.
 */
public final class AppelsDirects {

    /** Après ce délai sans réponse, l'appel est manqué : une sonnerie sans fin n'avertit plus. */
    public static final long DELAI_SONNERIE_SECONDES = 45;
    private static final double MILLIS_PAR_SECONDE = 1000.0;

    private AppelsDirects() {
    }

    /** Les états d'un appel : quatre terminaux, parce qu'un refus, un manqué et une annulation ne disent pas la même chose. */
    public enum Etat {
        SONNE("sonne", "en train de sonner", false), EN_COURS("en_cours", "en cours", false), TERMINE("termine", "terminé", true),
        REFUSE("refuse", "refusé", true), MANQUE("manque", "manqué", true), ANNULE("annule", "annulé", true);

        private final String code;
        private final String libelle;
        private final boolean terminal;

        Etat(String code, String libelle, boolean terminal) {
            this.code = code;
            this.libelle = libelle;
            this.terminal = terminal;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }

        public boolean terminal() {
            return terminal;
        }

        public static Etat de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("état d'appel inconnu : " + code));
        }
    }

    /** Les gestes qu'on pose sur un appel. */
    public enum Geste {
        DECROCHER("decrocher"), REFUSER("refuser"), RACCROCHER("raccrocher"), ANNULER("annuler"), EXPIRER("expirer");

        private final String code;

        Geste(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    /** Un appel : {@code termineParId} est nul quand c'est le délai qui a mis fin. */
    public record Appel(String id, String appelantId, String appeleId, String conversationId, Etat etat,
                        String lanceLe, String decrocheLe, String termineLe, String termineParId) {

        public Appel avec(Etat nouvel, String decroche, String termine, String par) {
            return new Appel(id, appelantId, appeleId, conversationId, nouvel, lanceLe, decroche, termine, par);
        }

        /** L'appel tel que servi à {@code pourQui} : sa durée parlée, et s'il lui est entrant. */
        public Map<String, Object> enCarte(String pourQui) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("appelantId", appelantId);
            m.put("appeleId", appeleId);
            m.put("conversationId", conversationId);
            m.put("etat", etat.code());
            m.put("lanceLe", lanceLe);
            m.put("decrocheLe", decrocheLe);
            m.put("termineLe", termineLe);
            m.put("termineParId", termineParId);
            m.put("dureeSecondes", dureeSecondes(this));
            m.put("entrant", appeleId.equals(pourQui));
            return m;
        }
    }

    /** Secondes parlées, du décrochage à la fin ; un appel jamais décroché dure zéro, pas « inconnu ». */
    public static long dureeSecondes(Appel a) {
        Optional<Instant> debut = instant(a.decrocheLe());
        Optional<Instant> fin = instant(a.termineLe());
        if (debut.isEmpty() || fin.isEmpty() || fin.get().isBefore(debut.get())) {
            return 0;
        }
        return Math.round(Duration.between(debut.get(), fin.get()).toMillis() / MILLIS_PAR_SECONDE);
    }

    /** L'appel a-t-il trop sonné ? L'instant est passé, jamais lu à l'horloge. */
    public static boolean sonnerieExpiree(Appel a, String maintenant) {
        Optional<Instant> debut = instant(a.lanceLe());
        Optional<Instant> now = instant(maintenant);
        if (a.etat() != Etat.SONNE || debut.isEmpty() || now.isEmpty()) {
            return false;
        }
        return Duration.between(debut.get(), now.get()).toMillis() / MILLIS_PAR_SECONDE > DELAI_SONNERIE_SECONDES;
    }

    private static Optional<Instant> instant(String iso) {
        if (iso == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(iso));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /** Ce qu'il faut pour lancer un appel : quelqu'un à appeler, qui ne soit pas soi. */
    public static List<String> validerInvitation(String appelantId, String appeleId) {
        List<String> erreurs = new ArrayList<>();
        String appele = appeleId == null ? "" : appeleId.trim();
        if (appele.isEmpty()) {
            erreurs.add("la personne à appeler est requise");
        } else if (appele.equals(appelantId)) {
            erreurs.add("on ne s'appelle pas soi-même");
        }
        return erreurs;
    }

    /**
     * <strong>Qui peut poser quel geste, et dans quel état</strong> — la seule autorité. Un refus porte
     * son motif : « vous ne pouvez pas » n'aide personne à comprendre qu'il décroche son propre appel.
     */
    public static Etat appliquer(Appel appel, Geste geste, String acteurId) {
        if (appel.etat().terminal()) {
            throw RefusCommunication.invalide("cet appel est déjà " + appel.etat().libelle());
        }
        return switch (geste) {
            case DECROCHER, REFUSER -> repondre(appel, geste, acteurId);
            case ANNULER -> annuler(appel, acteurId);
            case RACCROCHER -> raccrocher(appel, acteurId);
            case EXPIRER -> exiger(appel.etat() == Etat.SONNE, "cet appel a été décroché", Etat.MANQUE);
        };
    }

    private static Etat repondre(Appel appel, Geste geste, String acteurId) {
        // Seul l'appelé répond : l'appelant qui « décrocherait » son propre appel inventerait une réponse.
        exiger(acteurId.equals(appel.appeleId()), "seule la personne appelée peut répondre à cet appel", Etat.SONNE);
        return exiger(appel.etat() == Etat.SONNE, "cet appel a déjà été décroché", geste == Geste.DECROCHER ? Etat.EN_COURS : Etat.REFUSE);
    }

    private static Etat annuler(Appel appel, String acteurId) {
        exiger(acteurId.equals(appel.appelantId()), "seule la personne qui appelle peut annuler avant la réponse", Etat.SONNE);
        return exiger(appel.etat() == Etat.SONNE, "cet appel est déjà décroché : raccrochez", Etat.ANNULE);
    }

    private static Etat raccrocher(Appel appel, String acteurId) {
        boolean appelant = acteurId.equals(appel.appelantId());
        exiger(appelant || acteurId.equals(appel.appeleId()), "cet appel ne vous concerne pas", Etat.SONNE);
        if (appel.etat() == Etat.SONNE) {
            return appelant ? Etat.ANNULE : Etat.REFUSE;
        }
        return Etat.TERMINE;
    }

    private static Etat exiger(boolean condition, String motif, Etat etat) {
        if (!condition) {
            throw RefusCommunication.invalide(motif);
        }
        return etat;
    }
}
