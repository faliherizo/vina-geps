package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Groupes de travail de la communication — les unités en vigueur, leurs ressources actives qui ont un compte")
class AdaptateurGroupesDeTravailTest {

    private static final String T = "T";

    @Test
    @DisplayName("seules les unités en vigueur sont des groupes ; un membre est une ressource active de l'unité, liée à un compte, nommée par son nom de connexion")
    void groupesEtMembres(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        new SchemaPlanification(jdbc);
        String unite = "INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, comportement, attributs, effectifDepuis, effectifJusqua, creeLe, creePar) "
                + "VALUES (?, ?, 'unites', ?, ?, NULL, '{}', ?, ?, 'x', 'adm')";
        jdbc.sql(unite).params("r-1", T, "EST", "Secteur Est", "2026-01-01", null).update();
        jdbc.sql(unite).params("r-2", T, "VIEUX", "Ancien secteur", "2020-01-01", "2025-12-31").update();
        // La forme réelle d'un compte d'organisme : la clé technique « login@@organisme », et le nom de connexion à part.
        jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, createdAt) VALUES ('u-roy', 'roy@@T', 'roy', ?, 'x', 'employee', 'x'), "
                 + "('u-ngu', 'ngu@@T', 'ngu', ?, 'x', 'employee', 'x'), ('u-ancien', 'ancien', NULL, ?, 'x', 'employee', 'x')")
                .params(T, T, T).update();
        String ressource = "INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, titreEmploiCode, statutEmploiCode, conventionCode, actif, creeLe) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'PARA', 'TECH', 'TC', 'FSSS', ?, 'x')";
        jdbc.sql(ressource).params("rs-1", T, "P1", "Julie Roy", "u-roy", "EST", 1).update();
        jdbc.sql(ressource).params("rs-2", T, "P2", "Inactive", "u-ngu", "EST", 0).update();
        jdbc.sql(ressource).params("rs-3", T, "P3", "Sans compte", null, "EST", 1).update();

        AdaptateurGroupesDeTravail groupes = new AdaptateurGroupesDeTravail(new DepotReferentiels(jdbc, schema), jdbc,
                Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
        assertThat(groupes.groupes(T)).containsExactly(new GroupesDeTravailPort.Groupe("EST", "EST", "Secteur Est"));
        assertThat(groupes.membres(T, "EST")).containsExactly(new GroupesDeTravailPort.Membre("roy", "Julie Roy"));
        assertThat(groupes.membres("autre", "EST")).isEmpty();
        // L'unité d'une personne, d'une seule lecture : une ressource inactive ou une unité fermée ne compte pas.
        jdbc.sql(ressource).params("rs-4", T, "P4", "Ngu ancien", "u-ngu", "VIEUX", 1).update();
        assertThat(groupes.groupesDe(T, "roy")).containsExactly("EST");
        assertThat(groupes.groupesDe(T, "ngu")).isEmpty();
        assertThat(groupes.groupesDe(T, "personne")).isEmpty();
        // Jamais la clé technique : c'est le nom que porte l'acteur de la messagerie qu'on rend et qu'on cherche.
        assertThat(groupes.groupesDe(T, "roy@@T")).isEmpty();
        // Un compte sans nom de connexion (antérieur à la colonne) garde sa clé comme nom.
        jdbc.sql(ressource).params("rs-5", T, "P5", "Ancien compte", "u-ancien", "EST", 1).update();
        assertThat(groupes.membres(T, "EST")).extracting(GroupesDeTravailPort.Membre::personId).containsExactly("ancien", "roy");
        assertThat(groupes.groupesDe(T, "ancien")).containsExactly("EST");
    }
}
