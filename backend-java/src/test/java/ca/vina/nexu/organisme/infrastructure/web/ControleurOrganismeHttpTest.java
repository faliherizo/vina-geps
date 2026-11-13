package ca.vina.nexu.organisme.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.organisme.application.commandes.DeclencheurAlertes;
import ca.vina.nexu.organisme.application.commandes.GestionnaireCharteOrganisme;
import ca.vina.nexu.organisme.application.commandes.GestionnairePaysAutorises;
import ca.vina.nexu.organisme.application.commandes.GestionnaireReglesAlerte;
import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.infrastructure.persistance.AdaptateurEtablissementsPourOrganisme;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotCharteOrganisme;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotJournalAlertes;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotPaysAutorises;
import ca.vina.nexu.organisme.infrastructure.persistance.DepotReglesAlerte;
import ca.vina.nexu.organisme.infrastructure.persistance.SchemaOrganisme;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Options de l'organisme (R11) par HTTP</strong> — jumeau de {@code organisme-options.integration.test.ts} :
 * l'administration d'Urgences-santé pose sa charte (refusée si elle rend un texte illisible), restreint les
 * connexions au Canada et demande d'être prévenue des exports. Vraie base SQLite, horloge figée au 15 janvier
 * 2031, séquence déterministe.
 */
@DisplayName("/api/organisme — charte, pays autorisés, règles et journal des alertes")
class ControleurOrganismeHttpTest {

    private static final String T = "t-1";
    private static final String O = "/api/organisme";
    private static final String PNG = "data:image/png;base64," + "A".repeat(40);
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete DIR = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);

    private JdbcClient jdbc;
    private CourrierJournal courrier;
    private DepotPaysAutorises depotPays;
    private DepotReglesAlerte depotRegles;
    private DepotJournalAlertes depotJournal;
    private DepotCharteOrganisme depotCharte;
    private GardeOrganisme garde;
    private Sequence sequence;

    /** Une séquence déterministe : les identifiants se lisent dans les essais. */
    private static Sequence sequence() {
        AtomicInteger n = new AtomicInteger();
        return new Sequence() {
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
                return "secret-" + octets;
            }
        };
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, status) VALUES ('t-1', 'Urgences', 'active'), ('t-susp', 'Suspendu', 'suspended'), "
                + "('t-fin', 'Résilié', 'terminated')").update();
        SchemaOrganisme schema = new SchemaOrganisme(jdbc);
        courrier = new CourrierJournal(HORLOGE);
        depotCharte = new DepotCharteOrganisme(jdbc, schema);
        depotPays = new DepotPaysAutorises(jdbc, schema);
        depotRegles = new DepotReglesAlerte(jdbc, schema);
        depotJournal = new DepotJournalAlertes(jdbc, schema);
        sequence = sequence();
        garde = new GardeOrganisme(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurEtablissementsPourOrganisme(jdbc), HORLOGE);
    }

    /** Le contrôleur, pour un déploiement dont la passerelle transmet (ou non) le pays. */
    private ClientHttpEssai http(String entetePays) {
        ReglagesPays reglages = new ReglagesPays(entetePays);
        LectureOptionsOrganisme lecture = new LectureOptionsOrganisme(depotCharte, depotPays, depotRegles, depotJournal, reglages);
        return new ClientHttpEssai(new ControleurOrganisme(garde, lecture, new GestionnaireCharteOrganisme(depotCharte, lecture),
                new GestionnairePaysAutorises(depotPays, lecture, reglages), new GestionnaireReglesAlerte(depotRegles, sequence)));
    }

    private ClientHttpEssai http() {
        return http(null);
    }

    @Test
    @DisplayName("la charte : lue par tous, posée par l'administration, refusée si elle rend un texte illisible (P-TN-9)")
    void charte() throws Exception {
        ClientHttpEssai http = http();
        http.get(O + "/charte", JULIE).andExpect(status().isOk()).andExpect(content().string("null"));
        http.put(O + "/charte", JULIE, "{}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("insufficient role"));
        http.put(O + "/charte", DIR, "{\"nomAffiche\":\"Urgences-santé\",\"couleurPrincipale\":\"#8ab4f8\",\"couleurLaterale\":\"#0f172a\"}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value(Matchers.startsWith("contraste insuffisant : couleurPrincipale #8ab4f8")));
        http.put(O + "/charte", DIR, null).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« nomAffiche » : de 1 à 60 caractères"));
        http.put(O + "/charte", DIR, "{\"nomAffiche\":\"Urgences-santé\",\"couleurPrincipale\":\"#005a9c\",\"couleurLaterale\":\"#001a2e\",\"logo\":\"" + PNG + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nomAffiche").value("Urgences-santé")).andExpect(jsonPath("$.variables['--primary']").value("#005a9c"))
                .andExpect(jsonPath("$.variables['--sidebar']").value("#001a2e")).andExpect(jsonPath("$.logo").value(PNG));
        assertThat(jdbc.sql("SELECT modifieLe || ' ' || modifiePar FROM org_charte WHERE tenantId = ?").param(T).query(String.class).single())
                .isEqualTo("2031-01-15T10:00:00.000Z usr-admin");
        http.get(O + "/charte", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.couleurPrincipale").value("#005a9c"));
        http.delete(O + "/charte", JULIE).andExpect(status().isForbidden());
        http.delete(O + "/charte", DIR).andExpect(status().isOk()).andExpect(jsonPath("$.retiree").value(true));
        http.delete(O + "/charte", DIR).andExpect(status().isOk()).andExpect(jsonPath("$.retiree").value(false));
        http.put(O + "/charte", DIR, "{\"nomAffiche\":\"Urgences-santé\",\"couleurPrincipale\":\"#005a9c\",\"couleurLaterale\":\"#001a2e\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.logo").value(Matchers.nullValue()));
    }

    @Test
    @DisplayName("les règles d'alerte se posent par l'administration ; les destinataires sont validés (P-TN-10)")
    void regles() throws Exception {
        ClientHttpEssai http = http();
        http.get(O + "/alertes", JULIE).andExpect(status().isForbidden());
        http.post(O + "/alertes", DIR, "{\"evenement\":\"TOUT\",\"destinataires\":[\"a@b.ca\"]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("« evenement » : EXPORT_TELECHARGE, CONNEXION_REFUSEE_PAYS"));
        http.post(O + "/alertes", DIR, "{\"evenement\":\"EXPORT_TELECHARGE\",\"destinataires\":[\"pas-une-adresse\"]}").andExpect(status().is(422));
        http.post(O + "/alertes", DIR, null).andExpect(status().is(422));
        for (String evenement : new String[]{"EXPORT_TELECHARGE", "CONNEXION_REFUSEE_PAYS"}) {
            http.post(O + "/alertes", DIR, "{\"evenement\":\"" + evenement + "\",\"destinataires\":[\"securite@urgences-sante.ca\"]}").andExpect(status().isCreated())
                    .andExpect(jsonPath("$.evenement").value(evenement)).andExpect(jsonPath("$.creeLe").value("2031-01-15T10:00:00.000Z"))
                    .andExpect(jsonPath("$.creePar").value("usr-admin")).andExpect(jsonPath("$.destinataires[0]").value("securite@urgences-sante.ca"));
        }
        http.post(O + "/alertes", DIR, "{\"evenement\":\"EXPORT_TELECHARGE\",\"destinataires\":[\"x@y.ca\"]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("uuid-3"));
        http.delete(O + "/alertes/uuid-3", DIR).andExpect(status().isOk()).andExpect(jsonPath("$.retiree").value(true));
        http.delete(O + "/alertes/uuid-3", DIR).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("règle introuvable"));
        http.get(O + "/alertes", DIR).andExpect(status().isOk()).andExpect(jsonPath("$[*].evenement", Matchers.contains("EXPORT_TELECHARGE", "CONNEXION_REFUSEE_PAYS")));
    }

    @Test
    @DisplayName("un export en pièce jointe prévient la sécurité, sans rien retenir de son contenu ; le journal le dit")
    void exportAlerte() throws Exception {
        ClientHttpEssai http = http();
        http.post(O + "/alertes", DIR, "{\"evenement\":\"EXPORT_TELECHARGE\",\"destinataires\":[\"securite@urgences-sante.ca\"]}").andExpect(status().isCreated());
        http.post(O + "/alertes", DIR, "{\"evenement\":\"CONNEXION_REFUSEE_PAYS\",\"destinataires\":[\"autre@urgences-sante.ca\"]}").andExpect(status().isCreated());
        AlerteTelechargement alerte = new AlerteTelechargement(new DeclencheurAlertes(depotRegles, depotJournal, courrier, sequence), HORLOGE);
        alerte.afterCompletion(requete(DIR, "/api/effectifs/journal"), reponse(200, "attachment; filename=\"journal-effectifs.csv\""), null, null);
        assertThat(courrier.envoyes()).hasSize(1);
        Courrier.Message mail = courrier.envoyes().get(0);
        assertThat(mail.to()).isEqualTo("securite@urgences-sante.ca");
        assertThat(mail.subject()).isEqualTo("[Alerte] Téléchargement d'un export (CSV, PDF, tableur)");
        assertThat(mail.text()).contains("Qui : admin@t\nQuand : 2031-01-15T10:00:00.000Z\nQuoi : journal-effectifs.csv (GET /api/effectifs/journal)");
        http.get(O + "/alertes/journal", DIR).andExpect(status().isOk()).andExpect(jsonPath("$[0].evenement").value("EXPORT_TELECHARGE"))
                .andExpect(jsonPath("$[0].envoyees").value(1)).andExpect(jsonPath("$[0].erreur").value(Matchers.nullValue()))
                .andExpect(jsonPath("$[0].destinataires[0]").value("securite@urgences-sante.ca")).andExpect(jsonPath("$[0].tenantId").doesNotExist())
                .andExpect(jsonPath("$[0].qui").value("admin@t")).andExpect(jsonPath("$[0].horodatage").value("2031-01-15T10:00:00.000Z"));
        http.get(O + "/alertes/journal", JULIE).andExpect(status().isForbidden());
        alerte.afterCompletion(requete(DIR, "/api/effectifs/journal"), reponse(200, null), null, null);
        alerte.afterCompletion(requete(DIR, "/api/effectifs/journal"), reponse(403, "attachment"), null, null);
        alerte.afterCompletion(requete(null, "/api/effectifs/journal"), reponse(200, "attachment"), null, null);
        assertThat(courrier.envoyes()).hasSize(1);
    }

    @Test
    @DisplayName("un compte global désigne l'organisme par en-tête ; un compte sans nom est « inconnu »")
    void exportParUnCompteGlobal() throws Exception {
        http().post(O + "/alertes", DIR, "{\"evenement\":\"EXPORT_TELECHARGE\",\"destinataires\":[\"securite@urgences-sante.ca\"]}").andExpect(status().isCreated());
        AlerteTelechargement alerte = new AlerteTelechargement(new DeclencheurAlertes(depotRegles, depotJournal, courrier, sequence), HORLOGE);
        MockHttpServletRequest global = requete(ClientHttpEssai.superadmin(), "/api/x");
        global.addHeader("X-Tenant-Id", " " + T + " ");
        alerte.afterCompletion(global, reponse(200, "attachment"), null, null);
        alerte.afterCompletion(requete(ClientHttpEssai.superadmin(), "/api/x"), reponse(200, "attachment"), null, null);
        MockHttpServletRequest sansNom = requete(new ContexteRequete("usr-x", null, "admin", null, T), "/api/y");
        alerte.afterCompletion(sansNom, reponse(200, "attachment"), null, null);
        alerte.afterCompletion(requete(new ContexteRequete(null, "", "admin", null, T), "/api/z"), reponse(200, "attachment"), null, null);
        assertThat(depotJournal.lire(T, 10)).extracting(a -> a.qui() + " " + a.quoi())
                .containsExactlyInAnyOrder("sa export (GET /api/x)", "usr-x export (GET /api/y)", "inconnu export (GET /api/z)");
    }

    private static MockHttpServletRequest requete(ContexteRequete session, String chemin) {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", chemin);
        if (session != null) {
            r.setAttribute(ContexteRequete.ATTRIBUT, session);
        }
        return r;
    }

    private static MockHttpServletResponse reponse(int statut, String disposition) {
        MockHttpServletResponse r = new MockHttpServletResponse();
        r.setStatus(statut);
        if (disposition != null) {
            r.setHeader("Content-Disposition", disposition);
        }
        return r;
    }

    @Test
    @DisplayName("restreindre les pays exige que la passerelle transmette le pays — refusé à la configuration, jamais à la connexion (P-TN-11)")
    void pays() throws Exception {
        ClientHttpEssai sansEntete = http();
        sansEntete.put(O + "/pays", DIR, "{\"pays\":[\"CA\"]}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("variable PAYS_ENTETE absente")));
        sansEntete.put(O + "/pays", DIR, "{\"pays\":[]}").andExpect(status().isOk()).andExpect(jsonPath("$.enteteConfiguree").value(false));
        sansEntete.get(O + "/pays", DIR).andExpect(status().isOk()).andExpect(jsonPath("$.pays").isEmpty()).andExpect(jsonPath("$.enteteConfiguree").value(false));
        sansEntete.get(O + "/pays", JULIE).andExpect(status().isForbidden());
        ClientHttpEssai avecEntete = http("X-Pays");
        avecEntete.put(O + "/pays", DIR, "{\"pays\":[\"ca\",\"US\"]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.pays", Matchers.contains("CA", "US"))).andExpect(jsonPath("$.enteteConfiguree").value(true));
        avecEntete.put(O + "/pays", DIR, "{\"pays\":\"CA\"}").andExpect(status().is(422));
        avecEntete.put(O + "/pays", DIR, null).andExpect(status().is(422));
        avecEntete.get(O + "/pays", DIR).andExpect(jsonPath("$.pays", Matchers.contains("CA", "US")));
    }

    @Test
    @DisplayName("les gardes : session, établissement désigné, existant, ouvert, et rôle — superadmin compris")
    void gardes() throws Exception {
        ClientHttpEssai http = http();
        http.get(O + "/charte", null).andExpect(status().isUnauthorized());
        http.get(O + "/pays", ClientHttpEssai.superadmin()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("X-Tenant-Id header required"));
        http.envoyer(MockMvcRequestBuilders.get(O + "/pays").header("X-Tenant-Id", T), ClientHttpEssai.superadmin()).andExpect(status().isOk());
        http.envoyer(MockMvcRequestBuilders.get(O + "/charte").header("X-Tenant-Id", "t-2"), JULIE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("tenant mismatch"));
        http.get(O + "/charte", ClientHttpEssai.session("admin", "t-inconnu")).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("tenant not found"));
        http.get(O + "/charte", ClientHttpEssai.session("admin", "t-susp")).andExpect(status().is(402))
                .andExpect(jsonPath("$.errors[0]").value("tenant suspended (payment required)"));
        http.get(O + "/alertes", ClientHttpEssai.session("admin", "t-fin")).andExpect(status().is(410)).andExpect(jsonPath("$.errors[0]").value("tenant terminated"));
        http.get(O + "/alertes", ClientHttpEssai.session("rh-personnalise", T)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("une erreur qui n'est pas un refus d'option devient un 400 nommé, jamais un 500")
    void erreursInattendues() {
        GardeAcces.Verdict v = new GardeAcces.Verdict(T, null);
        assertThat(garde.repondre(v, 200, t -> {
            throw new IllegalStateException("base indisponible");
        }).getStatusCode().value()).isEqualTo(400);
        assertThat(garde.repondre(v, 200, t -> {
            throw new IllegalStateException();
        }).getBody()).hasToString("{errors=[erreur inattendue]}");
    }
}
