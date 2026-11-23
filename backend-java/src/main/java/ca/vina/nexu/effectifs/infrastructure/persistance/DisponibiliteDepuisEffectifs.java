package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.effectifs.domaine.AbsencesEffectifs;
import ca.vina.nexu.effectifs.domaine.Disponibilites;
import ca.vina.nexu.effectifs.domaine.PresenceEffectifs;
import ca.vina.nexu.partage.disponibilite.DisponibiliteDestinatairesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;

/**
 * Le calendrier interne des effectifs, vu des cascades (FX-AO-20260810, critère 25) : une personne n'est pas
 * disponible le jour (à Montréal) où une absence <strong>acceptée</strong> la couvre, ni celui où elle s'est déclarée
 * indisponible. Une personne sans ressource d'effectifs n'a pas de calendrier : elle est disponible.
 */
@Component
public class DisponibiliteDepuisEffectifs implements DisponibiliteDestinatairesPort {

    private static final String RESSOURCE = "SELECT r.id FROM eff_ressources r JOIN users u ON u.id = r.utilisateurId "
            + "WHERE r.tenantId = ? AND COALESCE(u.loginName, u.username) = ?";

    private final JdbcClient jdbc;

    public DisponibiliteDepuisEffectifs(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean disponible(String tenantId, String personId, String instant) {
        String jour = Instant.parse(instant).atZone(ZoneId.of(PresenceEffectifs.FUSEAU)).toLocalDate().toString();
        return jdbc.sql(RESSOURCE).params(tenantId, personId).query(String.class).list().stream().noneMatch(r -> empeche(tenantId, r, jour));
    }

    private boolean empeche(String tenantId, String ressourceId, String jour) {
        int absences = jdbc.sql("SELECT COUNT(*) FROM eff_absences WHERE tenantId = ? AND ressourceId = ? AND statut = ? AND du <= ? AND au >= ?")
                .params(tenantId, ressourceId, AbsencesEffectifs.Statut.ACCEPTEE.code(), jour, jour).query(Integer.class).single();
        int indisponibilites = jdbc.sql("SELECT COUNT(*) FROM eff_disponibilites WHERE tenantId = ? AND ressourceId = ? AND date = ? AND type = ?")
                .params(tenantId, ressourceId, jour, Disponibilites.TypeDisponibilite.INDISPONIBLE.name()).query(Integer.class).single();
        return absences + indisponibilites > 0;
    }
}
