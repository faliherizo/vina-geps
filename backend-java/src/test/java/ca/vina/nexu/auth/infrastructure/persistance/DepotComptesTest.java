package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.PolitiqueMotDePasse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Dépôt des comptes — création, unicité par établissement, modification champ par champ")
class DepotComptesTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private JdbcClient jdbc;
    private DepotComptes depot;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        depot = new DepotComptes(jdbc, HORLOGE);
    }

    private static DepotComptes.Creation creation(String nom, String mdp, String tenantId, boolean fort) {
        return new DepotComptes.Creation(nom, mdp, "employee", "emp-1", tenantId, true, true, "a@b.qc", fort);
    }

    @Test
    void leMemeIdentifiantVitDansDeuxEtablissementsMaisPasDeuxFoisDansLeMeme() {
        Compte a = depot.creer(creation("s.tremblay", "TempPass12", "t-1", false));
        Compte b = depot.creer(creation("s.tremblay", "TempPass12", "t-2", false));
        assertThat(a.username()).isEqualTo("s.tremblay@@t-1");
        assertThat(b.username()).isEqualTo("s.tremblay@@t-2");
        assertThat(a.id()).startsWith("usr-" + HORLOGE.millis() + "-");
        assertThat(a.createdAt()).isEqualTo("2026-09-13T12:00:00.000Z");
        assertThat(a.mustChangePassword()).isTrue();
        assertThat(a.securitySetupRequired()).isTrue();
        assertThat(depot.candidats("s.tremblay")).containsExactly("t-1", "t-2");
        assertThatThrownBy(() -> depot.creer(creation("s.tremblay", "TempPass12", "t-1", false))).hasMessage("username already exists");
    }

    @Test
    void lesMotsDePasseSontJugesSelonLeRegime() {
        assertThatThrownBy(() -> depot.creer(creation("x", "abc", null, false))).hasMessage("password too short");
        assertThatThrownBy(() -> depot.creer(creation("x", null, null, false))).hasMessage("password too short");
        assertThatThrownBy(() -> depot.creer(creation("x", "TempPass12", null, true)))
                .isInstanceOf(PolitiqueMotDePasse.MotDePasseFaibleException.class);
        Compte c = depot.creer(creation("x", "Tres-Solide-2026!", null, true));
        assertThat(depot.verifierMotDePasse(c, "Tres-Solide-2026!")).isTrue();
        assertThat(depot.verifierMotDePasse(c, "autre")).isFalse();
        assertThat(depot.verifierMotDePasse(c, null)).isFalse();
    }

    @Test
    void laModificationNeTouchQueLesChampsFournis() {
        Compte c = depot.creer(creation("carole", "TempPass12", "t-1", false));
        assertThat(depot.modifier(c.id(), DepotComptes.Modification.vide())).isEqualTo(c);
        Compte m = depot.modifier(c.id(), new DepotComptes.Modification("manager", false, "Nouveau-1", Optional.empty(), false, false, Optional.of("c@d.qc")));
        assertThat(m.role()).isEqualTo("manager");
        assertThat(m.active()).isFalse();
        assertThat(m.employeeId()).isNull();
        assertThat(m.email()).isEqualTo("c@d.qc");
        assertThat(m.mustChangePassword()).isFalse();
        assertThat(m.securitySetupRequired()).isFalse();
        assertThat(depot.verifierMotDePasse(m, "Nouveau-1")).isTrue();
        assertThatThrownBy(() -> depot.modifier(c.id(), new DepotComptes.Modification(null, null, "abc", null, null, null, null)))
                .hasMessage("password too short");
        assertThatThrownBy(() -> depot.modifier("absent", DepotComptes.Modification.vide()))
                .isInstanceOf(DepotComptes.IntrouvableException.class).hasMessage("user not found");
    }

    @Test
    void listesSuppressionEtSecurite() {
        Compte g = depot.creer(creation("admin", "admin", null, false));
        Compte l = depot.creer(creation("locale", "TempPass12", "t-1", false));
        assertThat(depot.lister()).extracting(Compte::id).containsExactly(g.id(), l.id());
        assertThat(depot.listerParTenant("t-1")).extracting(Compte::id).containsExactly(l.id());
        assertThat(depot.listerParTenant(null)).extracting(Compte::id).containsExactly(g.id());

        depot.mettreTotpEnAttente(l.id(), "SECRET");
        assertThat(depot.parId(l.id()).orElseThrow().pendingTotpSecret()).isEqualTo("SECRET");
        depot.activerMethodeTotp(l.id(), "SECRET");
        Compte totp = depot.parId(l.id()).orElseThrow();
        assertThat(totp.securityMethod()).isEqualTo("totp");
        assertThat(totp.totpSecret()).isEqualTo("SECRET");
        assertThat(totp.pendingTotpSecret()).isNull();
        assertThat(totp.securitySetupRequired()).isFalse();
        depot.reinitialiserSecurite(l.id());
        Compte r = depot.parId(l.id()).orElseThrow();
        assertThat(r.securityMethod()).isNull();
        assertThat(r.totpSecret()).isNull();
        assertThat(r.securitySetupRequired()).isTrue();

        assertThat(depot.supprimer(l.id())).isTrue();
        assertThat(depot.supprimer(l.id())).isFalse();
        assertThat(depot.parNom("locale", "t-1")).isEmpty();
    }

    @Test
    void lEtablissementDeLEmployeSeLitQuandIlExiste() {
        assertThat(depot.tenantDeLEmploye(null)).isNull();
        assertThat(depot.tenantDeLEmploye("emp-absent")).isNull();
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId) VALUES ('emp-1', 'P', 'Q', 't-9')").update();
        assertThat(depot.tenantDeLEmploye("emp-1")).isEqualTo("t-9");
        jdbc.sql("DROP TABLE employees").update();
        assertThat(depot.tenantDeLEmploye("emp-1")).isNull();
    }
}
