package ca.vina.nexu.saas.infrastructure.persistance;

import ca.vina.nexu.soutien.domaine.Soutien;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bannière de maintenance (AO-5338 Global-19), port de {@code saas/maintenance.ts} : le message
 * explicite du superadmin prime ; sinon, une fenêtre de maintenance en cours (exigence 29) allume
 * la bannière d'elle-même — la maintenance annoncée n'a pas à être re-saisie le jour venu.
 */
@Repository
public class DepotMaintenance {

    private static final List<String> NIVEAUX = List.of("info", "warning", "critical");
    /** « 16 septembre 2026 à 00 h 00 » — la forme de {@code Intl.DateTimeFormat("fr-CA", { dateStyle: "long", timeStyle: "short" })} à Toronto. */
    private static final DateTimeFormatter HEURE_QUEBEC = DateTimeFormatter.ofPattern("d MMMM uuuu 'à' HH 'h' mm", Locale.CANADA_FRENCH).withZone(ZoneId.of("America/Toronto"));
    private final JdbcClient jdbc;
    private final DepotSoutien soutien;
    private final Clock horloge;

    public DepotMaintenance(JdbcClient jdbc, DepotSoutien soutien, Clock horloge) {
        this.jdbc = jdbc;
        this.soutien = soutien;
        this.horloge = horloge;
    }

    public static String heureQuebec(String iso) {
        return HEURE_QUEBEC.format(Instant.parse(iso));
    }

    private Map<String, Object> ligne() {
        return jdbc.sql("SELECT message, level, active, updatedAt FROM maintenance_message WHERE id = 1").query().listOfRows().stream().findFirst().orElse(Map.of());
    }

    public Map<String, Object> lire() {
        Map<String, Object> r = ligne();
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("message", r.getOrDefault("message", ""));
        base.put("level", r.getOrDefault("level", "info"));
        boolean active = r.get("active") != null && ((Number) r.get("active")).intValue() != 0;
        base.put("active", active);
        base.put("updatedAt", r.get("updatedAt"));
        Optional<Soutien.Maintenance> prochaine = soutien.prochaineMaintenance();
        base.put("prochaine", prochaine.map(p -> Map.of("debut", p.debut(), "fin", p.fin(), "description", p.description())).orElse(null));
        if (active) {
            return base;
        }
        Optional<Soutien.Maintenance> enCours = soutien.maintenanceEnCours();
        if (enCours.isEmpty()) {
            return base;
        }
        base.put("active", true);
        base.put("level", "warning");
        base.put("message", "Maintenance en cours jusqu'à " + heureQuebec(enCours.get().fin()) + " : " + enCours.get().description());
        return base;
    }

    public Map<String, Object> regler(Map<String, Object> entree) {
        Map<String, Object> courant = lire();
        String message = entree.get("message") != null ? String.valueOf(entree.get("message")) : (String) courant.get("message");
        String level = entree.get("level") != null && NIVEAUX.contains(String.valueOf(entree.get("level"))) ? String.valueOf(entree.get("level")) : (String) courant.get("level");
        boolean active = entree.get("active") != null ? Boolean.TRUE.equals(entree.get("active")) : (Boolean) courant.get("active");
        if (active && message.isBlank()) {
            throw new IllegalArgumentException("message required to activate the banner");
        }
        // La ligne unique naît ici si le Node ne l'a jamais semée : un Java seul sur une base neuve doit pouvoir afficher la bannière.
        jdbc.sql("INSERT INTO maintenance_message (id, message, level, active, updatedAt) VALUES (1, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET message = excluded.message, level = excluded.level, active = excluded.active, updatedAt = excluded.updatedAt")
                .params(message, level, active ? 1 : 0, Iso.de(horloge.instant())).update();
        return lire();
    }
}
