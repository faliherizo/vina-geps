package ca.vina.nexu.alertes.domaine;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * <strong>La réponse par texto</strong> (ST-G05 ; critères 4, 9, 10) — la part pure. « 1 » ou « OK » vaut prise en
 * charge, « 2 » ou « NON » vaut refus ; tout autre mot reçoit l'aide et n'est <strong>jamais</strong> compté comme une
 * confirmation : une prise en charge devinée laisserait une alerte critique sans personne.
 */
public final class TextosEntrants {

    private static final Set<String> OUI = Set.of("1", "OK", "OUI", "O", "YES", "Y");
    private static final Set<String> NON = Set.of("2", "NON", "N", "NO");

    private TextosEntrants() {
    }

    /** Ce que le texto veut dire. */
    public enum Intention {
        PRISE_EN_CHARGE, REFUS, INCONNUE;

        /** « ok. », « Oui ! », « 1 » : accents, ponctuation et casse ne comptent pas ; un mot de plus rend inconnu. */
        public static Intention de(String corps) {
            String mot = Normalizer.normalize(corps == null ? "" : corps, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").replaceAll("[^\\p{Alnum}\\s]", "").trim().toUpperCase(Locale.ROOT);
            if (OUI.contains(mot)) {
                return PRISE_EN_CHARGE;
            }
            return NON.contains(mot) ? REFUS : INCONNUE;
        }

        /** La sémantique de réponse qu'elle cherche parmi celles que l'alerte propose. */
        public Semantique semantique() {
            return switch (this) {
                case PRISE_EN_CHARGE -> Semantique.PRISE_EN_CHARGE;
                case REFUS -> Semantique.REFUS;
                case INCONNUE -> throw new IllegalStateException("une intention inconnue n'a pas de sémantique");
            };
        }
    }

    /** Ce qui est arrivé du texto ; chaque issue dit à la personne, en retour, ce qui a été compris. */
    public enum Issue {
        PRISE_EN_CHARGE("prise_en_charge"),
        REFUS("refus"),
        AIDE("aide"),
        SANS_ALERTE("sans_alerte"),
        DEJA_REPONDU("deja_repondu"),
        NON_PROPOSEE("non_proposee"),
        /** Le même message rejoué : il est ignoré, et rien n'est répondu. */
        REJOUE("rejoue");

        private final String code;

        Issue(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static Issue de(String code) {
            return Arrays.stream(values()).filter(i -> i.code.equals(code)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("issue de texto inconnue : " + code));
        }

        /** Le texto renvoyé à la personne ; nul pour un message rejoué. */
        public String message(String titre) {
            return switch (this) {
                case PRISE_EN_CHARGE -> "Prise en charge enregistrée : « " + titre + " ». Merci.";
                case REFUS -> "Refus enregistré : « " + titre + " ».";
                case AIDE -> "Répondez 1 (ou OK) pour prendre en charge, 2 (ou NON) pour refuser.";
                case SANS_ALERTE -> "Aucune alerte n'attend votre réponse.";
                case DEJA_REPONDU -> "Vous avez déjà répondu à « " + titre + " ».";
                case NON_PROPOSEE -> "Cette réponse n'est pas proposée par « " + titre + " » : répondez depuis l'application.";
                case REJOUE -> null;
            };
        }
    }

    /** Le texto reçu : l'identifiant du fournisseur (contre le rejeu), le numéro qui l'envoie, ce qu'il dit. */
    public record Entrant(String messageSid, String de, String corps) { }

    /** La remise ouverte à laquelle un numéro répond : la plus récente alerte qui l'attend. */
    public record RemiseOuverte(String tenantId, String alerteId, String personId) { }

    /** La trace d'un texto reçu : jamais son contenu, seulement ce qu'on en a fait. */
    public record Recu(String messageSid, String tenantId, String alerteId, String personId, Issue issue, String recuLe) { }
}
