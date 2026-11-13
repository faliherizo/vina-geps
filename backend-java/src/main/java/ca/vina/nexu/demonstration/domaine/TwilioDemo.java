package ca.vina.nexu.demonstration.domaine;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Twilio de démonstration — l'API REST publiée (Message et Call), avec ses codes d'erreur :
 * 20003 authentification, 21211 {@code To} invalide, 21212 {@code From} invalide, 21602 corps
 * requis, 21205 {@code Url} ou {@code Twiml} requis, 20404 introuvable, 20429 trop de requêtes.
 * Un numéro injoignable donne {@code failed} puis {@code undelivered} ; un numéro limité, 429.
 * L'état est celui de l'instance : rien ne survit au processus.
 */
public final class TwilioDemo {
    /** Les états d'un message ou d'un appel Twilio, tels que l'API les écrit. */
    enum Statut {
        EN_FILE("queued"), LIVRE("delivered"), ECHOUE("failed"), NON_LIVRE("undelivered"), SANS_REPONSE("no-answer"), TERMINE("completed");

        private final String code;

        Statut(String code) {
            this.code = code;
        }

        String code() {
            return code;
        }

        boolean est(Object valeur) {
            return code.equals(valeur);
        }
    }


    /** Ce qu'une opération renvoie : le code HTTP et le corps, réussite ou erreur Twilio. */
    public record Reponse(int statut, Map<String, Object> corps) {
    }

    private static final DateTimeFormatter RFC_2822 = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
    private static final Pattern DIAL = Pattern.compile("<Dial[^>]*>\\s*([^<\\s]+)\\s*</Dial>", Pattern.CASE_INSENSITIVE);

    private final Clock horloge;
    private final RandomGenerator alea;
    private final Map<String, Map<String, Object>> messages = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> appels = new ConcurrentHashMap<>();

    public TwilioDemo(Clock horloge, RandomGenerator alea) {
        this.horloge = horloge;
        this.alea = alea;
    }

    public static Map<String, Object> erreur(int statut, int code, String message) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("code", code);
        e.put("message", message);
        e.put("more_info", "https://www.twilio.com/docs/errors/" + code);
        e.put("status", statut);
        return e;
    }

    private String date() {
        return RFC_2822.format(horloge.instant());
    }

    private String sid(String prefixe) {
        byte[] b = new byte[16];
        alea.nextBytes(b);
        return prefixe + HexFormat.of().formatHex(b);
    }

    private static String texte(Map<String, ?> corps, String cle) {
        Object v = corps.get(cle);
        return v == null ? "" : String.valueOf(v);
    }

    /** Ce que Twilio vérifie à la création d'un message, dans l'ordre où il le fait. */
    public Reponse creerMessage(Map<String, ?> corps, String accountSid) {
        String to = texte(corps, "To");
        String from = texte(corps, "From");
        String body = texte(corps, "Body");
        if (!DonneesDemo.E164.matcher(to).matches()) {
            return new Reponse(400, erreur(400, 21211, "The 'To' number " + to + " is not a valid phone number."));
        }
        if (from.isEmpty()) {
            return new Reponse(400, erreur(400, 21212, "The 'From' number " + from + " is not a valid phone number, shortcode, or alphanumeric sender ID."));
        }
        if (body.isBlank()) {
            return new Reponse(400, erreur(400, 21602, "Message body is required."));
        }
        if (DonneesDemo.NUMERO_LIMITE.equals(to)) {
            return new Reponse(429, erreur(429, 20429, "Too Many Requests"));
        }
        boolean injoignable = DonneesDemo.NUMERO_INJOIGNABLE.equals(to);
        String sid = sid("SM");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("sid", sid);
        m.put("account_sid", accountSid);
        m.put("to", to);
        m.put("from", from);
        m.put("body", body);
        m.put("status", (injoignable ? Statut.ECHOUE : Statut.EN_FILE).code());
        m.put("direction", "outbound-api");
        m.put("num_segments", String.valueOf(Math.max(1, (body.length() + 159) / 160)));
        m.put("date_created", date());
        m.put("date_updated", date());
        m.put("date_sent", null);
        m.put("error_code", injoignable ? 30003 : null);
        m.put("error_message", injoignable ? "Unreachable destination handset" : null);
        m.put("uri", "/2010-04-01/Accounts/" + accountSid + "/Messages/" + sid + ".json");
        messages.put(sid, m);
        return new Reponse(201, m);
    }

    /** Au suivi, le message a avancé : livré, ou non livré s'il visait l'injoignable. */
    public Map<String, Object> lireMessage(String sid) {
        Map<String, Object> m = messages.get(sid);
        if (m == null) {
            return null;
        }
        if (Statut.EN_FILE.est(m.get("status"))) {
            m.put("status", Statut.LIVRE.code());
            m.put("date_sent", date());
        }
        if (Statut.ECHOUE.est(m.get("status"))) {
            m.put("status", Statut.NON_LIVRE.code());
        }
        m.put("date_updated", date());
        return m;
    }

    /** Le numéro composé par le {@code <Dial>} du TwiML : c'est lui que le réseau peut ne pas joindre. */
    public static String numeroCompose(String twiml) {
        Matcher m = DIAL.matcher(twiml);
        return m.find() ? m.group(1) : null;
    }

    public Reponse creerAppel(Map<String, ?> corps, String accountSid) {
        String to = texte(corps, "To");
        String from = texte(corps, "From");
        String twiml = corps.get("Twiml") == null ? null : String.valueOf(corps.get("Twiml"));
        if (!DonneesDemo.E164.matcher(to).matches()) {
            return new Reponse(400, erreur(400, 21211, "The 'To' number " + to + " is not a valid phone number."));
        }
        if (!DonneesDemo.E164.matcher(from).matches()) {
            return new Reponse(400, erreur(400, 21212, "The 'From' number " + from + " is not a valid phone number."));
        }
        if (twiml == null && corps.get("Url") == null) {
            return new Reponse(400, erreur(400, 21205, "Url or Twiml is required."));
        }
        String compose = twiml == null ? null : numeroCompose(twiml);
        if (DonneesDemo.NUMERO_LIMITE.equals(to)) {
            return new Reponse(429, erreur(429, 20429, "Too Many Requests"));
        }
        boolean injoignable = DonneesDemo.NUMERO_INJOIGNABLE.equals(to) || DonneesDemo.NUMERO_INJOIGNABLE.equals(compose);
        String sid = sid("CA");
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("sid", sid);
        a.put("account_sid", accountSid);
        a.put("to", to);
        a.put("from", from);
        a.put("status", (injoignable ? Statut.SANS_REPONSE : Statut.EN_FILE).code());
        a.put("direction", "outbound-api");
        a.put("date_created", date());
        a.put("duration", null);
        a.put("twiml", twiml);
        a.put("uri", "/2010-04-01/Accounts/" + accountSid + "/Calls/" + sid + ".json");
        appels.put(sid, a);
        return new Reponse(201, a);
    }

    public Map<String, Object> lireAppel(String sid) {
        Map<String, Object> a = appels.get(sid);
        if (a == null) {
            return null;
        }
        if (Statut.EN_FILE.est(a.get("status"))) {
            a.put("status", Statut.TERMINE.code());
            a.put("duration", "42");
        }
        return a;
    }

    /** L'authentification Basic de Twilio : {@code AccountSid:AuthToken}, et le SID du chemin doit être celui du compte. */
    public static boolean authentifie(String enTete, String accountSidChemin) {
        if (enTete == null || !enTete.startsWith("Basic ")) {
            return false;
        }
        String[] parts;
        try {
            parts = new String(Base64.getDecoder().decode(enTete.substring(6)), StandardCharsets.UTF_8).split(":", 2);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return parts.length == 2 && DonneesDemo.TWILIO_ACCOUNT_SID.equals(parts[0]) && DonneesDemo.TWILIO_AUTH_TOKEN.equals(parts[1]) && parts[0].equals(accountSidChemin);
    }

    public List<Map<String, Object>> messages() {
        return List.copyOf(messages.values());
    }

    public List<Map<String, Object>> appels() {
        return List.copyOf(appels.values());
    }

    public void vider() {
        messages.clear();
        appels.clear();
    }
}
