package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Disponibilité vue des cascades (critère 25) : absence acceptée, indisponibilité déclarée, jour de Montréal")
class DisponibiliteDepuisEffectifsTest {

    @Test
    @DisplayName("absente le jour d'une absence acceptée ou d'une indisponibilité ; disponible sinon, ou sans ressource")
    void calendrier(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        new SchemaPlanification(jdbc);
        new SchemaAbsencesEffectifs(jdbc);
        new SchemaDisponibilites(jdbc);
        for (String login : new String[]{"julie", "marc", "lea"}) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'T', 'x', 'employee', 'x')")
                    .params("u-" + login, login + "@@T", login).update();
            jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, actif, creeLe) VALUES (?, 'T', ?, ?, ?, 'EST', 'PARA', 1, 'x')")
                    .params("r-" + login, login, login, "u-" + login).update();
        }
        jdbc.sql("INSERT INTO eff_absences (id, tenantId, ressourceId, uniteCode, codeAbsence, du, au, statut, actionRegles, declareeLe, declareePar) "
                + "VALUES ('a1', 'T', 'r-julie', 'EST', 'MAL', '2026-11-16', '2026-11-17', 'ACCEPTEE', 'aucune', 'x', 'x'),"
                + " ('a2', 'T', 'r-lea', 'EST', 'MAL', '2026-11-16', '2026-11-17', 'A_APPROUVER', 'aucune', 'x', 'x')").update();
        jdbc.sql("INSERT INTO eff_disponibilites (id, tenantId, periodeId, ressourceId, date, debut, fin, type) VALUES ('d1', 'T', 'p', 'r-marc', '2026-11-16', 0, 1440, 'INDISPONIBLE')")
                .update();
        DisponibiliteDepuisEffectifs d = new DisponibiliteDepuisEffectifs(jdbc);
        assertThat(d.disponible("T", "julie", "2026-11-16T15:00:00Z")).isFalse();
        assertThat(d.disponible("T", "marc", "2026-11-16T15:00:00Z")).isFalse();
        // Une absence encore à approuver n'empêche rien.
        assertThat(d.disponible("T", "lea", "2026-11-16T15:00:00Z")).isTrue();
        // 1 h UTC le 17 : encore le 16 à Montréal — Marc est indisponible ; le 18, Julie revient.
        assertThat(d.disponible("T", "marc", "2026-11-17T01:00:00Z")).isFalse();
        assertThat(d.disponible("T", "julie", "2026-11-18T15:00:00Z")).isTrue();
        assertThat(d.disponible("T", "inconnu", "2026-11-16T15:00:00Z")).isTrue();
    }
}
