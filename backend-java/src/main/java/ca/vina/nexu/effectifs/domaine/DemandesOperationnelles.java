package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * <strong>Les demandes opérationnelles</strong> (AO 26-015-DO, addenda 8, M-08) : au-delà des absences, des disponibilités
 * et des échanges, l'inscription à la liste de temps supplémentaire, les événements, la formation, les remplacements et
 * les autres demandes. Le type vient du référentiel {@code types_demande} ; son comportement dit ce que la demande fait.
 * Une demande attend la décision de l'encadrement de l'unité ; un refus se motive. Pur : ni base ni horloge.
 */
public final class DemandesOperationnelles {

    /** Au-delà, des précisions ne sont plus des précisions. */
    public static final int PRECISIONS_MAX = 500;

    private DemandesOperationnelles() {
    }

    /** Le cycle d'une demande : elle attend, puis elle est acceptée, refusée ou annulée par son auteur. */
    public enum Statut { EN_ATTENTE, ACCEPTEE, REFUSEE, ANNULEE;

        /** Un statut reçu de la chaîne de requête ; inconnu, refusé en le nommant. */
        public static Statut de(String code) {
            return Arrays.stream(values()).filter(s -> s.name().equals(code)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« statut » : " + Arrays.toString(values())));
        }
    }

    /** La décision de l'encadrement. */
    public enum Decision { ACCEPTER, REFUSER;

        public static Decision de(Object code) {
            return Arrays.stream(values()).filter(d -> d.name().equals(code)).findFirst()
                    .orElseThrow(() -> new EffectifsInvalideException("« decision » : ACCEPTER ou REFUSER"));
        }
    }

    public record Demande(String id, String ressourceId, String uniteCode, String typeCode, String du, String au, String commentaire,
                          Statut statut, String creeLe, String decideePar, String decideeLe, String motifDecision) {

        /** La demande décidée : acceptée, ou refusée avec son motif ; seule une demande en attente se décide. */
        public Demande decidee(Decision d, String motif, String par, String le) {
            exigerEnAttente();
            String m = motif == null ? "" : motif.strip();
            if (d == Decision.REFUSER && m.isEmpty()) {
                throw new EffectifsInvalideException("« motif » : un refus se motive");
            }
            return new Demande(id, ressourceId, uniteCode, typeCode, du, au, commentaire, d == Decision.ACCEPTER ? Statut.ACCEPTEE : Statut.REFUSEE,
                    creeLe, par, le, m.isEmpty() ? null : m);
        }

        /** Annulée par son auteur, tant qu'elle attend. */
        public Demande annulee(String par, String le) {
            exigerEnAttente();
            return new Demande(id, ressourceId, uniteCode, typeCode, du, au, commentaire, Statut.ANNULEE, creeLe, par, le, null);
        }

        public boolean couvre(String date) {
            return du.compareTo(date) <= 0 && au.compareTo(date) >= 0;
        }

        private void exigerEnAttente() {
            if (statut != Statut.EN_ATTENTE) {
                throw new ConflitException("la demande est déjà " + statut.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
            }
        }
    }

    /** Une saisie : dates valides et ordonnées, précisions bornées. */
    public record Saisie(String typeCode, String du, String au, String commentaire) { }

    public static Saisie saisie(Object typeCode, Object du, Object au, Object commentaire) {
        String type = typeCode instanceof String s && !s.isBlank() ? s.strip() : null;
        if (type == null) {
            throw new EffectifsInvalideException("« typeCode » requis");
        }
        String debut = Referentiels.dateValide(du, "du");
        String fin = Referentiels.dateValide(au, "au");
        if (fin.compareTo(debut) < 0) {
            throw new EffectifsInvalideException("« au » (" + fin + ") précède « du » (" + debut + ")");
        }
        String c = commentaire == null ? null : String.valueOf(commentaire).strip();
        if (c != null && c.length() > PRECISIONS_MAX) {
            throw new EffectifsInvalideException("« commentaire » : au plus " + PRECISIONS_MAX + " caractères");
        }
        return new Saisie(type, debut, fin, c == null || c.isEmpty() ? null : c);
    }

    /** Une personne inscrite au temps supplémentaire un jour donné. */
    public record Inscrit(String ressourceId, String ressourceNom, String uniteCode, double anciennete, String du, String au) { }

    /** La liste de temps supplémentaire : par ancienneté décroissante, puis par nom. */
    public static List<Inscrit> listeTempsSupplementaire(List<Inscrit> inscrits) {
        return inscrits.stream().sorted(Comparator.comparingDouble(Inscrit::anciennete).reversed().thenComparing(Inscrit::ressourceNom)).toList();
    }
}
