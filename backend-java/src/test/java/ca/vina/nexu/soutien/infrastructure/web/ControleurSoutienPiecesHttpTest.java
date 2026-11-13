package ca.vina.nexu.soutien.infrastructure.web;

import ca.vina.nexu.soutien.domaine.EngagementsDeService;

import ca.vina.nexu.soutien.infrastructure.persistance.AvisSoutienJournal;
import ca.vina.nexu.soutien.infrastructure.persistance.DepotSoutien;
import ca.vina.nexu.soutien.infrastructure.configuration.ConfigurationSoutien;
import ca.vina.nexu.partage.temps.Iso;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.Antivirus;
import ca.vina.nexu.soutien.domaine.AvisSoutien;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Port des ajouts du 14 sept. de `soutien.integration.test.ts` : billet complet (numéro, catégorie, impact, écran, pièces analysées),
 * réponse avec pièces et avis à l'auteur, satisfaction, résumé et dispositif d'avis.
 */
@DisplayName("/api/soutien — billets complets, pièces jointes, satisfaction, avis")
class ControleurSoutienPiecesHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-09-14T12:00:00Z"), ZoneOffset.UTC);
    private static final ContexteRequete ALICE = new ContexteRequete("usr-alice", "alice", "employee", "emp-alice", "t-1");
    private static final ContexteRequete BOB = new ContexteRequete("usr-bob", "bob", "employee", null, "t-1");
    private static final ContexteRequete SA = new ContexteRequete("usr-sa", "sa", "superadmin", null, null);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final TypeReference<Map<String, Object>> CARTE = new TypeReference<>() { };
    private static final String PNG = Base64.getEncoder().encodeToString("capture-png".getBytes(StandardCharsets.UTF_8));
    private static final String EICAR = Base64.getEncoder().encodeToString(Antivirus.EICAR.getBytes(StandardCharsets.UTF_8));

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private AvisSoutienJournal avis;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        avis = new AvisSoutienJournal();
        DepotSoutien depot = new DepotSoutien(jdbc, HORLOGE, avis, Map.of("SOUTIEN_COURRIEL", "soutien@partage.qc"), new Antivirus.AnalyseurSimule());
        ConfigurationSoutien config = new ConfigurationSoutien();
        http = new ClientHttpEssai(new ControleurSoutien(config.lectureSoutien(depot, () -> Iso.de(HORLOGE.instant()), t -> EngagementsDeService.Politique.STANDARD), config.gestionnaireSoutien(depot), new GardeAcces(new RegistrePolitiqueAcces())));
        jdbc.sql("INSERT INTO users (id, username, passwordHash, role, createdAt, active, email, employeeId) VALUES ('usr-sa', 'sa', 'x', 'superadmin', 'd', 1, NULL, NULL), ('usr-alice', 'alice', 'x', 'employee', 'd', 1, NULL, 'emp-alice'), ('usr-bob', 'bob', 'x', 'employee', 'd', 1, NULL, NULL)").update();
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, email) VALUES ('emp-alice', 'Alice', 'Roy', 't-1', 'alice@css.qc')").update();
    }

    private static String piece(String nom, String type, String contenu) {
        return "{\"nom\":\"" + nom + "\",\"typeMime\":\"" + type + "\",\"contenu\":\"" + contenu + "\"}";
    }

    private String ouvrir(ContexteRequete qui, String extra) throws Exception {
        String r = http.post("/api/soutien/tickets", qui, "{\"sujet\":\"Bouton Exporter sans effet\",\"description\":\"Rien ne se passe au clic sur Exporter.\",\"niveau\":\"N3\"" + extra + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return (String) JSON.readValue(r, CARTE).get("id");
    }

    @Test
    @DisplayName("politique : catégories, impacts, limites des pièces")
    void politique() throws Exception {
        http.get("/api/soutien/politique", null).andExpect(status().isOk()).andExpect(jsonPath("$.categories", hasSize(6))).andExpect(jsonPath("$.categories[0].code").value("incident"))
                .andExpect(jsonPath("$.impacts[2].libelle").value("Tout l'établissement")).andExpect(jsonPath("$.pieces.maxParEnvoi").value(5)).andExpect(jsonPath("$.pieces.tailleMaxOctets").value(10485760));
    }

    @Test
    @DisplayName("billet complet : numéro, catégorie, impact, écran, pièces analysées ; refus nommés sans laisser de billet")
    void billetComplet() throws Exception {
        String id = ouvrir(ALICE, ",\"categorie\":\"incident\",\"impact\":\"equipe\",\"ecran\":\"/admin/time\",\"navigateur\":\"Chrome 130 · macOS\",\"pieces\":[" + piece("capture.png", "image/png", PNG) + "," + piece("journal.txt", "text/plain", Base64.getEncoder().encodeToString("ERR 500".getBytes())) + "]");
        http.get("/api/soutien/tickets/" + id, ALICE).andExpect(jsonPath("$.numero").value(1)).andExpect(jsonPath("$.categorie").value("incident")).andExpect(jsonPath("$.impact").value("equipe"))
                .andExpect(jsonPath("$.ecran").value("/admin/time")).andExpect(jsonPath("$.navigateur").value("Chrome 130 · macOS")).andExpect(jsonPath("$.pieces", hasSize(2)))
                .andExpect(jsonPath("$.pieces[0].nom").value("capture.png")).andExpect(jsonPath("$.pieces[0].taille").value(11)).andExpect(jsonPath("$.pieces[0].messageId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pieces[0].contenu").doesNotExist()).andExpect(jsonPath("$.satisfaction").value(org.hamcrest.Matchers.nullValue()));
        String suivant = ouvrir(ALICE, "");
        http.get("/api/soutien/tickets/" + suivant, ALICE).andExpect(jsonPath("$.numero").value(2)).andExpect(jsonPath("$.categorie").value("incident")).andExpect(jsonPath("$.impact").value("moi")).andExpect(jsonPath("$.pieces", hasSize(0)));

        String pieceId = (String) ((Map<?, ?>) ((List<?>) JSON.readValue(http.get("/api/soutien/tickets/" + id, ALICE).andReturn().getResponse().getContentAsString(), CARTE).get("pieces")).get(0)).get("id");
        http.get("/api/soutien/tickets/" + id + "/pieces/" + pieceId, ALICE).andExpect(status().isOk()).andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff")).andExpect(header().string("Content-Disposition", "attachment; filename=\"capture.png\""))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("capture-png".getBytes()));
        http.get("/api/soutien/tickets/" + id + "/pieces/" + pieceId, BOB).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("pièce introuvable"));
        http.get("/api/soutien/tickets/" + id + "/pieces/absente", ALICE).andExpect(status().isNotFound());
        http.get("/api/soutien/tickets/" + id + "/pieces/" + pieceId, SA).andExpect(status().isOk());
        http.get("/api/soutien/tickets/" + id + "/pieces/" + pieceId, null).andExpect(status().isUnauthorized());

        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Pièce refusée\",\"description\":\"Une description assez longue.\",\"niveau\":\"N4\",\"pieces\":[" + piece("virus.exe", "application/x-msdownload", PNG) + "]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("pièce 1 : type non admis (application/x-msdownload)"));
        StringBuilder six = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            six.append(i > 0 ? "," : "").append(piece("p" + i + ".png", "image/png", PNG));
        }
        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Trop de pièces\",\"description\":\"Une description assez longue.\",\"niveau\":\"N4\",\"categorie\":\"bizarre\",\"impact\":\"x\",\"pieces\":[" + six + "]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("catégorie inconnue : incident, question, demande, acces, donnees, autre; impact inconnu : moi, equipe, etablissement; 5 pièces jointes au plus par envoi")));
        int avant = jdbc.sql("SELECT COUNT(*) FROM soutien_tickets").query(Integer.class).single();
        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Pièce infectée\",\"description\":\"Une description assez longue.\",\"niveau\":\"N4\",\"pieces\":[" + piece("eicar.txt", "text/plain", EICAR) + "]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.containsString("antivirus")));
        assertThat(jdbc.sql("SELECT COUNT(*) FROM soutien_tickets").query(Integer.class).single()).isEqualTo(avant);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM soutien_pieces").query(Integer.class).single()).isEqualTo(2);
        // Une pièce sans nom, sans contenu, illisible en base64 : nommée, pas plantée.
        http.post("/api/soutien/tickets", ALICE, "{\"sujet\":\"Pièce vide\",\"description\":\"Une description assez longue.\",\"niveau\":\"N4\",\"pieces\":[{\"nom\":\" \",\"typeMime\":\"image/png\",\"contenu\":\"%%%\"},\"pas une pièce\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("pièce 1 : nom requis (200 caractères au plus); pièce 2 : nom requis (200 caractères au plus); pièce 2 : contenu requis; pièce 2 : type non admis (inconnu)"));
    }

    @Test
    @DisplayName("réponse avec pièces : l'auteur est avisé par courriel (compte, sinon fiche) ; une note interne ne l'avise pas et cache ses pièces")
    void reponseEtAvisAuteur() throws Exception {
        String id = ouvrir(ALICE, "");
        int avant = avis.envoyes().size();
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"Pouvez-vous réessayer ?\",\"pieces\":[" + piece("correctif.pdf", "application/pdf", Base64.getEncoder().encodeToString("%PDF".getBytes())) + "]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.pieces", hasSize(1))).andExpect(jsonPath("$.pieces[0].messageId").isNotEmpty());
        AvisSoutien.Courriel c = avis.envoyes().get(avant);
        assertThat(c.to()).isEqualTo("alice@css.qc");
        assertThat(c.subject()).isEqualTo("[Soutien] Réponse à votre billet #1 : Bouton Exporter sans effet");
        assertThat(c.text()).contains("Bonjour alice,").contains("Pouvez-vous réessayer ?");
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"Vu avec l'équipe.\",\"interne\":true,\"pieces\":[" + piece("interne.txt", "text/plain", PNG) + "]}").andExpect(status().isCreated());
        // une pièce qui n'est pas un objet est ignorée comme une pièce vide (le dépôt la refuse)
        http.post("/api/soutien/tickets/" + id + "/messages", SA, "{\"texte\":\"x\",\"pieces\":[\"rien\"]}").andExpect(status().isBadRequest());
        assertThat(avis.envoyes()).hasSize(avant + 1);
        http.get("/api/soutien/tickets/" + id, ALICE).andExpect(jsonPath("$.pieces", hasSize(1)));
        http.get("/api/soutien/tickets/" + id, SA).andExpect(jsonPath("$.pieces", hasSize(2)));
        // Une pièce infectée sur une réponse : le message n'est pas écrit.
        int messages = jdbc.sql("SELECT COUNT(*) FROM soutien_messages").query(Integer.class).single();
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\"Voici.\",\"pieces\":[" + piece("eicar.txt", "text/plain", EICAR) + "]}").andExpect(status().isBadRequest());
        http.post("/api/soutien/tickets/" + id + "/messages", ALICE, "{\"texte\":\"Voici.\",\"pieces\":[" + piece("x.exe", "application/x-msdownload", PNG) + "]}").andExpect(status().isBadRequest());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM soutien_messages").query(Integer.class).single()).isEqualTo(messages);
        // Résolu : l'auteur est avisé ; Bob, sans courriel ni fiche, ne reçoit rien.
        http.post("/api/soutien/tickets/" + id + "/statut", SA, "{\"statut\":\"resolu\"}").andExpect(status().isOk());
        assertThat(avis.envoyes().get(avis.envoyes().size() - 1).subject()).isEqualTo("[Soutien] Votre billet #1 est résolu : Bouton Exporter sans effet");
        String deBob = ouvrir(BOB, "");
        int n = avis.envoyes().size();
        http.post("/api/soutien/tickets/" + deBob + "/messages", SA, "{\"texte\":\"On regarde.\"}").andExpect(status().isCreated());
        assertThat(avis.envoyes()).hasSize(n);
        jdbc.sql("UPDATE users SET employeeId = 'emp-absent' WHERE id = 'usr-bob'").update();
        http.post("/api/soutien/tickets/" + deBob + "/statut", SA, "{\"statut\":\"resolu\"}").andExpect(status().isOk());
        assertThat(avis.envoyes()).hasSize(n);
        jdbc.sql("UPDATE users SET email = 'bob@x.qc' WHERE id = 'usr-bob'").update();
        http.post("/api/soutien/tickets/" + deBob + "/messages", SA, "{\"texte\":\"Réglé.\"}").andExpect(status().isCreated());
        assertThat(avis.envoyes().get(avis.envoyes().size() - 1).to()).isEqualTo("bob@x.qc");
        assertThat(AvisSoutien.composerPourAuteur("x", new AvisSoutien.Billet("b", "S", "D", "N4", "a", "e"), null, false, null).subject()).isEqualTo("[Soutien] Votre billet est résolu : S");
    }

    @Test
    @DisplayName("satisfaction : une fois résolu, par l'auteur, note entière de 1 à 5")
    void satisfaction() throws Exception {
        String id = ouvrir(ALICE, "");
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":5}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("la satisfaction se donne une fois le billet résolu"));
        http.post("/api/soutien/tickets/" + id + "/statut", SA, "{\"statut\":\"resolu\"}").andExpect(status().isOk());
        http.post("/api/soutien/tickets/" + id + "/satisfaction", SA, "{\"note\":5}").andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("réservé à l'auteur du billet"));
        http.post("/api/soutien/tickets/" + id + "/satisfaction", BOB, "{\"note\":5}").andExpect(status().isNotFound());
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":9,\"commentaire\":\"" + "c".repeat(1001) + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("note de 1 à 5 requise; commentaire trop long (1000 caractères au plus)"));
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":2.5}").andExpect(status().isBadRequest());
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":\"4\"}").andExpect(status().isBadRequest());
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":4,\"commentaire\":\" Réglé vite. \"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfaction").value(4)).andExpect(jsonPath("$.satisfactionCommentaire").value("Réglé vite.")).andExpect(jsonPath("$.satisfactionLe").value("2026-09-14T12:00:00.000Z"));
        http.post("/api/soutien/tickets/" + id + "/satisfaction", ALICE, "{\"note\":5}").andExpect(status().isOk()).andExpect(jsonPath("$.satisfactionCommentaire").value(org.hamcrest.Matchers.nullValue()));
        http.post("/api/soutien/tickets/" + id + "/satisfaction", null, "{\"note\":5}").andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("résumé et dispositif d'avis : ouverts, nouveaux, en retard ; courriel du compte réglable, transport nommé")
    void resumeEtNotification() throws Exception {
        http.get("/api/soutien/tickets/resume", ALICE).andExpect(status().isForbidden());
        http.get("/api/soutien/tickets/resume", SA).andExpect(jsonPath("$.ouverts").value(0)).andExpect(jsonPath("$.dernierOuvertLe").value(org.hamcrest.Matchers.nullValue()));
        String a = ouvrir(ALICE, "");
        String b = ouvrir(ALICE, "");
        String c = ouvrir(BOB, "");
        http.post("/api/soutien/tickets/" + b + "/messages", SA, "{\"texte\":\"On regarde.\"}").andExpect(status().isCreated());
        http.post("/api/soutien/tickets/" + c + "/statut", SA, "{\"statut\":\"resolu\"}").andExpect(status().isOk());
        http.post("/api/soutien/tickets/" + c + "/statut", ALICE, "{\"statut\":\"ferme\"}").andExpect(status().isNotFound());
        http.post("/api/soutien/tickets/" + c + "/statut", BOB, "{\"statut\":\"ferme\"}").andExpect(status().isOk());
        // L'échéance se calcule depuis l'ouverture, selon la politique : un billet ouvert il y a cinq jours est en retard.
        jdbc.sql("UPDATE soutien_tickets SET ouvertLe = '2026-09-09T12:00:00.000Z' WHERE id = ?").param(a).update();
        http.get("/api/soutien/tickets/resume", SA).andExpect(jsonPath("$.ouverts").value(2)).andExpect(jsonPath("$.nouveaux").value(1)).andExpect(jsonPath("$.enRetard").value(1)).andExpect(jsonPath("$.dernierOuvertLe").value("2026-09-14T12:00:00.000Z"));

        http.get("/api/soutien/notification", ALICE).andExpect(status().isForbidden());
        http.get("/api/soutien/notification", SA).andExpect(status().isOk()).andExpect(jsonPath("$.transport.mode").value("journal")).andExpect(jsonPath("$.courrielDuCompte").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.boitePartagee").value("soutien@partage.qc")).andExpect(jsonPath("$.destinataires", hasSize(1)));
        http.put("/api/soutien/notification", SA, "{\"courriel\":\"pas-un-courriel\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("courriel invalide"));
        http.put("/api/soutien/notification", SA, "{\"courriel\":\" Chef@Soutien.qc \"}").andExpect(status().isOk()).andExpect(jsonPath("$.courrielDuCompte").value("chef@soutien.qc")).andExpect(jsonPath("$.destinataires[0]").value("chef@soutien.qc"));
        http.put("/api/soutien/notification", SA, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.courrielDuCompte").value(org.hamcrest.Matchers.nullValue()));
        http.put("/api/soutien/notification", new ContexteRequete("usr-inconnu", "x", "superadmin", null, null), "{\"courriel\":\"a@b.qc\"}").andExpect(status().isNotFound());
        assertThat(new DepotSoutien(jdbc, HORLOGE).etatNotification("usr-sa").boitePartagee()).isNull();
    }

    @Test
    @DisplayName("le transport du courrier se lit sur l'adaptateur d'avis, et un refus SMTP ne compte pas comme parti")
    void transport() {
        assertThat(avis.transport().mode()).isEqualTo("journal");
        AvisSoutienJournal refusant = new AvisSoutienJournal(new Courrier() {
            @Override
            public boolean envoyer(Message message) {
                return false;
            }

            @Override
            public Transport transport() {
                return new Transport("smtp", "smtp.exemple.qc", "x@y.qc");
            }
        });
        assertThat(refusant.aviser(List.of(new AvisSoutien.Courriel("a@b.qc", "S", "T")))).isZero();
        assertThat(refusant.envoyes()).hasSize(1);
        assertThat(refusant.transport().hote()).isEqualTo("smtp.exemple.qc");
        AvisSoutien parDefaut = courriels -> courriels.size();
        assertThat(parDefaut.transport().mode()).isEqualTo("journal");
    }
}
