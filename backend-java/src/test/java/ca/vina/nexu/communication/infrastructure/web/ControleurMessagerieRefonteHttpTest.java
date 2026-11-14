package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsGroupe;
import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.requetes.LectureAppelsGroupe;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.infrastructure.persistance.DepotAppelsGroupe;
import ca.vina.nexu.communication.infrastructure.persistance.DepotMessagerie;
import ca.vina.nexu.communication.infrastructure.persistance.JournalCommunication;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import com.jayway.jsonpath.JsonPath;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La refonte de la messagerie (REFONTE-MESSAGERIE.md) : le fil à deux unique, la réponse privée qui ne part
 * qu'à son destinataire, les réactions, l'appel de groupe — et les fils où l'on ne participe pas, introuvables.
 */
@DisplayName("/api/communication — refonte : fil à deux, réponse privée, réactions, appel de groupe")
class ControleurMessagerieRefonteHttpTest {

    /** Une horloge qui avance : la sonnerie d'un appel de groupe expire avec le temps. */
    private static final class HorlogeMobile extends Clock {
        private Instant instant = Instant.parse("2026-09-16T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        void avancer(int secondes) {
            instant = instant.plusSeconds(secondes);
        }
    }

    private static final String T = "T1";
    private static final String C = "/api/communication";
    private static final ContexteRequete JULIE = new ContexteRequete("julie", "julie", "employee", "julie", T);
    private static final ContexteRequete MARC = new ContexteRequete("marc", "marc", "employee", "marc", T);
    private static final ContexteRequete CHEF = new ContexteRequete("chef", "chef", "manager", "chef", T);
    private static final ContexteRequete LEA = new ContexteRequete("lea", "lea", "employee", "lea", T);
    private final HorlogeMobile horloge = new HorlogeMobile();
    private JdbcClient jdbc;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        JournalCommunication journal = new JournalCommunication(jdbc);
        AtomicInteger n = new AtomicInteger();
        DepotMessagerie depot = new DepotMessagerie(jdbc);
        AcheminementPort courriel = (t, p, e) -> List.of(new AcheminementPort.Canal("courriel", "sonnerie", 1, true));
        LectureMessagerie lecture = new LectureMessagerie(depot, courriel, journal, horloge);
        GestionnaireMessagerie messagerie = new GestionnaireMessagerie(new GestionnaireMessagerie.Dependances(lecture, depot, journal,
                () -> "id-" + n.incrementAndGet(), contenu -> { }), horloge);
        DepotAppelsGroupe appels = new DepotAppelsGroupe(jdbc);
        Sequence sequence = new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + "-" + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "appel-" + n.incrementAndGet();
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
        GestionnaireAppelsGroupe gestionnaireAppels = new GestionnaireAppelsGroupe(lecture, appels,
                new GestionnaireAppelsGroupe.Ecritures(appels, journal, sequence, horloge));
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"effectifs_communication\"]')").param(T).update();
        GardeCommunication garde = new GardeCommunication(new GardeAcces(new RegistrePolitiqueAcces()),
                new ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives(jdbc));
        http = new ClientHttpEssai(new ControleurMessagerie(garde, lecture, messagerie),
                new ControleurAppelsGroupe(garde, gestionnaireAppels, new LectureAppelsGroupe(appels, horloge)));
    }

    private String corps(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String groupe() throws Exception {
        return JsonPath.read(corps(http.post(C + "/conversations", CHEF, "{\"sujet\":\"Garde Est\",\"participants\":[\"julie\",\"marc\"]}")
                .andExpect(status().isCreated())), "$.id");
    }

    private String ecrire(ContexteRequete qui, String fil, String json) throws Exception {
        return JsonPath.read(corps(http.post(C + "/conversations/" + fil + "/messages", qui, json).andExpect(status().isCreated())), "$.id");
    }

    @Test
    @DisplayName("écrire à une personne : le fil à deux naît (201), puis se rouvre (200) — jamais deux ; ni à soi-même ni à personne")
    void filADeux() throws Exception {
        http.post(C + "/conversations/directe", JULIE, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« personId » : la personne à qui écrire est requise"));
        http.post(C + "/conversations/directe", JULIE, "{\"personId\":\" julie \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("on n'ouvre pas une conversation avec soi-même"));
        String fil = JsonPath.read(corps(http.post(C + "/conversations/directe", JULIE, "{\"personId\":\"marc\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("individuelle")).andExpect(jsonPath("$.participants[*].personId", contains("julie", "marc")))), "$.id");
        http.post(C + "/conversations/directe", JULIE, "{\"personId\":\"marc\"}").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(fil));
        // Marc qui écrit à Julie retrouve le même fil.
        http.post(C + "/conversations/directe", MARC, "{\"personId\":\"julie\"}").andExpect(status().isOk()).andExpect(jsonPath("$.id").value(fil));
        // Archivé, il ne se rouvre plus : un nouveau naît.
        http.post(C + "/conversations/" + fil + "/archiver", JULIE, null).andExpect(status().isOk());
        String nouveau = JsonPath.read(corps(http.post(C + "/conversations/directe", MARC, "{\"personId\":\"julie\"}").andExpect(status().isCreated())), "$.id");
        assertThat(nouveau).isNotEqualTo(fil);
        // Un fil où l'on ne participe pas est introuvable, son acheminement aussi.
        http.get(C + "/conversations/" + fil, LEA).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("conversation introuvable"));
        http.get(C + "/conversations/" + fil + "/acheminement", LEA).andExpect(status().isNotFound());
        http.get(C + "/conversations/" + fil + "/acheminement", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[0].personId").value("marc"));
    }

    @Test
    @DisplayName("une réponse privée ne se lit que de son auteur et de son destinataire — fil, non-lus, recherche ; son adresse est jugée")
    void reponsePrivee() throws Exception {
        String fil = groupe();
        String annonce = ecrire(CHEF, fil, "{\"corps\":\"Briefing à 18 h 45\"}");
        String prive = ecrire(JULIE, fil, "{\"corps\":\"Peux-tu me remplacer mardi ?\",\"priveA\":\"marc\",\"repondAuMessageId\":\"" + annonce + "\"}");
        http.get(C + "/conversations/" + fil + "/messages", MARC).andExpect(jsonPath("$[*].id", contains(annonce, prive)))
                .andExpect(jsonPath("$[1].priveA").value("marc")).andExpect(jsonPath("$[1].repondAuMessageId").value(annonce));
        http.get(C + "/conversations/" + fil + "/messages", JULIE).andExpect(jsonPath("$[*].id", contains(annonce, prive)));
        // Le chef ne la voit nulle part : ni dans le fil, ni dans ses non-lus, ni en cherchant.
        http.get(C + "/conversations/" + fil + "/messages", CHEF).andExpect(jsonPath("$[*].id", contains(annonce)))
                .andExpect(jsonPath("$[0].priveA").value(nullValue()));
        http.get(C + "/conversations/non-lus", CHEF).andExpect(jsonPath("$.total").value(0));
        http.get(C + "/messages/recherche?q=remplacer", CHEF).andExpect(jsonPath("$.length()").value(0));
        http.get(C + "/messages/recherche?q=remplacer", MARC).andExpect(jsonPath("$[0].messageId").value(prive));
        // L'adresse est jugée : soi-même, un non-participant, un message inconnu.
        http.post(C + "/conversations/" + fil + "/messages", JULIE, "{\"corps\":\"x\",\"priveA\":\"julie\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("un message privé s'adresse à une autre personne que vous"));
        http.post(C + "/conversations/" + fil + "/messages", JULIE, "{\"corps\":\"x\",\"priveA\":\"lea\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« priveA » : cette personne ne participe pas à la conversation"));
        http.post(C + "/conversations/" + fil + "/messages", JULIE, "{\"corps\":\"x\",\"repondAuMessageId\":\"inconnu\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« repondAuMessageId » : message introuvable dans cette conversation"));
        // Une chaîne vide n'adresse personne : le message va au fil.
        http.post(C + "/conversations/" + fil + "/messages", JULIE, "{\"corps\":\"Pour tous\",\"priveA\":\" \",\"repondAuMessageId\":\"\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.priveA").value(nullValue()));
        // Le journal sait qu'un message est privé ; jamais ce qu'il dit.
        assertThat(jdbc.sql("SELECT apres FROM msg_journal WHERE action = 'message.envoi' ORDER BY horodatage").query(String.class).list())
                .anySatisfy(a -> assertThat(a).contains("\"prive\":true").doesNotContain("remplacer"));
    }

    @Test
    @DisplayName("réagir : poser, compter par personne, retirer en recommençant ; inconnu, hors portée, privé à un autre, fil archivé refusés")
    void reactions() throws Exception {
        String fil = groupe();
        String annonce = ecrire(CHEF, fil, "{\"corps\":\"Briefing à 18 h 45\"}");
        String r = C + "/messages/" + annonce + "/reactions";
        http.post(r, MARC, "{\"reaction\":\"bravo\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« reaction » : pouce, fait, merci, regarde"));
        http.post(r, MARC, "{\"reaction\":\"pouce\"}").andExpect(status().isOk()).andExpect(jsonPath("$.pose").value(true))
                .andExpect(jsonPath("$.reactions[0].reaction").value("pouce")).andExpect(jsonPath("$.reactions[0].nombre").value(1))
                .andExpect(jsonPath("$.reactions[0].parMoi").value(true)).andExpect(jsonPath("$.reactions[0].mots").value("bien reçu"));
        http.post(r, JULIE, "{\"reaction\":\"pouce\"}").andExpect(jsonPath("$.reactions[0].nombre").value(2));
        http.post(r, JULIE, "{\"reaction\":\"fait\"}").andExpect(jsonPath("$.reactions[*].reaction", contains("pouce", "fait")));
        http.get(C + "/conversations/" + fil + "/messages", CHEF).andExpect(jsonPath("$[0].reactions[0].nombre").value(2))
                .andExpect(jsonPath("$[0].reactions[0].parMoi").value(false));
        http.post(r, MARC, "{\"reaction\":\"pouce\"}").andExpect(jsonPath("$.pose").value(false)).andExpect(jsonPath("$.reactions[0].nombre").value(1))
                .andExpect(jsonPath("$.reactions[0].parMoi").value(false));
        // Hors portée : introuvable, comme un message inexistant.
        http.post(r, LEA, "{\"reaction\":\"pouce\"}").andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("message introuvable"));
        http.post(C + "/messages/inconnu/reactions", MARC, "{\"reaction\":\"pouce\"}").andExpect(status().isNotFound());
        String prive = ecrire(JULIE, fil, "{\"corps\":\"Entre nous\",\"priveA\":\"marc\"}");
        http.post(C + "/messages/" + prive + "/reactions", CHEF, "{\"reaction\":\"pouce\"}").andExpect(status().isNotFound());
        // Une personne retirée ne réagit plus à ce qu'elle a lu.
        http.delete(C + "/conversations/" + fil + "/participants/marc", CHEF).andExpect(status().isOk());
        horloge.avancer(1);
        http.post(r, MARC, "{\"reaction\":\"merci\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous ne participez plus à cette conversation"));
        http.post(C + "/conversations/" + fil + "/archiver", CHEF, null).andExpect(status().isOk());
        http.post(r, JULIE, "{\"reaction\":\"merci\"}").andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("cette conversation est archivée"));
        assertThat(jdbc.sql("SELECT action FROM msg_journal WHERE action LIKE 'message.reaction%'").query(String.class).list())
                .contains("message.reaction", "message.reaction_retiree");
    }

    @Test
    @DisplayName("appel de groupe : lancé depuis le fil, un seul à la fois ; rejoindre, ignorer, quitter ; le dernier parti le termine, durée rendue")
    void appelDeGroupe() throws Exception {
        String fil = groupe();
        String appel = JsonPath.read(corps(http.post(C + "/conversations/" + fil + "/appel", CHEF, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.etat").value("sonne")).andExpect(jsonPath("$.presents").value(1)).andExpect(jsonPath("$.monEtat").value("present"))
                .andExpect(jsonPath("$.entrant").value(false)).andExpect(jsonPath("$.invites[*].personId", contains("chef", "julie", "marc")))), "$.id");
        http.post(C + "/conversations/" + fil + "/appel", JULIE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("un appel de groupe est déjà en cours dans ce fil : rejoignez-le"));
        http.get(C + "/appels-groupe/en-cours", JULIE).andExpect(jsonPath("$[0].id").value(appel)).andExpect(jsonPath("$[0].entrant").value(true));
        http.get(C + "/appels-groupe/en-cours", LEA).andExpect(jsonPath("$.length()").value(0));
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", LEA, null).andExpect(status().isNotFound());
        http.post(C + "/appels-groupe/" + appel + "/quitter", JULIE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous n'êtes pas dans l'appel"));
        horloge.avancer(10);
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", JULIE, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("en_cours"))
                .andExpect(jsonPath("$.presents").value(2)).andExpect(jsonPath("$.debuteLe").value("2026-09-16T12:00:10.000Z"));
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", JULIE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous êtes déjà dans l'appel"));
        http.post(C + "/appels-groupe/" + appel + "/ignorer", MARC, null).andExpect(status().isOk());
        http.get(C + "/appels-groupe/en-cours", MARC).andExpect(jsonPath("$.length()").value(0));
        http.post(C + "/appels-groupe/" + appel + "/ignorer", MARC, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("seul un invité qui n'a pas encore répondu peut ignorer l'appel"));
        // Ignorer n'empêche pas de revenir.
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", MARC, null).andExpect(jsonPath("$.presents").value(3));
        http.post(C + "/appels-groupe/" + appel + "/quitter", MARC, null).andExpect(jsonPath("$.presents").value(2));
        http.post(C + "/appels-groupe/" + appel + "/quitter", CHEF, null).andExpect(jsonPath("$.etat").value("en_cours"));
        horloge.avancer(120);
        http.post(C + "/appels-groupe/" + appel + "/quitter", JULIE, null).andExpect(status().isOk()).andExpect(jsonPath("$.etat").value("termine"))
                .andExpect(jsonPath("$.dureeSecondes").value(120)).andExpect(jsonPath("$.presents").value(0));
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", JULIE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("cet appel est déjà terminé"));
        assertThat(jdbc.sql("SELECT action FROM msg_journal WHERE action LIKE 'appel_groupe.%'").query(String.class).list())
                .contains("appel_groupe.lancement", "appel_groupe.rejoindre", "appel_groupe.ignorer", "appel_groupe.quitter");
    }

    @Test
    @DisplayName("appel de groupe : sans réponse il est manqué, puis un autre peut partir ; seul, archivé ou hors du fil, on n'appelle pas")
    void appelManqueEtRefus() throws Exception {
        String fil = groupe();
        String appel = JsonPath.read(corps(http.post(C + "/conversations/" + fil + "/appel", CHEF, null).andExpect(status().isCreated())), "$.id");
        horloge.avancer(46);
        http.get(C + "/appels-groupe/en-cours", JULIE).andExpect(jsonPath("$.length()").value(0));
        http.post(C + "/appels-groupe/" + appel + "/rejoindre", JULIE, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("cet appel est déjà manqué"));
        http.post(C + "/conversations/" + fil + "/appel", JULIE, null).andExpect(status().isCreated());
        assertThat(jdbc.sql("SELECT acteurId FROM msg_journal WHERE action = 'appel_groupe.manque'").query(String.class).list())
                .containsExactly(GestionnaireAppelsGroupe.MECANISME);
        // L'appelant seul qui raccroche : manqué, personne ne l'avait rejoint.
        String seul = JsonPath.read(corps(http.post(C + "/conversations/directe", JULIE, "{\"personId\":\"lea\"}")), "$.id");
        String court = JsonPath.read(corps(http.post(C + "/conversations/" + seul + "/appel", JULIE, null).andExpect(status().isCreated())), "$.id");
        http.post(C + "/appels-groupe/" + court + "/quitter", JULIE, null).andExpect(jsonPath("$.etat").value("manque")).andExpect(jsonPath("$.dureeSecondes").value(0));
        // Hors du fil : introuvable ; seul dans le fil : refusé ; archivé : conflit.
        http.post(C + "/conversations/" + fil + "/appel", LEA, null).andExpect(status().isNotFound());
        http.delete(C + "/conversations/" + seul + "/participants/lea", LEA).andExpect(status().isOk());
        // La participation close l'est à cet instant, borne comprise : une seconde plus tard, Léa n'est plus là.
        horloge.avancer(1);
        http.post(C + "/conversations/" + seul + "/appel", JULIE, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("un appel de groupe demande au moins un autre participant actif"));
        http.post(C + "/conversations/" + seul + "/appel", LEA, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("vous ne participez plus à cette conversation"));
        http.post(C + "/conversations/" + fil + "/archiver", CHEF, null).andExpect(status().isOk());
        http.post(C + "/conversations/" + fil + "/appel", CHEF, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("cette conversation est archivée"));
        http.get(C + "/appels-groupe/en-cours", CHEF).andExpect(jsonPath("$[*].etat", containsInAnyOrder("sonne")));
    }

    @Test
    @DisplayName("cycle de vie : envoyé, reçu à la relève des avis, lu ; l'acheminement ne se montre qu'à l'auteur")
    void envoyeRecuLu() throws Exception {
        String fil = groupe();
        String m = ecrire(JULIE, fil, "{\"corps\":\"Je prends le 3412\"}");
        String fils = C + "/conversations/" + fil + "/messages";
        http.get(fils, JULIE).andExpect(jsonPath("$[0].statut.destinataires").value(2)).andExpect(jsonPath("$[0].statut.recus").value(0))
                .andExpect(jsonPath("$[0].statut.libelle").value("Envoyé"));
        http.get(fils, MARC).andExpect(jsonPath("$[0].statut").value(nullValue()));
        // Marc relève ses avis : la notification lui est parvenue.
        http.get(C + "/conversations/non-lus", MARC).andExpect(jsonPath("$.total").value(1));
        http.get(C + "/conversations/non-lus", MARC).andExpect(jsonPath("$.total").value(1));
        http.get(fils, JULIE).andExpect(jsonPath("$[0].statut.recus").value(1)).andExpect(jsonPath("$[0].statut.libelle").value("Reçu par 1 sur 2"));
        // Le chef le lit sans avoir relevé ses avis : une lecture vaut réception.
        http.post(C + "/messages/" + m + "/lu", CHEF, null).andExpect(status().isOk());
        http.get(fils, JULIE).andExpect(jsonPath("$[0].statut.recus").value(2)).andExpect(jsonPath("$[0].statut.lus").value(1))
                .andExpect(jsonPath("$[0].statut.libelle").value("Lu par 1 sur 2"));
        // Un message privé n'a qu'un destinataire.
        ecrire(JULIE, fil, "{\"corps\":\"Entre nous\",\"priveA\":\"marc\"}");
        http.get(fils, JULIE).andExpect(jsonPath("$[1].statut.destinataires").value(1));
        // Qui a lu : seulement pour qui lit lui-même le message.
        http.get(C + "/messages/" + m + "/lectures", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[0].personId").value("chef"));
        http.get(C + "/messages/" + m + "/lectures", LEA).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("modifier : l'auteur seul, un vrai changement, l'historique garde chaque version ; supprimer : auteur ou modérateur, le texte ne se sert plus")
    void modifierEtSupprimer() throws Exception {
        String fil = groupe();
        String m = ecrire(JULIE, fil, "{\"corps\":\"Briefing à 18 h\"}");
        String msg = C + "/messages/" + m;
        http.put(msg, MARC, "{\"corps\":\"autre\"}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("seul l'auteur modifie son message"));
        http.put(msg, JULIE, "{\"corps\":\" \"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("corps requis"));
        http.put(msg, JULIE, "{\"corps\":\"Briefing à 18 h \"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("aucun changement : le texte est le même"));
        horloge.avancer(60);
        http.put(msg, JULIE, "{\"corps\":\"Briefing à 18 h 45\"}").andExpect(status().isOk()).andExpect(jsonPath("$.modifie").value(true))
                .andExpect(jsonPath("$.corps").value("Briefing à 18 h 45")).andExpect(jsonPath("$.editeLe").value("2026-09-16T12:01:00.000Z"));
        horloge.avancer(60);
        http.put(msg, JULIE, "{\"corps\":\"Briefing à 18 h 45, salle 2\"}").andExpect(status().isOk());
        http.get(msg + "/historique", MARC).andExpect(status().isOk()).andExpect(jsonPath("$[*].version", contains(1, 2, 3)))
                .andExpect(jsonPath("$[*].corps", contains("Briefing à 18 h", "Briefing à 18 h 45", "Briefing à 18 h 45, salle 2")))
                .andExpect(jsonPath("$[0].ecritLe").value("2026-09-16T12:00:00.000Z")).andExpect(jsonPath("$[0].remplaceLe").value("2026-09-16T12:01:00.000Z"))
                .andExpect(jsonPath("$[2].courante").value(true));
        http.get(msg + "/historique", LEA).andExpect(status().isNotFound());
        // Supprimer : Marc, simple membre, ne supprime pas le message de Julie ; le chef, modérateur, le peut.
        http.delete(msg, MARC).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("seul l'auteur ou un modérateur du fil supprime un message"));
        http.delete(msg, CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.supprime").value(true)).andExpect(jsonPath("$.corps").value(nullValue()))
                .andExpect(jsonPath("$.supprimePar").value("chef"));
        http.get(C + "/conversations/" + fil + "/messages", MARC).andExpect(jsonPath("$[0].supprime").value(true))
                .andExpect(jsonPath("$[0].corps").value(nullValue())).andExpect(jsonPath("$[0].modifie").value(false));
        http.get(msg + "/historique", MARC).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("ce message est supprimé : son historique ne se sert plus"));
        http.delete(msg, JULIE).andExpect(status().isConflict()).andExpect(jsonPath("$.errors[0]").value("ce message est supprimé"));
        http.put(msg, JULIE, "{\"corps\":\"x\"}").andExpect(status().isConflict());
        http.post(msg + "/reactions", MARC, "{\"reaction\":\"pouce\"}").andExpect(status().isConflict());
        // Supprimé, il ne se cherche plus, ne compte plus parmi les non-lus, et l'aperçu ne dit que le fait.
        http.get(C + "/messages/recherche?q=briefing", MARC).andExpect(jsonPath("$.length()").value(0));
        http.get(C + "/conversations/non-lus", MARC).andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.conversations[0].dernierExtrait").value("Message supprimé"));
        // L'auteur supprime le sien.
        String autre = ecrire(MARC, fil, "{\"corps\":\"Erreur de fil\"}");
        http.delete(C + "/messages/" + autre, MARC).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT action || ' ' || apres FROM msg_journal WHERE action IN ('message.modification', 'message.suppression') ORDER BY horodatage")
                .query(String.class).list()).allSatisfy(l -> assertThat(l).doesNotContain("Briefing").doesNotContain("Erreur de fil"))
                .hasSize(4);
    }
}
