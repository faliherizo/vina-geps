package ca.vina.nexu.auth.domaine;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Predicate;

/**
 * <strong>L'historique des mots de passe</strong> (FX-AO-20260810, ST-G10, critère 86) : les 10 dernières empreintes
 * sont conservées, et un mot de passe employé dans les 6 derniers mois — le courant compris — ne se réemploie pas.
 * Seules les empreintes (BCrypt) sont gardées, jamais un mot de passe.
 */
public final class HistoriqueMotsDePasse {

    /** Combien d'empreintes on garde. */
    public static final int TAILLE = 10;
    /** Pendant combien de mois un mot de passe ne revient pas. */
    public static final int MOIS = 6;
    public static final String REFUS = "ce mot de passe a déjà été employé ces " + MOIS + " derniers mois : choisissez-en un autre";

    private HistoriqueMotsDePasse() {
    }

    /** Une empreinte, et l'instant où elle a cessé d'être le mot de passe courant (maintenant, pour le courant). */
    public record Empreinte(String empreinte, Instant retireeLe) { }

    /**
     * Le mot de passe proposé est-il un des {@value #TAILLE} derniers, retiré il y a moins de {@value #MOIS} mois ?
     * {@code correspond} compare le mot de passe en clair à une empreinte (BCrypt) : le domaine n'en connaît pas l'algorithme.
     */
    public static boolean reemploye(List<Empreinte> plusRecentesDAbord, Predicate<String> correspond, Instant maintenant) {
        Instant limite = maintenant.atOffset(ZoneOffset.UTC).minusMonths(MOIS).toInstant();
        return plusRecentesDAbord.stream().limit(TAILLE).filter(e -> e.retireeLe().isAfter(limite)).anyMatch(e -> correspond.test(e.empreinte()));
    }
}
