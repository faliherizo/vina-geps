package ca.vina.nexu.alertes.domaine;

import ca.vina.nexu.alertes.domaine.Alertes.Alerte;

import java.util.Arrays;
import java.util.Locale;

/**
 * <strong>L'appel automatique</strong> (ST-G06 ; critères 13, 14) — la part pure. Une étape de cascade de canal
 * « appel » fait sonner la personne ; un message en synthèse française dit l'alerte ; « appuyez sur 1 » vaut prise en
 * charge et arrête la cascade, « 2 » vaut refus. Le résultat de l'appel et sa durée sont tracés.
 */
public final class AppelsAlertes {

    /** Au-delà, la synthèse vocale dure trop : on dit le titre et le début du message. */
    static final int MESSAGE_MAX = 400;
    private static final String UN = "1";
    private static final String DEUX = "2";

    private AppelsAlertes() {
    }

    /** Ce qu'il est advenu d'un appel. */
    public enum Resultat {
        LANCE("lance"),
        /** La personne a décroché et appuyé sur une touche. */
        REPONDU("repondu"),
        /** Décroché, sans touche, ou par un humain qui a raccroché. */
        DECROCHE("decroche"),
        MESSAGERIE("messagerie"),
        OCCUPE("occupe"),
        SANS_REPONSE("sans_reponse"),
        ECHEC("echec");

        private final String code;

        Resultat(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Resultat de(String code) {
            return Arrays.stream(values()).filter(r -> r.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("résultat d'appel inconnu : " + code));
        }

        /**
         * Le statut de fin rapporté par le fournisseur ({@code CallStatus}, et {@code AnsweredBy} quand la détection de
         * répondeur est active). Un statut inconnu est un échec : on ne suppose jamais qu'un appel a abouti.
         */
        public static Resultat duStatut(String callStatus, String answeredBy) {
            return StatutFournisseur.de(callStatus).map(s -> switch (s) {
                case COMPLETED -> ReponduPar.de(answeredBy).estUneMachine() ? MESSAGERIE : DECROCHE;
                case BUSY -> OCCUPE;
                case NO_ANSWER -> SANS_REPONSE;
                case QUEUED, RINGING, IN_PROGRESS -> LANCE;
                case FAILED, CANCELED -> ECHEC;
            }).orElse(ECHEC);
        }

        /** Le résultat après un nouveau rapport : une touche reçue reste « répondu », quoi que dise la fin d'appel. */
        public Resultat apres(Resultat rapporte) {
            return this == REPONDU ? REPONDU : rapporte;
        }
    }

    /** Les statuts d'appel du fournisseur ({@code CallStatus}), dans son vocabulaire. */
    public enum StatutFournisseur {
        QUEUED("queued"), RINGING("ringing"), IN_PROGRESS("in-progress"), COMPLETED("completed"), BUSY("busy"), NO_ANSWER("no-answer"),
        FAILED("failed"), CANCELED("canceled");

        private final String code;

        StatutFournisseur(String code) {
            this.code = code;
        }

        public static java.util.Optional<StatutFournisseur> de(String code) {
            String c = code == null ? "" : code.toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(s -> s.code.equals(c)).findFirst();
        }
    }

    /** Qui a décroché, selon la détection de répondeur ({@code AnsweredBy}) ; absent : on ne sait pas, donc un humain. */
    public enum ReponduPar {
        HUMAN("human", false), MACHINE_START("machine_start", true), MACHINE_END_BEEP("machine_end_beep", true),
        MACHINE_END_SILENCE("machine_end_silence", true), MACHINE_END_OTHER("machine_end_other", true), FAX("fax", true), UNKNOWN("unknown", false);

        private final String code;
        private final boolean machine;

        ReponduPar(String code, boolean machine) {
            this.code = code;
            this.machine = machine;
        }

        public boolean estUneMachine() {
            return machine;
        }

        public static ReponduPar de(String code) {
            String c = code == null ? "" : code.toLowerCase(Locale.ROOT);
            return Arrays.stream(values()).filter(r -> r.code.equals(c)).findFirst().orElse(UNKNOWN);
        }
    }

    /** Ce que veut dire la touche pressée. */
    public enum Touche {
        PRISE_EN_CHARGE, REFUS, AUTRE;

        public static Touche de(String chiffres) {
            String c = chiffres == null ? "" : chiffres.trim();
            if (UN.equals(c)) {
                return PRISE_EN_CHARGE;
            }
            return DEUX.equals(c) ? REFUS : AUTRE;
        }

        public Semantique semantique() {
            return switch (this) {
                case PRISE_EN_CHARGE -> Semantique.PRISE_EN_CHARGE;
                case REFUS -> Semantique.REFUS;
                case AUTRE -> throw new IllegalStateException("une touche inconnue n'a pas de sémantique");
            };
        }
    }

    /** Un appel à passer : l'étape d'une cascade, la personne, le compte par lequel trouver son numéro. */
    public record AAppeler(String alerteId, int etape, String personId, String compteAvis) { }

    /** La trace d'un appel. {@code numero} n'est jamais exposé hors du contexte. */
    public record Appel(String id, String tenantId, String alerteId, int etape, String personId, String fournisseurId,
                        Resultat resultat, String touche, Integer dureeSecondes, String lanceLe, String termineLe) {

        public Appel avecFournisseur(String sid) {
            return new Appel(id, tenantId, alerteId, etape, personId, sid, resultat, touche, dureeSecondes, lanceLe, termineLe);
        }

        public Appel touchee(String chiffres) {
            return new Appel(id, tenantId, alerteId, etape, personId, fournisseurId, Resultat.REPONDU, chiffres, dureeSecondes, lanceLe, termineLe);
        }

        public Appel termine(Resultat rapporte, Integer duree, String instant) {
            return new Appel(id, tenantId, alerteId, etape, personId, fournisseurId, resultat.apres(rapporte), touche, duree, lanceLe, instant);
        }
    }

    /** Le message dit au téléphone : la priorité, le titre, le début du message, puis la consigne. */
    public static String message(Alerte a) {
        String texte = "Alerte " + a.priorite().code() + " de VINA GEPS : " + a.titre() + ". " + a.corps();
        String borne = texte.length() > MESSAGE_MAX ? texte.substring(0, MESSAGE_MAX) : texte;
        return borne + ". Appuyez sur 1 pour prendre en charge, sur 2 pour refuser.";
    }

    /** Ce que la voix répond à la touche. */
    public static String reponseVocale(Touche t, boolean enregistree) {
        if (!enregistree) {
            return "Une réponse est déjà enregistrée pour cette alerte. Au revoir.";
        }
        return switch (t) {
            case PRISE_EN_CHARGE -> "Prise en charge enregistrée. Merci. Au revoir.";
            case REFUS -> "Refus enregistré. Au revoir.";
            case AUTRE -> "Touche non reconnue. Répondez depuis l'application. Au revoir.";
        };
    }
}
