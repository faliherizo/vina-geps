package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.Canal;
import ca.vina.nexu.notifications.domaine.Evenements;
import ca.vina.nexu.notifications.domaine.Preferences;
import ca.vina.nexu.partage.json.infrastructure.Json;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Les canaux de chaque compte ({@code notification_preferences}) et leur lecture par personne —
 * port de {@code notifications/preferences.ts}. Une personne peut avoir plusieurs comptes (le sien,
 * ceux rattachés à son dossier, le professeur lié à un employé) : le canal est ouvert dès qu'un
 * de ses comptes l'a laissé ouvert.
 */
@Repository
public class DepotPreferences {

    private final JdbcClient jdbc;
    private final Clock horloge;

    public DepotPreferences(JdbcClient jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    public Preferences preferencesDe(String userId) {
        return jdbc.sql("SELECT courriel, texto, poussee, evenements, courrielPersonnel, updatedAt FROM notification_preferences WHERE userId = ?").params(userId)
                .query((rs, i) -> new Preferences(rs.getInt("courriel") != 0, rs.getInt("texto") != 0, rs.getInt("poussee") != 0, lireEvenements(rs.getString("evenements")), rs.getString("courrielPersonnel"), rs.getString("updatedAt")))
                .optional().orElse(Preferences.PAR_DEFAUT);
    }

    /** Les réglages par événement (JSON code → canaux) ; un code disparu du catalogue ou une colonne corrompue rendent les défauts, jamais une panne. */
    @SuppressWarnings("unchecked")
    static Map<String, Evenements.Canaux> lireEvenements(String brut) {
        Map<String, Evenements.Canaux> regles = new LinkedHashMap<>();
        if (brut == null || brut.isBlank()) {
            return regles;
        }
        Map<String, Object> carte;
        try {
            carte = Json.carte(brut);
        } catch (RuntimeException e) {
            return regles;
        }
        carte.forEach((code, v) -> Evenements.parCode(code).ifPresent(e -> regles.put(code, Evenements.completer(e, v instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of()))));
        return regles;
    }

    public Preferences regler(String userId, String tenantId, Map<String, Object> correctif) {
        Preferences p = preferencesDe(userId).fusionner(correctif == null ? Map.of() : correctif, Iso.de(horloge.instant()));
        Map<String, Object> regles = new LinkedHashMap<>();
        p.evenements().forEach((code, c) -> regles.put(code, c.vue()));
        jdbc.sql("INSERT INTO notification_preferences (userId, tenantId, courriel, texto, poussee, evenements, courrielPersonnel, updatedAt) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(userId) DO UPDATE SET tenantId = excluded.tenantId, courriel = excluded.courriel, texto = excluded.texto, poussee = excluded.poussee, evenements = excluded.evenements, courrielPersonnel = excluded.courrielPersonnel, updatedAt = excluded.updatedAt")
                .params(userId, tenantId, p.courriel() ? 1 : 0, p.texto() ? 1 : 0, p.poussee() ? 1 : 0, Json.texte(regles), p.courrielPersonnel(), p.updatedAt()).update();
        return preferencesDe(userId);
    }

    /** L'adresse personnelle d'une personne (celle d'un de ses comptes qui en a donné une), ou vide (CSSMB 21). */
    public Optional<String> courrielPersonnelDe(String tenantId, String personId) {
        return comptesDePersonne(tenantId, personId).stream().map(c -> preferencesDe(c).courrielPersonnel()).filter(a -> a != null && !a.isBlank()).findFirst();
    }

    /** Les comptes qui « sont » une personne : le compte dont c'est l'identifiant, ceux rattachés à ce dossier, et ceux de l'employé lié à un professeur. */
    public List<String> comptesDePersonne(String tenantId, String personId) {
        Set<String> ids = new LinkedHashSet<>(comptesDe(tenantId, personId));
        jdbc.sql("SELECT linkedEmployeeId FROM varpay_teachers WHERE tenantId = ? AND id = ? AND linkedEmployeeId IS NOT NULL").params(tenantId, personId).query(String.class).optional()
                .ifPresent(lie -> ids.addAll(comptesDe(tenantId, lie)));
        return List.copyOf(ids);
    }

    private List<String> comptesDe(String tenantId, String id) {
        return jdbc.sql("SELECT id FROM users WHERE (id = ? OR employeeId = ?) AND (tenantId = ? OR tenantId IS NULL)").params(id, id, tenantId).query(String.class).list();
    }

    /** Ouvert si aucun compte n'existe (rien n'a été fermé), ou si l'un des comptes l'a laissé ouvert. */
    public boolean canalOuvert(String tenantId, String personId, Canal canal) {
        return canalOuvert(tenantId, personId, canal, null);
    }

    /** Idem, pour un événement du catalogue (ADR 0029) : le canal général <em>et</em> celui de l'événement. */
    public boolean canalOuvert(String tenantId, String personId, Canal canal, String evenement) {
        List<String> comptes = comptesDePersonne(tenantId, personId);
        return comptes.isEmpty() || comptes.stream().anyMatch(c -> preferencesDe(c).ouvert(canal, evenement));
    }
}
