package ca.vina.nexu.communication.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * <strong>L'appel de groupe</strong> — lancé depuis un fil, il invite les participants actifs ; chacun rejoint, ignore,
 * part et revient (REFONTE-MESSAGERIE.md § 2). Comme l'appel à deux ({@link AppelsDirects}), le domaine tient la
 * <em>session</em> : qui est invité, qui est présent, depuis quand. Le transport de la voix s'y branche.
 */
public final class AppelsGroupe {

    /** Le même délai que l'appel à deux : au-delà, un appel que personne n'a rejoint est manqué. */
    public static final long DELAI_SONNERIE_SECONDES = AppelsDirects.DELAI_SONNERIE_SECONDES;

    private AppelsGroupe() {
    }

    /** L'état de l'appel : il sonne tant que personne n'a rejoint l'appelant. */
    public enum EtatAppel {
        SONNE("sonne", "en train de sonner", false), EN_COURS("en_cours", "en cours", false),
        TERMINE("termine", "terminé", true), MANQUE("manque", "manqué", true);

        private final String code;
        private final String libelle;
        private final boolean terminal;

        EtatAppel(String code, String libelle, boolean terminal) {
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

        public static EtatAppel de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("état d'appel de groupe inconnu : " + code));
        }
    }

    /** Où en est chaque invité. */
    public enum EtatInvite {
        INVITE("invite"), PRESENT("present"), PARTI("parti"), IGNORE("ignore");

        private final String code;

        EtatInvite(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static EtatInvite de(String code) {
            return Arrays.stream(values()).filter(e -> e.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("état d'invité inconnu : " + code));
        }
    }

    /** Les gestes d'un invité. */
    public enum Geste {
        REJOINDRE("rejoindre"), QUITTER("quitter"), IGNORER("ignorer");

        private final String code;

        Geste(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public record Invite(String personId, EtatInvite etat, String rejointLe, String partiLe) {
        Invite vers(EtatInvite nouvel, String rejoint, String parti) {
            return new Invite(personId, nouvel, rejoint, parti);
        }

        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("personId", personId);
            m.put("etat", etat.code());
            m.put("rejointLe", rejointLe);
            m.put("partiLe", partiLe);
            return m;
        }
    }

    /** {@code debuteLe} : quand une deuxième personne est entrée — c'est là qu'on commence à se parler. */
    public record Appel(String id, String conversationId, String lanceParId, EtatAppel etat, String lanceLe, String debuteLe, String termineLe,
                        List<Invite> invites) {

        public Optional<Invite> invite(String personId) {
            return invites.stream().filter(i -> i.personId().equals(personId)).findFirst();
        }

        public long presents() {
            return invites.stream().filter(i -> i.etat() == EtatInvite.PRESENT).count();
        }

        Appel avec(EtatAppel nouvel, String debute, String termine, List<Invite> liste) {
            return new Appel(id, conversationId, lanceParId, nouvel, lanceLe, debute, termine, List.copyOf(liste));
        }

        /** L'appel servi à {@code pourQui} : mon état d'invité, et s'il m'est entrant (je n'y suis pas encore). */
        public Map<String, Object> enCarte(String pourQui) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("conversationId", conversationId);
            m.put("lanceParId", lanceParId);
            m.put("etat", etat.code());
            m.put("lanceLe", lanceLe);
            m.put("debuteLe", debuteLe);
            m.put("termineLe", termineLe);
            m.put("presents", presents());
            m.put("dureeSecondes", dureeSecondes(this));
            m.put("monEtat", invite(pourQui).map(i -> i.etat().code()).orElse(null));
            m.put("entrant", invite(pourQui).map(i -> i.etat() == EtatInvite.INVITE).orElse(false) && !etat.terminal());
            m.put("invites", invites.stream().map(Invite::enCarte).toList());
            return m;
        }
    }

    /** Lancer : l'appelant entre d'office, les autres participants sont invités. Seul, on n'appelle personne. */
    public static Appel lancer(String id, String conversationId, String appelantId, List<String> participants, String maintenant) {
        List<Invite> invites = new ArrayList<>();
        invites.add(new Invite(appelantId, EtatInvite.PRESENT, maintenant, null));
        participants.stream().filter(p -> !p.equals(appelantId)).distinct().forEach(p -> invites.add(new Invite(p, EtatInvite.INVITE, null, null)));
        if (invites.size() < 2) {
            throw RefusCommunication.invalide("un appel de groupe demande au moins un autre participant actif");
        }
        return new Appel(id, conversationId, appelantId, EtatAppel.SONNE, maintenant, null, null, invites);
    }

    /**
     * <strong>Qui peut poser quel geste, et dans quel état</strong> — la seule autorité. Une personne qui n'est pas
     * invitée ne voit pas l'appel : c'est au gestionnaire d'en faire un « introuvable ».
     */
    public static Appel appliquer(Appel appel, Geste geste, String acteurId, String maintenant) {
        if (appel.etat().terminal()) {
            throw RefusCommunication.conflit("cet appel est déjà " + appel.etat().libelle());
        }
        Invite moi = appel.invite(acteurId).orElseThrow(() -> RefusCommunication.introuvable("appel introuvable"));
        return switch (geste) {
            case REJOINDRE -> rejoindre(appel, moi, maintenant);
            case QUITTER -> quitter(appel, moi, maintenant);
            case IGNORER -> ignorer(appel, moi);
        };
    }

    private static Appel rejoindre(Appel appel, Invite moi, String maintenant) {
        if (moi.etat() == EtatInvite.PRESENT) {
            throw RefusCommunication.conflit("vous êtes déjà dans l'appel");
        }
        List<Invite> liste = remplacer(appel, moi.vers(EtatInvite.PRESENT, maintenant, null));
        String debute = appel.debuteLe() != null ? appel.debuteLe() : maintenant;
        return appel.avec(EtatAppel.EN_COURS, debute, null, liste);
    }

    private static Appel quitter(Appel appel, Invite moi, String maintenant) {
        if (moi.etat() != EtatInvite.PRESENT) {
            throw RefusCommunication.conflit("vous n'êtes pas dans l'appel");
        }
        List<Invite> liste = remplacer(appel, moi.vers(EtatInvite.PARTI, moi.rejointLe(), maintenant));
        boolean personne = liste.stream().noneMatch(i -> i.etat() == EtatInvite.PRESENT);
        if (!personne) {
            return appel.avec(appel.etat(), appel.debuteLe(), null, liste);
        }
        // Le dernier présent part : l'appel finit — manqué si personne ne l'avait jamais rejoint.
        return appel.avec(appel.debuteLe() == null ? EtatAppel.MANQUE : EtatAppel.TERMINE, appel.debuteLe(), maintenant, liste);
    }

    private static Appel ignorer(Appel appel, Invite moi) {
        if (moi.etat() != EtatInvite.INVITE) {
            throw RefusCommunication.conflit("seul un invité qui n'a pas encore répondu peut ignorer l'appel");
        }
        return appel.avec(appel.etat(), appel.debuteLe(), appel.termineLe(), remplacer(appel, moi.vers(EtatInvite.IGNORE, null, null)));
    }

    private static List<Invite> remplacer(Appel appel, Invite nouveau) {
        return appel.invites().stream().map(i -> i.personId().equals(nouveau.personId()) ? nouveau : i).toList();
    }

    /** Un appel qui sonne depuis trop longtemps sans que personne ne l'ait rejoint est manqué ; l'instant est passé, jamais lu. */
    public static Appel expirerSi(Appel appel, String maintenant) {
        if (appel.etat() != EtatAppel.SONNE) {
            return appel;
        }
        long ecoule = Duration.between(Instant.parse(appel.lanceLe()), Instant.parse(maintenant)).getSeconds();
        if (ecoule <= DELAI_SONNERIE_SECONDES) {
            return appel;
        }
        List<Invite> liste = appel.invites().stream()
                .map(i -> i.etat() == EtatInvite.PRESENT ? i.vers(EtatInvite.PARTI, i.rejointLe(), maintenant) : i).toList();
        return appel.avec(EtatAppel.MANQUE, null, maintenant, liste);
    }

    /** Secondes parlées : de l'arrivée de la deuxième personne à la fin ; un appel jamais commencé dure zéro. */
    public static long dureeSecondes(Appel a) {
        if (a.debuteLe() == null || a.termineLe() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(Instant.parse(a.debuteLe()), Instant.parse(a.termineLe())).getSeconds());
    }
}
