package ca.vina.nexu.auth.application;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.DemandesLiaison;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.Invitations;
import ca.vina.nexu.auth.infrastructure.configuration.ConfigurationIdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurInvitationsLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotDemandesLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotIdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotInvitations;
import ca.vina.nexu.partage.crypto.Primitives;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La résolution d'identité et l'approbation, sur la vraie base SQLite et les vrais adaptateurs : les décisions
 * que les essais HTTP n'atteignent pas (échéance de la liaison, course entre deux preuves, compte disparu,
 * fournisseur d'instance sans organisme, invitation échue ou d'un autre organisme).
 */
@DisplayName("ServiceResolutionIdentite et ServiceApprobationsLiaison — décisions aux limites")
class ServiceResolutionIdentiteTest {

    /** Une horloge que l'essai avance. */
    private static final class Horloge extends Clock {
        Instant maintenant = Instant.parse("2031-01-15T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return maintenant;
        }
    }

    private static final String MDP = "Passe!Essai2031";
    private final Horloge horloge = new Horloge();
    private JdbcClient jdbc;
    private DepotComptes comptes;
    private DepotIdentitesExternes identites;
    private DepotInvitations invitations;
    private ServiceResolutionIdentite.Ports ports;
    private ServiceResolutionIdentite service;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        comptes = new DepotComptes(jdbc, horloge);
        identites = new DepotIdentitesExternes(jdbc);
        invitations = new DepotInvitations(jdbc);
        ConfigurationIdentitesExternes configuration = new ConfigurationIdentitesExternes();
        ports = configuration.portsIdentitesExternes(identites, comptes, invitations, new DepotDemandesLiaison(jdbc), horloge);
        service = configuration.serviceResolutionIdentite(ports, horloge);
        assertThat(configuration.serviceApprobationsLiaison(ports, horloge).enAttente("T1")).isEmpty();
    }

    private static IdentitesExternes.IdentiteVerifiee identite(String sujet, String courriel, IdentitesExternes.Provenance p) {
        return new IdentitesExternes.IdentiteVerifiee(IdentitesExternes.Cle.de("https://idp", sujet), courriel, p);
    }

    private static final IdentitesExternes.Provenance T1 = IdentitesExternes.Provenance.annuaire("ann-1", "T1");
    private static final ServiceResolutionIdentite.Admission SANS_JAT = new ServiceResolutionIdentite.Admission(Optional.of("manager"), true, false);

    private Compte compte(String nom, String tenant) {
        return comptes.creer(new DepotComptes.Creation(nom, MDP, "employee", null, tenant, false, false, nom + "@x.ca", false));
    }

    private String jeton(ServiceResolutionIdentite.Issue issue) {
        return ((ServiceResolutionIdentite.LiaisonRequise) issue).jeton();
    }

    @Test
    @DisplayName("la liaison en cours expire après dix minutes ; une identité reliée entre-temps par une autre preuve n'est pas reliée deux fois")
    void echeanceEtCourse() {
        compte("jean", "T1");
        String jeton = jeton(service.resoudre(identite("s1", "jean@x.ca", T1), SANS_JAT));
        horloge.maintenant = horloge.maintenant.plus(Duration.ofMinutes(11));
        assertThatThrownBy(() -> service.etat(jeton)).isInstanceOf(IdentitesExternes.LiaisonRefuseeException.class)
                .hasMessageContaining("aucune liaison en cours");
        assertThatThrownBy(() -> service.etat(null)).hasMessageContaining("aucune liaison en cours");

        String j1 = jeton(service.resoudre(identite("s2", "jean@x.ca", T1), SANS_JAT));
        String j2 = jeton(service.resoudre(identite("s2", "jean@x.ca", T1), SANS_JAT));
        assertThat(service.prouverParMotDePasse(j1, "", MDP).compte().role()).isEqualTo("manager");
        assertThatThrownBy(() -> service.prouverParMotDePasse(j2, "jean", MDP)).hasMessage("cette identité est déjà reliée à un compte");
    }

    @Test
    @DisplayName("fournisseur d'instance : sans organisme, le juste-à-temps crée un compte global ; le rôle d'un compte existant n'est pas reclassé")
    void instance() {
        ServiceResolutionIdentite.Admission jat = new ServiceResolutionIdentite.Admission(Optional.of("rh"), false, true);
        ServiceResolutionIdentite.Reconnue r = (ServiceResolutionIdentite.Reconnue) service.resoudre(identite("s1", "neuf@x.ca", IdentitesExternes.Provenance.instance()), jat);
        assertThat(r.cree()).isTrue();
        assertThat(r.compte().tenantId()).isNull();
        assertThat(r.compte().role()).isEqualTo("rh");
        ServiceResolutionIdentite.Reconnue encore = (ServiceResolutionIdentite.Reconnue) service.resoudre(identite("s1", "neuf@x.ca", IdentitesExternes.Provenance.instance()),
                new ServiceResolutionIdentite.Admission(Optional.of("admin"), false, true));
        assertThat(encore.compte().role()).isEqualTo("rh");
        assertThat(encore.cree()).isFalse();
        // Sans rôle, pas de juste-à-temps.
        assertThat(service.resoudre(identite("s9", "autre@x.ca", IdentitesExternes.Provenance.instance()), new ServiceResolutionIdentite.Admission(Optional.empty(), false, true)))
                .isInstanceOf(ServiceResolutionIdentite.LiaisonRequise.class);
        // Un compte global n'a pas d'administration pour approuver ; deux comptes au même courriel ne désignent personne.
        compte("glob", null);
        String j = jeton(service.resoudre(identite("s3", "glob@x.ca", IdentitesExternes.Provenance.instance()), SANS_JAT));
        assertThatThrownBy(() -> service.demanderApprobation(j)).hasMessageContaining("aucun compte de l'organisme");
        comptes.creer(new DepotComptes.Creation("glob", MDP, "employee", null, "T1", false, false, "glob@x.ca", false));
        assertThat(new AdaptateurComptesAnnuaire(comptes, new Random(1)).parCourriel(Optional.empty(), "glob@x.ca")).isEmpty();
        assertThat(new AdaptateurComptesAnnuaire(comptes, new Random(1)).authentifier(Optional.empty(), "glob", MDP)).isEmpty();
        assertThat(new AdaptateurComptesAnnuaire(comptes, new Random(1)).authentifier(Optional.of("T1"), "glob", MDP)).isPresent();
    }

    @Test
    @DisplayName("l'approbation refuse un compte disparu ou une identité déjà reliée entre la demande et la décision")
    void approbationAuxLimites() {
        ServiceApprobationsLiaison approbations = new ServiceApprobationsLiaison(ports, horloge);
        Compte jean = compte("jean", "T1");
        DemandesLiaison.Demande d = service.demanderApprobation(jeton(service.resoudre(identite("s1", "jean@x.ca", T1), SANS_JAT)));
        assertThat(d.annuaireId()).contains("ann-1");
        service.prouverParMotDePasse(jeton(service.resoudre(identite("s1", "jean@x.ca", T1), SANS_JAT)), "jean", MDP);
        assertThatThrownBy(() -> approbations.approuver("T1", d.id(), "adm")).hasMessage("cette identité est déjà reliée à un compte");

        Compte lea = compte("lea", "T1");
        DemandesLiaison.Demande dl = service.demanderApprobation(jeton(service.resoudre(identite("s2", "lea@x.ca", T1), SANS_JAT)));
        jdbc.sql("DELETE FROM users WHERE id = ?").params(lea.id()).update();
        assertThatThrownBy(() -> approbations.approuver("T1", dl.id(), "adm")).hasMessage("le compte relié à cette identité n'existe plus");
        assertThatThrownBy(() -> approbations.refuser("T1", "inconnue", "adm")).hasMessage("demande de liaison introuvable ou déjà traitée");
        assertThat(approbations.enAttente("T1")).extracting(DemandesLiaison.Demande::id).containsExactlyInAnyOrder(d.id(), dl.id());
        assertThat(jean.id()).isNotNull();
    }

    @Test
    @DisplayName("l'invitation ne prouve rien si elle est échue, révoquée, d'un autre organisme ou d'un autre courriel ; le lien se réduit à son jeton")
    void invitations() {
        AdaptateurInvitationsLiaison adaptateur = new AdaptateurInvitationsLiaison(invitations, comptes, horloge, new Random(2));
        poser("i1", "T1", "nadia@x.ca", "j-nadia", "2031-02-01T00:00:00.000Z", null);
        poser("i2", "T1", "echue@x.ca", "j-echue", "2031-01-01T00:00:00.000Z", null);
        poser("i3", "T1", "rev@x.ca", "j-rev", "2031-02-01T00:00:00.000Z", "2031-01-14T00:00:00.000Z");
        assertThat(adaptateur.accepter("j-nadia", "nadia@x.ca", Optional.of("T2"))).isEmpty();
        assertThat(adaptateur.accepter("j-nadia", "autre@x.ca", Optional.of("T1"))).isEmpty();
        assertThat(adaptateur.accepter("j-echue", "echue@x.ca", Optional.of("T1"))).isEmpty();
        assertThat(adaptateur.accepter("j-rev", "rev@x.ca", Optional.of("T1"))).isEmpty();
        assertThat(adaptateur.accepter("https://app/admin/invitation/j-nadia#x", "NADIA@x.ca", Optional.empty())).isPresent();
        assertThat(adaptateur.accepter("j-nadia", "nadia@x.ca", Optional.of("T1"))).isEmpty();
        assertThat(List.of("j-a", "https://h/admin/invitation/j-a", "https://h/admin/invitation/j-a?b=1", " j-a/ ")).allSatisfy(s ->
                assertThat(adaptateur.accepter(s, "x@x.ca", Optional.empty())).isEmpty());
    }

    private void poser(String id, String tenant, String courriel, String jeton, String expire, String revoquee) {
        invitations.poser(new Invitations.Invitation(id, tenant, courriel, courriel.substring(0, courriel.indexOf('@')), "employee", null, expire, null, revoquee,
                "2031-01-10T00:00:00.000Z", "adm", null), HexFormat.of().formatHex(Primitives.sha256(jeton.getBytes(StandardCharsets.UTF_8))));
        if (revoquee != null) {
            invitations.revoquer(id, revoquee);
        }
    }
}
