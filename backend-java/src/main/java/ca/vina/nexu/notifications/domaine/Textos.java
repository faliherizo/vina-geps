package ca.vina.nexu.notifications.domaine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Port SMS — port de {@code notifications/sms.ts} : un texto part vers un numéro, et l'on sait
 * s'il est parti (S4, grille 25). Le simulateur est le défaut, y compris en production, tant
 * qu'aucun fournisseur n'est configuré : un texto réel envoyé par accident pendant un essai serait
 * pire qu'un texto simulé. Le port garde le suivi : un texto non livré à un suppléant est une
 * recherche qui tourne à vide.
 */
public final class Textos {

    private Textos() {
    }

    public static final String ENVOYE = "envoye";
    public static final String LIVRE = "livre";
    public static final String ECHOUE = "echoue";
    /** Un texto de plus de 160 caractères se fragmente ; on le dit plutôt que de le découvrir sur la facture. */
    public static final int LONGUEUR_MAX = 480;
    public static final String EXPEDITEUR_PAR_DEFAUT = "NexuVINA";

    public record Envoi(String id, String statut, String emisLe) {
    }

    public interface PorteurSms {
        /** {@code expediteur} est ce que le destinataire voit : omis, celui de la plateforme ; fourni, le numéro du client (ADR 0022). */
        Envoi envoyer(String numero, String texte, String expediteur);

        String statut(String envoiId);
    }

    /**
     * Un numéro nord-américain à dix chiffres, avec ou sans +1, espaces, tirets ou parenthèses ; ou
     * un numéro international E.164 (« + » puis 8 à 15 chiffres, le premier non nul).
     */
    public static String normaliserNumero(String brut) {
        String texte = (brut == null ? "" : brut).trim();
        String chiffres = texte.replaceAll("\\D", "");
        if (texte.startsWith("+")) {
            return chiffres.matches("[1-9]\\d{7,14}") ? "+" + chiffres : null;
        }
        if (chiffres.length() == 10) {
            return "+1" + chiffres;
        }
        if (chiffres.length() == 11 && chiffres.startsWith("1")) {
            return "+" + chiffres;
        }
        return null;
    }

    public static List<String> validerTexte(String texte) {
        List<String> erreurs = new ArrayList<>();
        if (texte == null || texte.trim().isEmpty()) {
            erreurs.add("texte requis");
        }
        if (texte != null && texte.length() > LONGUEUR_MAX) {
            erreurs.add("texte trop long (" + texte.length() + " > " + LONGUEUR_MAX + ")");
        }
        return erreurs;
    }

    /** Le numéro et le texte, vérifiés ensemble : le numéro d'abord, comme le Node. */
    public static String exigerNumeroEtTexte(String numero, String texte) {
        String n = normaliserNumero(numero);
        if (n == null) {
            throw new IllegalArgumentException("numéro invalide : « " + numero + " »");
        }
        List<String> erreurs = validerTexte(texte);
        if (!erreurs.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", erreurs));
        }
        return n;
    }

    private static final Set<String> LIVRES = Set.of("delivered", "livre", "read");
    private static final Set<String> ECHOUES = Set.of("failed", "undelivered", "rejected", "echoue", "error");

    /** Les statuts des fournisseurs varient ; on les ramène aux trois qui comptent pour nous. */
    public static String traduireStatut(Object brut) {
        String s = String.valueOf(brut == null ? "" : brut).toLowerCase(Locale.ROOT);
        if (LIVRES.contains(s)) {
            return LIVRE;
        }
        return ECHOUES.contains(s) ? ECHOUE : ENVOYE;
    }

    /** Les statuts Telnyx qui ne sont pas dans le vocabulaire commun : les deux échecs finaux. */
    public static String traduireStatutTelnyx(Object brut) {
        String s = String.valueOf(brut == null ? "" : brut).toLowerCase(Locale.ROOT);
        return s.equals("delivery_failed") || s.equals("sending_failed") ? ECHOUE : traduireStatut(s);
    }

    /** Le simulateur retient ce qui part ; les numéros « injoignables » rejouent l'échec d'un fournisseur réel. */
    public static final class SmsSimule implements PorteurSms {
        public record Trace(String id, String numero, String texte, String statut, String expediteur) {
        }

        private final List<String> injoignables;
        private final Supplier<String> horloge;
        private final String expediteurParDefaut;
        private final List<Trace> envois = new ArrayList<>();

        public SmsSimule(List<String> injoignables, Supplier<String> horloge, String expediteurParDefaut) {
            this.injoignables = List.copyOf(injoignables);
            this.horloge = horloge;
            this.expediteurParDefaut = expediteurParDefaut;
        }

        @Override
        public synchronized Envoi envoyer(String numero, String texte, String expediteur) {
            String n = exigerNumeroEtTexte(numero, texte);
            String statut = injoignables.contains(n) ? ECHOUE : ENVOYE;
            String id = "sim-" + (envois.size() + 1);
            envois.add(new Trace(id, n, texte, statut, expediteur == null ? expediteurParDefaut : expediteur));
            return new Envoi(id, statut, horloge.get());
        }

        @Override
        public synchronized String statut(String envoiId) {
            return envois.stream().filter(e -> e.id().equals(envoiId)).map(Trace::statut).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("envoi introuvable"));
        }

        public synchronized List<Trace> envois() {
            return List.copyOf(envois);
        }
    }
}
