package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <strong>Les disponibilités d'une campagne AGPH : la semaine type</strong> (E-CH-1) — jumeau de la partie « semaine
 * type » de {@code backend/src/effectifs/agph/domaine.ts}.
 *
 * <p>Une campagne annuelle ne recueille pas des dates mais une <strong>semaine type</strong> : quels jours, à quelles
 * heures, la personne peut travailler toute l'année. Pour la résumer avec les mêmes faits qu'un dépôt de période, on la
 * pose sur une semaine de référence — un lundi quelconque. Fichier pur : aucune E/S, aucune horloge.
 */
public final class AgphSemaineType {

    /** Un lundi quelconque : la semaine type s'y pose pour se résumer comme un dépôt de période. */
    public static final String LUNDI_REFERENCE = "2001-01-01";
    private static final String DIMANCHE_REFERENCE = "2001-01-07";
    /** Les jours d'une semaine type, dans l'ordre ISO (1 = lundi) : le message d'erreur les nomme. */
    public enum Jour {
        LUNDI("lundi"), MARDI("mardi"), MERCREDI("mercredi"), JEUDI("jeudi"), VENDREDI("vendredi"), SAMEDI("samedi"), DIMANCHE("dimanche");

        private final String libelle;

        Jour(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        /** Le jour d'un numéro ISO (1 à 7). */
        public static Jour de(int numero) {
            return values()[numero - 1];
        }
    }

    public static final int PLAGES_TYPE_MAX = 50;
    private static final int LUNDI = 1;
    private static final int DIMANCHE = 7;
    private static final double CENTIEMES = 100.0;
    private static final double MILLIEMES = 1000.0;
    private static final double DIXIEMES = 10.0;

    private AgphSemaineType() {
    }

    /** Une plage de la semaine type : un jour (1 = lundi à 7 = dimanche), un créneau, un type. */
    public record PlageType(int jour, int debut, int fin, Disponibilites.TypeDisponibilite type) {
        Disponibilites.PlageDisponible surLaSemaineDeReference() {
            return new Disponibilites.PlageDisponible(dateDuJour(jour), debut, fin, type);
        }
    }

    /**
     * Les faits d'un dépôt de campagne : nombre et durée des quarts offerts sur la semaine type, et leur poids sur le
     * budget — les heures hebdomadaires offertes par toute la campagne, ce dépôt compris, rapportées au budget fixé.
     */
    public record Faits(int quartsParSemaine, int dureeMinutes, int finsDeSemaine, int nuitsConsecutives, double heuresParSemaine, double heuresCampagne,
                        double budgetHeures, double tauxBudget) {

        /** Les faits que les règles {@code DEPOT_AGPH} lisent, hors ceux de la personne (statut, corps). */
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("quartsParSemaine", quartsParSemaine);
            m.put("dureeMinutes", dureeMinutes);
            m.put("finsDeSemaine", finsDeSemaine);
            m.put("nuitsConsecutives", nuitsConsecutives);
            m.put("heuresParSemaine", heuresParSemaine);
            m.put("heuresCampagne", heuresCampagne);
            m.put("budgetHeures", budgetHeures);
            m.put("tauxBudget", tauxBudget);
            return m;
        }
    }

    private static String dateDuJour(int jour) {
        return "2001-01-0" + jour;
    }

    /** Valide une semaine type : jours 1 (lundi) à 7, créneaux valides, types connus, aucun chevauchement. */
    public static List<PlageType> plagesTypeDe(Object brut) {
        if (!(brut instanceof List<?> liste)) {
            throw new EffectifsInvalideException("« plages » : une liste de { jour, debut, fin, type } est attendue");
        }
        if (liste.size() > PLAGES_TYPE_MAX) {
            throw new EffectifsInvalideException("une semaine type compte au plus " + PLAGES_TYPE_MAX + " plages");
        }
        List<PlageType> plages = new ArrayList<>();
        for (int i = 0; i < liste.size(); i++) {
            plages.add(plageDe(liste.get(i), i + 1));
        }
        plages.sort(Comparator.comparingInt(PlageType::jour).thenComparingInt(PlageType::debut));
        for (int i = 1; i < plages.size(); i++) {
            PlageType b = plages.get(i);
            if (Planification.chevauchent(plages.get(i - 1).surLaSemaineDeReference().intervalle(), b.surLaSemaineDeReference().intervalle())) {
                throw new EffectifsInvalideException("deux plages se chevauchent le " + Jour.de(b.jour()).libelle() + " : corrigez l'une des deux");
            }
        }
        return List.copyOf(plages);
    }

    private static PlageType plageDe(Object x, int rang) {
        Map<?, ?> m = x instanceof Map<?, ?> carte ? carte : Map.of();
        double jour = Agph.nombre(m.get("jour"));
        if (!Agph.estEntier(jour) || jour < LUNDI || jour > DIMANCHE) {
            throw new EffectifsInvalideException("plage " + rang + " : « jour » de 1 (lundi) à 7 (dimanche)");
        }
        Planification.Plage c = Disponibilites.creneauDePlage(m.get("debut"), m.get("fin"));
        return new PlageType((int) jour, c.debut(), c.fin(), Disponibilites.typeDe(m.get("type"), rang));
    }

    /** La semaine type posée sur la semaine de référence, pour la résumer comme un dépôt de période. */
    public static List<Disponibilites.PlageDisponible> versPlages(List<PlageType> plages) {
        return plages.stream().map(PlageType::surLaSemaineDeReference).toList();
    }

    /**
     * Résume la semaine type et son poids sur le budget. Sans budget ({@code budget} nul), {@code tauxBudget} vaut 0 :
     * aucune règle budgétaire ne se déclenche sur une donnée qui n'existe pas.
     */
    public static Faits faits(List<PlageType> plages, double heuresDesAutres, Double budget) {
        Disponibilites.Faits f = Disponibilites.faitsDuDepot(versPlages(plages), LUNDI_REFERENCE, DIMANCHE_REFERENCE);
        double heuresCampagne = Math.round((heuresDesAutres + f.heuresParPeriode()) * CENTIEMES) / CENTIEMES;
        double budgetHeures = budget == null ? 0 : budget;
        double tauxBudget = budgetHeures > 0 ? Math.round(heuresCampagne / budgetHeures * MILLIEMES) / DIXIEMES : 0;
        return new Faits(f.quartsParSemaine(), f.dureeMinutes(), f.finsDeSemaine(), f.nuitsConsecutives(), f.heuresParPeriode(), heuresCampagne, budgetHeures,
                tauxBudget);
    }
}
