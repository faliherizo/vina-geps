package ca.vina.nexu.securite.domaine;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;

/**
 * Pare-feu applicatif (AO 2026-PLAT-GA, exigence 32 : WAF, OWASP, gestion des capacités) — port
 * de {@code securite/pareFeu.ts}, la partie pure : signatures d'attaque sur le chemin et la
 * requête, limitation de débit par adresse, verrou de connexion après trop d'échecs, et mesure
 * de capacité (requêtes par minute, latence p95). L'horloge et le journal sont injectés.
 *
 * <p>Le corps des requêtes n'est pas inspecté : l'application transporte du HTML légitime et des
 * documents en base64 ; y chercher {@code <script} ne ferait que des faux positifs.
 */
public final class PareFeu {

    public enum Mode { BLOQUER, OBSERVER, INACTIF;
        public String code() {
            return name().toLowerCase();
        }
    }

    public record Signature(String code, String libelle, Pattern motif) {
    }

    public static final List<Signature> SIGNATURES = List.of(
            new Signature("traversee", "traversée de répertoire", Pattern.compile("(^|[\\\\/])\\.\\.([\\\\/]|$)|%2e%2e[\\\\/%]|/etc/passwd|\\\\windows\\\\", Pattern.CASE_INSENSITIVE)),
            new Signature("octet_nul", "octet nul", Pattern.compile("%00|\\x00")),
            new Signature("injection_sql", "injection SQL", Pattern.compile("(\\bunion\\b[\\s(]+(all\\s+)?select\\b)|(\\b(select|insert|update|delete|drop|alter)\\b[\\s(]+[\\w*]+[\\s(]+\\b(from|into|table|set)\\b)|('\\s*(or|and)\\s+[\\w']+\\s*=\\s*[\\w']+)|(\\bsleep\\s*\\()|(\\bbenchmark\\s*\\()|(xp_cmdshell)|(;\\s*--)", Pattern.CASE_INSENSITIVE)),
            new Signature("script", "script inséré", Pattern.compile("<\\s*script\\b|javascript\\s*:|onerror\\s*=|onload\\s*=|<\\s*iframe\\b", Pattern.CASE_INSENSITIVE)),
            new Signature("commande", "injection de commande", Pattern.compile("(\\||;|`|\\$\\()\\s*(cat|ls|wget|curl|bash|sh|nc|powershell)\\b", Pattern.CASE_INSENSITIVE)));

    public static final List<String> REGLES = List.of("traversee", "octet_nul", "injection_sql", "script", "commande", "longueur", "debit", "verrou", "verrou_pose", "verrou_adresse", "verrou_adresse_pose");

    public record Verdict(boolean bloquee, String regle, String libelle) {
    }

    /**
     * {@code echecsConnexionParAdresse} : échecs d'une même adresse, tous comptes confondus, avant que l'adresse soit
     * verrouillée (aspersion de mots de passe, IN-05) ; 0 désactive la règle.
     */
    public record Reglages(Mode mode, int requetesParMinute, int echecsConnexionAvantVerrou, int verrouMinutes, int echecsConnexionParAdresse) {

        /** Sans seuil par adresse : les réglages d'avant les tests d'intrusion du 22 novembre 2026. */
        public Reglages(Mode mode, int requetesParMinute, int echecsConnexionAvantVerrou, int verrouMinutes) {
            this(mode, requetesParMinute, echecsConnexionAvantVerrou, verrouMinutes, 0);
        }

        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mode", mode.code());
            m.put("requetesParMinute", requetesParMinute);
            m.put("echecsConnexionAvantVerrou", echecsConnexionAvantVerrou);
            m.put("verrouMinutes", verrouMinutes);
            m.put("echecsConnexionParAdresse", echecsConnexionParAdresse);
            return m;
        }
    }

    /** Les réglages depuis l'environnement ({@code WAF_MODE}, {@code WAF_REQUETES_PAR_MINUTE}…), avec les défauts du Node. */
    /** Cinq comptes à dix essais : au-delà, une même adresse ne devine plus, elle asperge. */
    static final int ECHECS_PAR_ADRESSE_DEFAUT = 50;

    public static Reglages reglages(Map<String, String> env) {
        String mode = env.get("WAF_MODE");
        Mode m = "observer".equals(mode) ? Mode.OBSERVER : "inactif".equals(mode) ? Mode.INACTIF : Mode.BLOQUER;
        return new Reglages(m, entier(env.get("WAF_REQUETES_PAR_MINUTE"), 20_000), entier(env.get("WAF_ECHECS_CONNEXION"), 10), entier(env.get("WAF_VERROU_MINUTES"), 15),
                entier(env.get("WAF_ECHECS_CONNEXION_ADRESSE"), ECHECS_PAR_ADRESSE_DEFAUT));
    }

    private static int entier(String v, int defaut) {
        if (v == null || v.isBlank()) {
            return defaut;
        }
        try {
            int x = Integer.parseInt(v.trim());
            return x >= 0 ? x : defaut;
        } catch (NumberFormatException e) {
            return defaut;
        }
    }

    private static String decoder(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return s;
        }
    }

    /** Le verdict sur ce qui vient de l'extérieur sans schéma : chemin, chaîne de requête, en-têtes choisis. */
    public static Verdict inspecter(String chemin, String requete, String userAgent, String referer) {
        if (chemin.length() + requete.length() > 4096) {
            return new Verdict(true, "longueur", "requête démesurée");
        }
        List<String> sujets = List.of(chemin, decoder(chemin), requete, decoder(requete), userAgent == null ? "" : userAgent, referer == null ? "" : referer);
        for (Signature s : SIGNATURES) {
            if (sujets.stream().anyMatch(sujet -> s.motif().matcher(sujet).find())) {
                return new Verdict(true, s.code(), s.libelle());
            }
        }
        return new Verdict(false, null, null);
    }

    // ── Compteurs glissants ────────────────────────────────────────────────

    /** Une fenêtre glissante par clé : dates des événements retenus, taillées à chaque lecture. */
    public static final class Fenetres {
        private final Map<String, List<Long>> evenements = new LinkedHashMap<>();
        private final long dureeMs;
        private final int maximumCles;

        public Fenetres(long dureeMs, int maximumCles) {
            this.dureeMs = dureeMs;
            this.maximumCles = maximumCles;
        }

        public Fenetres(long dureeMs) {
            this(dureeMs, 50_000);
        }

        private List<Long> vivants(String cle, long maintenant) {
            List<Long> liste = new ArrayList<>();
            for (Long t : evenements.getOrDefault(cle, List.of())) {
                if (maintenant - t < dureeMs) {
                    liste.add(t);
                }
            }
            return liste;
        }

        public synchronized int compter(String cle, long maintenant) {
            List<Long> liste = vivants(cle, maintenant);
            liste.add(maintenant);
            evenements.put(cle, liste);
            if (evenements.size() > maximumCles) {
                Iterator<String> premiere = evenements.keySet().iterator();
                premiere.next();
                premiere.remove();
            }
            return liste.size();
        }

        public synchronized int lire(String cle, long maintenant) {
            return vivants(cle, maintenant).size();
        }

        public synchronized void oublier(String cle) {
            evenements.remove(cle);
        }
    }

    // ── Mesure de capacité ─────────────────────────────────────────────────

    public record Capacite(int requetesDerniereMinute, int requetesCinqMinutes, Long latenceP95Ms, Long latenceP50Ms, int enVol) {
        public Map<String, Object> enCarte() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("requetesDerniereMinute", requetesDerniereMinute);
            m.put("requetesCinqMinutes", requetesCinqMinutes);
            m.put("latenceP95Ms", latenceP95Ms);
            m.put("latenceP50Ms", latenceP50Ms);
            m.put("enVol", enVol);
            return m;
        }
    }

    /** Les latences des cinq dernières minutes, pour un p95 honnête (pas une moyenne). */
    public static final class Mesure {
        private record Echantillon(long t, long ms) {
        }

        private final List<Echantillon> echantillons = new ArrayList<>();
        private int enVol;

        public synchronized void entrer() {
            enVol++;
        }

        public synchronized void sortir() {
            enVol--;
        }

        public synchronized void noter(long maintenant, long ms) {
            echantillons.add(new Echantillon(maintenant, ms));
            while (!echantillons.isEmpty() && maintenant - echantillons.get(0).t() > 5 * 60_000L) {
                echantillons.remove(0);
            }
            while (echantillons.size() > 100_000) {
                echantillons.remove(0);
            }
        }

        public synchronized Capacite lire(long maintenant) {
            List<Long> tri = new ArrayList<>();
            int une = 0;
            for (Echantillon e : echantillons) {
                if (maintenant - e.t() <= 5 * 60_000L) {
                    tri.add(e.ms());
                    if (maintenant - e.t() <= 60_000L) {
                        une++;
                    }
                }
            }
            tri.sort(Long::compare);
            return new Capacite(une, tri.size(), centile(tri, 0.95), centile(tri, 0.5), enVol);
        }

        private static Long centile(List<Long> tri, double p) {
            return tri.isEmpty() ? null : tri.get(Math.min(tri.size() - 1, (int) Math.floor(p * tri.size())));
        }
    }

    // ── Le pare-feu ────────────────────────────────────────────────────────

    private final Reglages reglages;
    private final LongSupplier horloge;
    private final BiConsumer<String, Map<String, Object>> journal;
    private final Fenetres debit;
    private final Fenetres echecs;
    private final Map<String, Long> verrous = new LinkedHashMap<>();
    private final Fenetres blocages = new Fenetres(24 * 3_600_000L);
    private final Mesure mesure = new Mesure();

    /** @param journal reçoit l'action d'audit ({@code securite.waf.bloque} ou {@code …observe}) et ses détails. */
    public PareFeu(Reglages reglages, LongSupplier horloge, BiConsumer<String, Map<String, Object>> journal) {
        this.reglages = reglages;
        this.horloge = horloge;
        this.journal = journal;
        this.debit = new Fenetres(60_000L);
        this.echecs = new Fenetres(reglages.verrouMinutes() * 60_000L);
    }

    public Reglages reglages() {
        return reglages;
    }

    public Mesure mesure() {
        return mesure;
    }

    private void journaliser(String regle, String chemin, String methode, String ip, Map<String, Object> details) {
        blocages.compter(regle, horloge.getAsLong());
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("regle", regle);
        d.put("ip", ip);
        d.put("methode", methode);
        d.putAll(details);
        journal.accept(reglages.mode() == Mode.OBSERVER ? "securite.waf.observe" : "securite.waf.bloque", Map.of("target", chemin, "details", d));
    }

    /** Ce que la requête reçoit : rien (elle passe), ou un code et un message de refus. */
    public record Refus(int code, String message) {
    }

    /** Le contrôle d'une requête entrante : signatures puis débit. Vide = elle passe (ou le mode observe). */
    public Optional<Refus> controler(String chemin, String requete, String methode, String ip, String userAgent, String referer) {
        if (reglages.mode() == Mode.INACTIF) {
            return Optional.empty();
        }
        Verdict verdict = inspecter(chemin, requete, userAgent, referer);
        if (verdict.bloquee()) {
            journaliser(verdict.regle(), chemin, methode, ip, Map.of("libelle", verdict.libelle()));
            return refuser(403, "requête refusée par le pare-feu applicatif (" + verdict.libelle() + ")");
        }
        if (reglages.requetesParMinute() > 0 && debit.compter(ip, horloge.getAsLong()) > reglages.requetesParMinute()) {
            journaliser("debit", chemin, methode, ip, Map.of("parMinute", reglages.requetesParMinute()));
            return refuser(429, "trop de requêtes : réessayez dans une minute");
        }
        return Optional.empty();
    }

    private Optional<Refus> refuser(int code, String message) {
        return reglages.mode() == Mode.OBSERVER ? Optional.empty() : Optional.of(new Refus(code, message));
    }

    private static String cleVerrou(String ip, String username) {
        return ip + "|" + (username == null ? "" : username.toLowerCase());
    }

    /** Toutes les tentatives d'une adresse, quel que soit le compte visé (IN-05). */
    private static String cleAdresse(String ip) {
        return ip + "|*";
    }

    /** Avant {@code /login} : un compte verrouillé, ou une adresse qui asperge, ne se tente même plus. */
    public synchronized Optional<Refus> gardeConnexion(String ip, String username) {
        if (reglages.mode() == Mode.INACTIF) {
            return Optional.empty();
        }
        Optional<Refus> compte = refusSiVerrouille(cleVerrou(ip, username), "verrou", ip, username);
        return compte.isPresent() ? compte : refusSiVerrouille(cleAdresse(ip), "verrou_adresse", ip, username);
    }

    private Optional<Refus> refusSiVerrouille(String cle, String regle, String ip, String username) {
        Long jusqua = verrous.get(cle);
        long maintenant = horloge.getAsLong();
        if (jusqua == null || jusqua <= maintenant) {
            return Optional.empty();
        }
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("username", username);
        journaliser(regle, "/api/auth/login", "POST", ip, d);
        long minutes = (long) Math.ceil((jusqua - maintenant) / 60_000.0);
        return refuser(429, "compte temporairement verrouillé après trop d'échecs : réessayez dans " + minutes + " min");
    }

    /** Appelé sur chaque échec d'authentification ; au seuil du compte, ou de l'adresse, le verrou se pose. */
    public synchronized void echecConnexion(String ip, String username) {
        if (reglages.mode() == Mode.INACTIF) {
            return;
        }
        compterEchec(cleVerrou(ip, username), reglages.echecsConnexionAvantVerrou(), "verrou_pose", ip, username);
        compterEchec(cleAdresse(ip), reglages.echecsConnexionParAdresse(), "verrou_adresse_pose", ip, username);
    }

    private void compterEchec(String cle, int seuil, String regle, String ip, String username) {
        if (seuil == 0 || echecs.compter(cle, horloge.getAsLong()) < seuil) {
            return;
        }
        verrous.put(cle, horloge.getAsLong() + reglages.verrouMinutes() * 60_000L);
        echecs.oublier(cle);
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("username", username);
        d.put("minutes", reglages.verrouMinutes());
        journaliser(regle, "/api/auth/login", "POST", ip, d);
    }

    /** Une connexion réussie efface les échecs précédents : ce n'était pas une attaque. */
    public synchronized void connexionReussie(String ip, String username) {
        echecs.oublier(cleVerrou(ip, username));
    }

    /** L'état lu par l'Exploitation : mode, réglages, blocages des 24 h par règle, verrous actifs, capacité. */
    public synchronized Map<String, Object> etat() {
        long maintenant = horloge.getAsLong();
        verrous.entrySet().removeIf(e -> e.getValue() <= maintenant);
        Map<String, Object> blocages24h = new LinkedHashMap<>();
        for (String regle : REGLES) {
            int n = blocages.lire(regle, maintenant);
            if (n > 0) {
                blocages24h.put(regle, n);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("mode", reglages.mode().code());
        m.put("reglages", reglages.enCarte());
        m.put("blocages24h", blocages24h);
        m.put("verrousActifs", verrous.size());
        m.put("capacite", mesure.lire(maintenant).enCarte());
        return m;
    }
}
