package ca.vina.nexu.approbations.domaine;

import java.util.Optional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Le moteur d'approbation générique — port de {@code approvals/engine.ts} et des décisions de
 * {@code approvals/service.ts} : règles par niveau, sélection des étapes, tolérance de relance,
 * délégation datée, autorisation à décider et **séparation des tâches** (qui soumet n'approuve
 * pas ; un même approbateur ne décide qu'une étape).
 */
public final class Approbations {

    public static final Set<String> OPERATEURS = Set.of("gte", "lte", "eq", "neq");
    public static final double TOLERANCE_PAR_DEFAUT = 0.1;
    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private Approbations() {
    }

    /**
     * Le catalogue des types de document de l'organisme : une règle ne vise qu'un type qui y figure,
     * actif. Sans ce contrôle, une règle visant un type que rien n'émet serait enregistrée muette.
     */
    public interface CatalogueTypes {
        boolean actif(String tenantId, String code);
    }

    /** Port : règles, demandes, étapes et délégations d'approbation ({@code approvals/service.ts}). */
    public interface Depot {
        List<Regle> regles(String tenantId, String documentType);

        Map<String, Object> creerRegle(String tenantId, Regle r, Object valeurBrute);

        Optional<Map<String, Object>> demande(String tenantId, String id);

        List<Map<String, Object>> demandes(String tenantId, String statut);

        Map<String, Object> creerDemande(String tenantId, String documentType, String documentId, Map<String, Object> contexte, String demandeur);

        Map<String, Object> ajouterApprobateur(String tenantId, String demandeId, String approbateurId);

        Optional<Map<String, Object>> demandeDuDocument(String tenantId, String documentType, String documentId);

        Map<String, Object> relancer(String tenantId, String documentType, String documentId, double ancien, double nouveau, Double tolerance);

        Map<String, Object> deleguer(String tenantId, Delegation d);

        List<Map<String, Object>> delegations(String tenantId, String deUserId);

        /** La délégation en vigueur aujourd'hui d'un approbateur, la plus récente d'abord ; vide s'il est présent. */
        Optional<Delegation> delegationActive(String tenantId, String deUserId);

        /** Les délégations en vigueur aujourd'hui que ce compte a reçues : il approuve pour le compte de leurs auteurs. */
        List<Delegation> delegationsRecues(String tenantId, String versUserId);

        /** Met fin à une délégation de son auteur (retour d'absence) ; faux si elle n'existe pas ou n'est pas la sienne. */
        boolean revoquer(String tenantId, String id, String deUserId);

        Map<String, Object> decider(String tenantId, String etapeId, String decision, String acteurId, String role);

        /** Une demande dont l'appelant peut imposer les règles (un flux par défaut que personne n'a écrit en règle). */
        Map<String, Object> creerDemande(String tenantId, NouvelleDemande d);

        /** Retire une règle ; les demandes déjà créées gardent leurs étapes : elles ont été jugées avec elle. */
        void retirerRegle(String tenantId, String id);

        /** Décide une étape, avec la voie d'autorisation supplémentaire du décideur. */
        Map<String, Object> decider(String tenantId, String etapeId, String decision, Decideur decideur);
    }

    /** Une demande à créer ; {@code regles} nulles : celles du type de document. */
    public record NouvelleDemande(String documentType, String documentId, Map<String, Object> contexte, String demandeur, List<Regle> regles) {
    }

    /** Ce qu'une étape désigne : un rôle ou une personne. */
    public record Designation(String approverRole, String approverId) {
    }

    /**
     * Qui décide. {@code porte} est une désignation que le module appelant sait juger et que ce moteur ne connaît pas — le
     * niveau de périmètre d'une unité d'effectifs, par exemple : elle <strong>s'ajoute</strong> aux voies d'autorisation
     * (nommé, délégataire, rôle, administrateur) sans en retirer aucune, et la séparation des tâches reste appliquée.
     */
    public record Decideur(String acteurId, String role, Predicate<Designation> porte) {
        public static Decideur de(String acteurId, String role) {
            return new Decideur(acteurId, role, d -> false);
        }
    }

    public record Regle(String id, String documentType, String field, String operator, String value, int level, String approverRole, String approverId) {
        /** La forme du Node : les clés absentes ({@code undefined}) ne s'écrivent pas. */
        public Map<String, Object> vue() {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", id);
            m.put("documentType", documentType);
            m.put("field", field);
            m.put("operator", operator);
            m.put("value", value);
            m.put("level", level);
            if (approverRole != null) {
                m.put("approverRole", approverRole);
            }
            if (approverId != null) {
                m.put("approverId", approverId);
            }
            return m;
        }
    }

    public record Etape(int level, String approverRole, String approverId, String ruleId) {
    }

    /** Valide ce qu'une règle reçoit — tous les motifs, dans l'ordre du Node. */
    public static Regle validerRegle(String documentType, String field, String operator, Object value, Integer level, String approverRole, String approverId) {
        return validerRegle(new Saisie(documentType, field, operator, value, level, approverRole, approverId), code -> true);
    }

    /** Ce qu'une règle reçoit, avant validation. */
    public record Saisie(String documentType, String field, String operator, Object value, Integer level, String approverRole, String approverId) {
    }

    /**
     * Valide une règle, type de document compris : l'erreur de catalogue <em>s'ajoute</em> aux autres,
     * pour qu'un formulaire à trois manques se corrige en un aller-retour.
     */
    public static Regle validerRegle(Saisie e, java.util.function.Predicate<String> typeActif) {
        String documentType = e.documentType();
        String field = e.field();
        String operator = e.operator();
        Integer level = e.level();
        String approverRole = e.approverRole();
        String approverId = e.approverId();
        List<String> erreurs = new ArrayList<>();
        if (documentType == null || documentType.trim().isEmpty()) {
            erreurs.add("documentType required");
        } else if (!typeActif.test(documentType.trim())) {
            erreurs.add("unknown or inactive documentType \"" + documentType.trim() + "\" (see the document type catalogue)");
        }
        if (field == null || field.trim().isEmpty()) {
            erreurs.add("field required");
        }
        if (operator == null || !OPERATEURS.contains(operator)) {
            erreurs.add("invalid operator (gte|lte|eq|neq)");
        }
        if (level == null || level < 1) {
            erreurs.add("level must be a positive integer");
        }
        boolean roleVide = approverRole == null || approverRole.trim().isEmpty();
        boolean idVide = approverId == null || approverId.trim().isEmpty();
        if (roleVide && idVide) {
            erreurs.add("approverRole or approverId required");
        }
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        return new Regle(null, documentType.trim(), field.trim(), operator, String.valueOf(e.value()), level, roleVide ? null : approverRole.trim(), idVide ? null : approverId.trim());
    }

    /** Une règle s'applique-t-elle au contexte ? Les comparaisons numériques suivent {@code Number()} de JavaScript. */
    public static boolean correspond(Regle regle, Map<String, Object> contexte) {
        Object actuel = contexte.get(regle.field());
        if (actuel == null) {
            return false;
        }
        return switch (regle.operator()) {
            case "gte" -> nombre(actuel) >= nombre(regle.value());
            case "lte" -> nombre(actuel) <= nombre(regle.value());
            case "eq" -> String.valueOf(actuel).equals(regle.value());
            case "neq" -> !String.valueOf(actuel).equals(regle.value());
            default -> false;
        };
    }

    static double nombre(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    /** Les étapes d'un contexte, triées par niveau ; une seule règle par niveau — la première qui correspond. */
    public static List<Etape> selectionner(List<Regle> regles, Map<String, Object> contexte) {
        Map<Integer, Etape> parNiveau = new LinkedHashMap<>();
        for (Regle r : regles.stream().sorted(Comparator.comparingInt(Regle::level)).toList()) {
            if (!correspond(r, contexte) || parNiveau.containsKey(r.level())) {
                continue;
            }
            parNiveau.put(r.level(), new Etape(r.level(), r.approverRole(), r.approverId(), r.id()));
        }
        return parNiveau.values().stream().sorted(Comparator.comparingInt(Etape::level)).toList();
    }

    /** Une variation de montant au-delà de la tolérance (fraction) relance le flux ; de zéro, toute variation. */
    public static boolean doitRelancer(double ancien, double nouveau, double tolerance) {
        if (ancien == 0) {
            return nouveau != 0;
        }
        return Math.abs(nouveau - ancien) / Math.abs(ancien) > tolerance;
    }

    public record Delegation(String fromUserId, String toUserId, String fromDate, String toDate) {
    }

    public static Delegation validerDelegation(String de, String vers, String du, String au) {
        if (de == null || de.isEmpty() || vers == null || vers.isEmpty()) {
            throw new IllegalArgumentException("fromUserId and toUserId required");
        }
        if (de.equals(vers)) {
            throw new IllegalArgumentException("cannot delegate to oneself");
        }
        if (du != null && !DATE.matcher(du).matches()) {
            throw new IllegalArgumentException("fromDate must be YYYY-MM-DD");
        }
        if (au != null && !DATE.matcher(au).matches()) {
            throw new IllegalArgumentException("toDate must be YYYY-MM-DD");
        }
        if (du != null && au != null && au.compareTo(du) < 0) {
            throw new IllegalArgumentException("toDate must be ≥ fromDate");
        }
        return new Delegation(de, vers, du, au);
    }

    /** Une délégation vaut à la date {@code aLaDate} si sa période, quand elle existe, la contient. */
    public static boolean delegationActive(Delegation d, String aLaDate) {
        boolean apresDebut = d.fromDate() == null || aLaDate.compareTo(d.fromDate()) >= 0;
        boolean avantFin = d.toDate() == null || aLaDate.compareTo(d.toDate()) <= 0;
        return apresDebut && avantFin;
    }

    /** Le courriel au délégataire, mot pour mot celui du Node. */
    public static String texteDelegation(String du, String au) {
        String periode = du != null || au != null ? " pour la période " + (du == null ? "…" : du) + " → " + (au == null ? "…" : au) : "";
        return "Une délégation de validation vous a été confiée" + periode + ". Vous pouvez approuver les demandes au nom du délégant.";
    }

    /** {@code porteeParLActeur} : la voie d'autorisation du module appelant ({@link Decideur#porte()}), jugée avant. */
    public record EtapeADecider(String id, String approverRole, String approverId, String status, boolean porteeParLActeur) {
        public EtapeADecider(String id, String approverRole, String approverId, String status) {
            this(id, approverRole, approverId, status, false);
        }
    }

    public record EtapeDecidee(String id, String status, String decidedBy) {
    }

    /** Ce que la décision d'une étape produit : qui signe, et pour le compte de qui. */
    public record Signature(String onBehalfOf) {
    }

    /**
     * L'approbateur désigné, son délégataire actif, un porteur du rôle ou un admin peut décider ;
     * puis la séparation des tâches s'applique à tous, admin compris.
     */
    public static Signature autoriser(EtapeADecider etape, String acteurId, String role, String delegataireActif, String demandeur, List<EtapeDecidee> autresEtapes) {
        if (!"pending".equals(etape.status())) {
            throw new IllegalStateException("step already decided");
        }
        boolean admin = "admin".equals(role);
        boolean nomme = etape.approverId() != null && etape.approverId().equals(acteurId);
        boolean delegue = etape.approverId() != null && acteurId.equals(delegataireActif);
        boolean parRole = etape.approverRole() != null && etape.approverRole().equals(role);
        if (!admin && !nomme && !delegue && !parRole && !etape.porteeParLActeur()) {
            throw new NonAutoriseException(Refus.NON_AUTORISE, "not authorized to decide this step");
        }
        if (demandeur != null && demandeur.equals(acteurId)) {
            throw new NonAutoriseException(Refus.DEMANDEUR, "segregation of duties: the requester cannot approve their own request");
        }
        if (autresEtapes.stream().anyMatch(s -> !s.id().equals(etape.id()) && !"pending".equals(s.status()) && acteurId.equals(s.decidedBy()))) {
            throw new NonAutoriseException(Refus.PLUSIEURS_NIVEAUX, "segregation of duties: an approver cannot decide more than one level of the same request");
        }
        return new Signature(delegue && !nomme ? etape.approverId() : null);
    }

    /** Le statut de la demande après une décision : un rejet rejette ; toutes approuvées approuve ; sinon inchangé. */
    public static String statutApres(String decision, String statutActuel, List<String> statutsEtapes) {
        if ("rejected".equals(decision)) {
            return "rejected";
        }
        return statutsEtapes.stream().allMatch("approved"::equals) ? "approved" : statutActuel;
    }

    /** Pourquoi une décision est refusée : l'appelant qui traduit le refus dans ses mots les distingue par là. */
    public enum Refus { NON_AUTORISE, DEMANDEUR, PLUSIEURS_NIVEAUX }

    /** Un refus de droit sur une étape : le bord le rend 403, quel que soit le message (ségrégation des tâches comprise). */
    public static final class NonAutoriseException extends ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException {
        private final Refus refus;

        public NonAutoriseException(Refus refus, String message) {
            super(null, message);
            this.refus = refus;
        }

        public Refus refus() {
            return refus;
        }
    }
}
