package ca.vina.nexu.effectifs.domaine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <strong>Échanges de quarts</strong> — AO 26-015-DO, lot R6 (E-GR-7, E-GR-12) ; le pur de
 * {@code backend/src/effectifs/echanges/domaine.ts}.
 *
 * <p>Deux personnes s'échangent leurs places. L'échange se juge <strong>dans les deux sens</strong> : ce que
 * la destinataire reçoit (son repos, sa semaine, la contiguïté, ses heures sur 24 h) et ce que la demandeuse reçoit en retour — un
 * seul sens, c'était l'écart relevé. La politique entre périodes (autorisé, restreint, interdit) n'est pas un
 * réglage à part : c'est une règle ECHANGE sur le fait {@code memePeriode}, datée et portée par l'organisme,
 * l'unité ou la convention (décision D-9). Aucune E/S, aucune horloge.
 */
public final class EchangeEffectifs {

    /** Sans quart voisin, le repos est tenu pour illimité : une règle de repos minimal ne se déclenche pas. */
    public static final double REPOS_SANS_VOISIN = 999;
    public static final int MOTIF_MAX = 500;
    /** Le type d'objet sous lequel un échange est jugé et journalisé. */
    public static final String OBJET = "echange";

    private EchangeEffectifs() {
    }

    /** Le cycle d'un échange ; le libellé est ce que lit la personne dans un refus. */
    public enum Statut {
        PROPOSE("proposé"), A_VALIDER("à valider"), APPLIQUE("appliqué"), REFUSE("refusé"), REJETE("rejeté"), ANNULE("annulé");

        private final String libelle;

        Statut(String libelle) {
            this.libelle = libelle;
        }

        public String libelle() {
            return libelle;
        }

        /** Un échange ouvert retient ses deux places : aucun autre échange ne peut les viser. */
        public boolean estOuvert() {
            return this == PROPOSE || this == A_VALIDER;
        }
    }

    /**
     * Le geste, les états où il se fait, l'état où il mène. « ACCEPTER » mène à APPLIQUE, ou à A_VALIDER quand
     * les règles exigent une approbation : voir {@link #issueDeLAcceptation}.
     */
    public enum Geste {
        ACCEPTER(Set.of(Statut.PROPOSE), Statut.APPLIQUE),
        REFUSER(Set.of(Statut.PROPOSE), Statut.REFUSE),
        VALIDER(Set.of(Statut.A_VALIDER), Statut.APPLIQUE),
        REJETER(Set.of(Statut.A_VALIDER), Statut.REJETE),
        ANNULER(Set.of(Statut.PROPOSE, Statut.A_VALIDER), Statut.ANNULE);

        private final Set<Statut> depuis;
        private final Statut vers;

        Geste(Set<Statut> depuis, Statut vers) {
            this.depuis = depuis;
            this.vers = vers;
        }

        public static Geste de(Object v) {
            return Arrays.stream(values()).filter(g -> g.name().equals(v)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« geste » : " + Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "))));
        }

        public String minuscule() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Accepter et valider appliquent l'échange : l'horaire a pu bouger, il se rejuge. */
        public boolean rejuge() {
            return this == ACCEPTER || this == VALIDER;
        }
    }

    public static Statut transition(Statut statut, Geste geste) {
        if (!geste.depuis.contains(statut)) {
            throw new EffectifsInvalideException("un échange " + statut.libelle() + " ne se prête plus à ce geste (" + geste.minuscule() + ")");
        }
        return geste.vers;
    }

    /** Accepter n'a de sens que sur une proposition ; l'issue dépend du verdict combiné des deux sens. */
    public static Statut issueDeLAcceptation(Statut statut, ActionRegle action) {
        if (statut != Statut.PROPOSE) {
            throw new EffectifsInvalideException("un échange " + statut.libelle() + " ne s'accepte plus");
        }
        return action == ActionRegle.EXIGER_APPROBATION ? Statut.A_VALIDER : Statut.APPLIQUE;
    }

    // ── Jugement dans les deux sens ─────────────────────────────────────────

    /** Les faits d'un sens de l'échange, dans l'ordre du catalogue du contexte ECHANGE. */
    public record Faits(double reposHeures, double heuresSemaine, boolean contigu, boolean memePeriode, double heures24h) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reposHeures", reposHeures);
            m.put("heuresSemaine", heuresSemaine);
            m.put("contigu", contigu);
            m.put("memePeriode", memePeriode);
            m.put("heures24h", heures24h);
            return m;
        }
    }

    /**
     * Les faits d'un sens : la personne cède une place et reçoit {@code recu}. Son horaire, sans la place cédée,
     * ne doit pas chevaucher ce qu'elle reçoit — sinon l'échange est impossible, pas seulement déconseillé.
     */
    public static Faits faitsDUnSens(List<Planification.Creneau> horaire, Planification.Creneau recu, boolean memePeriode) {
        if (Planification.chevaucheUnQuart(horaire, recu)) {
            throw new EffectifsInvalideException("le quart du " + recu.date() + " chevaucherait un autre quart de la même personne");
        }
        Optional<Double> repos = Planification.reposHeures(horaire, recu);
        return new Faits(repos.orElse(REPOS_SANS_VOISIN), Planification.heuresSemaine(horaire, recu), repos.filter(r -> r == 0).isPresent(), memePeriode,
                Planification.heuresSur24h(horaire, recu));
    }

    /** Un sens jugé : la personne qui reçoit, et le verdict des règles pour elle. */
    public record Sens(String nom, Regles.Verdict verdict) { }

    /** Le verdict des deux sens : l'action la plus stricte, et des motifs qui nomment chacun leur personne. */
    public record VerdictCombine(ActionRegle action, List<Regles.Motif> motifs) {
        public VerdictCombine {
            motifs = List.copyOf(motifs);
        }

        public boolean refuse() {
            return EchangeEffectifs.refuse(action);
        }

        public String message() {
            return motifs.stream().map(Regles.Motif::message).collect(Collectors.joining(" ; "));
        }

        /** La forme qu'emporte un refus : les versions restent tracées par chaque évaluation, pas ici. */
        public Regles.Verdict enVerdict() {
            return new Regles.Verdict(action, motifs, List.of());
        }
    }

    /**
     * L'action la plus restrictive l'emporte, et chaque motif dit <strong>à qui</strong> il s'applique — « pour
     * Marc : repos de 4 h » se corrige, « repos de 4 h » non.
     */
    public static VerdictCombine combiner(List<Sens> sens) {
        ActionRegle action = sens.stream().map(s -> s.verdict().action()).reduce(ActionRegle.ACCEPTER, ActionRegle::laPlusRestrictive);
        List<Regles.Motif> motifs = new ArrayList<>();
        for (Sens s : sens) {
            s.verdict().motifs().forEach(m -> motifs.add(new Regles.Motif(m.regleId(), m.version(), m.cle(), m.action(), "Pour " + s.nom() + " : " + m.message())));
        }
        return new VerdictCombine(action, motifs);
    }

    public static boolean refuse(ActionRegle action) {
        return Regles.estBloquant(action);
    }

    /** Le motif d'une décision : facultatif, rogné, borné. */
    public static String motifDecision(Object v) {
        String m = v instanceof String s ? s.trim() : "";
        if (m.isEmpty()) {
            return null;
        }
        return m.length() > MOTIF_MAX ? m.substring(0, MOTIF_MAX) : m;
    }

    // ── Ce qu'un échange connaît de ses deux places ─────────────────────────

    /** Une place avec son quart et l'état de sa période. */
    public record PlaceDetaillee(String id, String ressourceId, String corpsEmploiCode, String date, int debut, int fin, String periodeId,
                                 Planification.EtatPeriode periodeEtat, String uniteCode) {

        public boolean estTenue() {
            return ressourceId != null;
        }

        public Planification.Creneau creneau() {
            return new Planification.Creneau(date, debut, fin);
        }
    }

    public record Quart(String date, int debut, int fin) { }

    /** Un échange tel qu'il se lit, avec le nom des deux personnes et les deux quarts. */
    public record Fiche(String id, String placeCedeeId, String placeVoulueId, String ressourceAId, String ressourceBId, String uniteA, String uniteB,
                        Statut statut, ActionRegle actionRegles, List<Object> motifsRegles, String proposeLe, String proposePar, String decideLe,
                        String decidePar, String motifDecision, String nomA, String nomB, Quart quartCede, Quart quartVoulu) {

        /**
         * Visible des deux personnes et de l'encadrement de l'une des deux unités.
         *
         * @param moi      les ressources reliées au compte de l'acteur
         * @param visibles les unités qu'il lit
         */
        public boolean estVisiblePar(Set<String> moi, Set<String> visibles) {
            return moi.contains(ressourceAId) || moi.contains(ressourceBId) || visibles.contains(uniteA) || visibles.contains(uniteB);
        }
    }

    /** Un quart publié à venir, tenu par quelqu'un : ce qui s'offre ou se demande en échange. */
    /**
     * Un quart publié, tel qu'on le montre : le <b>site</b> en fait partie.
     *
     * {@code milieu} est nul pour un quart sans milieu rattaché — un ad hoc posé sans site.
     * L'écran écrit alors « — », jamais un site deviné : se présenter au mauvais garage n'est
     * pas une erreur rattrapable dans la journée.
     */
    public record QuartEchangeable(String placeId, String date, int debut, int fin, String uniteCode, String corpsEmploiCode,
                                   String ressourceId, String nom, String milieuId, String milieu) { }
}
