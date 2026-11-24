package ca.vina.nexu.effectifs.domaine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <strong>Le choix annuel des congés</strong> (AO 26-015-DO, addenda 8, M-02) : le choix des congés annuels et hors
 * saison se fait par formulaire ; l'octroi est paramétrable par groupe d'employés (unité et corps d'emploi) et selon les
 * règles conventionnées. Une campagne offre les semaines d'une saison ; chacun les classe ; l'octroi suit l'ancienneté,
 * sous un plafond d'absents par semaine et un nombre de semaines par personne. Pur : ni base ni horloge.
 */
public final class CongesAnnuels {

    private static final int JOURS_SEMAINE = 7;
    private static final int PLAFOND_MAX = 500;
    private static final int SEMAINES_MAX = 52;

    private CongesAnnuels() {
    }

    /** Le cycle d'une campagne : préparée, ouverte aux choix, fermée, puis octroyée. */
    public enum Etat {
        BROUILLON, OUVERTE, FERMEE, OCTROYEE;

        public static Etat de(Object code) {
            return Arrays.stream(values()).filter(e -> e.name().equals(code)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« etat » : " + Arrays.toString(values())));
        }

        /** Les seules transitions permises à la main : ouvrir un brouillon, fermer des choix ouverts. */
        public Etat vers(Etat cible) {
            boolean permise = this == BROUILLON && cible == OUVERTE || this == OUVERTE && cible == FERMEE;
            if (!permise) {
                throw new ConflitException("la campagne est « " + name() + " » : elle ne passe pas à « " + cible.name() + " »");
            }
            return cible;
        }
    }

    public enum StatutChoix { DEMANDE, OCTROYE, REFUSE }

    public record Campagne(String id, String nom, String uniteCode, String corpsEmploiCode, int annee, String du, String au, String codeAbsence,
                           int plafondParSemaine, int maxSemainesParPersonne, Etat etat) {

        public Campagne avec(Etat e) {
            return new Campagne(id, nom, uniteCode, corpsEmploiCode, annee, du, au, codeAbsence, plafondParSemaine, maxSemainesParPersonne, e);
        }

        /** Les semaines offertes : chaque lundi dont la semaine entière tient dans la saison. */
        public List<String> semaines() {
            List<String> l = new ArrayList<>();
            LocalDate fin = LocalDate.parse(au);
            for (LocalDate l0 = LocalDate.parse(du).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)); !l0.plusDays(JOURS_SEMAINE - 1).isAfter(fin);
                 l0 = l0.plusDays(JOURS_SEMAINE)) {
                l.add(l0.toString());
            }
            return l;
        }

        public boolean visePour(Ressource r) {
            return uniteCode.equals(r.uniteCode()) && corpsEmploiCode.equals(r.corpsEmploiCode());
        }
    }

    public record Choix(String ressourceId, int rang, String semaine, StatutChoix statut, String motif) {
        public Choix decide(StatutChoix s, String m) {
            return new Choix(ressourceId, rang, semaine, s, m);
        }
    }

    /** Une saisie de campagne : champs requis, saison ordonnée et dans l'année, plafonds bornés. */
    public static Campagne saisie(String id, Map<String, Object> e) {
        String nom = texte(e.get("nom"), "nom");
        int annee = entier(e.get("annee"), "annee", 2000, 2100);
        String du = Referentiels.dateValide(e.get("du"), "du");
        String au = Referentiels.dateValide(e.get("au"), "au");
        if (au.compareTo(du) < 0 || !du.startsWith(String.valueOf(annee))) {
            throw new EffectifsInvalideException("la saison (du " + du + " au " + au + ") doit commencer en " + annee + " et finir après son début");
        }
        Campagne c = new Campagne(id, nom, texte(e.get("uniteCode"), "uniteCode"), texte(e.get("corpsEmploiCode"), "corpsEmploiCode"), annee, du, au,
                texte(e.get("codeAbsence"), "codeAbsence"), entier(e.get("plafondParSemaine"), "plafondParSemaine", 1, PLAFOND_MAX),
                e.get("maxSemainesParPersonne") == null ? SEMAINES_MAX : entier(e.get("maxSemainesParPersonne"), "maxSemainesParPersonne", 1, SEMAINES_MAX), Etat.BROUILLON);
        if (c.semaines().isEmpty()) {
            throw new EffectifsInvalideException("la saison ne contient aucune semaine entière (du lundi au dimanche)");
        }
        return c;
    }

    /** Les choix d'une personne, dans l'ordre : semaines distinctes et offertes par la campagne ouverte. */
    public static List<Choix> choix(Campagne c, String ressourceId, Object semaines) {
        if (c.etat() != Etat.OUVERTE) {
            throw new ConflitException("les choix de « " + c.nom() + " » ne sont pas ouverts");
        }
        if (!(semaines instanceof List<?> l)) {
            throw new EffectifsInvalideException("« semaines » : une liste de lundis, dans l'ordre de préférence");
        }
        List<String> offertes = c.semaines();
        Set<String> vues = new HashSet<>();
        List<Choix> r = new ArrayList<>();
        for (Object o : l) {
            String s = String.valueOf(o);
            if (!offertes.contains(s) || !vues.add(s)) {
                throw new EffectifsInvalideException("« " + s + " » n'est pas une semaine offerte, ou figure deux fois");
            }
            r.add(new Choix(ressourceId, r.size() + 1, s, StatutChoix.DEMANDE, null));
        }
        return r;
    }

    /** Une personne à départager : son ancienneté et son nom, pour un ordre stable. */
    public record Candidat(String ressourceId, String nom, double anciennete) { }

    /**
     * L'octroi : par ancienneté décroissante (puis par nom), chaque choix dans l'ordre de préférence, tant que la semaine
     * reste sous le plafond et que la personne n'a pas atteint son nombre de semaines.
     */
    public static List<Choix> octroi(Campagne c, List<Candidat> candidats, List<Choix> choix) {
        Map<String, Integer> parSemaine = new HashMap<>();
        List<Choix> r = new ArrayList<>();
        for (Candidat k : candidats.stream().sorted(Comparator.comparingDouble(Candidat::anciennete).reversed().thenComparing(Candidat::nom)).toList()) {
            int obtenues = 0;
            for (Choix x : choix.stream().filter(x -> x.ressourceId().equals(k.ressourceId())).sorted(Comparator.comparingInt(Choix::rang)).toList()) {
                int deja = parSemaine.getOrDefault(x.semaine(), 0);
                if (obtenues >= c.maxSemainesParPersonne()) {
                    r.add(x.decide(StatutChoix.REFUSE, "au plus " + c.maxSemainesParPersonne() + " semaine(s) par personne"));
                } else if (deja >= c.plafondParSemaine()) {
                    r.add(x.decide(StatutChoix.REFUSE, "plafond de " + c.plafondParSemaine() + " absent(s) atteint cette semaine"));
                } else {
                    parSemaine.put(x.semaine(), deja + 1);
                    obtenues++;
                    r.add(x.decide(StatutChoix.OCTROYE, null));
                }
            }
        }
        return r;
    }

    /** Le dimanche de la semaine d'un lundi. */
    public static String finDeSemaine(String lundi) {
        return LocalDate.parse(lundi).plusDays(JOURS_SEMAINE - 1).toString();
    }

    private static String texte(Object v, String champ) {
        if (v instanceof String s && !s.isBlank()) {
            return s.strip();
        }
        throw new EffectifsInvalideException("« " + champ + " » requis");
    }

    private static int entier(Object v, String champ, int min, int max) {
        if (v instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= min && n.intValue() <= max) {
            return n.intValue();
        }
        throw new EffectifsInvalideException("« " + champ + " » : un entier de " + min + " à " + max);
    }
}
