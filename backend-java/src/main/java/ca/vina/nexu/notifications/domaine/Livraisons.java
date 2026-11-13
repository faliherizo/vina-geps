package ca.vina.nexu.notifications.domaine;

import ca.vina.nexu.partage.erreurs.domaine.VerrouException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <strong>Le journal des livraisons d'avis</strong> (maquette P69) — jumeau de
 * {@code backend/src/notifications/livraisons.ts}. Il dit à l'administration ce qui est parti, ce
 * qui a échoué, et pourquoi, <i>sans</i> montrer les coordonnées de quiconque : une adresse ou un
 * numéro cité dans un message d'erreur de fournisseur est masqué avant de quitter le serveur.
 */
public final class Livraisons {

    private static final Pattern COURRIEL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern TELEPHONE = Pattern.compile("(?:\\+?1[\\s.-]?)?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}");
    private static final String ADRESSE_MASQUEE = "(adresse masquée)";
    private static final String NUMERO_MASQUE = "(numéro masqué)";
    public static final int LIMITE_PAR_DEFAUT = 200;
    public static final int LIMITE_MAX = 1000;

    private Livraisons() {
    }

    /** Les états d'un envoi, et le mot qu'on en montre. */
    public enum Etat {
        QUEUED("queued", "en attente"), SENT("sent", "parti"), FAILED("failed", "échoué"),
        DESACTIVE("desactive", "canal fermé par la personne");

        private final String code;
        private final String libelle;

        Etat(String code, String libelle) {
            this.code = code;
            this.libelle = libelle;
        }

        public String code() {
            return code;
        }

        /** Un état inconnu se montre tel quel, comme {@code ETATS_LIVRAISON[x] ?? x}. */
        public static String libelleDe(String code) {
            for (Etat e : values()) {
                if (e.code.equals(code)) {
                    return e.libelle;
                }
            }
            return code;
        }
    }

    public record Envoi(String id, String evenement, String canal, String typeDestinataire, String destinataire, String sujet,
                        String corps, String etat, String erreur, int tentative, String creeLe, String envoyeLe) {
    }

    public record Ligne(String id, String evenement, String canal, String destinataire, String sujet, String etat,
                        String libelleEtat, String erreur, int tentative, String creeLe, String envoyeLe) {
    }

    public record Filtre(String evenement, String canal, String etat, String destinataire, Integer limite) {
    }

    public record Resume(int total, Map<String, Integer> parEtat) {
    }

    public static String expurger(String erreur) {
        if (erreur == null || erreur.isEmpty()) {
            return null;
        }
        return TELEPHONE.matcher(COURRIEL.matcher(erreur).replaceAll(ADRESSE_MASQUEE)).replaceAll(NUMERO_MASQUE);
    }

    public static Ligne ligne(Envoi e) {
        return new Ligne(e.id(), e.evenement(), e.canal(), e.destinataire(), e.sujet(), e.etat(), Etat.libelleDe(e.etat()),
                expurger(e.erreur()), e.tentative(), e.creeLe(), e.envoyeLe());
    }

    /** {@code Math.min(Math.max(Number(limite) || 200, 1), 1000)}. */
    public static int limite(Integer demandee) {
        int n = demandee == null || demandee == 0 ? LIMITE_PAR_DEFAUT : demandee;
        return Math.min(Math.max(n, 1), LIMITE_MAX);
    }

    public static Resume resumer(List<Ligne> lignes) {
        Map<String, Integer> parEtat = new LinkedHashMap<>();
        lignes.forEach(l -> parEtat.merge(l.libelleEtat(), 1, Integer::sum));
        return new Resume(lignes.size(), parEtat);
    }

    /**
     * <strong>Seul un échec se relance.</strong> Relancer un envoi parti le ferait arriver deux
     * fois ; un canal fermé par la personne, passer outre son choix ; un envoi en attente, doubler
     * ce qui n'a pas encore abouti.
     */
    public static void verifierRelance(String etat) {
        if (Etat.FAILED.code.equals(etat)) {
            return;
        }
        if (Etat.SENT.code.equals(etat)) {
            throw new VerrouException("cet avis est déjà parti : le relancer le ferait arriver deux fois");
        }
        if (Etat.DESACTIVE.code.equals(etat)) {
            throw new VerrouException("la personne a fermé ce canal : relancer passerait outre son choix");
        }
        throw new VerrouException("cet avis n'est pas encore traité : attendre son issue");
    }
}
