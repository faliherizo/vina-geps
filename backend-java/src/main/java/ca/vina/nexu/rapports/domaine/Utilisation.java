package ca.vina.nexu.rapports.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * <b>Les listes d'utilisation</b> (BF-130) — jumeau de {@code rapports/utilisation.ts}, sans base ni horloge.
 *
 * <p>« Inactif » et « sans activité récente » ne sont pas la même chose : un compte <b>inactif</b> a
 * été fermé par quelqu'un ; un compte <b>sans activité récente</b> est ouvert, payé, et personne ne
 * s'en sert — c'est celui qu'on cherche avant de renouveler une licence. Le seuil (90 jours par
 * défaut, un trimestre) est une donnée que l'établissement règle.
 */
public final class Utilisation {

    private Utilisation() {
    }

    public enum EtatCompte {
        INVITE("invite", "invité, n'a pas encore ouvert son compte"),
        ACTIF("actif", "actif sur la période"),
        SANS_ACTIVITE_RECENTE("sans_activite_recente", "compte ouvert, sans activité récente"),
        INACTIF("inactif", "compte fermé");

        private final String code;
        private final String libelle;

        EtatCompte(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        public String libelle() {
            return libelle;
        }
    }

    /** Un trimestre : assez long pour couvrir un congé, assez court pour que la liste serve. */
    public static final int SEUIL_INACTIVITE_JOURS = 90;
    public static final int SEUIL_MIN = 1;
    public static final int SEUIL_MAX = 730;
    private static final double CENT = 100;

    /** {@code derniereActiviteLe} nul : <b>jamais rien fait</b>. */
    public record CompteObserve(String identifiant, String role, boolean actif, boolean invitationEnCours, String derniereActiviteLe) {
    }

    public record CompteListe(String identifiant, String role, String derniereActiviteLe) {
    }

    public record Repartition(String etat, String libelle, int nombre, List<CompteListe> comptes) {
    }

    /** {@code tauxComblement} nul quand aucun quart n'est publié : un dénominateur nul rend « indéterminé » (ADR 0052). */
    public record UsageDesQuarts(long publies, long combles, long aCombler, Double tauxComblement) {
    }

    /**
     * L'état d'un compte à un instant : l'ordre des règles est le sens du classement. Un compte fermé
     * est fermé, même sans activité ; une invitation en cours précède tout le reste.
     */
    public static EtatCompte etatDuCompte(CompteObserve c, String maintenant, int seuilJours) {
        if (c.invitationEnCours()) {
            return EtatCompte.INVITE;
        }
        if (!c.actif()) {
            return EtatCompte.INACTIF;
        }
        if (c.derniereActiviteLe() == null) {
            return EtatCompte.SANS_ACTIVITE_RECENTE;
        }
        Duration ecart = Duration.between(Instant.parse(c.derniereActiviteLe()), Instant.parse(maintenant));
        return ecart.compareTo(Duration.ofDays(seuilJours)) > 0 ? EtatCompte.SANS_ACTIVITE_RECENTE : EtatCompte.ACTIF;
    }

    public static int validerSeuil(Object brut) {
        if (brut == null || String.valueOf(brut).isEmpty()) {
            return SEUIL_INACTIVITE_JOURS;
        }
        double n;
        try {
            n = Double.parseDouble(String.valueOf(brut));
        } catch (NumberFormatException e) {
            n = Double.NaN;
        }
        if (n != Math.rint(n) || n < SEUIL_MIN || n > SEUIL_MAX) {
            throw new CatalogueRapports.RapportInvalideException(
                    "le seuil d'inactivité attend un nombre de jours entre " + SEUIL_MIN + " et " + SEUIL_MAX);
        }
        return (int) n;
    }

    /** Range les comptes par état, <b>tous les états présents</b> même vides : zéro se lit « personne ». */
    public static List<Repartition> repartir(List<CompteObserve> comptes, String maintenant, int seuilJours) {
        return Arrays.stream(EtatCompte.values()).map(etat -> {
            List<CompteListe> retenus = comptes.stream().filter(c -> etatDuCompte(c, maintenant, seuilJours) == etat)
                    .map(c -> new CompteListe(c.identifiant(), c.role(), c.derniereActiviteLe()))
                    .sorted(Comparator.comparing(CompteListe::identifiant)).toList();
            return new Repartition(etat.code(), etat.libelle(), retenus.size(), retenus);
        }).toList();
    }

    /** Le comblement des quarts <b>publiés</b> : un brouillon n'engage personne. */
    public static UsageDesQuarts usageDesQuarts(long publies, long combles) {
        return new UsageDesQuarts(publies, combles, Math.max(0, publies - combles),
                publies == 0 ? null : Math.round(CENT * combles / publies * CENT) / CENT);
    }
}
