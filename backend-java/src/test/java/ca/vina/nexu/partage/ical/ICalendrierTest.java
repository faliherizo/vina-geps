package ca.vina.nexu.partage.ical;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("iCalendar partagé — événements d'une journée, fin exclusive, échappement et repli RFC 5545")
class ICalendrierTest {

    @Test
    void exporte() {
        String ics = ICalendrier.versIcs(List.of(
                new ICalendrier.Entree("a", "2026-06-24", null, "Fête nationale; grand, congé", "holiday"),
                new ICalendrier.Entree("b", "2026-07-01", "2026-07-02", null, null)), "Calendrier personnel", "Calendrier");
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Nexu VINA//Calendrier//FR").contains("X-WR-CALNAME:Calendrier personnel")
                .contains("UID:a@nexu-vina\r\nDTSTART;VALUE=DATE:20260624\r\nDTEND;VALUE=DATE:20260625\r\nSUMMARY:Fête nationale\\; grand\\, congé\r\nCATEGORIES:HOLIDAY")
                .contains("DTSTART;VALUE=DATE:20260701\r\nDTEND;VALUE=DATE:20260703\r\nSUMMARY:\r\nCATEGORIES:\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ICalendrier.versIcs(List.of(), null, "X")).doesNotContain("X-WR-CALNAME");
        // Une ligne longue se replie à 75 octets, puis par tranches de 74 précédées d'une espace.
        String longue = ICalendrier.replier("SUMMARY:" + "x".repeat(200));
        assertThat(longue.split("\r\n ")).hasSize(3);
        assertThat(longue.split("\r\n ")[0]).hasSize(75);
        assertThat(ICalendrier.replier("SUMMARY:" + "y".repeat(67)).split("\r\n ")).hasSize(1);
        assertThat(ICalendrier.replier("SUMMARY:" + "y".repeat(140))).endsWith("\r\n " + "y".repeat(73));
    }
}
