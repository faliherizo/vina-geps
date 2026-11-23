package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireTextosEntrants;
import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.Semantique;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.communication.infrastructure.persistance.DepotGroupesTransverses;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotTextosEntrants;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.telephonie.Telephonie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ST-G05 : un texto entrant, signé par Twilio, répond à la dernière alerte ouverte vers ce numéro (critères 4, 9, 10). */
@DisplayName("/api/alertes/webhooks/texto — signature, rattachement, intention, rejeu")
class ControleurWebhooksAlertesHttpTest {

    private static final String T = "T1";
    private static final String JETON = "jeton-twilio-essai";
    private static final String URL = "https://geps.exemple";
    private static final String CHEMIN = ControleurWebhooksAlertes.CHEMIN_TEXTO;

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private GestionnaireEnvoiAlertes envoi;
    private DepotAlertes depot;
    private int sequence;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        new ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification(jdbc);
        for (String[] u : new String[][]{{"chef", null}, {"julie", "(514) 555-0101"}, {"marc", "514-555-0102"}}) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt) VALUES (?, ?, ?, ?, 'x', 'employee', ?, 'x')")
                    .params("u-" + u[0], u[0] + "@@" + T, u[0], T, "e-" + u[0]).update();
            jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, phone) VALUES (?, ?, 'Essai', ?, ?)").params("e-" + u[0], u[0], T, u[1]).update();
        }
        GroupesDeTravailPort groupes = new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of();
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                return List.of();
            }
        };
        AdaptateurAnnuaireAlertes annuaire = new AdaptateurAnnuaireAlertes(groupes, new LectureListesDistributionPort() {
            @Override
            public Optional<ListesDistribution.Liste> lire(String tenantId, String listeId) {
                return Optional.empty();
            }

            @Override
            public List<ListesDistribution.Liste> visibles(String tenantId, String acteurId) {
                return List.of();
            }

            @Override
            public boolean nomPris(String tenantId, String auteurId, String nom) {
                return false;
            }

            @Override
            public List<String> membresActifsDuGroupe(String tenantId, String groupeId) {
                return List.of();
            }
        }, new DepotGroupesTransverses(jdbc), jdbc);
        depot = new DepotAlertes(jdbc);
        Clock horloge = Clock.fixed(Instant.parse("2026-11-16T14:00:00Z"), ZoneOffset.UTC);
        envoi = new GestionnaireEnvoiAlertes(new GestionnaireEnvoiAlertes.Dependances(depot, depot, annuaire, PublicationAvis.MUET), () -> "al-" + ++sequence, horloge);
        GestionnaireTextosEntrants textos = new GestionnaireTextosEntrants(depot, new DepotTextosEntrants(jdbc), new GestionnaireReceptionAlertes(depot, depot, horloge), horloge);
        ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes appels = new ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes(
                new ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes.Dependances(depot,
                        new ca.vina.nexu.alertes.infrastructure.persistance.DepotAppelsAlertes(jdbc), a -> "SIM", new GestionnaireReceptionAlertes(depot, depot, horloge)),
                new ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes.Rappels(URL), () -> "ap", horloge);
        http = new ClientHttpEssai(new ControleurWebhooksAlertes(textos, appels, new ControleurWebhooksAlertes.Reglages(URL, JETON)));
        depot.enregistrer(T, Alertes.ReponsePredefinie.nouvelle("rp-ok", "J'arrive", Semantique.PRISE_EN_CHARGE));
        depot.enregistrer(T, Alertes.ReponsePredefinie.nouvelle("rp-non", "Indisponible", Semantique.REFUS));
    }

    private String alerte(String titre, List<String> reponses, String... personnes) {
        return envoi.envoyer(new Acteur(T, "chef"), new GestionnaireEnvoiAlertes.Demande(new Alertes.Composition(titre, "5e", Priorite.URGENTE, reponses),
                new GestionnaireEnvoiAlertes.Cibles(List.of(personnes), List.of(), List.of()), null, false, null)).id();
    }

    private ResultActions texto(String sid, String de, String corps, boolean signe) throws Exception {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("MessageSid", sid);
        p.put("From", de);
        p.put("Body", corps);
        StringBuilder donnees = new StringBuilder(URL + CHEMIN);
        new TreeMap<>(p).forEach((k, v) -> donnees.append(k).append(v));
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(CHEMIN).header("X-Twilio-Signature", signe ? Telephonie.signer(donnees.toString(), JETON) : "faux");
        p.forEach(b::param);
        return http.envoyer(b, null);
    }

    private String reponseDe(String alerteId, String personId) {
        return depot.alerte(T, alerteId).orElseThrow().remiseDe(personId).reponseId();
    }

    @Test
    @DisplayName("« OK » prend en charge la dernière alerte ouverte vers ce numéro ; la confirmation revient en TwiML")
    void priseEnCharge() throws Exception {
        String ancienne = alerte("Ancienne", List.of("rp-ok", "rp-non"), "julie");
        String recente = alerte("Code bleu <512> & 5e", List.of("rp-ok", "rp-non"), "julie", "marc");
        texto("SM1", "+15145550101", "ok", true).andExpect(status().isOk())
                .andExpect(content().string("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response><Message>Prise en charge enregistrée : "
                        + "« Code bleu &lt;512&gt; &amp; 5e ». Merci.</Message></Response>"));
        assertThat(reponseDe(recente, "julie")).isEqualTo("rp-ok");
        assertThat(reponseDe(ancienne, "julie")).isNull();
        // La suivante ouverte, maintenant, est l'ancienne : « 2 » la refuse.
        texto("SM2", "514 555 0101", "Non !", true).andExpect(content().string(org.hamcrest.Matchers.containsString("Refus enregistré : « Ancienne »")));
        assertThat(reponseDe(ancienne, "julie")).isEqualTo("rp-non");
        assertThat(jdbc.sql("SELECT issue FROM alr_textos_entrants ORDER BY recuLe, messageSid").query(String.class).list()).containsExactly("prise_en_charge", "refus");
    }

    @Test
    @DisplayName("un mot inconnu reçoit l'aide et ne compte pas ; un rejeu ne fait rien ; une signature fausse est refusée")
    void aideRejeuSignature() throws Exception {
        String a = alerte("Code bleu", List.of("rp-ok"), "marc");
        texto("SM1", "+15145550102", "j'arrive dans 5", true).andExpect(content().string(org.hamcrest.Matchers.containsString("Répondez 1 (ou OK)")));
        assertThat(reponseDe(a, "marc")).isNull();
        texto("SM2", "+15145550102", "1", true).andExpect(status().isOk());
        assertThat(reponseDe(a, "marc")).isEqualTo("rp-ok");
        texto("SM2", "+15145550102", "1", true).andExpect(content().string("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Response/>"));
        texto("SM3", "+15145550102", "1", false).andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM alr_textos_entrants").query(Integer.class).single()).isEqualTo(2);
        http.envoyer(MockMvcRequestBuilders.post(CHEMIN).header("X-Twilio-Signature", Telephonie.signer(URL + CHEMIN, JETON)), null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("un numéro inconnu, illisible, ou sans alerte ouverte : « aucune alerte » ; une réponse non proposée le dit")
    void sansAlerte() throws Exception {
        texto("SM1", "+15145559999", "1", true).andExpect(content().string(org.hamcrest.Matchers.containsString("Aucune alerte n'attend votre réponse.")));
        texto("SM2", "pas un numéro", "1", true).andExpect(content().string(org.hamcrest.Matchers.containsString("Aucune alerte")));
        String a = alerte("Info", List.of("rp-ok"), "julie");
        texto("SM3", "+15145550101", "2", true).andExpect(content().string(org.hamcrest.Matchers.containsString("n'est pas proposée par « Info »")));
        assertThat(reponseDe(a, "julie")).isNull();
    }

    @Test
    @DisplayName("déjà répondu à l'écran entre-temps : le texto le dit, sans rien changer")
    void dejaRepondu() throws Exception {
        String a = alerte("Code bleu", List.of("rp-ok", "rp-non"), "julie");
        DepotTextosEntrants port = new DepotTextosEntrants(jdbc) {
            @Override
            public Optional<ca.vina.nexu.alertes.domaine.TextosEntrants.RemiseOuverte> derniereRemiseOuverte(String numero) {
                return Optional.of(new ca.vina.nexu.alertes.domaine.TextosEntrants.RemiseOuverte(T, a, "julie"));
            }
        };
        Clock horloge = Clock.fixed(Instant.parse("2026-11-16T14:00:00Z"), ZoneOffset.UTC);
        GestionnaireReceptionAlertes reception = new GestionnaireReceptionAlertes(depot, depot, horloge);
        reception.repondre(new Acteur(T, "julie"), a, "rp-non");
        GestionnaireTextosEntrants textos = new GestionnaireTextosEntrants(depot, port, reception, horloge);
        assertThat(textos.recevoir(new ca.vina.nexu.alertes.domaine.TextosEntrants.Entrant("SM9", "+15145550101", "1")).issue())
                .isEqualTo(ca.vina.nexu.alertes.domaine.TextosEntrants.Issue.DEJA_REPONDU);
        assertThat(reponseDe(a, "julie")).isEqualTo("rp-non");
    }

    private ResultActions signe(String chemin, String requete, Map<String, String> corps) throws Exception {
        StringBuilder donnees = new StringBuilder(URL + chemin + requete);
        new TreeMap<>(corps).forEach((k, v) -> donnees.append(k).append(v));
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.post(chemin + requete).header("X-Twilio-Signature", Telephonie.signer(donnees.toString(), JETON));
        corps.forEach(b::param);
        return http.envoyer(b, null);
    }

    @Test
    @DisplayName("les rappels mal formés : texto sans expéditeur (400), rappel vocal sans appel (403), durée illisible ignorée")
    void rappelsMalFormes() throws Exception {
        signe(CHEMIN, "", Map.of("MessageSid", "SM1", "From", " ")).andExpect(status().isBadRequest());
        signe(CHEMIN, "", Map.of("MessageSid", " ", "From", "+15145550101")).andExpect(status().isBadRequest());
        signe("/api/alertes/webhooks/voix", "", Map.of("Digits", "1")).andExpect(status().isForbidden());
        jdbc.sql("INSERT INTO alr_appels (id, tenantId, alerteId, etape, personId, resultat, lanceLe) VALUES ('ap-9', ?, 'a', 0, 'julie', 'lance', 'x')").param(T).update();
        signe("/api/alertes/webhooks/voix/statut", "?appel=ap-9", Map.of("CallStatus", "busy", "CallDuration", "trois")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT resultat || '|' || COALESCE(dureeSecondes, -1) FROM alr_appels WHERE id = 'ap-9'").query(String.class).single()).isEqualTo("occupe|-1");
        signe("/api/alertes/webhooks/voix/statut", "?appel=ap-9", Map.of("CallStatus", "completed")).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT resultat FROM alr_appels WHERE id = 'ap-9'").query(String.class).single()).isEqualTo("decroche");
    }
}
