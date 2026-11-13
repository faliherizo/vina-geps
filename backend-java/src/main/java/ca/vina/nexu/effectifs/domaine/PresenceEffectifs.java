package ca.vina.nexu.effectifs.domaine;

import ca.vina.nexu.partage.temps.Iso;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>Présence : pointage et preuves</strong> — AO 26-015-DO, lot R8 (E-GP-1, E-GP-2, E-GP-4, E-GP-7, E-GP-9 à
 * E-GP-15, E-RF-13, P-GP-1 à P-GP-4) ; le pur de {@code backend/src/effectifs/presence/domaine.ts}.
 *
 * <p>Un pointage (entrée, pause, fin de pause, sortie) se rattache au quart de la personne et se <strong>prouve</strong>
 * au milieu du quart par les mécanismes que le milieu exige : distance au milieu (géorepérage), code QR de lieu récent,
 * réseau. La position est lue au moment du pointage, jamais en continu ; seule la <strong>distance</strong> décide
 * (Loi 25). Les segments de temps se <strong>calculent</strong> des pointages : rien de ce qui se déduit n'est stocké.
 * Aucune E/S, aucune horloge : le calcul du code QR est une empreinte.
 */
public final class PresenceEffectifs {

    /** La fenêtre du code QR : cinq minutes. Le code affiché au milieu change à chaque fenêtre. */
    public static final long FENETRE_QR_MS = 5 * 60_000L;
    /** La précision annoncée du téléphone s'ajoute au rayon, plafonnée : un point flou ne passe pas une rue entière. */
    public static final int TOLERANCE_MAX_METRES = 100;
    /** Tolérance d'une corroboration : un horodatage externe du même sens, à cinq minutes près. */
    public static final long TOLERANCE_CORROBORATION_MS = 5 * 60_000L;
    /** Le fuseau des horaires planifiés : les quarts se disent en heure du Québec. */
    public static final String FUSEAU = "America/Toronto";
    /** Le moteur exige chaque fait qu'une règle lit : sans position, la distance vaut -1, qu'aucune règle de distance ne franchit. */
    public static final long DISTANCE_INCONNUE = -1;
    /** Le mécanisme déclaré aux règles quand aucune preuve n'est satisfaite. */
    public static final String MECANISME_AUCUN = "AUCUN";
    public static final int SEGMENTS_MAX = 20;
    public static final int JUSTIFICATION_MIN = 5;
    public static final int TEXTE_MAX = 500;

    private static final long MINUTE_MS = 60_000L;
    private static final long JOURNEE_MS = 24 * 3_600_000L;
    private static final int LONGUEUR_EMPREINTE = 16;
    private static final String HMAC = "HmacSHA256";
    private static final Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");
    private static final String PLAGES = "plages";
    private static final double LATITUDE_MAX = 90;
    private static final double LONGITUDE_MAX = 180;
    private static final Set<TypeSegment> SAISISSABLES = EnumSet.of(TypeSegment.TRAVAIL, TypeSegment.PAUSE);

    private PresenceEffectifs() {
    }

    // ── Énumérations ─────────────────────────────────────────────────────────

    /** Le sens d'un pointage, avec son libellé dans les messages. */
    public enum Sens {
        ENTREE("l'entrée"), DEBUT_PAUSE("le début de pause"), FIN_PAUSE("la fin de pause"), SORTIE("la sortie");

        private final String libelle;

        Sens(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        /** Ce qui peut suivre : on entre, on s'absente en pause et on en revient, on sort. */
        List<Sens> suivants() {
            return switch (this) {
                case ENTREE -> List.of(DEBUT_PAUSE, SORTIE);
                case DEBUT_PAUSE -> List.of(FIN_PAUSE);
                case FIN_PAUSE -> List.of(DEBUT_PAUSE, SORTIE);
                case SORTIE -> List.of();
            };
        }
    }

    public enum Resultat { CONFORME, ECART, NON_VERIFIE }

    public enum Ecart { HORS_MILIEU, MAUVAIS_MILIEU, PREUVE_ABSENTE }

    public enum VerdictQr { VALIDE, AUTRE_MILIEU, INVALIDE }

    public enum TypeSegment { PRESENCE_PREALABLE, TRAVAIL, PAUSE }

    /** Le statut d'un pointage ; un pointage rejeté ne compte plus dans la séquence du quart. */
    public enum StatutPointage {
        ENREGISTRE, A_APPROUVER, APPROUVE, REJETE;

        public boolean estActif() {
            return this != REJETE;
        }
    }

    /** D'où vient un pointage : de l'appareil au moment du geste, ou d'une saisie a posteriori. */
    public enum OriginePointage {
        APPAREIL("presence.pointage"), SAISIE("presence.saisie");

        private final String actionJournal;

        OriginePointage(String actionJournal) {
            this.actionJournal = actionJournal;
        }

        public String actionJournal() {
            return actionJournal;
        }
    }

    /** La décision de l'encadrement sur un pointage en attente. */
    public enum DecisionPointage {
        APPROUVER(StatutPointage.APPROUVE), REJETER(StatutPointage.REJETE);

        private final StatutPointage vers;

        DecisionPointage(StatutPointage vers) {
            this.vers = vers;
        }

        public StatutPointage vers() {
            return vers;
        }

        public String actionJournal() {
            return "presence." + name().toLowerCase(Locale.ROOT);
        }
    }

    // ── Valeurs ──────────────────────────────────────────────────────────────

    /** Ce que le pointage apporte ; chaque champ peut manquer (nul). */
    public record Soumission(Double latitude, Double longitude, Double precisionMetres, String codeQr, String ip) { }

    /** Le verdict d'un mécanisme de preuve ; {@code distanceMetres} et {@code detail} nuls quand sans objet. */
    public record VerdictPreuve(Milieux.Mecanisme mecanisme, boolean exigee, boolean fournie, boolean satisfaite, Long distanceMetres, VerdictQr detail) { }

    /** Le milieu tel que la preuve le lit : ses coordonnées (nulles = aucune), ses preuves, le secret de son code QR. */
    public record MilieuPreuves(String id, Milieux.Coordonnees coordonnees, List<Milieux.Preuve> preuves, String secretQr) { }

    /** Le résultat d'un pointage et, le cas échéant, l'écart qu'il nomme. */
    public record Jugement(Resultat resultat, Ecart ecart) { }

    /** Un segment de temps déduit ; {@code fin} nulle tant qu'il est ouvert. */
    /**
     * Un temps du quart. {@code milieuId} est le site où il a été fait (E-GP-7) : une personne passée de la
     * caserne 12 à la caserne 7 a travaillé dans les deux, et un seul site « du quart » rendrait la répartition
     * impossible. {@code activiteCode} est ce qu'elle faisait (E-GP-15) ; tous deux sont nuls quand le pointage
     * ne l'a pas dit.
     */
    public record Segment(String debut, String fin, TypeSegment type, long minutes, String milieuId, String activiteCode) {

        /** Le même temps, clos ou recompté — sans perdre son site ni son activité. */
        Segment avec(String nouvelleFin, long nouvellesMinutes) {
            return new Segment(debut, nouvelleFin, type, nouvellesMinutes, milieuId, activiteCode);
        }
    }

    /**
     * Un pointage réduit à ce qu'il faut pour calculer : son sens, son instant, et — quand il les porte — le site où
     * il a été fait (E-GP-7), la nature de la pause qu'il ouvre (E-GP-2), l'activité du travail qu'il ouvre (E-GP-15).
     */
    public record PointageCourt(Sens sens, String horodatage, String milieuId, NaturePause nature, String activiteCode) {

        /** Un pointage sans site, ni nature, ni activité : ce qu'une source tierce ou un calcul ancien connaît. */
        public PointageCourt(Sens sens, String horodatage) {
            this(sens, horodatage, null, null, null);
        }
    }

    /**
     * Un segment déclaré après coup. {@code nature} ne qualifie qu'une pause, {@code activiteCode} que du travail :
     * l'inverse serait un contresens, et la lecture le refuse plutôt que de l'ignorer en silence.
     */
    public record SegmentSaisi(String debut, String fin, TypeSegment type, NaturePause nature, String activiteCode) {

        /** Un segment sans nature ni activité : la forme qu'un calcul de feuille manipule. */
        public SegmentSaisi(String debut, String fin, TypeSegment type) {
            this(debut, fin, type, null, null);
        }
    }

    /** Une place d'un quart publié, telle que la base la rend (heures en minutes depuis minuit). */
    public record QuartPublie(String placeId, String milieuId, String milieu, String uniteCode, String natureCode, String date, int debut, int fin) { }

    /** Le quart pointé : la place, ses heures prévues en instants UTC, et la présence préalable rémunérée. */
    public record QuartPointe(String placeId, String milieuId, String milieu, String uniteCode, String natureCode, String date, int debut, int fin,
                              String debutPrevu, String finPrevue, int presencePrealableMinutes) { }

    // ── Séquence ─────────────────────────────────────────────────────────────

    public static Sens sensDe(Object v) {
        return Arrays.stream(Sens.values()).filter(s -> s.name().equals(v)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("« sens » : " + Arrays.stream(Sens.values()).map(Enum::name).collect(Collectors.joining(", "))));
    }

    /** Les pointages permis après le dernier (nul : aucun) : ce que l'écran propose. */
    public static List<Sens> sensPermis(Sens dernier) {
        return dernier == null ? List.of(Sens.ENTREE) : dernier.suivants();
    }

    public static void verifierSequence(Sens dernier, Sens suivant) {
        List<Sens> permis = sensPermis(dernier);
        if (permis.contains(suivant)) {
            return;
        }
        String quoi = permis.isEmpty() ? "rien : le quart est terminé" : permis.stream().map(Sens::libelle).collect(Collectors.joining(" ou "));
        throw new EffectifsInvalideException("après " + (dernier == null ? "aucun pointage" : dernier.libelle()) + ", on ne peut pointer que " + quoi);
    }

    // ── Code QR de lieu ──────────────────────────────────────────────────────

    private static long fenetreDe(long instantMs) {
        return Math.floorDiv(instantMs, FENETRE_QR_MS);
    }

    /** L'instant où la fenêtre courante se termine : le code affiché cesse d'être le plus récent. */
    public static String finDeFenetre(long instantMs) {
        return Iso.de(Instant.ofEpochMilli((fenetreDe(instantMs) + 1) * FENETRE_QR_MS));
    }

    /** Le code d'un milieu pour une fenêtre : {@code milieu.fenêtre.empreinte}. */
    public static String codeQr(String secret, String milieuId, long instantMs) {
        long f = fenetreDe(instantMs);
        String empreinte = Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(secret, milieuId + "." + f));
        return milieuId + "." + f + "." + empreinte.substring(0, LONGUEUR_EMPREINTE);
    }

    /** Un calcul dont l'exception vérifiée est déclarée par un paramètre de type. */
    private interface Calcul<T, E extends Exception> {
        T calculer() throws E;
    }

    /**
     * HMAC-SHA256. Toute JVM doit fournir cet algorithme (spécification JCA) et une clé HMAC accepte tout octet :
     * les exceptions vérifiées de {@link Mac} ne peuvent pas survenir, on les tait sans bloc mort à couvrir.
     */
    @SuppressWarnings("unchecked")
    private static byte[] hmac(String secret, String message) {
        Calcul<byte[], Exception> calcul = () -> {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        };
        return ((Calcul<byte[], RuntimeException>) (Calcul<?, ?>) calcul).calculer();
    }

    /** Le code lu est-il celui de ce milieu, de la fenêtre courante ou de la précédente ? */
    public static VerdictQr qrValide(String secret, String milieuId, String code, long instantMs) {
        String mid = code.split("\\.", -1)[0];
        if (!mid.isEmpty() && !mid.equals(milieuId)) {
            return VerdictQr.AUTRE_MILIEU;
        }
        boolean valide = codeQr(secret, milieuId, instantMs).equals(code) || codeQr(secret, milieuId, instantMs - FENETRE_QR_MS).equals(code);
        return valide ? VerdictQr.VALIDE : VerdictQr.INVALIDE;
    }

    // ── Preuves ──────────────────────────────────────────────────────────────

    /** Un nombre comme {@code Number(v)} du Node ; absent (nul) pour {@code undefined}, {@code null} et {@code ""}. */
    static Double nombre(Object v) {
        if (v == null || "".equals(v)) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1.0 : 0.0;
        }
        if (!(v instanceof String s)) {
            return Double.NaN;
        }
        try {
            return s.isBlank() ? 0.0 : Double.parseDouble(s.strip());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static boolean horsBornes(double v, double max) {
        return !Double.isFinite(v) || Math.abs(v) > max;
    }

    public static Soumission soumissionDe(Map<String, Object> brut, String ip) {
        Double latitude = nombre(brut.get("latitude"));
        Double longitude = nombre(brut.get("longitude"));
        if ((latitude == null) != (longitude == null)) {
            throw new EffectifsInvalideException("latitude et longitude se transmettent ensemble");
        }
        if (latitude != null && (horsBornes(latitude, LATITUDE_MAX) || horsBornes(longitude, LONGITUDE_MAX))) {
            throw new EffectifsInvalideException("position invalide");
        }
        String code = brut.get("codeQr") instanceof String s && !s.isBlank() ? s.strip() : null;
        return new Soumission(latitude, longitude, nombre(brut.get("precisionMetres")), code, ip);
    }

    /** Juge chaque mécanisme du milieu contre ce que le pointage apporte. */
    public static List<VerdictPreuve> jugerPreuves(MilieuPreuves m, Soumission s, long instantMs) {
        return m.preuves().stream().map(p -> switch (p.mecanisme()) {
            case GEOREPERAGE -> georeperage(p, m, s);
            case CODE_QR_DE_LIEU -> codeQrDeLieu(p, m, s, instantMs);
            case RESEAU -> reseau(p, s);
            // Borne et source externe : la preuve arrive par un autre canal (import d'horodatages, R8/R10).
            case BORNE, SOURCE_EXTERNE -> absente(p, false);
        }).toList();
    }

    private static VerdictPreuve absente(Milieux.Preuve p, boolean fournie) {
        return new VerdictPreuve(p.mecanisme(), p.exige(), fournie, false, null, null);
    }

    private static VerdictPreuve georeperage(Milieux.Preuve p, MilieuPreuves m, Soumission s) {
        Milieux.Coordonnees c = m.coordonnees();
        if (s.latitude() == null || c == null) {
            return absente(p, false);
        }
        long d = Milieux.distanceMetres(s.latitude(), s.longitude(), c.latitude(), c.longitude());
        double tolerance = Math.min(Math.max(s.precisionMetres() == null ? 0 : s.precisionMetres(), 0), TOLERANCE_MAX_METRES);
        return new VerdictPreuve(p.mecanisme(), p.exige(), true, d - tolerance <= c.rayonMetres(), d, null);
    }

    private static VerdictPreuve codeQrDeLieu(Milieux.Preuve p, MilieuPreuves m, Soumission s, long instantMs) {
        if (s.codeQr() == null || m.secretQr() == null) {
            return absente(p, s.codeQr() != null);
        }
        VerdictQr v = qrValide(m.secretQr(), m.id(), s.codeQr(), instantMs);
        return new VerdictPreuve(p.mecanisme(), p.exige(), true, v == VerdictQr.VALIDE, null, v);
    }

    private static VerdictPreuve reseau(Milieux.Preuve p, Soumission s) {
        if (s.ip() == null || !IPV4.matcher(s.ip()).matches()) {
            return absente(p, false);
        }
        List<String> plages = p.parametres().get(PLAGES) instanceof Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
        return new VerdictPreuve(p.mecanisme(), p.exige(), true, plages.stream().anyMatch(cidr -> Milieux.dansPlage(s.ip(), cidr)), null, null);
    }

    /**
     * Le résultat d'un pointage : conforme quand toutes les preuves exigées sont satisfaites (et, si aucune n'est
     * exigée, qu'au moins une fournie l'est) ; sinon un écart nommé. Un code QR d'un autre milieu est un « mauvais
     * milieu », distinct d'une preuve absente.
     */
    public static Jugement resultatDe(List<VerdictPreuve> v) {
        if (v.stream().anyMatch(x -> x.detail() == VerdictQr.AUTRE_MILIEU)) {
            return new Jugement(Resultat.ECART, Ecart.MAUVAIS_MILIEU);
        }
        List<VerdictPreuve> exigees = v.stream().filter(VerdictPreuve::exigee).toList();
        List<VerdictPreuve> fournies = v.stream().filter(VerdictPreuve::fournie).toList();
        if (exigees.stream().anyMatch(x -> !x.fournie())) {
            return new Jugement(Resultat.ECART, Ecart.PREUVE_ABSENTE);
        }
        if (exigees.stream().anyMatch(x -> !x.satisfaite())) {
            return new Jugement(Resultat.ECART, Ecart.HORS_MILIEU);
        }
        if (!exigees.isEmpty()) {
            return new Jugement(Resultat.CONFORME, null);
        }
        if (fournies.isEmpty()) {
            return new Jugement(Resultat.NON_VERIFIE, null);
        }
        return fournies.stream().anyMatch(VerdictPreuve::satisfaite) ? new Jugement(Resultat.CONFORME, null) : new Jugement(Resultat.ECART, Ecart.HORS_MILIEU);
    }

    /** Les faits que le contexte POINTAGE expose aux règles, dans l'ordre du catalogue. */
    public static Map<String, Object> faitsDuPointage(QuartPointe q, Sens sens, String instant, List<VerdictPreuve> verdicts) {
        boolean bornee = sens == Sens.ENTREE || sens == Sens.SORTIE;
        String prevu = sens == Sens.SORTIE ? q.finPrevue() : q.debutPrevu();
        Map<String, Object> faits = new LinkedHashMap<>();
        faits.put("ecartDebutMinutes", bornee ? ecartMinutes(instant, prevu) : 0L);
        faits.put("distanceMetres", distanceDe(verdicts).orElse(DISTANCE_INCONNUE));
        faits.put("mecanisme", verdicts.stream().filter(VerdictPreuve::satisfaite).findFirst().map(x -> x.mecanisme().name()).orElse(MECANISME_AUCUN));
        faits.put("preuveExigeePresente", verdicts.stream().filter(VerdictPreuve::exigee).allMatch(VerdictPreuve::fournie));
        return faits;
    }

    /** La seule mesure de lieu conservée : la distance au milieu, quand une position a été fournie. */
    public static Optional<Long> distanceDe(List<VerdictPreuve> verdicts) {
        return verdicts.stream().map(VerdictPreuve::distanceMetres).filter(d -> d != null).findFirst();
    }

    // ── Segments de temps ────────────────────────────────────────────────────

    static long ms(String iso) {
        return Planification.instantDe(iso).orElseThrow().toEpochMilli();
    }

    private static long minutes(String a, String b) {
        return Math.max(0, Math.round((ms(b) - ms(a)) / (double) MINUTE_MS));
    }

    /**
     * Les segments d'un quart, déduits des pointages dans l'ordre : l'entrée ouvre le travail, la pause le suspend,
     * la sortie le clôt. Une entrée faite avant le début prévu, dans la limite de la présence préalable rémunérée,
     * ouvre un segment « présence préalable » jusqu'au début prévu (E-GP-9) ; plus tôt encore, ce temps n'est pas compté.
     */
    public static List<Segment> segmentsDe(List<PointageCourt> pointages, String debutPrevu, int presencePrealableMinutes, String maintenant) {
        List<Segment> segments = new ArrayList<>();
        // Le site du dernier pointage : le temps qui suit s'est fait là, jusqu'au pointage suivant (E-GP-7).
        // L'activité suit la même règle mais se **réarme** à chaque ouverture de travail : persistant après une
        // pause, elle imputerait à l'intervention un temps de soutien (E-GP-15).
        String milieu = null;
        String activite = null;
        for (PointageCourt p : pointages.stream().sorted(Comparator.comparing(PointageCourt::horodatage)).toList()) {
            milieu = p.milieuId() != null ? p.milieuId() : milieu;
            activite = p.sens() == Sens.ENTREE || p.sens() == Sens.FIN_PAUSE ? p.activiteCode() : activite;
            if (p.sens() == Sens.ENTREE) {
                entrer(segments, p.horodatage(), new Ouverture(debutPrevu, presencePrealableMinutes, milieu, activite));
                continue;
            }
            clore(segments, p.horodatage());
            if (p.sens() != Sens.SORTIE) {
                boolean pause = p.sens() == Sens.DEBUT_PAUSE;
                segments.add(new Segment(p.horodatage(), null, pause ? TypeSegment.PAUSE : TypeSegment.TRAVAIL, 0, milieu, pause ? null : activite));
            }
        }
        if (!segments.isEmpty() && segments.getLast().fin() == null) {
            Segment o = segments.getLast();
            segments.set(segments.size() - 1, o.avec(null, minutes(o.debut(), maintenant)));
        }
        return List.copyOf(segments);
    }

    /** Ce qu'une entrée ouvre : l'horaire prévu, la présence préalable admise, et le site et l'activité en cours. */
    private record Ouverture(String debutPrevu, int prealable, String milieu, String activite) { }

    private static void entrer(List<Segment> segments, String h, Ouverture o) {
        boolean enAvance = o.debutPrevu() != null && h.compareTo(o.debutPrevu()) < 0;
        if (enAvance && o.prealable() > 0) {
            String plancher = Iso.de(Instant.ofEpochMilli(ms(o.debutPrevu()) - o.prealable() * MINUTE_MS));
            String debut = h.compareTo(plancher) > 0 ? h : plancher;
            segments.add(new Segment(debut, o.debutPrevu(), TypeSegment.PRESENCE_PREALABLE, minutes(debut, o.debutPrevu()), o.milieu(), o.activite()));
            segments.add(new Segment(o.debutPrevu(), null, TypeSegment.TRAVAIL, 0, o.milieu(), o.activite()));
            return;
        }
        segments.add(new Segment(enAvance ? o.debutPrevu() : h, null, TypeSegment.TRAVAIL, 0, o.milieu(), o.activite()));
    }

    private static void clore(List<Segment> segments, String fin) {
        if (!segments.isEmpty() && segments.getLast().fin() == null) {
            Segment s = segments.getLast();
            segments.set(segments.size() - 1, s.avec(fin, minutes(s.debut(), fin)));
        }
    }

    /** Les minutes payées d'un quart : travail et présence préalable ; jamais la pause. */
    public static long minutesPayees(List<Segment> segments) {
        return segments.stream().filter(s -> s.type() != TypeSegment.PAUSE).mapToLong(Segment::minutes).sum();
    }

    // ── Saisie a posteriori (E-GP-15, P-GP-1, P-GP-3, P-GP-4) ────────────────

    private static String instantIso(Object v) {
        return v instanceof String s ? Planification.instantDe(s).map(Iso::de).orElse("") : "";
    }

    private static SegmentSaisi segmentSaisi(Object x, int rang) {
        Map<?, ?> m = x instanceof Map<?, ?> carte ? carte : Map.of();
        String debut = instantIso(m.get("debut"));
        String fin = instantIso(m.get("fin"));
        if (debut.isEmpty() || fin.isEmpty()) {
            throw new EffectifsInvalideException("segment " + rang + " : début et fin sont des instants ISO 8601");
        }
        if (fin.compareTo(debut) <= 0) {
            throw new EffectifsInvalideException("segment " + rang + " : la fin doit suivre le début");
        }
        TypeSegment type = SAISISSABLES.stream().filter(t -> t.name().equals(m.get("type"))).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("segment " + rang + " : type TRAVAIL ou PAUSE"));
        return new SegmentSaisi(debut, fin, type, natureDuSegment(type, m.get("nature"), rang), activiteDuSegment(type, m.get("activiteCode"), rang));
    }

    /**
     * La nature d'une pause (E-GP-2) ; {@code PAUSE} à défaut, le cas courant. Sur du travail, une nature est un
     * contresens — le travail est payé, elle ne qualifie que du temps non payé — : la refuser vaut mieux que
     * l'ignorer en silence.
     */
    private static NaturePause natureDuSegment(TypeSegment type, Object brute, int rang) {
        boolean absente = brute == null || "".equals(brute);
        if (type == TypeSegment.TRAVAIL) {
            if (!absente) {
                throw new EffectifsInvalideException("segment " + rang + " : une nature ne se pose que sur une pause");
            }
            return null;
        }
        return absente ? NaturePause.PAUSE : NaturePause.de(brute, "segment " + rang + " : nature");
    }

    /** L'activité d'un temps de travail (E-GP-15), facultative ; sur une pause, un contresens refusé. */
    private static String activiteDuSegment(TypeSegment type, Object brute, int rang) {
        String code = brute == null ? "" : String.valueOf(brute).strip();
        if (type == TypeSegment.PAUSE && !code.isEmpty()) {
            throw new EffectifsInvalideException("segment " + rang + " : une activité ne se pose que sur du travail");
        }
        return code.isEmpty() ? null : code;
    }

    /** Des segments saisis : instants valides, fin après début, aucun chevauchement entre eux, 24 h au plus. */
    public static List<SegmentSaisi> segmentsSaisis(Object brut) {
        if (!(brut instanceof List<?> liste) || liste.isEmpty()) {
            throw new EffectifsInvalideException("« segments » : une liste non vide de { debut, fin, type }");
        }
        if (liste.size() > SEGMENTS_MAX) {
            throw new EffectifsInvalideException("vingt segments au plus par saisie");
        }
        List<SegmentSaisi> s = new ArrayList<>();
        for (int i = 0; i < liste.size(); i++) {
            s.add(segmentSaisi(liste.get(i), i + 1));
        }
        s.sort(Comparator.comparing(SegmentSaisi::debut));
        for (int i = 1; i < s.size(); i++) {
            if (s.get(i).debut().compareTo(s.get(i - 1).fin()) < 0) {
                throw new EffectifsInvalideException("deux segments se chevauchent : corrigez l'un des deux");
            }
        }
        if (ms(s.getLast().fin()) - ms(s.getFirst().debut()) > JOURNEE_MS) {
            throw new EffectifsInvalideException("une saisie couvre 24 heures au plus");
        }
        return List.copyOf(s);
    }

    /**
     * Les pointages d'une saisie a posteriori : les segments se suivent sans trou, commencent et finissent par du
     * travail et alternent travail et pause — sinon l'entre-deux serait ambigu (travail non pointé ou absence ?), et
     * c'est à la personne de le dire.
     */
    public static List<PointageCourt> pointagesDeSegments(List<SegmentSaisi> segments) {
        if (segments.getFirst().type() != TypeSegment.TRAVAIL || segments.getLast().type() != TypeSegment.TRAVAIL) {
            throw new EffectifsInvalideException("une saisie commence et finit par du travail");
        }
        for (int i = 1; i < segments.size(); i++) {
            if (!segments.get(i).debut().equals(segments.get(i - 1).fin())) {
                throw new EffectifsInvalideException("segment " + (i + 1) + " : il doit commencer à la fin du précédent (un trou se déclare comme pause)");
            }
            if (segments.get(i).type() == segments.get(i - 1).type()) {
                throw new EffectifsInvalideException("segment " + (i + 1) + " : travail et pause alternent");
            }
        }
        // L'activité voyage sur le pointage **qui ouvre** le travail, la nature sur celui qui ouvre la pause : c'est
        // lui qui les datera au journal.
        SegmentSaisi premier = segments.getFirst();
        List<PointageCourt> p = new ArrayList<>(List.of(new PointageCourt(Sens.ENTREE, premier.debut(), null, null, premier.activiteCode())));
        for (SegmentSaisi s : segments.subList(1, segments.size())) {
            p.add(s.type() == TypeSegment.PAUSE
                    ? new PointageCourt(Sens.DEBUT_PAUSE, s.debut(), null, s.nature(), null)
                    : new PointageCourt(Sens.FIN_PAUSE, s.debut(), null, null, s.activiteCode()));
        }
        p.add(new PointageCourt(Sens.SORTIE, segments.getLast().fin()));
        return List.copyOf(p);
    }

    // ── Corrélation des horodatages externes (E-RF-13) ───────────────────────

    public static boolean corrobore(PointageCourt pointage, List<PointageCourt> externes) {
        return externes.stream().anyMatch(e -> e.sens() == pointage.sens() && Math.abs(ms(e.horodatage()) - ms(pointage.horodatage())) <= TOLERANCE_CORROBORATION_MS);
    }

    // ── Heure locale de l'établissement ──────────────────────────────────────

    public static String instantLocal(String date, int minutes) {
        return instantLocal(date, minutes, FUSEAU);
    }

    /**
     * L'instant UTC d'une heure planifiée (date locale + minutes depuis minuit, au-delà de 1440 pour un quart qui
     * passe minuit). On estime en UTC, puis on corrige de l'écart réel du fuseau à cet instant — la date locale lève
     * l'ambiguïté d'une heure au cadran.
     */
    public static String instantLocal(String date, int minutes, String fuseau) {
        long estimation = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + minutes * MINUTE_MS;
        LocalDateTime cadran = LocalDateTime.ofInstant(Instant.ofEpochMilli(estimation), ZoneId.of(fuseau)).truncatedTo(ChronoUnit.MINUTES);
        long local = cadran.toInstant(ZoneOffset.UTC).toEpochMilli();
        return Iso.de(Instant.ofEpochMilli(estimation - (local - estimation)));
    }

    /** L'écart d'un pointage à l'heure prévue, en minutes entières (positif : en retard). */
    public static long ecartMinutes(String instant, String prevu) {
        return Math.round((ms(instant) - ms(prevu)) / (double) MINUTE_MS);
    }

    // ── Le quart pointé ──────────────────────────────────────────────────────

    /** Un quart publié, avec ses heures prévues en instants UTC. */
    public static QuartPointe quartPointe(QuartPublie q, int presencePrealableMinutes) {
        String debutPrevu = instantLocal(q.date(), q.debut());
        String finPrevue = instantLocal(q.date(), q.debut() + Planification.dureeMinutes(q.debut(), q.fin()));
        return new QuartPointe(q.placeId(), q.milieuId(), q.milieu(), q.uniteCode(), q.natureCode(), q.date(), q.debut(), q.fin(), debutPrevu, finPrevue,
                presencePrealableMinutes);
    }

    /**
     * Le quart dont la fenêtre de pointage contient l'instant — de {@code margeMs} (ou la présence préalable, si plus
     * longue) avant son début à {@code margeMs} après sa fin — le plus proche de son début s'il y en a deux.
     */
    public static Optional<QuartPointe> quartAutourDe(List<QuartPointe> quarts, String instant, long margeMs) {
        long t = ms(instant);
        return quarts.stream().filter(q -> {
            long avant = Math.max(margeMs, q.presencePrealableMinutes() * MINUTE_MS);
            return t >= ms(q.debutPrevu()) - avant && t <= ms(q.finPrevue()) + margeMs;
        }).min(Comparator.comparingLong(q -> Math.abs(ms(q.debutPrevu()) - t)));
    }
}
