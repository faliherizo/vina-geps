package ca.vina.nexu.notifications.domaine;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Le journal des textos ({@code sms_message}) — port de la partie pure de
 * {@code notifications/journalSms.ts} et de {@code routesTextos.ts} : un texto n'est pas « envoyé »
 * parce qu'une méthode a rendu la main, il l'est quand le fournisseur l'a pris, livré quand il le
 * dit, échoué avec l'erreur nommée sinon. Le numéro est gardé tel que le fournisseur l'a reçu,
 * mais jamais rendu tel quel à un écran : la vue le masque. Le quota mensuel se compte sur le
 * journal, jamais à part — un compteur qui dérive ment sans bruit.
 */
public final class JournalTextos {

    private JournalTextos() {
    }

    public static final String EN_ATTENTE = "en_attente";
    public static final List<String> STATUTS = List.of(EN_ATTENTE, Textos.ENVOYE, Textos.LIVRE, Textos.ECHOUE);
    public static final int LIMITE_PAR_DEFAUT = 1000;
    public static final int LIMITE_MAX = 1_000_000;
    public static final int LIMITE_LISTE_PAR_DEFAUT = 100;
    public static final int LIMITE_LISTE_MAX = 1000;
    public static final String REFUS_FOURNISSEUR = "refusé par le fournisseur";
    public static final String CHEMIN_RAPPEL_TWILIO = "/api/notifications/sms/statut/twilio";

    public record Texto(long id, String tenantId, String numero, String message, String statut, String fournisseur, String identifiantFournisseur,
                        String creeLe, String envoyeLe, String erreur, String contexte, String expediteur, int segments) {

        public boolean echoue() {
            return Textos.ECHOUE.equals(statut);
        }

        /** Refusé avant même d'atteindre le fournisseur : quota, numéro, ou panne — rien à redemander. */
        public boolean refuseAvantLeFournisseur() {
            return echoue() && identifiantFournisseur == null;
        }

        public boolean refusePourQuota() {
            return erreur != null && erreur.startsWith("quota mensuel atteint");
        }

        /** Ce que l'écran montre : le numéro masqué, jamais le numéro. */
        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("tenantId", tenantId);
            m.put("numero", masquer(numero));
            m.put("message", message);
            m.put("statut", statut);
            m.put("fournisseur", fournisseur);
            m.put("identifiantFournisseur", identifiantFournisseur);
            m.put("creeLe", creeLe);
            m.put("envoyeLe", envoyeLe);
            m.put("erreur", erreur);
            m.put("contexte", contexte);
            m.put("expediteur", expediteur);
            return m;
        }
    }

    public record Demande(String tenantId, String numero, String texte, String expediteur, String contexte) {
    }

    /** Un numéro masqué : les quatre derniers chiffres seulement. */
    public static String masquer(String numero) {
        String chiffres = (numero == null ? "" : numero).replaceAll("\\D", "");
        return chiffres.length() >= 4 ? "••••••••" + chiffres.substring(chiffres.length() - 4) : "••••";
    }

    /** Le nom du fournisseur en service, tel que la configuration le dit — ou « simule » quand rien n'est posé. */
    public static String nomFournisseur(Map<String, String> env) {
        String nom = env.getOrDefault("SMS_FOURNISSEUR", "").toLowerCase(Locale.ROOT);
        if (!nom.isEmpty()) {
            return nom;
        }
        return !env.getOrDefault("SMS_URL", "").isEmpty() && !env.getOrDefault("SMS_JETON", "").isEmpty() ? "http" : "simule";
    }

    /** La limite mensuelle par défaut de l'instance ({@code SMS_QUOTA_MENSUEL}, 0 = illimité) ; illisible, mille. */
    public static int limiteParDefaut(Map<String, String> env) {
        try {
            int n = Integer.parseInt(env.getOrDefault("SMS_QUOTA_MENSUEL", "").trim());
            return n >= 0 ? n : LIMITE_PAR_DEFAUT;
        } catch (NumberFormatException e) {
            return LIMITE_PAR_DEFAUT;
        }
    }

    /** L'URL publique du rappel de statut, si le déploiement se connaît ({@code PUBLIC_API_URL}). */
    public static String urlRappelTwilio(Map<String, String> env) {
        String base = env.getOrDefault("PUBLIC_API_URL", "").trim().replaceAll("/+$", "");
        return base.isEmpty() ? null : base + CHEMIN_RAPPEL_TWILIO;
    }

    /** Ce que l'écran dit du dispositif : fournisseur, variables manquantes, rappel branché ou non, fournisseur d'appel. */
    public record Etat(String fournisseur, boolean simule, List<String> manquantes, String rappel, String appel) {
        public static Etat de(Map<String, String> env) {
            String fournisseur = nomFournisseur(env);
            List<String> manquantes;
            try {
                Fournisseurs.Choix choix = Fournisseurs.lireChoix(env, "SMS_FOURNISSEUR");
                manquantes = choix == null ? List.of() : choix.manquantes();
            } catch (IllegalStateException e) {
                manquantes = List.of(e.getMessage());
            }
            String appel;
            try {
                Fournisseurs.Choix choix = Fournisseurs.lireChoix(env, "APPEL_FOURNISSEUR");
                appel = choix == null ? "simule" : choix.fournisseur();
            } catch (IllegalStateException e) {
                appel = "invalide";
            }
            return new Etat(fournisseur, fournisseur.equals("simule"), manquantes, fournisseur.equals("twilio") ? urlRappelTwilio(env) : null, appel);
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fournisseur", fournisseur);
            m.put("simule", simule);
            m.put("manquantes", manquantes);
            m.put("rappel", rappel);
            m.put("appel", appel);
            return m;
        }
    }

    /** Les réglages du journal, lus une fois au démarrage : le fournisseur nommé, la limite d'instance, le rappel, le jeton qui le signe. */
    public record Reglages(String fournisseur, int limiteParDefaut, String urlRappel, String jetonTwilio, Etat etat) {
        public static Reglages de(Map<String, String> env) {
            return new Reglages(nomFournisseur(env), JournalTextos.limiteParDefaut(env), urlRappelTwilio(env), env.getOrDefault("TWILIO_AUTH_TOKEN", ""), Etat.de(env));
        }
    }

    // ── Quota mensuel ─────────────────────────────────────────────────────

    /**
     * Le quota du mois, <b>en segments</b> — l'unité que le fournisseur facture.
     *
     * <p>Compter les messages sous-évaluait la consommation d'un facteur allant jusqu'à huit :
     * un avis de 480 caractères contenant un « ê » ou un « ç » minuscule bascule en UCS-2 (70
     * caractères par segment). Un organisme pouvait donc épuiser huit fois son enveloppe sans
     * jamais la « dépasser ». {@code messages} est conservé à part : c'est l'écart entre les
     * deux qui renseigne.
     */
    public record Quota(String mois, int limite, int utilises, Integer restants, boolean illimite, int messages) {
        public static Quota de(String mois, int limite, int utilises, int messages) {
            return new Quota(mois, limite, utilises, limite == 0 ? null : Math.max(0, limite - utilises), limite == 0, messages);
        }

        /**
         * Le message <b>entier</b> tient-il dans ce qui reste ?
         *
         * <p>Accepter un texto de quatre segments quand il en reste deux ferait dépasser
         * l'enveloppe — et c'est précisément ce que le comptage par message laissait faire.
         */
        public boolean refuse(int segmentsDemandes) {
            return !illimite && utilises + segmentsDemandes > limite;
        }

        /** Ce qui explique le refus, et <b>pourquoi le message coûte cher</b> — sinon rien n'est corrigeable. */
        public String motifDeRefus(Segments.Decoupage decoupe) {
            String pourquoi = decoupe.caractereDeclencheur() == null
                    ? " ; ce message en coûte " + decoupe.segments()
                    : " ; ce message en coûte " + decoupe.segments() + " (le caractère « " + decoupe.caractereDeclencheur()
                      + " » le fait passer en UCS-2, 70 caractères par segment)";
            return "quota mensuel atteint (" + utilises + "/" + limite + " segments en " + mois + ")" + pourquoi;
        }

        public Map<String, Object> vue() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mois", mois);
            m.put("limite", limite);
            m.put("utilises", utilises);
            m.put("messages", messages);
            m.put("restants", restants);
            m.put("illimite", illimite);
            return m;
        }
    }

    /** Le mois d'un instant ISO : ses sept premiers caractères. */
    public static String moisDe(String instant) {
        return instant.substring(0, 7);
    }

    /** La limite demandée par l'administration : un entier de 0 (illimité) à un million, quelle qu'en soit la forme. */
    public static int exigerLimite(Object brut) {
        if (brut instanceof Number n && n.doubleValue() == Math.rint(n.doubleValue()) && n.intValue() >= 0 && n.intValue() <= LIMITE_MAX) {
            return n.intValue();
        }
        throw new IllegalArgumentException("limite mensuelle : entier de 0 (illimité) à 1 000 000 attendu");
    }

    /** La taille d'une page du journal : cent à défaut, mille au plus, une au moins. */
    public static int limiteListe(Integer demandee) {
        int n = demandee == null || demandee <= 0 ? LIMITE_LISTE_PAR_DEFAUT : demandee;
        return Math.min(n, LIMITE_LISTE_MAX);
    }

    // ── Rappel du fournisseur ─────────────────────────────────────────────

    /** L'erreur d'un rappel Twilio : le code, suivi du message s'il y en a un ; rien sans code. */
    public static String erreurDuRappel(String code, String message) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return message == null || message.isEmpty() ? code : code + " " + message;
    }

    /** Ce qu'un échec garde comme erreur : celle du rappel, sinon celle déjà connue, sinon le statut brut. */
    public static String erreurApresRappel(String statut, String erreurRappel, String erreurConnue, String statutBrut) {
        if (!Textos.ECHOUE.equals(statut)) {
            return erreurConnue;
        }
        if (erreurRappel != null) {
            return erreurRappel;
        }
        return erreurConnue != null ? erreurConnue : REFUS_FOURNISSEUR + " (" + statutBrut + ")";
    }

    /**
     * La signature Twilio ({@code X-Twilio-Signature}) : HMAC-SHA1, clé = jeton d'authentification,
     * sur l'URL complète suivie des paramètres POST triés par nom, valeur accolée, en base64
     * (« Validating requests are coming from Twilio »). Sans jeton, rien n'est accepté : un rappel
     * non vérifié pourrait marquer « livré » ce qui ne l'est pas.
     */
    public static boolean verifierSignatureTwilio(String url, Map<String, String> params, String signature, String jeton) {
        if (jeton == null || jeton.isEmpty() || signature == null || signature.isEmpty()) {
            return false;
        }
        StringBuilder donnees = new StringBuilder(url);
        new TreeMap<>(params).forEach((k, v) -> donnees.append(k).append(v));
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(jeton.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String attendue = Base64.getEncoder().encodeToString(mac.doFinal(donnees.toString().getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(attendue.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponible", e);
        }
    }

    /** Le résumé du journal d'un établissement : combien, dans quel état, le dernier. */
    public static Map<String, Object> resume(List<Map<String, Object>> parStatutBrut) {
        Map<String, Integer> parStatut = new LinkedHashMap<>();
        STATUTS.forEach(s -> parStatut.put(s, 0));
        int total = 0;
        String dernierLe = null;
        for (Map<String, Object> l : parStatutBrut) {
            int n = ((Number) l.get("n")).intValue();
            parStatut.put(String.valueOf(l.get("status")), n);
            total += n;
            String dernier = (String) l.get("dernier");
            if (dernierLe == null || (dernier != null && dernier.compareTo(dernierLe) > 0)) {
                dernierLe = dernier;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("parStatut", parStatut);
        m.put("dernierLe", dernierLe);
        return m;
    }

    /** Les erreurs d'un envoi composé à l'écran : le numéro, puis le texte — les deux dits ensemble. */
    public static List<String> refusDeComposition(String numeroNormalise, String texte) {
        List<String> erreurs = new ArrayList<>();
        if (numeroNormalise == null) {
            erreurs.add("numéro invalide : format international (+15145551234) ou nord-américain à dix chiffres attendu");
        }
        erreurs.addAll(Textos.validerTexte(texte));
        return erreurs;
    }
}
