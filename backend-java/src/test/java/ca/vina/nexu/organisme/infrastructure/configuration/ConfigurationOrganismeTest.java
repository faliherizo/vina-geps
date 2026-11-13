package ca.vina.nexu.organisme.infrastructure.configuration;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.domaine.RestrictionPaysConnexion;
import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.infrastructure.persistance.AdaptateurEtablissementsPourOrganisme;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotCharteOrganisme;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotJournalAlertes;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotPaysAutorises;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotReglesAlerte;
import ca.vina.nexu.organisme.infrastructure.persistance.SchemaOrganisme;
import ca.vina.nexu.organisme.infrastructure.web.AlerteTelechargement;
import ca.vina.nexu.organisme.infrastructure.web.ControleurOrganisme;
import ca.vina.nexu.organisme.infrastructure.web.GardeOrganisme;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Le câblage de l'organisme, bean par bean, et le signalement différé : une alerte qui échoue — à la remise
 * au fil comme à l'envoi — se journalise sans rien lever.
 */
@DisplayName("Options de l'organisme — câblage et signalement différé")
class ConfigurationOrganismeTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Circonstances C = new Circonstances("julie", "2031-01-15T10:00:00.000Z", "x.csv (GET /api/x)");

    /** Donne accès aux intercepteurs posés, que le registre garde pour Spring. */
    private static final class Registre extends InterceptorRegistry {
        List<Object> poses() {
            return getInterceptors();
        }
    }

    private static Sequence sequence() {
        AtomicInteger n = new AtomicInteger();
        return new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "uuid-" + n.incrementAndGet();
            }

            @Override
            public String jetonSignature() {
                return "j";
            }

            @Override
            public String jetonBase64Url(int octets) {
                return "s";
            }
        };
    }

    @Test
    @DisplayName("chaque bean se construit, et la restriction offerte à l'authentification est bien celle de l'organisme")
    void cablage(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        SchemaOrganisme schema = new SchemaOrganisme(jdbc);
        ConfigurationOrganisme config = new ConfigurationOrganisme();
        DepotCharteOrganisme chartes = new DepotCharteOrganisme(jdbc, schema);
        DepotPaysAutorises pays = new DepotPaysAutorises(jdbc, schema);
        DepotReglesAlerte regles = new DepotReglesAlerte(jdbc, schema);
        DepotJournalAlertes journal = new DepotJournalAlertes(jdbc, schema);
        ReglagesPays reglages = config.organismeReglagesPays("X-Pays");
        LectureOptionsOrganisme lecture = config.organismeLectureOptions(chartes, pays, regles, journal, reglages);
        Sequence sequence = sequence();
        CourrierJournal courrier = new CourrierJournal(HORLOGE);
        SignalementDiffere signalement = config.organismeSignalement(regles, journal, courrier, sequence);
        RestrictionPaysConnexion restriction = config.organismeRestrictionPaysConnexion(config.organismeGardeConnexionPays(pays, signalement, reglages, HORLOGE));
        assertThat(new ControleurOrganisme(new GardeOrganisme(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurEtablissementsPourOrganisme(jdbc), HORLOGE),
                lecture, config.organismeGestionnaireCharte(chartes, lecture), config.organismeGestionnairePays(pays, lecture, reglages),
                config.organismeGestionnaireRegles(regles, sequence))).isNotNull();
        pays.enregistrer("t-1", List.of("CA"), new Modification("2031-01-01T00:00:00.000Z", "usr-admin"));
        assertThat(restriction.refus("t-1", nom -> "FR", "julie")).contains("connexion refusée depuis ce pays (FR) : cet organisme ne l'autorise pas");
        assertThat(restriction.refus("t-1", nom -> "CA", "julie")).isEmpty();
        assertThat(RestrictionPaysConnexion.AUCUNE.refus("t-1", nom -> "FR", "julie")).isEmpty();
    }

    @Test
    @DisplayName("l'alerte au téléchargement est posée sur toute l'API")
    void intercepteur() {
        Registre registre = new Registre();
        AlerteTelechargement alerte = new AlerteTelechargement((t, e, c) -> { }, HORLOGE);
        new ConfigurationWebOrganisme(alerte).addInterceptors(registre);
        assertThat(registre.poses()).hasSize(1);
    }

    @Test
    @DisplayName("le signalement part sur l'exécuteur ; un refus de l'exécuteur ou une panne de l'envoi ne lève rien")
    void signalementDiffere() {
        AtomicInteger recus = new AtomicInteger();
        new SignalementDiffere((t, e, c) -> recus.incrementAndGet(), Runnable::run).signaler("t-1", EvenementAlerte.EXPORT_TELECHARGE, C);
        assertThat(recus).hasValue(1);
        assertThatCode(() -> new SignalementDiffere((t, e, c) -> {
            throw new IllegalStateException("base indisponible");
        }, Runnable::run).signaler("t-1", EvenementAlerte.EXPORT_TELECHARGE, C)).doesNotThrowAnyException();
        assertThatCode(() -> new SignalementDiffere((t, e, c) -> recus.incrementAndGet(), tache -> {
            throw new RejectedExecutionException("fermé");
        }).signaler("t-1", EvenementAlerte.CONNEXION_REFUSEE_PAYS, C)).doesNotThrowAnyException();
        assertThat(recus).hasValue(1);
    }
}
