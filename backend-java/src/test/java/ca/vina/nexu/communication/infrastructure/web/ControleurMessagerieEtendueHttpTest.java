package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.infrastructure.persistance.JournalCommunication;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.communication.application.commandes.BalayageSonneries;
import ca.vina.nexu.communication.application.requetes.LectureListesDistribution;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.infrastructure.configuration.ConfigurationCommunication;
import ca.vina.nexu.communication.infrastructure.persistance.DepotAppelsDirects;
import ca.vina.nexu.communication.infrastructure.persistance.DepotBrouillonsMessages;
import ca.vina.nexu.communication.infrastructure.persistance.DepotListesDistribution;
import ca.vina.nexu.communication.infrastructure.persistance.DepotMessagerie;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/communication — appels entre collègues, brouillons, listes de distribution")
class ControleurMessagerieEtendueHttpTest {

    private static final class HorlogeMobile extends Clock {
        private Instant instant = Instant.parse("2026-11-01T10:00:00Z");

        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return instant;
        }

        void avancer(int secondes) {
            instant = instant.plusSeconds(secondes);
        }
    }

    private static final class SequenceEssai implements Sequence {
        private final AtomicInteger n = new AtomicInteger();

        public String suivant(String prefixe) {
            return prefixe + n.incrementAndGet();
        }

        public String uuid() {
            return "id-" + n.incrementAndGet();
        }

        public String jetonSignature() {
            return uuid();
        }

        public String jetonBase64Url(int octets) {
            return uuid();
        }
    }

    private static final String T = "T1";
    private static final ContexteRequete ROY = new ContexteRequete("roy", "roy", "employee", "roy", T);
    private static final ContexteRequete NGU = new ContexteRequete("ngu", "ngu", "employee", "ngu", T);
    private static final ContexteRequete TRE = new ContexteRequete("tre", "tre", "employee", "tre", T);
    private static final ContexteRequete LAV = new ContexteRequete("lav", "lav", "employee", "lav", T);
    private static final ContexteRequete AUTRE = new ContexteRequete("ngu", "ngu", "employee", "ngu", "T2");
    private static final ContexteRequete SANS_MODULE = new ContexteRequete("roy", "roy", "employee", "roy", "T3");
    private static final List<String> DDL = List.of(
            "CREATE TABLE IF NOT EXISTS msg_appels (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, appelantId TEXT NOT NULL, appeleId TEXT NOT NULL, conversationId TEXT, etat TEXT NOT NULL, lanceLe TEXT NOT NULL, decrocheLe TEXT, termineLe TEXT, termineParId TEXT)",
            "CREATE TABLE IF NOT EXISTS msg_brouillons (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, conversationId TEXT NOT NULL, auteurId TEXT NOT NULL, corps TEXT NOT NULL, modifieLe TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_msg_brouillon ON msg_brouillons (tenantId, conversationId, auteurId)",
            "CREATE TABLE IF NOT EXISTS msg_listes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, nom TEXT NOT NULL, partagee INTEGER NOT NULL DEFAULT 0, creeePar TEXT NOT NULL, creeeLe TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_msg_liste_nom ON msg_listes (tenantId, creeePar, nom)",
            "CREATE TABLE IF NOT EXISTS msg_liste_membres (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, listeId TEXT NOT NULL, nature TEXT NOT NULL, cible TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS uq_msg_liste_membre ON msg_liste_membres (tenantId, listeId, nature, cible)");

    private final HorlogeMobile horloge = new HorlogeMobile();
    private final SequenceEssai sequence = new SequenceEssai();
    private JdbcClient jdbc;
    private ClientHttpEssai http;
    /** Les groupes de travail, tenus en mémoire : l'adaptateur réel (unités d'effectifs) a son propre essai. */
    private final java.util.Map<String, java.util.List<String>> membresParGroupe = new java.util.HashMap<>();
    private final GroupesDeTravailPort groupes = new GroupesDeTravailPort() {
        @Override
        public List<Groupe> groupes(String tenantId) {
            return membresParGroupe.keySet().stream().sorted().map(g -> new Groupe(g, g, g)).toList();
        }

        @Override
        public List<Membre> membres(String tenantId, String groupeId) {
            return membresParGroupe.getOrDefault(groupeId, List.of()).stream().map(p -> new Membre(p, p)).toList();
        }
    };
    private GestionnaireMessagerie messagerie;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        DDL.forEach(d -> jdbc.sql(d).update());
        for (String t : List.of(T, "T2")) {
            jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, ?)").params(t, "[\"effectifs_communication\"]").update();
        }
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('T3', 1, 1, '[\"horaires_x\"]')").update();
        JournalCommunication journal = new JournalCommunication(jdbc);
        JournalCommunicationPort jp = journal;
        GardeCommunication garde = new GardeCommunication(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc));
        ConfigurationCommunication c = new ConfigurationCommunication();
        DepotMessagerie depot = new DepotMessagerie(jdbc);
        LectureMessagerie lectureMessagerie = c.lectureMessagerie(depot, (t, p, e) -> List.of(), jp, horloge);
        messagerie = c.gestionnaireMessagerie(lectureMessagerie, depot, jp, sequence, horloge);
        DepotAppelsDirects appels = c.depotAppelsDirects(jdbc);
        BalayageSonneries balayage = c.balayageSonneries(appels, jp);
        ConversationsPourListesPort conversations = c.conversationsPourListes(lectureMessagerie, messagerie);
        DepotBrouillonsMessages brouillons = c.depotBrouillonsMessages(jdbc);
        DepotListesDistribution listes = c.depotListesDistribution(jdbc, sequence, groupes);
        LectureListesDistribution lecture = c.lectureListesDistribution(listes);
        http = new ClientHttpEssai(
                new ControleurAppelsDirects(garde, c.gestionnaireAppelsDirects(appels, balayage, jp, sequence, horloge), c.lectureAppelsDirects(appels, balayage, horloge)),
                new ControleurBrouillonsMessages(garde, c.gestionnaireBrouillonsMessages(conversations, brouillons, sequence, horloge), c.lectureBrouillonsMessages(brouillons)),
                new ControleurListesDistribution(garde, c.gestionnaireListesDistribution(listes, conversations, jp, lecture, sequence, horloge), lecture));
    }

    private List<String> journal() {
        return jdbc.sql("SELECT action FROM msg_journal").query(String.class).list();
    }

    @Test
    @DisplayName("appels : sonner, décrocher, raccrocher ; seule l'appelée répond ; deux clics, un appel")
    void appelDecrocheEtRaccroche() throws Exception {
        http.get("/api/communication/appels/en-cours", null).andExpect(status().isUnauthorized());
        http.get("/api/communication/appels/en-cours", SANS_MODULE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'effectifs_communication' not included in plan"));
        http.post("/api/communication/appels", ROY, "{\"appeleId\":\"ngu\",\"conversationId\":\"c9\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1")).andExpect(jsonPath("$.etat").value("sonne")).andExpect(jsonPath("$.appelantId").value("roy"))
                .andExpect(jsonPath("$.conversationId").value("c9")).andExpect(jsonPath("$.entrant").value(false)).andExpect(jsonPath("$.dureeSecondes").value(0));
        http.post("/api/communication/appels", ROY, "{\"appeleId\":\"ngu\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("id-1"));
        http.get("/api/communication/appels/en-cours", NGU).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].entrant").value(true));
        http.get("/api/communication/appels/en-cours", ROY).andExpect(jsonPath("$[0].entrant").value(false));
        http.post("/api/communication/appels/id-1/decrocher", ROY, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(containsString("appelée")));
        for (String g : List.of("decrocher", "refuser", "raccrocher")) {
            http.post("/api/communication/appels/id-1/" + g, TRE, null).andExpect(status().isBadRequest());
        }
        http.post("/api/communication/appels/id-1/decrocher", AUTRE, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("appel introuvable"));
        http.post("/api/communication/appels/id-1/decrocher", NGU, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("en_cours"))
                .andExpect(jsonPath("$.decrocheLe").value("2026-11-01T10:00:00.000Z")).andExpect(jsonPath("$.termineLe").value(nullValue()));
        horloge.avancer(30);
        http.post("/api/communication/appels/id-1/raccrocher", ROY, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("termine"))
                .andExpect(jsonPath("$.termineParId").value("roy")).andExpect(jsonPath("$.dureeSecondes").value(30));
        http.post("/api/communication/appels/id-1/raccrocher", NGU, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cet appel est déjà terminé"));
        http.get("/api/communication/appels/en-cours", NGU).andExpect(content().json("[]"));
        http.post("/api/communication/appels/inconnu/refuser", NGU, null).andExpect(status().isNotFound());
        assertThat(journal()).contains("appel.lancement", "appel.decrocher", "appel.raccrocher");
    }

    @Test
    @DisplayName("appels : refus, invitation invalide, sonnerie expirée, historique borné")
    void refusExpirationHistorique() throws Exception {
        http.post("/api/communication/appels", ROY, "{\"appeleId\":\"roy\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("on ne s'appelle pas soi-même"));
        http.post("/api/communication/appels", ROY, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("la personne à appeler est requise"));
        http.post("/api/communication/appels", ROY, "{\"appeleId\":\"ngu\",\"conversationId\":\"\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.conversationId").value(nullValue()));
        http.post("/api/communication/appels/id-1/refuser", NGU, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("refuse"))
                .andExpect(jsonPath("$.termineParId").value("ngu")).andExpect(jsonPath("$.dureeSecondes").value(0));
        horloge.avancer(1);
        http.post("/api/communication/appels", ROY, "{\"appeleId\":\"ngu\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("id-2"));
        horloge.avancer(46);
        http.get("/api/communication/appels/en-cours", NGU).andExpect(content().json("[]"));
        assertThat(journal()).contains("appel.manque", "appel.refuser");
        horloge.avancer(1);
        http.post("/api/communication/appels", TRE, "{\"appeleId\":\"ngu\"}").andExpect(status().isCreated());
        http.get("/api/communication/appels", NGU).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].appelantId").value("tre")).andExpect(jsonPath("$[1].etat").value("manque")).andExpect(jsonPath("$[2].etat").value("refuse"));
        http.get("/api/communication/appels?limite=1", NGU).andExpect(jsonPath("$.length()").value(1));
        http.get("/api/communication/appels?limite=", NGU).andExpect(jsonPath("$.length()").value(1));
        http.get("/api/communication/appels?limite=abc", NGU).andExpect(jsonPath("$.length()").value(3));
        http.get("/api/communication/appels", AUTRE).andExpect(content().json("[]"));
        assertThat(ControleurAppelsDirects.limite("2.9")).isEqualTo(2);
    }

    @Test
    @DisplayName("brouillons : à l'auteur seul, remplacé, effacé par un corps vide, refusé hors conversation")
    void brouillons() throws Exception {
        String c = messagerie.creer(new Appelant(T, "roy", null, null), new GestionnaireMessagerie.DemandeConversation("Garde", null, null, List.of("ngu"))).id();
        String chemin = "/api/communication/conversations/" + c + "/brouillon";
        http.get(chemin, SANS_MODULE).andExpect(status().isForbidden());
        http.put(chemin, ROY, "{\"corps\":\" Je peux prendre le samedi \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.corps").value("Je peux prendre le samedi")).andExpect(jsonPath("$.conversationId").value(c)).andExpect(jsonPath("$.modifieLe").value("2026-11-01T10:00:00.000Z"));
        http.get(chemin, ROY).andExpect(status().isOk()).andExpect(jsonPath("$.corps").value("Je peux prendre le samedi"));
        http.get(chemin, NGU).andExpect(status().isOk()).andExpect(content().string("null"));
        http.put(chemin, ROY, "{\"corps\":\"Version deux\"}").andExpect(status().isOk());
        // La liste des brouillons de l'appelant (GET /brouillons) : les siens seulement, jamais ceux d'un autre.
        String autre = messagerie.creer(new Appelant(T, "roy", null, null), new GestionnaireMessagerie.DemandeConversation("Relève", null, null, List.of("tre"))).id();
        horloge.avancer(60);
        http.put("/api/communication/conversations/" + autre + "/brouillon", ROY, "{\"corps\":\"Plus récent\"}").andExpect(status().isOk());
        http.get("/api/communication/brouillons", ROY).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].conversationId").value(autre)).andExpect(jsonPath("$[1].corps").value("Version deux"));
        http.get("/api/communication/brouillons", NGU).andExpect(content().json("[]"));
        http.get("/api/communication/brouillons", SANS_MODULE).andExpect(status().isForbidden());
        http.delete("/api/communication/conversations/" + autre + "/brouillon", ROY).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT corps FROM msg_brouillons").query(String.class).list()).containsExactly("Version deux");
        http.put(chemin, NGU, "{\"corps\":\"x\"}").andExpect(status().isOk());
        http.put(chemin, NGU, "{\"corps\":\"  \"}").andExpect(status().isOk()).andExpect(content().string("null"));
        http.get(chemin, NGU).andExpect(content().string("null"));
        http.put(chemin, TRE, "{\"corps\":\"Je m'invite\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("vous ne participez pas à cette conversation"));
        http.put(chemin, ROY, "{\"corps\":3}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("corps requis"));
        http.put(chemin, ROY, "{\"corps\":\"" + "x".repeat(10_001) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(containsString("brouillon trop long")));
        http.put("/api/communication/conversations/inconnue/brouillon", ROY, "{\"corps\":\"x\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("conversation introuvable"));
        http.put(chemin, AUTRE, "{\"corps\":\"x\"}").andExpect(status().isNotFound());
        http.delete(chemin, ROY).andExpect(status().isOk()).andExpect(jsonPath("$.supprime").value(true));
        http.delete(chemin, ROY).andExpect(status().isOk()).andExpect(jsonPath("$.supprime").value(false));
        http.get(chemin, ROY).andExpect(content().string("null"));
    }

    @Test
    @DisplayName("listes : création mêlant personne et groupe, destinataires recalculés, doublon 409, privée invisible, auteur seul")
    void listes() throws Exception {
        membresParGroupe.put("g1", new java.util.ArrayList<>(List.of("lav", "tre")));
        http.get("/api/communication/listes-distribution", SANS_MODULE).andExpect(status().isForbidden());
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\" Garde week-end \",\"partagee\":true,\"membres\":[{\"nature\":\"personne\",\"cible\":\"ngu\"},{\"nature\":\"groupe\",\"cible\":\"g1\"}]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.nom").value("Garde week-end")).andExpect(jsonPath("$.partagee").value(true))
                .andExpect(jsonPath("$.membres.length()").value(2)).andExpect(jsonPath("$.membres[0].nature").value("groupe")).andExpect(jsonPath("$.creeePar").value("roy"));
        String liste = jdbc.sql("SELECT id FROM msg_listes WHERE creeePar = 'roy'").query(String.class).single();
        http.get("/api/communication/listes-distribution/" + liste + "/destinataires", ROY).andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Garde week-end")).andExpect(content().json("{\"destinataires\":[\"lav\",\"ngu\",\"tre\"]}"));
        membresParGroupe.get("g1").add("roy");
        http.get("/api/communication/listes-distribution/" + liste + "/destinataires", ROY).andExpect(jsonPath("$.destinataires.length()").value(4));
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Garde week-end\",\"membres\":[]}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous avez déjà une liste nommée « Garde week-end »"));
        http.post("/api/communication/listes-distribution", LAV, "{\"nom\":\"Garde week-end\",\"membres\":[]}").andExpect(status().isCreated()).andExpect(jsonPath("$.partagee").value(false));
        String privee = jdbc.sql("SELECT id FROM msg_listes WHERE creeePar = 'lav'").query(String.class).single();
        http.get("/api/communication/listes-distribution/" + privee, TRE).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("liste de distribution introuvable"));
        http.get("/api/communication/listes-distribution", TRE).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(liste));
        http.get("/api/communication/listes-distribution", AUTRE).andExpect(content().json("[]"));
        http.get("/api/communication/listes-distribution/" + liste, AUTRE).andExpect(status().isNotFound());
        http.put("/api/communication/listes-distribution/" + liste, TRE, "{\"nom\":\"Détournée\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("seul l'auteur d'une liste la modifie"));
        http.delete("/api/communication/listes-distribution/" + liste, TRE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("seul l'auteur d'une liste la supprime"));
        http.put("/api/communication/listes-distribution/inconnue", ROY, "{}").andExpect(status().isNotFound());
        // Renommer vers un nom déjà pris par l'auteur : le même conflit qu'à la création, jamais une erreur de base.
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Relève\",\"membres\":[{\"nature\":\"personne\",\"cible\":\"ngu\"}]}").andExpect(status().isCreated());
        http.put("/api/communication/listes-distribution/" + liste, ROY, "{\"nom\":\" Relève \"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous avez déjà une liste nommée « Relève »"));
        http.put("/api/communication/listes-distribution/" + liste, ROY, "{\"nom\":\"Garde week-end\"}").andExpect(status().isOk());
        http.post("/api/communication/listes-distribution/" + liste + "/conversation", ROY, "{\"sujet\":\"Couverture du 12 décembre\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.sujet").value("Couverture du 12 décembre")).andExpect(jsonPath("$.type").value("groupe")).andExpect(jsonPath("$.participants.length()").value(4));
        http.post("/api/communication/listes-distribution/" + liste + "/conversation", NGU, null).andExpect(status().isCreated()).andExpect(jsonPath("$.sujet").value("Garde week-end"));
        assertThat(journal()).contains("liste_distribution.creation", "conversation.creation");
    }

    @Test
    @DisplayName("listes : diffusion à soi seul refusée, modification partielle, suppression, saisies invalides")
    void listesModificationEtValidation() throws Exception {
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Moi seule\",\"membres\":[{\"nature\":\"personne\",\"cible\":\"roy\"}]}").andExpect(status().isCreated());
        String seule = jdbc.sql("SELECT id FROM msg_listes").query(String.class).single();
        http.post("/api/communication/listes-distribution/" + seule + "/conversation", ROY, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value(containsString("ne désigne personne d'autre que vous")));
        http.put("/api/communication/listes-distribution/" + seule, ROY, "{\"nom\":\"Révisée\",\"partagee\":1,\"membres\":[{\"nature\":\"personne\",\"cible\":\"ngu\"}]}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Révisée")).andExpect(jsonPath("$.partagee").value(true)).andExpect(content().json("{\"membres\":[{\"nature\":\"personne\",\"cible\":\"ngu\"}]}"));
        http.put("/api/communication/listes-distribution/" + seule, ROY, "{\"partagee\":\"\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Révisée")).andExpect(jsonPath("$.partagee").value(false)).andExpect(jsonPath("$.membres[0].cible").value("ngu"));
        http.put("/api/communication/listes-distribution/" + seule, ROY, "{\"partagee\":{}}").andExpect(jsonPath("$.partagee").value(true));
        http.put("/api/communication/listes-distribution/" + seule, ROY, "{\"nom\":5}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("nom requis"));
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\" \",\"membres\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("nom requis"));
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Bancale\",\"membres\":[{\"nature\":\"service\",\"cible\":\"x\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("nature attendue")));
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Bancale\",\"membres\":[\"x\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("cible requise")));
        http.post("/api/communication/listes-distribution", ROY, "{\"nom\":\"Vide\",\"membres\":\"x\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.membres.length()").value(0));
        http.delete("/api/communication/listes-distribution/" + seule, ROY).andExpect(status().isOk()).andExpect(jsonPath("$.supprime").value(true));
        http.get("/api/communication/listes-distribution/" + seule, ROY).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM msg_liste_membres WHERE listeId = ?").param(seule).query(Integer.class).single()).isZero();
        assertThat(journal()).contains("liste_distribution.modification", "liste_distribution.suppression");
    }
}
