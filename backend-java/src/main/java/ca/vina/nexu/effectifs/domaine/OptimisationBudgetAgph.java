package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * <strong>L'optimisation du budget d'heures d'une campagne AGPH</strong> (E-CH-1 ; addenda 7, Q11 : « un calcul d'optimisation
 * proposé par la solution », budget en heures ; ADR 0087, AD-04). Fichier pur : aucune E/S, aucune horloge.
 *
 * <p>Un poste n'est retenu que s'il est <strong>couvert</strong> — au moins une semaine type déposée contient chacun de ses
 * quarts. Parmi les postes couverts, la proposition choisit l'ensemble qui <strong>maximise les heures hebdomadaires</strong>
 * sans dépasser le budget : un sac à dos 0-1 dont la valeur est le poids, résolu exactement par programmation dynamique sur
 * les quarts d'heure. À heures égales, l'ensemble qui totalise le plus de candidats l'emporte, ce qui rend la proposition
 * déterministe. Rien n'est écrit : la décision d'offrir un poste reste au service de planification.
 */
public final class OptimisationBudgetAgph {

    private static final int QUARTS_PAR_HEURE = 4;
    private static final int MINUTES_HEURE = 60;
    private static final int JOURS_SEMAINE = 7;
    private static final double CENTIEMES = 100.0;

    private OptimisationBudgetAgph() {
    }

    /** Pourquoi un poste n'est pas proposé ; le libellé est ce que lit le service de planification. */
    public enum MotifEcart {
        AUCUNE_DISPONIBILITE("aucune semaine type déposée ne couvre tous ses quarts"),
        HORS_BUDGET("il ferait dépasser le budget d'heures");

        private final String libelle;

        MotifEcart(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Un quart de la semaine d'un poste : un jour (1 = lundi à 7 = dimanche) et un créneau en minutes depuis minuit. */
    public record QuartDuPoste(int jour, int debut, int fin) { }

    /** Un poste offert à la campagne, avec les quarts de son cycle, semaine par semaine mis bout à bout. */
    public record PosteAEvaluer(String id, String code, String libelle, int semainesDuCycle, List<QuartDuPoste> quarts) {
        public PosteAEvaluer {
            quarts = List.copyOf(quarts);
        }

        /** Les heures hebdomadaires moyennes du cycle. */
        public double heuresSemaine() {
            int minutes = quarts.stream().mapToInt(q -> Planification.dureeMinutes(q.debut(), q.fin())).sum();
            return Math.round(minutes / (double) MINUTES_HEURE / Math.max(1, semainesDuCycle) * CENTIEMES) / CENTIEMES;
        }
    }

    /** Un poste jugé : ses heures, combien de semaines types le couvrent, et, s'il est écarté, pourquoi. */
    public record PosteJuge(String id, String code, String libelle, double heuresSemaine, int candidats, MotifEcart motif) { }

    /** La proposition : postes retenus et écartés, total, budget, et ce qu'il en reste. */
    public record Proposition(double budgetHeures, double totalHeures, double resteHeures, List<PosteJuge> retenus, List<PosteJuge> ecartes) {
        public Proposition {
            retenus = List.copyOf(retenus);
            ecartes = List.copyOf(ecartes);
        }
    }

    /** Une semaine type couvre-t-elle tous les quarts du poste ? Chaque quart doit tenir dans une plage du même jour. */
    public static boolean couvre(List<AgphSemaineType.PlageType> semaine, PosteAEvaluer poste) {
        return !poste.quarts().isEmpty() && poste.quarts().stream().allMatch(q -> semaine.stream().anyMatch(p -> contient(p, q)));
    }

    private static boolean contient(AgphSemaineType.PlageType p, QuartDuPoste q) {
        if (p.jour() != q.jour()) {
            return false;
        }
        long[] plage = Planification.intervalle(AgphSemaineType.LUNDI_REFERENCE, p.debut(), p.fin());
        long[] quart = Planification.intervalle(AgphSemaineType.LUNDI_REFERENCE, q.debut(), q.fin());
        return plage[0] <= quart[0] && quart[1] <= plage[1];
    }

    /** Les quarts d'un cycle (semaines de 7 jours, une plage par jour ou nul), traduits par le créneau de chaque plage. */
    public static List<QuartDuPoste> quartsDuCycle(List<List<String>> cycle, Function<String, Planification.Plage> plage) {
        List<QuartDuPoste> quarts = new ArrayList<>();
        for (List<String> semaine : cycle) {
            for (int j = 0; j < JOURS_SEMAINE; j++) {
                String code = semaine.get(j);
                if (code != null) {
                    Planification.Plage c = plage.apply(code);
                    quarts.add(new QuartDuPoste(j + 1, c.debut(), c.fin()));
                }
            }
        }
        return quarts;
    }

    /** La proposition pour un budget d'heures hebdomadaires et les semaines types déposées. */
    public static Proposition proposer(List<PosteAEvaluer> postes, List<List<AgphSemaineType.PlageType>> depots, double budgetHeures) {
        List<PosteJuge> juges = postes.stream().map(p -> juger(p, depots)).toList();
        List<PosteJuge> couverts = new ArrayList<>(juges.stream().filter(j -> j.motif() == null).toList());
        List<PosteJuge> ecartes = new ArrayList<>(juges.stream().filter(j -> j.motif() != null).toList());
        // Le plus de candidats d'abord, puis le code : à valeur égale, l'ordre départage et la proposition reste stable.
        couverts.sort(Comparator.comparingInt(PosteJuge::candidats).reversed().thenComparing(PosteJuge::code));
        BitSet choisis = sacADos(couverts, Math.max(0, (int) Math.floor(budgetHeures * QUARTS_PAR_HEURE)));
        List<PosteJuge> retenus = new ArrayList<>();
        for (int i = 0; i < couverts.size(); i++) {
            PosteJuge j = couverts.get(i);
            if (choisis.get(i)) {
                retenus.add(j);
            } else {
                ecartes.add(new PosteJuge(j.id(), j.code(), j.libelle(), j.heuresSemaine(), j.candidats(), MotifEcart.HORS_BUDGET));
            }
        }
        double total = Math.round(retenus.stream().mapToDouble(PosteJuge::heuresSemaine).sum() * CENTIEMES) / CENTIEMES;
        return new Proposition(budgetHeures, total, Math.round((budgetHeures - total) * CENTIEMES) / CENTIEMES, retenus, ecartes);
    }

    private static PosteJuge juger(PosteAEvaluer p, List<List<AgphSemaineType.PlageType>> depots) {
        int candidats = (int) depots.stream().filter(d -> couvre(d, p)).count();
        return new PosteJuge(p.id(), p.code(), p.libelle(), p.heuresSemaine(), candidats, candidats == 0 ? MotifEcart.AUCUNE_DISPONIBILITE : null);
    }

    private static int poids(PosteJuge j) {
        return (int) Math.round(j.heuresSemaine() * QUARTS_PAR_HEURE);
    }

    /**
     * Sac à dos 0-1 exact : {@code meilleur[c]} est la meilleure valeur sous la capacité {@code c}, la valeur étant les heures
     * (en quarts d'heure) puis, à heures égales, les candidats. {@code pris[i]} garde, pour chaque capacité, si le poste i est
     * pris : de quoi reconstituer le choix en remontant.
     */
    private static BitSet sacADos(List<PosteJuge> postes, int capacite) {
        long departage = postes.stream().mapToLong(PosteJuge::candidats).sum() + 1;
        long[] meilleur = new long[capacite + 1];
        List<BitSet> pris = new ArrayList<>();
        for (PosteJuge p : postes) {
            int w = poids(p);
            long valeur = w * departage + p.candidats();
            BitSet ligne = new BitSet(capacite + 1);
            for (int c = capacite; c >= w; c--) {
                if (meilleur[c - w] + valeur > meilleur[c]) {
                    meilleur[c] = meilleur[c - w] + valeur;
                    ligne.set(c);
                }
            }
            pris.add(ligne);
        }
        BitSet choisis = new BitSet(postes.size());
        int c = capacite;
        for (int i = postes.size() - 1; i >= 0; i--) {
            if (pris.get(i).get(c)) {
                choisis.set(i);
                c -= poids(postes.get(i));
            }
        }
        return choisis;
    }
}
