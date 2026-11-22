package ca.vina.nexu.effectifs.domaine;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Absences d'effectifs</strong> — AO 26-015-DO, lot R5 (E-GN-5, E-GN-6, E-CH-6, E-GR-2 à E-GR-4) ; le pur
 * de {@code backend/src/effectifs/absences/domaine.ts}.
 * <p>
 * Une absence se déclare par la façade {@code /api/absences} (ADR 0034) ; ici vit ce qui lui est propre : le
 * <strong>code</strong> admissible selon le titre d'emploi, la <strong>décision automatique</strong> des règles —
 * conservée même quand elle refuse, parce que c'est elle qu'une dérogation renverse —, et les transitions
 * permises. Le taux de comblement « après » se calcule, il ne se stocke pas.
 */
public final class AbsencesEffectifs {

    /** Au-delà d'un an, ce n'est plus une absence : c'est un remplacement long terme (lot R7). */
    public static final int JOURS_MAX = 366;
    /** Une dérogation se justifie : un mot ne suffit pas à expliquer qu'on renverse une règle. */
    public static final int MOTIF_DEROGATION_MIN = 10;
    /** La longueur retenue d'un motif libre, comme le Node. */
    public static final int MOTIF_MAX = 500;
    /** Une unité sans place est comblée par définition. */
    private static final double COMBLEE = 100;
    private static final double POUR_MILLE = 1000;
    private static final double DIXIEMES = 10;

    private AbsencesEffectifs() {
    }

    /** Les états d'une absence d'effectifs, avec le libellé qui nomme l'état dans un refus. */
    public enum Statut {
        ACCEPTEE("acceptée"), A_APPROUVER("à approuver"), REFUSEE_AUTO("refusée par les règles"), REFUSEE("refusée"), ANNULEE("annulée");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String code() {
            return name();
        }

        /** Une absence active retient la personne : elle bloque un chevauchement et rend ses places vacantes. */
        public boolean estActive() {
            return this == ACCEPTEE || this == A_APPROUVER;
        }

        /** Seule une absence en attente reste à décider (les pièces se déposent encore, dit la façade). */
        public boolean estDecidee() {
            return this != A_APPROUVER;
        }

        public static Statut de(String code) {
            return Arrays.stream(values()).filter(s -> s.code().equals(code)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("statut d'absence inconnu : « " + code + " »"));
        }
    }

    /** Les décisions humaines, chacune avec l'état qu'elle exige et celui où elle mène (diagramme 7.3 du document 03). */
    public enum Decision {
        APPROUVER("approuvée"), REFUSER("refusée"), DEROGER("accordée par dérogation"), ANNULER("annulée");

        private final String participe;

        Decision(String participe) {
            this.participe = participe;
        }

        public Set<Statut> depuis() {
            return switch (this) {
                case APPROUVER, REFUSER -> EnumSet.of(Statut.A_APPROUVER);
                case DEROGER -> EnumSet.of(Statut.REFUSEE_AUTO);
                case ANNULER -> EnumSet.of(Statut.ACCEPTEE, Statut.A_APPROUVER);
            };
        }

        public Statut vers() {
            return switch (this) {
                case APPROUVER, DEROGER -> Statut.ACCEPTEE;
                case REFUSER -> Statut.REFUSEE;
                case ANNULER -> Statut.ANNULEE;
            };
        }

        /**
         * La décision lue à la frontière de la route des décisions : un code inconnu est un 422 nommé, jamais un
         * défaut silencieux ; l'annulation a sa propre route.
         */
        public static Decision humaineDe(Object v) {
            List<Decision> admises = Arrays.stream(values()).filter(d -> d != ANNULER).toList();
            return admises.stream().filter(d -> d.name().equals(v)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« decision » : "
                            + admises.stream().map(Decision::name).collect(Collectors.joining(", ")) + " (l'annulation a sa propre route)"));
        }
    }

    /** Une place tenue par la personne pendant l'absence : c'est elle qui deviendrait vacante. */
    public record PlaceTenue(String id, String date, int debut, int fin) { }

    /** Le comblement d'une unité entre deux dates. */
    public record Comblement(int places, int pourvues) { }

    /** Ce qu'une absence fait au comblement de son unité (E-GR-3). */
    public record Impact(List<PlaceTenue> places, double tauxAvant, double tauxApres) {
        public Impact {
            places = List.copyOf(places);
        }

        public static Impact de(List<PlaceTenue> places, Comblement c) {
            return new Impact(places, AbsencesEffectifs.tauxApres(c.places(), c.pourvues(), 0), AbsencesEffectifs.tauxApres(c.places(), c.pourvues(), places.size()));
        }
    }

    /** Une dérogation motivée, qui attribue un code d'absence. */
    public record Derogation(String motif, String codeAbsence) { }

    public static Statut transition(Statut statut, Decision decision) {
        if (!decision.depuis().contains(statut)) {
            throw new EffectifsInvalideException("une absence " + statut.libelle + " ne peut pas être " + decision.participe);
        }
        return decision.vers();
    }

    /**
     * La décision automatique : accepter ou avertir accordent ; exiger une approbation met en attente ; tout ce
     * qui bloque est un <strong>refus automatique</strong>, conservé avec ses motifs.
     */
    public static Statut statutDepuisAction(ActionRegle action) {
        return switch (action) {
            case ACCEPTER, AVERTIR -> Statut.ACCEPTEE;
            case EXIGER_APPROBATION -> Statut.A_APPROUVER;
            case EXCLURE_DU_BASSIN, BLOQUER, REFUSER -> Statut.REFUSEE_AUTO;
        };
    }

    /**
     * D'où vient l'absence (AD-06c). Une <strong>demande</strong> se soumet aux règles, préavis compris. Une absence
     * <strong>constatée aux opérations</strong> (addenda 5 d : la personne ne s'est pas présentée, ou s'est déclarée
     * malade au répartiteur) est un fait : acceptée d'office, sans exigence de préavis ; le verdict des règles reste
     * consigné avec elle, pour que l'avertissement — un solde dépassé, par exemple — ne se perde pas.
     */
    public enum Origine {
        DEMANDE("absence.declaree"), OPERATIONS("absence.constatee");

        private final String actionJournal;

        Origine(String actionJournal) {
            this.actionJournal = actionJournal;
        }

        public String actionJournal() {
            return actionJournal;
        }

        public Statut statut(ActionRegle action) {
            return switch (this) {
                case DEMANDE -> statutDepuisAction(action);
                case OPERATIONS -> Statut.ACCEPTEE;
            };
        }

        public boolean exigeUnCodePlanifiable() {
            return switch (this) {
                case DEMANDE -> true;
                case OPERATIONS -> false;
            };
        }
    }

    /** La même absence, renvoyée : même code, mêmes dates, toujours active. Un renvoi après une coupure ne la double pas. */
    public static boolean estLaMeme(AbsenceEffectifs a, String codeAbsence, String du, String au) {
        return a.statut().estActive() && a.codeAbsence().equals(codeAbsence) && a.du().equals(du) && a.au().equals(au);
    }

    /** Le code d'absence est-il admissible pour ce titre d'emploi ? Une liste vide admet tous les titres. */
    public static boolean codeAdmissible(List<String> titres, String titreEmploi) {
        return titres.isEmpty() || titreEmploi != null && titres.contains(titreEmploi);
    }

    /** Jours civils demandés, bornes comprises. */
    public static int joursDemandes(String du, String au) {
        return (int) (jour(au) - jour(du)) + 1;
    }

    /** Préavis en jours entre aujourd'hui et le premier jour ; négatif pour une absence déjà commencée. */
    public static int joursPreavis(String aujourdhui, String du) {
        return (int) (jour(du) - jour(aujourdhui));
    }

    private static long jour(String date) {
        return LocalDate.parse(date).toEpochDay();
    }

    /** Les bornes d'une absence : du ≤ au, au plus un an — une absence plus longue est un remplacement long terme (R7). */
    public static void bornesValides(String du, String au) {
        if (au.compareTo(du) < 0) {
            throw new EffectifsInvalideException("la fin (" + au + ") précède le début (" + du + ")");
        }
        if (joursDemandes(du, au) > JOURS_MAX) {
            throw new EffectifsInvalideException("une absence d'effectifs couvre au plus " + JOURS_MAX + " jours : au-delà, c'est un remplacement long terme");
        }
    }

    /** Deux absences se chevauchent-elles (dates incluses) ? */
    public static boolean chevauche(AbsenceEffectifs a, String du, String au) {
        return a.du().compareTo(au) <= 0 && du.compareTo(a.au()) <= 0;
    }

    /** Taux de comblement, en pour cent à une décimale, quand les places de la personne deviennent vacantes. */
    public static double tauxApres(int places, int pourvues, int placesDeLaPersonne) {
        if (places == 0) {
            return COMBLEE;
        }
        return Math.round((double) (pourvues - placesDeLaPersonne) / places * POUR_MILLE) / DIXIEMES;
    }

    /** Une dérogation est motivée (10 caractères au moins) et attribue un code d'absence (E-GN-5). */
    public static Derogation derogationDe(Map<String, Object> entree) {
        String motif = entree.get("motif") instanceof String s ? s.trim() : "";
        if (motif.length() < MOTIF_DEROGATION_MIN) {
            throw new EffectifsInvalideException("« motif » : une dérogation se justifie en " + MOTIF_DEROGATION_MIN + " caractères au moins");
        }
        String code = entree.get("codeAbsence") instanceof String s ? s.trim() : "";
        if (code.isEmpty()) {
            throw new EffectifsInvalideException("« codeAbsence » : la dérogation attribue un code d'absence");
        }
        return new Derogation(motif, code);
    }

    /** Un motif libre : texte non vide, élagué, borné ; nul sinon. */
    public static String motifLibre(Object v) {
        if (!(v instanceof String s) || s.isBlank()) {
            return null;
        }
        String t = s.trim();
        return t.length() > MOTIF_MAX ? t.substring(0, MOTIF_MAX) : t;
    }

    // ── Absences planifiées (P-GH-1, P-CH-8) ─────────────────────────────────

    /** Une absence est <strong>planifiée</strong> quand elle se déclare avant son premier jour ; ponctuelle (imprévue) sinon. */
    public static boolean estPlanifiee(int joursPreavis) {
        return joursPreavis > 0;
    }

    /** L'attribut {@code planifiable} du code : seul un {@code false} explicite ferme la déclaration d'avance. */
    public static boolean codePlanifiable(Object attribut) {
        return !Boolean.FALSE.equals(attribut);
    }

    /** Un code non planifiable se déclare le jour même ou après : le refus dit quoi faire, pas seulement non. */
    public static void verifierPlanifiable(String code, Object attribut, int joursPreavis, String du) {
        if (codePlanifiable(attribut) || !estPlanifiee(joursPreavis)) {
            return;
        }
        throw new EffectifsInvalideException("le code « " + code + " » ne se planifie pas d'avance : il se déclare à partir du premier jour d'absence ("
                + du + ", dans " + joursPreavis + " jour" + (joursPreavis > 1 ? "s" : "") + "). Choisissez un code planifiable, ou déclarez-la le jour venu");
    }

    /** Les durées qui ne se comblent pas quart par quart mais par un remplacement long terme (P-CH-8, E-CH-7). */
    public static boolean estDureeLongue(Object duree) {
        return Comportements.DureeAbsence.MOYEN_TERME.name().equals(duree) || Comportements.DureeAbsence.LONG_TERME.name().equals(duree);
    }

    /** L'attribut du code d'absence qui fixe, en jours, la durée à partir de laquelle l'absence ouvre un remplacement long terme. */
    public static final String SEUIL_LONG_TERME = "seuilJoursLongTerme";

    /**
     * L'absence relève-t-elle du remplacement long terme ? Avec un seuil sur le code (addenda 7, Q7 : « configurer, par code
     * d'absence, le seuil qui détermine le processus applicable »), sa durée en décide ; sans seuil, la catégorie du code,
     * comme avant — un code déjà paramétré ne change pas de comportement.
     */
    public static boolean estLongTerme(Map<String, Object> attributsDuCode, int joursAbsence) {
        if (attributsDuCode.get(SEUIL_LONG_TERME) instanceof Number seuil) {
            return joursAbsence >= seuil.intValue();
        }
        return estDureeLongue(attributsDuCode.get("duree"));
    }

    // ── Comblement des places libérées (E-GN-6, P-GN-1, P-GH-3) ──────────────

    /** Ce que le mécanisme a fait d'une place libérée. */
    public enum ResultatComblement { ATTRIBUEE, OFFERTE, SANS_CANDIDAT, REMPLACEMENT_LONG_TERME }

    /** La voie d'une place libérée, avant toute recherche. */
    public enum VoieComblement { RESERVER, ATTRIBUER, OFFRIR }

    /**
     * Une absence longue réserve la place au remplacement long terme, sauf si la période de la place est déjà en cours
     * (addenda 7, Q7) ; sinon le <strong>mode d'attribution du quart</strong> décide — le paramétrage de l'équipage, pas
     * l'acteur, choisit entre attribuer d'office et offrir.
     */
    public static VoieComblement voieDeComblement(boolean dureeLongue, boolean periodeEnCours, Planification.ModeAttribution mode) {
        if (dureeLongue && !periodeEnCours) {
            return VoieComblement.RESERVER;
        }
        return mode == Planification.ModeAttribution.AUTOMATISE ? VoieComblement.ATTRIBUER : VoieComblement.OFFRIR;
    }

    /** Le bilan d'un passage du mécanisme, compté depuis les résultats place par place. */
    public record BilanComblement(int attribuees, int offertes, int sansCandidat, int reservees) {

        public static BilanComblement de(List<ResultatComblement> resultats) {
            return new BilanComblement(compte(resultats, ResultatComblement.ATTRIBUEE), compte(resultats, ResultatComblement.OFFERTE),
                    compte(resultats, ResultatComblement.SANS_CANDIDAT), compte(resultats, ResultatComblement.REMPLACEMENT_LONG_TERME));
        }

        private static int compte(List<ResultatComblement> resultats, ResultatComblement r) {
            return (int) resultats.stream().filter(x -> x == r).count();
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("attribuees", attribuees);
            m.put("offertes", offertes);
            m.put("sansCandidat", sansCandidat);
            m.put("reservees", reservees);
            return m;
        }
    }
}
