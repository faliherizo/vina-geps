package ca.vina.nexu.organisme.application;

import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.organisme.application.commandes.DeclencheurAlertes;
import ca.vina.nexu.organisme.application.requetes.GardeConnexionPays;
import ca.vina.nexu.organisme.domaine.AlerteConsignee;
import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.RegleAlerte;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotJournalAlertes;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotPaysAutorises;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotReglesAlerte;
import ca.vina.nexu.organisme.infrastructure.persistance.SchemaOrganisme;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>Le déclenchement des alertes et la garde de connexion par pays</strong> (lot R11), sur une vraie base :
 * chaque déclenchement se consigne, courriel parti ou non ; un compte global n'est pas concerné par la
 * restriction d'un organisme ; le pays se lit dans l'en-tête nommé, et seulement là.
 */
@DisplayName("Options de l'organisme — alertes et connexion par pays")
class AlertesEtConnexionTest {

    private static final String T = "t-1";
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final Circonstances EXPORT = new Circonstances("julie", "2031-01-15T10:00:00.000Z", "paie.csv (GET /api/paie)");

    private DepotReglesAlerte regles;
    private DepotJournalAlertes journal;
    private DepotPaysAutorises pays;
    private final List<Courrier.Message> envoyes = new ArrayList<>();
    private final AtomicInteger n = new AtomicInteger();

    private final Sequence sequence = new Sequence() {
        @Override
        public String suivant(String prefixe) {
            return prefixe + "-" + n.incrementAndGet();
        }

        @Override
        public String uuid() {
            return "uuid-" + n.incrementAndGet();
        }

        @Override
        public String jetonSignature() {
            return "jeton";
        }

        @Override
        public String jetonBase64Url(int octets) {
            return "secret";
        }
    };

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        SchemaOrganisme schema = new SchemaOrganisme(jdbc);
        regles = new DepotReglesAlerte(jdbc, schema);
        journal = new DepotJournalAlertes(jdbc, schema);
        pays = new DepotPaysAutorises(jdbc, schema);
    }

    private void regle(String id, EvenementAlerte e, String... destinataires) {
        regles.inserer(T, new RegleAlerte(id, e, List.of(destinataires), "2031-01-01T00:00:00.000Z", "usr-admin"));
    }

    private DeclencheurAlertes declencheur(Courrier courrier) {
        return new DeclencheurAlertes(regles, journal, courrier, sequence);
    }

    @Test
    @DisplayName("seules les règles de l'événement se déclenchent : un courriel par destinataire, une ligne par règle")
    void declenchement() {
        regle("r-1", EvenementAlerte.EXPORT_TELECHARGE, "a@x.ca", "b@x.ca");
        regle("r-2", EvenementAlerte.CONNEXION_REFUSEE_PAYS, "c@x.ca");
        int declenchees = declencheur(m -> envoyes.add(m)).alerter(T, EvenementAlerte.EXPORT_TELECHARGE, EXPORT);
        assertThat(declenchees).isEqualTo(1);
        assertThat(envoyes).extracting(Courrier.Message::to).containsExactly("a@x.ca", "b@x.ca");
        assertThat(journal.lire(T, 10)).containsExactly(new AlerteConsignee("uuid-1", "r-1", "EXPORT_TELECHARGE", "paie.csv (GET /api/paie)", "julie", 2, null,
                "2031-01-15T10:00:00.000Z", List.of("a@x.ca", "b@x.ca")));
        assertThat(declencheur(m -> true).alerter("t-autre", EvenementAlerte.EXPORT_TELECHARGE, EXPORT)).isZero();
    }

    @Test
    @DisplayName("un courriel qui ne part pas reste visible au journal, avec la raison — bornée à 500 caractères")
    void echecs() {
        regle("r-1", EvenementAlerte.EXPORT_TELECHARGE, "refuse@x.ca", "panne@x.ca", "muet@x.ca", "ok@x.ca");
        Map<String, RuntimeException> pannes = Map.of("panne@x.ca", new IllegalStateException("smtp injoignable"), "muet@x.ca", new IllegalStateException());
        declencheur(m -> {
            if (pannes.containsKey(m.to())) {
                throw pannes.get(m.to());
            }
            return !m.to().startsWith("refuse");
        }).signaler(T, EvenementAlerte.EXPORT_TELECHARGE, EXPORT);
        AlerteConsignee a = journal.lire(T, 10).get(0);
        assertThat(a.envoyees()).isEqualTo(1);
        assertThat(a.erreur()).isEqualTo("envoi refusé par le transport de courriel ; smtp injoignable ; envoi refusé par le transport de courriel");
        regle("r-2", EvenementAlerte.CONNEXION_REFUSEE_PAYS, "long@x.ca");
        declencheur(m -> {
            throw new IllegalStateException("x".repeat(600));
        }).alerter(T, EvenementAlerte.CONNEXION_REFUSEE_PAYS, EXPORT);
        assertThat(journal.lire(T, 10)).extracting(AlerteConsignee::regleId).containsExactly("r-2", "r-1");
        assertThat(journal.lire(T, 1).get(0).erreur()).hasSize(500);
    }

    private GardeConnexionPays garde(String entete, DeclencheurAlertes declencheur) {
        return new GardeConnexionPays(pays, declencheur, new ReglagesPays(entete), HORLOGE);
    }

    private static UnaryOperator<String> entetes(String nom, String valeur) {
        return demande -> demande.equalsIgnoreCase(nom) ? valeur : null;
    }

    @Test
    @DisplayName("la connexion se juge au pays : Canada admis, France refusée et signalée, pays inconnu refusé, compte global jamais concerné")
    void connexion() {
        regle("r-1", EvenementAlerte.CONNEXION_REFUSEE_PAYS, "securite@x.ca");
        GardeConnexionPays garde = garde("X-Pays", declencheur(m -> envoyes.add(m)));
        assertThat(garde.refus(T, entetes("x-pays", "FR"), "julie")).isEmpty();
        pays.enregistrer(T, List.of("CA"), new Modification("2031-01-01T00:00:00.000Z", "usr-admin"));
        assertThat(garde.refus(T, entetes("x-pays", "ca"), "julie")).isEmpty();
        assertThat(garde.refus(T, entetes("x-pays", "FR"), "julie")).contains("connexion refusée depuis ce pays (FR) : cet organisme ne l'autorise pas");
        assertThat(envoyes.get(0).text()).contains("Qui : julie\nQuand : 2031-01-15T10:00:00.000Z\nQuoi : connexion depuis FR");
        assertThat(garde.refus(T, entetes("x-pays", null), "julie")).hasValueSatisfying(m -> assertThat(m).contains("le pays d'origine n'a pas pu être établi"));
        assertThat(envoyes.get(1).text()).contains("Quoi : connexion depuis un pays inconnu");
        assertThat(garde.refus(null, entetes("x-pays", "FR"), "admin")).isEmpty();
        assertThat(garde.refus("", entetes("x-pays", "FR"), "admin")).isEmpty();
        assertThat(envoyes).hasSize(2);
    }

    @Test
    @DisplayName("le pays se lit dans l'en-tête nommé, et seulement là")
    void enteteNomme() {
        GardeConnexionPays garde = garde("CloudFront-Viewer-Country", declencheur(m -> true));
        assertThat(garde.paysDeLaRequete(entetes("cloudfront-viewer-country", "ca"))).isEqualTo("CA");
        assertThat(garde.paysDeLaRequete(entetes("cloudfront-viewer-country", "Canada"))).isNull();
        assertThat(garde("", declencheur(m -> true)).paysDeLaRequete(entetes("x-pays", "CA"))).isNull();
        assertThat(garde(null, declencheur(m -> true)).paysDeLaRequete(entetes("x-pays", "CA"))).isNull();
    }
}
