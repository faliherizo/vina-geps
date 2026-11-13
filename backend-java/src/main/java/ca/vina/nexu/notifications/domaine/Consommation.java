package ca.vina.nexu.notifications.domaine;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * <b>La consommation d'avis par organisme</b> — ce que l'exploitant facture, et ce qu'il plafonne.
 *
 * <p>Deux canaux, deux unités, et c'est le point de tout ce module : les <b>textos</b> se comptent
 * en <b>segments</b> (ce que le fournisseur facture), les <b>courriels</b> en messages. Un écran
 * qui afficherait « 1 200 textos » sans dire qu'ils valent 3 100 segments laisserait croire à une
 * enveloppe respectée.
 *
 * <p>Jumeau de {@code backend/src/notifications/consommation.ts}.
 */
public final class Consommation {

    /** L'identifiant de la ligne qui porte les envois n'appartenant à aucun organisme. */
    public static final String NON_ATTRIBUE = "__non_attribue__";

    public static final String NOM_NON_ATTRIBUE = "Non attribué (exploitant, hors organisme)";

    private static final Pattern MOIS = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    /**
     * Les réglages de l'instance pour le canal courriel.
     *
     * @param limiteMensuelleParDefaut le plafond appliqué à un organisme qui n'en a pas de
     *                                 propre. <b>Zéro — illimité — par défaut</b>, à l'inverse
     *                                 des textos : un courriel ne coûte pas au message, et
     *                                 plafonner par défaut bloquerait des remises d'accès pour
     *                                 aucun gain.
     */
    public record ReglagesCourriel(int limiteMensuelleParDefaut) {

        /**
         * Lit {@code COURRIEL_QUOTA_MENSUEL}.
         *
         * <p>Lu plutôt qu'écrit en dur parce que <b>le Node lit la même variable</b> : les deux
         * piles doivent répondre le même chiffre, sans quoi la bascule de l'une à l'autre
         * changerait le plafond d'un organisme sans que personne ne l'ait demandé. Une valeur
         * illisible ou négative vaut zéro — plafonner sur une faute de frappe couperait des avis.
         */
        public static ReglagesCourriel de(Map<String, String> env) {
            String brut = env.getOrDefault("COURRIEL_QUOTA_MENSUEL", "").trim();
            if (brut.isEmpty()) {
                return new ReglagesCourriel(0);
            }
            try {
                int n = Integer.parseInt(brut);
                return new ReglagesCourriel(Math.max(0, n));
            } catch (NumberFormatException e) {
                return new ReglagesCourriel(0);
            }
        }
    }

    private Consommation() {
    }

    /**
     * Ce qu'un canal a consommé et ce qu'il avait droit de consommer.
     *
     * @param utilises l'unité facturée : segments pour les textos, messages pour les courriels
     * @param messages le nombre de messages — égal à {@code utilises} pour le courriel, souvent
     *                 inférieur pour le texto ; c'est l'écart qui renseigne
     */
    public record Canal(int utilises, int messages, int limite, boolean illimite, int depassement, int echecs) {

        public static Canal de(int utilises, int messages, int limite, int echecs) {
            boolean illimite = limite == 0;
            return new Canal(utilises, messages, limite, illimite, illimite ? 0 : Math.max(0, utilises - limite), echecs);
        }

        /** Sans plafond ni facture — pour ce qui n'appartient à aucun organisme vivant. */
        public Canal horsQuota() {
            return new Canal(utilises, messages, 0, true, 0, echecs);
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("utilises", utilises);
            m.put("messages", messages);
            m.put("limite", limite);
            m.put("illimite", illimite);
            m.put("depassement", depassement);
            m.put("echecs", echecs);
            return m;
        }
    }

    public record Organisme(String tenantId, String nom, Canal textos, Canal courriels) {

        public Organisme horsQuota() {
            return new Organisme(tenantId, nom, textos.horsQuota(), courriels.horsQuota());
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenantId", tenantId);
            m.put("nom", nom);
            m.put("textos", textos.vue());
            m.put("courriels", courriels.vue());
            return m;
        }
    }

    public record Vue(String mois, List<Organisme> organismes) {

        public Map<String, Object> vue() {
            Map<String, Object> totaux = new LinkedHashMap<>();
            totaux.put("textosSegments", somme(Organisme::textos, Canal::utilises));
            totaux.put("textosMessages", somme(Organisme::textos, Canal::messages));
            totaux.put("courriels", somme(Organisme::courriels, Canal::utilises));
            totaux.put("depassementTextos", somme(Organisme::textos, Canal::depassement));
            totaux.put("depassementCourriels", somme(Organisme::courriels, Canal::depassement));

            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mois", mois);
            m.put("organismes", organismes.stream().map(Organisme::vue).toList());
            m.put("totaux", totaux);
            return m;
        }

        private int somme(java.util.function.Function<Organisme, Canal> canal, java.util.function.ToIntFunction<Canal> champ) {
            return organismes.stream().map(canal).mapToInt(champ).sum();
        }
    }

    /**
     * Le mois demandé, ou le mois courant.
     *
     * <p>Refuse ce qui n'est pas {@code AAAA-MM} plutôt que de deviner : un mois mal lu donnerait
     * une facture portant sur une période que personne n'a demandée.
     */
    public static String moisDemande(String brut, String instant) {
        String t = brut == null ? "" : brut.trim();
        if (t.isEmpty()) {
            return instant.substring(0, 7);
        }
        if (!MOIS.matcher(t).matches()) {
            throw new IllegalArgumentException("mois attendu au format AAAA-MM");
        }
        return t;
    }
}
