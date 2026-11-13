package ca.vina.nexu.effectifs.domaine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * <strong>Disponibilités par période</strong> — AO 26-015-DO, lot R4 (E-GN-12 à E-GN-16, E-GR-11, P-GH-4) ; le
 * pur de {@code backend/src/effectifs/disponibilites/domaine.ts}.
 *
 * <p>Un dépôt n'est pas une liste libre : il se <strong>résume en faits</strong> que le moteur de règles confronte
 * à la convention. Un fait hebdomadaire se calcule sur la <strong>pire semaine</strong> : une règle « au moins trois
 * quarts par semaine » ne se satisfait pas d'une semaine à cinq et d'une à un. Aucune E/S, aucune horloge.
 */
public final class Disponibilites {

    public static final int PLAGES_MAX = 400;
    /** Le type de tâche de l'ordonnanceur qui ferme la collecte et lance l'attribution (P-GH-4). */
    public static final String TACHE_FIN_DE_COLLECTE = "effectifs_fin_collecte";
    /** Une plage qui commence à 18 h ou plus tard est une nuit. */
    private static final int DEBUT_DE_NUIT = 1080;
    private static final int JOURNEE_ENTIERE = 1440;
    private static final int JOURS_SEMAINE = 7;
    private static final double MINUTES_HEURE = 60.0;
    private static final double CENTIEMES = 100.0;
    private static final String NON_DEFINI = "undefined";

    private Disponibilites() {
    }

    public enum TypeDisponibilite {
        DISPONIBLE, PREFERENCE, INDISPONIBLE;

        /** Une indisponibilité déclarée n'est pas une disponibilité de zéro heure : elle n'est jamais offerte. */
        public boolean estOfferte() {
            return this != INDISPONIBLE;
        }
    }

    public enum Conformite { CONFORME, AVERTIE, APPROBATION_REQUISE }

    public record PlageDisponible(String date, int debut, int fin, TypeDisponibilite type) {
        long[] intervalle() {
            return Planification.intervalle(date, debut, fin);
        }
    }

    public record Faits(int quartsParSemaine, double heuresParPeriode, int dureeMinutes, int finsDeSemaine, int nuitsConsecutives) {
        /** Les faits dans l'ordre du catalogue du contexte DISPONIBILITE. */
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quartsParSemaine", quartsParSemaine);
            m.put("heuresParPeriode", heuresParPeriode);
            m.put("dureeMinutes", dureeMinutes);
            m.put("finsDeSemaine", finsDeSemaine);
            m.put("nuitsConsecutives", nuitsConsecutives);
            return m;
        }
    }

    /** Un candidat à une place, et ce qui le classe. */
    public record Candidature(String ressourceId, String nom, boolean preference, double anciennete, double heuresPlanifiees) { }

    public static boolean estNuit(int debut, int fin) {
        return fin <= debut || debut >= DEBUT_DE_NUIT;
    }

    /** {@code Number(v)} du Node : vrai vaut 1, un texte vide 0, l'absent n'est pas un nombre. */
    private static double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof Boolean b) {
            return b ? 1 : 0;
        }
        try {
            String t = v == null ? NON_DEFINI : v.toString().trim();
            return t.isEmpty() ? 0 : Double.parseDouble(t);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /**
     * Le créneau d'une plage. Contrairement à un quart, une plage peut couvrir la <strong>journée entière</strong> :
     * « indisponible le mercredi » est la saisie la plus courante qui soit.
     */
    public static Planification.Plage creneauDePlage(Object debut, Object fin) {
        if (nombre(debut) == 0 && nombre(fin) == JOURNEE_ENTIERE) {
            return new Planification.Plage(0, JOURNEE_ENTIERE);
        }
        return Planification.creneauDe(debut, fin);
    }

    /** Valide un dépôt : dates dans la période, créneaux valides, types connus, aucun chevauchement. */
    public static List<PlageDisponible> plagesDe(Object brut, String debutPeriode, String finPeriode) {
        if (!(brut instanceof List<?> liste)) {
            throw new EffectifsInvalideException("« plages » : une liste de { date, debut, fin, type } est attendue");
        }
        if (liste.size() > PLAGES_MAX) {
            throw new EffectifsInvalideException("un dépôt compte au plus " + PLAGES_MAX + " plages");
        }
        List<PlageDisponible> plages = new ArrayList<>();
        for (int i = 0; i < liste.size(); i++) {
            plages.add(plageDe(liste.get(i), i + 1, debutPeriode, finPeriode));
        }
        plages.sort(Comparator.comparingLong(p -> p.intervalle()[0]));
        for (int i = 1; i < plages.size(); i++) {
            if (Planification.chevauchent(plages.get(i - 1).intervalle(), plages.get(i).intervalle())) {
                throw new EffectifsInvalideException("deux plages se chevauchent le " + plages.get(i).date() + " : corrigez l'une des deux");
            }
        }
        return plages;
    }

    private static PlageDisponible plageDe(Object x, int rang, String debutPeriode, String finPeriode) {
        Map<?, ?> m = x instanceof Map<?, ?> carte ? carte : Map.of();
        String date = Planification.dateDe(m.get("date"), "date de la plage " + rang);
        if (date.compareTo(debutPeriode) < 0 || date.compareTo(finPeriode) > 0) {
            throw new EffectifsInvalideException("plage " + rang + " : le " + date + " est hors de la période (" + debutPeriode + " au " + finPeriode + ")");
        }
        Planification.Plage c = creneauDePlage(m.get("debut"), m.get("fin"));
        return new PlageDisponible(date, c.debut(), c.fin(), typeDe(m.get("type"), rang));
    }

    /** Le type d'une plage, lu à la frontière ; le refus nomme la plage et les types attendus. */
    static TypeDisponibilite typeDe(Object type, int rang) {
        return Arrays.stream(TypeDisponibilite.values()).filter(v -> v.name().equals(type)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("plage " + rang + " : type inconnu « " + (type == null ? NON_DEFINI : Regles.texte(type))
                        + " » (attendu : " + Arrays.stream(TypeDisponibilite.values()).map(Enum::name).collect(Collectors.joining(", ")) + ")"));
    }

    private static String decaler(String date, int jours) {
        return LocalDate.parse(date).plusDays(jours).toString();
    }

    /**
     * Les semaines ISO (lundis) sur lesquelles juger un fait hebdomadaire : les semaines <strong>entières</strong>,
     * pour qu'une semaine tronquée au bord ne passe pas pour la pire ; toutes celles touchées si la période est
     * plus courte qu'une semaine.
     */
    public static List<String> semainesDe(String debut, String fin) {
        List<String> touchees = new ArrayList<>();
        for (String l = Planification.lundiDe(debut); l.compareTo(fin) <= 0; l = decaler(l, JOURS_SEMAINE)) {
            touchees.add(l);
        }
        List<String> entieres = touchees.stream().filter(l -> l.compareTo(debut) >= 0 && decaler(l, JOURS_SEMAINE - 1).compareTo(fin) <= 0).toList();
        return entieres.isEmpty() ? touchees : entieres;
    }

    /** Résume un dépôt en faits ; seules les plages offertes comptent. */
    public static Faits faitsDuDepot(List<PlageDisponible> plages, String debutPeriode, String finPeriode) {
        List<PlageDisponible> offertes = plages.stream().filter(p -> p.type().estOfferte()).toList();
        Map<String, Integer> parSemaine = new LinkedHashMap<>();
        semainesDe(debutPeriode, finPeriode).forEach(l -> parSemaine.put(l, 0));
        offertes.forEach(p -> parSemaine.computeIfPresent(Planification.lundiDe(p.date()), (l, n) -> n + 1));
        int minutes = offertes.stream().mapToInt(p -> Planification.dureeMinutes(p.debut(), p.fin())).sum();
        return new Faits(parSemaine.values().stream().mapToInt(Integer::intValue).min().orElse(0), Math.round(minutes / MINUTES_HEURE * CENTIEMES) / CENTIEMES,
                offertes.stream().mapToInt(p -> Planification.dureeMinutes(p.debut(), p.fin())).min().orElse(0), finsDeSemaine(offertes), nuitsConsecutives(offertes));
    }

    private static int finsDeSemaine(List<PlageDisponible> offertes) {
        return (int) offertes.stream().filter(p -> {
            DayOfWeek j = LocalDate.parse(p.date()).getDayOfWeek();
            return j == DayOfWeek.SATURDAY || j == DayOfWeek.SUNDAY;
        }).map(p -> Planification.lundiDe(p.date())).distinct().count();
    }

    private static int nuitsConsecutives(List<PlageDisponible> offertes) {
        Set<String> jours = offertes.stream().filter(p -> estNuit(p.debut(), p.fin())).map(PlageDisponible::date).collect(Collectors.toCollection(TreeSet::new));
        int meilleure = 0;
        int serie = 0;
        String precedent = null;
        for (String d : jours) {
            serie = precedent != null && decaler(precedent, 1).equals(d) ? serie + 1 : 1;
            meilleure = Math.max(meilleure, serie);
            precedent = d;
        }
        return meilleure;
    }

    /** La conformité enregistrée d'un dépôt accepté, selon l'action la plus restrictive des règles. */
    public static Conformite conformiteDe(ActionRegle action) {
        return switch (action) {
            case AVERTIR -> Conformite.AVERTIE;
            case EXIGER_APPROBATION -> Conformite.APPROBATION_REQUISE;
            case ACCEPTER, EXCLURE_DU_BASSIN, BLOQUER, REFUSER -> Conformite.CONFORME;
        };
    }

    // ── Fin de collecte automatique (P-GH-4) ───────────────────────────────

    /**
     * L'instant où la collecte est finie : le lendemain de sa date de fin, à minuit (UTC — l'instant de référence de tout
     * le module). Un dépôt est admis jusqu'à {@code finCollecte} <strong>incluse</strong> (E-GR-11) : attribuer plus tôt
     * priverait du bassin quelqu'un qui dépose le dernier soir.
     */
    public static String echeanceFinDeCollecte(String finCollecte) {
        return decaler(finCollecte, 1) + "T00:00:00.000Z";
    }

    /** Un dépôt se fait pendant la collecte, et jusqu'à sa date de fin incluse (E-GR-11). */
    public static void verifierFenetre(Planification.EtatPeriode etat, String finCollecte, String aujourdhui) {
        if (!Planification.collecteOuverte(etat)) {
            throw new EffectifsInvalideException("la collecte de cette période n'est pas ouverte : les disponibilités ne se déposent plus");
        }
        if (finCollecte != null && aujourdhui.compareTo(finCollecte) > 0) {
            throw new EffectifsInvalideException("la collecte s'est terminée le " + finCollecte);
        }
    }

    /** La première plage offerte qui couvre entièrement le quart. */
    public static Optional<PlageDisponible> couvre(Collection<PlageDisponible> plages, Planification.Creneau quart) {
        long[] q = Planification.intervalle(quart.date(), quart.debut(), quart.fin());
        return plages.stream().filter(p -> p.type().estOfferte()).filter(p -> {
            long[] i = p.intervalle();
            return i[0] <= q[0] && q[1] <= i[1];
        }).findFirst();
    }

    /** Une indisponibilité déclarée touche-t-elle le quart ? */
    public static boolean indisponible(Collection<PlageDisponible> plages, Planification.Creneau quart) {
        long[] q = Planification.intervalle(quart.date(), quart.debut(), quart.fin());
        return plages.stream().anyMatch(p -> !p.type().estOfferte() && Planification.chevauchent(p.intervalle(), q));
    }

    /**
     * L'ordre d'attribution de fin de collecte (P-GH-4) : la préférence d'abord, puis l'ancienneté, puis celui qui a
     * le moins d'heures — l'équité départage à ancienneté égale.
     */
    public static List<Candidature> classer(Collection<Candidature> candidats) {
        return candidats.stream().sorted(Comparator.comparing(Candidature::preference).reversed()
                .thenComparing(Comparator.comparingDouble(Candidature::anciennete).reversed())
                .thenComparingDouble(Candidature::heuresPlanifiees).thenComparing(Candidature::ressourceId)).toList();
    }
}
