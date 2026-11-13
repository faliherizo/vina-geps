package ca.vina.nexu.partage.ical;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * **Export iCalendar (RFC 5545)** partagé par tous les calendriers de l'application — personnel,
 * pédagogique, scolaire (refonte D.27 ★ : « un .ics partout »). Pur : des entrées datées
 * entrent, un texte sort ; chaque contexte y plie ses propres objets.
 */
public final class ICalendrier {

    private ICalendrier() {
    }

    /** Un événement « toute la journée » ; la fin, si elle est donnée, est le dernier jour inclus. */
    public record Entree(String id, String date, String endDate, String title, String categorie) {
    }

    public static String versIcs(List<Entree> entrees, String nomCalendrier, String producteur) {
        List<String> lignes = new ArrayList<>(List.of("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Nexu VINA//" + producteur + "//FR", "CALSCALE:GREGORIAN", "METHOD:PUBLISH"));
        if (nomCalendrier != null) {
            lignes.add("X-WR-CALNAME:" + echapper(nomCalendrier));
        }
        for (Entree e : entrees) {
            String fin = LocalDate.parse(e.endDate() != null ? e.endDate() : e.date()).plusDays(1).toString().replace("-", "");
            lignes.add("BEGIN:VEVENT");
            lignes.add("UID:" + e.id() + "@nexu-vina");
            lignes.add("DTSTART;VALUE=DATE:" + e.date().replace("-", ""));
            lignes.add("DTEND;VALUE=DATE:" + fin);
            lignes.add("SUMMARY:" + echapper(e.title() == null ? "" : e.title()));
            lignes.add("CATEGORIES:" + (e.categorie() == null ? "" : e.categorie().toUpperCase(Locale.ROOT)));
            lignes.add("END:VEVENT");
        }
        lignes.add("END:VCALENDAR");
        return String.join("\r\n", lignes.stream().map(ICalendrier::replier).toList()) + "\r\n";
    }

    static String echapper(String texte) {
        return texte.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }

    /** Replie une ligne à 75 octets (RFC 5545 §3.1), continuation par une espace. */
    static String replier(String ligne) {
        if (ligne.length() <= 75) {
            return ligne;
        }
        StringBuilder sb = new StringBuilder(ligne.substring(0, 75));
        String reste = ligne.substring(75);
        while (reste.length() > 74) {
            sb.append("\r\n ").append(reste, 0, 74);
            reste = reste.substring(74);
        }
        if (!reste.isEmpty()) {
            sb.append("\r\n ").append(reste);
        }
        return sb.toString();
    }
}
