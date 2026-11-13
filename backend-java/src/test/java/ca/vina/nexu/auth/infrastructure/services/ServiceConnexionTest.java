package ca.vina.nexu.auth.infrastructure.services;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.QuestionsSecretes;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.securite.JetonDeSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Port de `login-tenant-resolution.test.ts` et des cas de `login` dans `auth.test.ts`. */
@DisplayName("Connexion — résolution de l'établissement, orphelins, second facteur")
class ServiceConnexionTest {

    private static final String SECRET = "secret-d-essai-de-trente-deux-octets-au-moins";
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-13T12:00:00Z"), ZoneOffset.UTC);
    private static final ServiceConnexion.Facteurs SANS = new ServiceConnexion.Facteurs(List.of(), "");

    private JdbcClient jdbc;
    private DepotComptes comptes;
    private DepotQuestionsSecretes questions;
    private ServiceConnexion service;
    private JetonDeSession jetons;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, HORLOGE);
        questions = new DepotQuestionsSecretes(jdbc, HORLOGE);
        jetons = new JetonDeSession(SECRET);
        service = new ServiceConnexion(comptes, questions, jetons, HORLOGE);
    }

    private Compte creer(String nom, String mdp, String tenantId, String employeeId) {
        return comptes.creer(new DepotComptes.Creation(nom, mdp, "employee", employeeId, tenantId, false, false, null, false));
    }

    @Test
    void unIdentifiantPresentDansUnSeulEtablissementSeConnecteDirectement() {
        creer("s.tremblay", "Mot-de-passe-1", "t-1", null);
        ServiceConnexion.Issue issue = service.connecter("s.tremblay", "Mot-de-passe-1", null, SANS);
        assertThat(issue).isInstanceOf(ServiceConnexion.Succes.class);
        JetonDeSession.Revendications r = jetons.lire(((ServiceConnexion.Succes) issue).jeton());
        assertThat(r.username()).isEqualTo("s.tremblay");
        assertThat(r.tenantId()).isEqualTo("t-1");
        assertThat(r.role()).isEqualTo("employee");
    }

    @Test
    void leChoixNEstDemandeQueSiLesIdentifiantsValentDansPlusieursEtablissements() {
        creer("s.tremblay", "Mot-de-passe-1", "t-1", null);
        creer("s.tremblay", "Mot-de-passe-1", "t-2", null);
        creer("s.tremblay", "Autre-mdp-9!", "t-3", null);
        assertThat(service.connecter("s.tremblay", "Mot-de-passe-1", null, SANS))
                .isEqualTo(new ServiceConnexion.ChoixEtablissement(List.of("t-1", "t-2")));
        // Le mot de passe cloisonne : l'homonyme de t-3 se connecte seul, sans choix.
        assertThat(service.connecter("s.tremblay", "Autre-mdp-9!", null, SANS)).isInstanceOf(ServiceConnexion.Succes.class);
        // Avec l'établissement fourni, la connexion est directe.
        assertThat(service.connecter("s.tremblay", "Mot-de-passe-1", "t-2", SANS)).isInstanceOf(ServiceConnexion.Succes.class);
    }

    @Test
    void rienNEstReveleSansMotDePasseValide() {
        creer("s.tremblay", "Mot-de-passe-1", "t-1", null);
        creer("s.tremblay", "Mot-de-passe-1", "t-2", null);
        assertThat(service.etablissementsAuthentifies("s.tremblay", "faux")).isEmpty();
        assertThat(service.etablissementsAuthentifies("  ", "faux")).isEmpty();
        assertThat(service.connecter("s.tremblay", "faux", null, SANS)).isEqualTo(new ServiceConnexion.Refus());
        assertThat(service.connecter("inconnu", "faux", null, SANS)).isEqualTo(new ServiceConnexion.Refus());
    }

    @Test
    void unCompteGlobalSeConnecteMalgreUnEnTeteResiduelEtUnCompteLocalNeTraverseRien() {
        creer("admin", "admin", null, null);
        creer("locale", "p4ssword", "T1", null);
        ServiceConnexion.Issue global = service.connecter("admin", "admin", "un-tenant-quelconque", SANS);
        assertThat(global).isInstanceOf(ServiceConnexion.Succes.class);
        assertThat(((ServiceConnexion.Succes) global).compte().tenantId()).isNull();
        assertThat(service.connecter("locale", "p4ssword", "T1", SANS)).isInstanceOf(ServiceConnexion.Succes.class);
        assertThat(service.connecter("locale", "p4ssword", "T2", SANS)).isEqualTo(new ServiceConnexion.Refus());
    }

    @Test
    void unCompteInactifEstRefuseEtUnOrphelinNomme() {
        Compte c = creer("carole", "TempPass12", null, null);
        comptes.modifier(c.id(), new DepotComptes.Modification(null, false, null, null, null, null, null));
        assertThat(service.connecter("carole", "TempPass12", null, SANS)).isEqualTo(new ServiceConnexion.Refus());

        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId) VALUES ('emp-1', 'P', 'Q', 't-9')").update();
        creer("prof", "Mot-de-passe-1", null, "emp-1");
        assertThat(service.connecter("prof", "Mot-de-passe-1", null, SANS)).isEqualTo(new ServiceConnexion.Orphelin());
        assertThat(service.etablissementsAuthentifies("prof", "Mot-de-passe-1")).isEmpty();
        // Une fois rattaché, le même compte passe.
        Compte orphelin = comptes.parNom("prof", null).orElseThrow();
        jdbc.sql("UPDATE users SET tenantId = 't-9', username = 'prof@@t-9' WHERE id = ?").params(orphelin.id()).update();
        assertThat(service.connecter("prof", "Mot-de-passe-1", null, SANS)).isInstanceOf(ServiceConnexion.Succes.class);
    }

    @Test
    void leSecondFacteurParQuestionsEstExigePuisAccepte() {
        Compte c = creer("qauser", "TempSecret1", null, null);
        questions.definir(c.id(), List.of(new QuestionsSecretes.Paire("Ville de naissance ?", "Lyon"),
                new QuestionsSecretes.Paire("Prénom de votre mère ?", "Marie"), new QuestionsSecretes.Paire("Premier animal ?", "Rex")));
        comptes.activerMethodeQuestion(c.id());
        ServiceConnexion.Issue etape1 = service.connecter("qauser", "TempSecret1", null, SANS);
        assertThat(etape1).isInstanceOf(ServiceConnexion.SecondFacteur.class);
        assertThat(((ServiceConnexion.SecondFacteur) etape1).questions()).containsExactly("Ville de naissance ?", "Prénom de votre mère ?", "Premier animal ?");
        assertThat(service.connecter("qauser", "TempSecret1", null, new ServiceConnexion.Facteurs(List.of("lyon", "MARIE", " Rex "), "")))
                .isInstanceOf(ServiceConnexion.Succes.class);
        assertThat(service.connecter("qauser", "TempSecret1", null, new ServiceConnexion.Facteurs(List.of("Lyon", "Marie"), "")))
                .isInstanceOf(ServiceConnexion.SecondFacteur.class);
    }

    @Test
    void leSecondFacteurTotpEstExigePuisAccepte() {
        Compte c = creer("totpuser", "TempSecret1", null, null);
        String secret = Totp.encoderBase32("12345678901234567890".getBytes());
        comptes.activerMethodeTotp(c.id(), secret);
        ServiceConnexion.Issue etape1 = service.connecter("totpuser", "TempSecret1", null, SANS);
        assertThat(etape1).isEqualTo(new ServiceConnexion.SecondFacteur(comptes.parId(c.id()).orElseThrow(), "totp", List.of()));
        assertThat(service.connecter("totpuser", "TempSecret1", null, new ServiceConnexion.Facteurs(List.of(), Totp.code(secret, HORLOGE.millis()))))
                .isInstanceOf(ServiceConnexion.Succes.class);
        assertThat(service.connecter("totpuser", "TempSecret1", null, new ServiceConnexion.Facteurs(List.of(), null)))
                .isInstanceOf(ServiceConnexion.SecondFacteur.class);
    }

    @Test
    void unCompteDontLaSecuriteResteAConfigurerNEstPasInterroge() {
        Compte c = comptes.creer(new DepotComptes.Creation("neuf", "TempSecret1", "employee", null, null, true, true, null, false));
        comptes.activerMethodeTotp(c.id(), "GEZDGNBVGY3TQOJQ");
        comptes.modifier(c.id(), new DepotComptes.Modification(null, null, null, null, null, true, null));
        assertThat(service.connecter("neuf", "TempSecret1", null, SANS)).isInstanceOf(ServiceConnexion.Succes.class);
    }
}
