package ca.vina.nexu.partage.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <strong>Le journal des erreurs rendues</strong> (ADR 0041). Une erreur traduite en réponse n'est
 * plus levée : sans cette ligne, la ligne {@code http} n'en garderait que le statut, et une
 * exception imprévue convertie en 400 perdrait sa pile d'appels.
 *
 * <p>On journalise le <em>type</em> de l'erreur, jamais son message : les motifs de refus sont des
 * phrases pour l'utilisateur qui nomment des personnes (« ce n'est pas encore le tour de Julie »),
 * et le journal garde des faits et des identifiants, pas des contenus (Masquage, Loi 25).
 */
public final class JournalErreurs {

    static final String EVENEMENT_REFUS = "refus";
    static final String EVENEMENT_ERREUR = "erreur";
    private static final Logger JOURNAL = LoggerFactory.getLogger(JournalErreurs.class);

    private JournalErreurs() {
    }

    /** Un refus prévu par le métier (introuvable, hors périmètre, règle…) : un fait, sans pile. */
    public static void refus(int statut, RuntimeException e) {
        JOURNAL.atInfo().addKeyValue("evenement", EVENEMENT_REFUS).addKeyValue("statut", statut).addKeyValue("type", e.getClass().getSimpleName())
                .log("refus {} : {}", statut, e.getClass().getSimpleName());
    }

    /**
     * Une erreur qu'aucun type du métier ne décrit, mais que le bord a tout de même traduite : la pile
     * accompagne la ligne, car c'est elle — non le statut rendu — qui dit où chercher.
     */
    public static void imprevue(int statut, RuntimeException e) {
        JOURNAL.atWarn().addKeyValue("evenement", EVENEMENT_ERREUR).addKeyValue("statut", statut).addKeyValue("type", e.getClass().getName())
                .setCause(e).log("erreur imprévue rendue en {} : {}", statut, e.getClass().getName());
    }
}
