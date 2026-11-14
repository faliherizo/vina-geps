package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.infrastructure.persistance.DepotMessagerie;
import ca.vina.nexu.communication.infrastructure.persistance.JournalCommunication;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.communication.domaine.Messagerie;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/communication — messagerie sécurisée (L6)")
class ControleurMessagerieHttpTest {

    /** Une horloge qui avance : la portée de lecture se joue sur les instants. */
    private static final class HorlogeMobile extends Clock {
        private Instant instant = Instant.parse("2026-09-16T12:00:00Z");

        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return instant;
        }

        void avancer(int secondes) {
            instant = instant.plusSeconds(secondes);
        }
    }

    private static final String T = "T1";
    private static final ContexteRequete DR = new ContexteRequete("dr", "dr", "employee", "dr", T);
    private static final ContexteRequete RES = new ContexteRequete("res", "res", "employee", "res", T);
    private static final ContexteRequete INF = new ContexteRequete("inf", "inf", "employee", "inf", T);
    private static final ContexteRequete AUTRE = new ContexteRequete("x", "x", "employee", "x", "T2");
    private final HorlogeMobile horloge = new HorlogeMobile();
    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private LectureMessagerie lecture;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        JournalCommunication journal = new JournalCommunication(jdbc);
        AtomicInteger n = new AtomicInteger();
        DepotMessagerie depot = new DepotMessagerie(jdbc);
        AcheminementPort courriel = (t, p, e) -> List.of(new AcheminementPort.Canal("courriel", "sonnerie", 1, true));
        lecture = new LectureMessagerie(depot, courriel, journal, horloge);
        GestionnaireMessagerie gestionnaire = new GestionnaireMessagerie(new GestionnaireMessagerie.Dependances(lecture, depot, journal, () -> "id-" + n.incrementAndGet(),
                c -> ca.vina.nexu.securite.domaine.Antivirus.exigerSain(new ca.vina.nexu.securite.domaine.Antivirus.AnalyseurSimule(), c)), horloge);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"effectifs_communication\"]'), ('T2', 1, 1, '[\"effectifs_communication\"]'), ('T3', 1, 1, '[\"effectifs\"]')").params(T).update();
        GardeCommunication garde = new GardeCommunication(new GardeAcces(new RegistrePolitiqueAcces()), new ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives(jdbc));
        http = new ClientHttpEssai(new ControleurMessagerie(garde, lecture, gestionnaire));
    }

    @Test
    @DisplayName("conversations : création (l'auteur modère), lecture, participants gérés par un modérateur, archivage")
    void conversations() throws Exception {
        http.get("/api/communication/conversations", null).andExpect(status().isUnauthorized());
        // Le module coupé coupe l'API, pas seulement le menu.
        http.get("/api/communication/conversations", new ContexteRequete("y", "y", "employee", "y", "T3")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("module 'effectifs_communication' not included in plan"));
        http.post("/api/communication/conversations", DR, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("sujet requis · une conversation compte au moins deux participants"));
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Garde\",\"type\":\"duo\",\"participants\":[\"res\",\"res\"]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(containsString("type doit être individuelle ou groupe")));
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Garde\",\"type\":\"individuelle\",\"participants\":[\"res\",\"inf\"]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("une conversation individuelle compte exactement deux participants"));
        http.post("/api/communication/conversations", DR, "{\"sujet\":\" Garde de nuit \",\"participants\":[\"res\",\"dr\"],\"groupeId\":\"g1\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("id-1")).andExpect(jsonPath("$.sujet").value("Garde de nuit")).andExpect(jsonPath("$.type").value("groupe")).andExpect(jsonPath("$.groupeId").value("g1"))
                .andExpect(jsonPath("$.participants.length()").value(2)).andExpect(jsonPath("$.participants[0].personId").value("dr")).andExpect(jsonPath("$.participants[0].role").value("moderateur")).andExpect(jsonPath("$.participants[1].role").value("membre"));
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Duo\",\"type\":\"individuelle\",\"participants\":[\"inf\"]}").andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("id-4"));
        http.get("/api/communication/conversations", DR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        http.get("/api/communication/conversations", RES).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        http.get("/api/communication/conversations/id-1", RES).andExpect(status().isOk()).andExpect(jsonPath("$.sujet").value("Garde de nuit"));
        http.get("/api/communication/conversations/id-1", AUTRE).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("conversation introuvable"));
        // participants : seul un modérateur ajoute ; une individuelle ne se modifie pas ; un membre se retire lui-même
        http.post("/api/communication/conversations/id-1/participants", RES, "{\"personId\":\"inf\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("seul un modérateur ajoute un participant"));
        http.post("/api/communication/conversations/id-4/participants", DR, "{\"personId\":\"res\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("la composition d'une conversation individuelle ne se modifie pas"));
        http.post("/api/communication/conversations/id-1/participants", DR, "{\"personId\":\"res\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cette personne participe déjà"));
        http.post("/api/communication/conversations/id-1/participants", DR, "{\"personId\":\"inf\",\"role\":\"moderateur\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.participants.length()").value(3)).andExpect(jsonPath("$.participants[2].role").value("moderateur"));
        http.delete("/api/communication/conversations/id-1/participants/inf", RES).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("seul un modérateur retire un autre participant"));
        http.delete("/api/communication/conversations/id-1/participants/res", RES).andExpect(status().isOk()).andExpect(jsonPath("$.participants[1].jusqua").value("2026-09-16T12:00:00.000Z"));
        http.delete("/api/communication/conversations/id-1/participants/res", DR).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cette personne ne participe pas à la conversation"));
        http.post("/api/communication/conversations/id-1/participants", DR, "{\"personId\":\"res\"}").andExpect(status().isCreated()).andExpect(jsonPath("$.participants.length()").value(4));
        // archivage : modérateur d'un groupe, ou n'importe quel participant d'une individuelle ; pas deux fois
        http.post("/api/communication/conversations/id-1/archiver", RES, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("seul un modérateur archive la conversation"));
        http.post("/api/communication/conversations/id-4/archiver", INF, null).andExpect(status().isOk()).andExpect(jsonPath("$.archiveeLe").value("2026-09-16T12:00:00.000Z"));
        http.post("/api/communication/conversations/id-4/archiver", INF, null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("conversation déjà archivée"));
        http.post("/api/communication/conversations/id-1/archiver", DR, null).andExpect(status().isOk());
        http.post("/api/communication/conversations/id-1/participants", DR, "{\"personId\":\"zz\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("seul un modérateur ajoute un participant"));
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"x\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("cette conversation est archivée"));
        assertThat(jdbc.sql("SELECT action FROM msg_journal ORDER BY horodatage, action").query(String.class).list()).contains("conversation.creation", "conversation.participant_ajoute", "conversation.participant_retire", "conversation.archivage");
    }

    @Test
    @DisplayName("messages : envoi par un participant actif, portée de lecture par fenêtre, lectures, recherche, pièces jointes, acheminement")
    void messages() throws Exception {
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Garde\",\"participants\":[\"res\"]}").andExpect(status().isCreated());
        http.post("/api/communication/conversations/id-1/messages", INF, "{\"corps\":\"Bonjour\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("vous ne participez pas à cette conversation"));
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"  \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("corps requis"));
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"" + "x".repeat(10_001) + "\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("message trop long (10 000 caractères au maximum)"));
        // Une pièce porte son fichier : déclarée sans contenu, elle ne transporterait rien (défaut du 1er novembre 2026).
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Bonjour\",\"piecesJointes\":[{\"nom\":\"../x.pdf\",\"typeMime\":\"application/zip\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("pièce jointe 1 : contenu requis : le fichier, encodé en base64 · typeMime doit être l'un de")))
                .andExpect(jsonPath("$.errors[0]").value(containsString("nom de fichier invalide")));
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Bonjour\",\"piecesJointes\":[{\"nom\":\"a.pdf\",\"typeMime\":\"application/pdf\",\"contenu\":\"!!pas du base64!!\"}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("pièce jointe 1 : contenu requis : le fichier, encodé en base64"));
        // L'antivirus passe avant toute écriture : la pièce infectée est nommée, rien n'est rangé.
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Bonjour\",\"piecesJointes\":[{\"nom\":\"a.txt\",\"typeMime\":\"text/plain\",\"contenu\":\"EICAR_B64\"}]}"
                        .replace("EICAR_B64", java.util.Base64.getEncoder().encodeToString(ca.vina.nexu.securite.domaine.Antivirus.EICAR.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("fichier refusé par l'antivirus (Eicar-Test-Signature)"));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM msg_messages").query(Integer.class).single()).isZero();
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Bonjour\",\"piecesJointes\":[\"pas une carte\"]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(containsString("nom requis")));
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\" Bonjour l'équipe \",\"piecesJointes\":[{\"nom\":\"consigne.pdf\",\"typeMime\":\"application/pdf\",\"contenu\":\"data:application/pdf;base64,JVBERi0xLjQgY29uc2lnbmUgZGUgZ2FyZGU=\"}]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.corps").value("Bonjour l'équipe")).andExpect(jsonPath("$.auteurId").value("dr")).andExpect(jsonPath("$.piecesJointes[0].nom").value("consigne.pdf")).andExpect(jsonPath("$.editeLe").value(org.hamcrest.Matchers.nullValue()));
        horloge.avancer(60);
        // inf rejoint après le premier message : il ne le lit pas, et ne voit pas sa pièce jointe
        http.post("/api/communication/conversations/id-1/participants", DR, "{\"personId\":\"inf\"}").andExpect(status().isCreated());
        horloge.avancer(60);
        http.post("/api/communication/conversations/id-1/messages", RES, "{\"corps\":\"Reçu, merci — je prends la garde de vendredi\"}").andExpect(status().isCreated());
        http.get("/api/communication/conversations/id-1/messages", DR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        http.get("/api/communication/conversations/id-1/messages", INF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].auteurId").value("res"));
        http.get("/api/communication/conversations/id-1/messages", AUTRE).andExpect(status().isNotFound());
        http.get("/api/communication/conversations/inconnue/messages", DR).andExpect(status().isNotFound());
        String pieceId = jdbc.sql("SELECT id FROM msg_pieces_jointes").query(String.class).single();
        // Le fichier lui-même, en pièce à enregistrer, sous son nom ; son type n'est pas deviné par le navigateur.
        http.get("/api/communication/pieces-jointes/" + pieceId, RES).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes("%PDF-1.4 consigne de garde".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "application/pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", containsString("attachment; filename=\"consigne.pdf\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"));
        assertThat(jdbc.sql("SELECT taille FROM msg_pieces_jointes").query(Integer.class).single()).isEqualTo(26);
        // Une pièce déposée avant que les pièces ne portent leur fichier : sa description existe, son fichier non.
        jdbc.sql("UPDATE msg_pieces_jointes SET contenu = NULL").update();
        http.get("/api/communication/pieces-jointes/" + pieceId, RES).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("le fichier de cette pièce jointe n'a pas été conservé"));
        http.get("/api/communication/pieces-jointes/" + pieceId, INF).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("pièce jointe introuvable"));
        http.get("/api/communication/pieces-jointes/inconnue", DR).andExpect(status().isNotFound());
        // lectures : idempotentes
        String messageRes = jdbc.sql("SELECT id FROM msg_messages WHERE auteurId = 'res'").query(String.class).single();
        http.post("/api/communication/messages/" + messageRes + "/lu", DR, null).andExpect(status().isOk()).andExpect(jsonPath("$.lu").value(true));
        http.post("/api/communication/messages/" + messageRes + "/lu", DR, null).andExpect(status().isOk());
        http.get("/api/communication/messages/" + messageRes + "/lectures", RES).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].personId").value("dr"));
        // recherche dans sa portée, insensible aux accents, extrait
        http.get("/api/communication/messages/recherche?q=e", DR).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("la recherche demande au moins deux caractères"));
        http.get("/api/communication/messages/recherche", DR).andExpect(status().isBadRequest());
        http.get("/api/communication/messages/recherche?q=ÉQUIPE", DR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].extrait").value("Bonjour l'équipe")).andExpect(jsonPath("$[0].auteurId").value("dr"));
        http.get("/api/communication/messages/recherche?q=équipe", INF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        http.get("/api/communication/messages/recherche?q=garde", INF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        // acheminement des autres participants actifs, sans l'auteur
        http.get("/api/communication/conversations/id-1/acheminement", DR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].personId").value("res")).andExpect(jsonPath("$[0].canaux[0].canal").value("courriel"));
        http.delete("/api/communication/conversations/id-1/participants/inf", DR).andExpect(status().isOk());
        horloge.avancer(60);
        http.get("/api/communication/conversations/id-1/acheminement", DR).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        http.post("/api/communication/conversations/id-1/messages", INF, "{\"corps\":\"encore ?\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("vous ne participez pas à cette conversation"));
        http.get("/api/communication/conversations/id-1/messages", INF).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));   // sa fenêtre passée reste lisible
    }

    @Test
    @DisplayName("le domaine : extraits longs, type servi par défaut, portée vide")
    void domaine() {
        String long1 = "a".repeat(200) + " garde " + "b".repeat(200);
        Messagerie.Message m = new Messagerie.Message("m", "c", "dr", long1, "2026-09-16T12:00:00.000Z", null, List.of());
        Messagerie.PorteeLecture p = new Messagerie.PorteeLecture("dr", List.of(new Messagerie.Fenetre("c", "2026-09-16T00:00:00.000Z", "2026-09-17T00:00:00.000Z")));
        Map<String, Object> r = Messagerie.rechercher(p, List.of(m), "GARDE").get(0);
        assertThat((String) r.get("extrait")).startsWith("…").endsWith("…").contains("garde");
        assertThat(Messagerie.rechercher(p, List.of(new Messagerie.Message("m2", "c", "dr", "x", "2026-09-18T12:00:00.000Z", null, List.of())), "xx")).isEmpty();
        assertThatThrownBy(() -> Messagerie.rechercher(p, List.of(), " ")).hasMessage("la recherche demande au moins deux caractères");
        assertThat(Messagerie.typeServi("application/x-msdownload")).isEqualTo("application/octet-stream");
        assertThat(Messagerie.validerPieceJointe("a.pdf", "application/pdf", 1.5)).containsExactly("taille doit être un entier positif");
        assertThat(Messagerie.validerPieceJointe("a.pdf", "application/pdf", 26_214_401L)).containsExactly("taille supérieure au maximum de 25 Mo");
        assertThat(Messagerie.contenuDe(42)).isEmpty();
        assertThat(Messagerie.contenuDe(" ")).isEmpty();
        assertThat(Messagerie.contenuDe("aGVsbG8=")).hasValueSatisfying(b -> assertThat(new String(b, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("hello"));
        assertThat(Messagerie.validerPieceJointe("a\\b.pdf", "application/pdf", 10)).containsExactly("nom de fichier invalide");
        assertThat(Messagerie.validerConversation("s", "individuelle", List.of("a", "b"))).isEmpty();
        assertThat(Messagerie.validerConversation("s", "groupe", List.of("a", "a"))).containsExactly("un participant est répété");
        assertThat(Messagerie.validerMessage(null)).containsExactly("corps requis");
        Messagerie.Conversation archivee = new Messagerie.Conversation("c", "s", "groupe", null, "dr", "2026-01-01", "2026-02-01", List.of(new Messagerie.Participation("dr", "moderateur", "2026-01-01", null)));
        assertThat(Messagerie.peutGererParticipants(archivee, "dr", "2026-03-01")).isFalse();
        assertThat(Messagerie.peutEcrire(archivee, "dr", "2026-03-01")).isFalse();
        Messagerie.Conversation partie = new Messagerie.Conversation("c", "s", "groupe", null, "dr", "2026-01-01", null, List.of(new Messagerie.Participation("dr", "moderateur", "2026-01-01", "2026-01-10")));
        assertThat(Messagerie.peutGererParticipants(partie, "dr", "2026-03-01")).isFalse();
        assertThat(Messagerie.participantActif(partie, "dr", "2026-01-05")).isTrue();
        assertThat(Messagerie.participantsAvec("dr", List.of("res", "dr", "res"))).containsExactly("dr", "res");
        assertThat(Messagerie.porteeDeLecture(lecture.conversations(new Appelant(T, "personne", null, null)), "personne").fenetres()).isEmpty();
        Messagerie.Fenetre f = new Messagerie.Fenetre("c", "2026-09-16T00:00:00.000Z", "2026-09-17T00:00:00.000Z");
        assertThat(new Messagerie.PorteeLecture("dr", List.of(f)).couvre("c", "2026-09-15T00:00:00.000Z")).isFalse();
        assertThat(new Messagerie.PorteeLecture("dr", List.of(f)).couvre("c", "2026-09-18T00:00:00.000Z")).isFalse();
        assertThat(Messagerie.participantActif(partie, "dr", "2025-12-31")).isFalse();
        assertThat(Messagerie.validerConversation(null, "groupe", List.of("a", "b"))).containsExactly("sujet requis");
        assertThat(Messagerie.validerPieceJointe(null, "text/plain", 1)).containsExactly("nom requis");
        assertThat(Messagerie.validerPieceJointe("a/b.txt", "text/plain", 1)).containsExactly("nom de fichier invalide");
        assertThat(Messagerie.validerPieceJointe("a..b.txt", "text/plain", 1)).containsExactly("nom de fichier invalide");
    }
    @Test
    @DisplayName("non-lus : ce qui m'attend, sans mes propres messages, sans les archivés, et rien d'un fil où je ne suis pas")
    void nonLus() throws Exception {
        http.get("/api/communication/conversations/non-lus", null).andExpect(status().isUnauthorized());

        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Transfert\",\"participants\":[\"res\"]}").andExpect(status().isCreated());
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Deux civières au sous-sol.\"}").andExpect(status().isCreated());
        horloge.avancer(60);
        http.post("/api/communication/conversations/id-1/messages", RES, "{\"corps\":\"Je descends, j'ai le relais.\"}").andExpect(status().isCreated());

        // res a écrit le second : un seul l'attend, et l'aperçu porte le dernier message visible.
        http.get("/api/communication/conversations/non-lus", RES).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.conversations[0].conversationId").value("id-1"))
                .andExpect(jsonPath("$.conversations[0].sujet").value("Transfert"))
                .andExpect(jsonPath("$.conversations[0].nonLus").value(1))
                .andExpect(jsonPath("$.conversations[0].dernierAuteurId").value("res"))
                .andExpect(jsonPath("$.conversations[0].dernierExtrait").value(containsString("relais")));

        // Marqué lu, il cesse d'attendre : sans cela la barre sonnerait pour ce qu'on vient de lire.
        String premier = jdbc.sql("SELECT id FROM msg_messages ORDER BY envoyeLe LIMIT 1").query(String.class).single();
        http.post("/api/communication/messages/" + premier + "/lu", RES, null).andExpect(status().isOk());
        http.get("/api/communication/conversations/non-lus", RES).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));

        // Un tiers ne voit pas la conversation : elle ne lui est pas comptée non plus.
        http.get("/api/communication/conversations/non-lus", INF).andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations.length()").value(0));

        // Archivée, elle ne réclame plus rien : elle reste lisible, elle ne reste pas urgente.
        horloge.avancer(60);
        http.post("/api/communication/conversations/id-1/messages", DR, "{\"corps\":\"Dernier point avant la relève.\"}").andExpect(status().isCreated());
        http.get("/api/communication/conversations/non-lus", RES).andExpect(jsonPath("$.total").value(1));
        http.post("/api/communication/conversations/id-1/archiver", DR, null).andExpect(status().isOk());
        http.get("/api/communication/conversations/non-lus", RES).andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations.length()").value(0));

        // Deux fils laissés derrière, deux messages chacun : de quoi juger l'ordre à égalité de non-lus.
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Relève de nuit\",\"participants\":[\"res\"]}").andExpect(status().isCreated());
        String fil2 = filDe("Relève de nuit");
        http.post("/api/communication/conversations/" + fil2 + "/messages", DR, "{\"corps\":\"Deux véhicules immobilisés.\"}").andExpect(status().isCreated());
        horloge.avancer(60);
        http.post("/api/communication/conversations/" + fil2 + "/messages", DR, "{\"corps\":\"Remorquage demandé.\"}").andExpect(status().isCreated());
        http.post("/api/communication/conversations", DR, "{\"sujet\":\"Garde de fin de semaine\",\"participants\":[\"res\"]}").andExpect(status().isCreated());
        String fil3 = filDe("Garde de fin de semaine");
        horloge.avancer(60);
        http.post("/api/communication/conversations/" + fil3 + "/messages", DR, "{\"corps\":\"Deux places à combler.\"}").andExpect(status().isCreated());
        horloge.avancer(60);
        http.post("/api/communication/conversations/" + fil3 + "/messages", DR, "{\"corps\":\"Une est prise.\"}").andExpect(status().isCreated());

        // À nombre de non-lus égal, le fil dont le dernier message est le plus récent passe devant :
        // sans ce départage, l'ordre dépendrait de la base et la liste sauterait d'un appel à l'autre.
        http.get("/api/communication/conversations/non-lus", RES).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.conversations[0].conversationId").value(fil3))
                .andExpect(jsonPath("$.conversations[1].conversationId").value(fil2));

        // Tout le fil d'un coup : le compte rendu est celui des messages qui attendaient vraiment,
        // et le second appel n'en marque aucun — c'est ce qui rend le geste rejouable sans fausser la barre.
        http.post("/api/communication/conversations/" + fil3 + "/lu", RES, null).andExpect(status().isOk()).andExpect(jsonPath("$.marques").value(2));
        http.post("/api/communication/conversations/" + fil3 + "/lu", RES, null).andExpect(status().isOk()).andExpect(jsonPath("$.marques").value(0));
        http.get("/api/communication/conversations/non-lus", RES).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.conversations[0].conversationId").value(fil2));
        http.post("/api/communication/conversations/inconnue/lu", RES, null).andExpect(status().isNotFound());
    }

    /** L'identifiant d'un fil, lu à la base : le compteur d'identifiants est partagé avec les messages. */
    private String filDe(String sujet) {
        return jdbc.sql("SELECT id FROM msg_conversations WHERE sujet = ?").params(sujet).query(String.class).single();
    }
}
