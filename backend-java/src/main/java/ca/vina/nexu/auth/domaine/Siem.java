package ca.vina.nexu.auth.domaine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Export et console SIEM — port de {@code siemExport.ts} et {@code securityConsole.ts}
 * (AO-5338 #86) : sévérité déduite de l'action, ligne CEF, NDJSON, synthèse par niveau.
 * Fonctions pures ; le JSON des lignes NDJSON est écrit à la main, le domaine n'ayant pas de
 * sérialiseur — et {@code JSON.stringify} ne connaît que ces sept champs, dans cet ordre.
 */
public final class Siem {

    public static final List<String> FORMATS = List.of("cef", "ndjson");
    public static final List<String> NIVEAUX = List.of("high", "medium", "low");

    private static final Pattern GRAVE = Pattern.compile("fail|denied|blocked|mismatch|reject|destroy|delete", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECURITE = Pattern.compile("login|logout|mfa|sso|password|role|permission", Pattern.CASE_INSENSITIVE);

    private Siem() {
    }

    /** Sévérité CEF (0-10) : échecs et suppressions à 7, gestes de session et de droits à 4, le reste à 3. */
    public static int severite(String action) {
        if (GRAVE.matcher(action).find()) {
            return 7;
        }
        return SECURITE.matcher(action).find() ? 4 : 3;
    }

    public static String niveau(String action) {
        int s = severite(action);
        return s >= 7 ? "high" : s >= 4 ? "medium" : "low";
    }

    private static int rang(String niveau) {
        return NIVEAUX.size() - 1 - NIVEAUX.indexOf(niveau);
    }

    private static String echapper(String s) {
        return s.replace("\\", "\\\\").replace("|", "\\|").replace("\n", " ");
    }

    private static String echapperExtension(String s) {
        return s.replace("\\", "\\\\").replace("=", "\\=").replace("\n", " ");
    }

    /** Une ligne CEF par entrée. */
    public static String ligneCef(EvenementAudit e) {
        String entete = String.join("|", "CEF:0", "NexuVINA", "Finance", "1.0", echapper(e.action()), echapper(e.action()), String.valueOf(severite(e.action())));
        List<String> extension = new ArrayList<>();
        extension.add("rt=" + echapperExtension(e.at()));
        if (e.username() != null && !e.username().isEmpty()) {
            extension.add("suser=" + echapperExtension(e.username()));
        }
        if (e.userId() != null && !e.userId().isEmpty()) {
            extension.add("suid=" + echapperExtension(e.userId()));
        }
        if (e.target() != null && !e.target().isEmpty()) {
            extension.add("act=" + echapperExtension(e.target()));
        }
        if (e.details() != null && !e.details().isEmpty()) {
            extension.add("msg=" + echapperExtension(e.details()));
        }
        return entete + "|" + String.join(" ", extension);
    }

    static String json(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }

    static String ligneNdjson(EvenementAudit e) {
        return "{\"id\":" + json(e.id()) + ",\"at\":" + json(e.at()) + ",\"userId\":" + json(e.userId()) + ",\"username\":" + json(e.username())
                + ",\"action\":" + json(e.action()) + ",\"target\":" + json(e.target()) + ",\"details\":" + json(e.details()) + "}";
    }

    /** Le lot dans le format demandé ; une ligne par entrée, terminée par un saut de ligne si le lot n'est pas vide. */
    public static String exporter(List<EvenementAudit> entrees, String format) {
        if (entrees.isEmpty()) {
            return "";
        }
        List<String> lignes = new ArrayList<>();
        for (EvenementAudit e : entrees) {
            lignes.add("ndjson".equals(format) ? ligneNdjson(e) : ligneCef(e));
        }
        return String.join("\n", lignes) + "\n";
    }

    /** La console : événements enrichis (sévérité, niveau) et synthèse ; filtre optionnel sur un niveau minimum. */
    public static Map<String, Object> console(List<EvenementAudit> entrees, String niveauMinimum, int limite) {
        int high = 0;
        int medium = 0;
        int low = 0;
        List<Map<String, Object>> evenements = new ArrayList<>();
        for (EvenementAudit e : entrees) {
            String niveau = niveau(e.action());
            if ("high".equals(niveau)) {
                high++;
            } else if ("medium".equals(niveau)) {
                medium++;
            } else {
                low++;
            }
            if ((niveauMinimum == null || rang(niveau) >= rang(niveauMinimum)) && evenements.size() < limite) {
                Map<String, Object> v = e.vue();
                v.put("severity", severite(e.action()));
                v.put("level", niveau);
                evenements.add(v);
            }
        }
        Map<String, Object> synthese = new LinkedHashMap<>();
        synthese.put("high", high);
        synthese.put("medium", medium);
        synthese.put("low", low);
        synthese.put("total", entrees.size());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("events", evenements);
        r.put("summary", synthese);
        return r;
    }
}
