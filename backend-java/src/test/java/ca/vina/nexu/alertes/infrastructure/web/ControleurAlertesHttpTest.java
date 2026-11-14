package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.infrastructure.configuration.ConfigurationAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAlertes;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("/api/alertes — paramètres, envoi, réception, suivi : les règles R1 à R8 à travers HTTP et la base")
class ControleurAlertesHttpTest {

    private static final String T = "T1";
    private static final String A = "/api/alertes";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "admin", "admin", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", "E-CHEF", T);
    private static final ContexteRequete DIRECTRICE = new ContexteRequete("u-dir", "dir", "director", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("u-julie", "julie", "employee", "E-JULIE", T);
    private static final ContexteRequete MARC = new ContexteRequete("u-marc", "marc", "employee", null, T);
    private static final ContexteRequete LEA = new ContexteRequete("u-lea", "lea", "employee", null, T);

    private final List<PublicationAvis.Avis> avisPublies = new ArrayList<>();
    private JdbcClient jdbc;
    private final List<PublicationAvis.Envoi> envoisRendus = new ArrayList<>();
    /** Deux listes de la messagerie : une partagée (une personne, un groupe), une privée de Léa. */
    private final LectureListesDistributionPort listes = new LectureListesDistributionPort() {
        private final List<ListesDistribution.Liste> toutes = List.of(
                new ListesDistribution.Liste("l-garde", "Garde de nuit", true, "chef", "x",
                        List.of(new ListesDistribution.Membre(ListesDistribution.Nature.PERSONNE, "lea"), new ListesDistribution.Membre(ListesDistribution.Nature.GROUPE, "EST"))),
                new ListesDistribution.Liste("l-lea", "Mes proches", false, "lea", "x", List.of(new ListesDistribution.Membre(ListesDistribution.Nature.PERSONNE, "marc"))));

        @Override
        public java.util.Optional<ListesDistribution.Liste> lire(String tenantId, String listeId) {
            return toutes.stream().filter(l -> l.id().equals(listeId)).findFirst();
        }

        @Override
        public List<ListesDistribution.Liste> visibles(String tenantId, String acteurId) {
            return toutes.stream().filter(l -> l.partagee() || l.creeePar().equals(acteurId)).toList();
        }

        @Override
        public boolean nomPris(String tenantId, String auteurId, String nom) {
            return false;
        }

        @Override
        public List<String> membresActifsDuGroupe(String tenantId, String groupeId) {
            return "EST".equals(groupeId) ? List.of("julie") : List.of();
        }
    };
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"alertes\"]')").param(T).update();
        // La forme réelle des comptes : la clé technique « login@@organisme » et le nom de connexion à part.
        for (String[] u : new String[][]{{"u-julie", "julie", "E-JULIE"}, {"u-marc", "marc", null}, {"u-chef", "chef", "E-CHEF"}, {"u-lea", "lea", null}, {"u-dir", "dir", null}}) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, employeeId, createdAt) VALUES (?, ?, ?, ?, 'x', 'employee', ?, 'x')")
                    .params(u[0], u[1] + "@@" + T, u[1], T, u[2]).update();
        }
        // Les ressources d'effectifs nomment les personnes : « Julie Roy » plutôt que « julie ».
        new ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification(jdbc);
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, titreEmploiCode, statutEmploiCode, conventionCode, actif, creeLe) "
                + "VALUES ('rs1', ?, 'P1', 'Julie Roy', 'u-julie', 'EST', 'PARA', 'TECH', 'TC', 'FSSS', 1, 'x')").param(T).update();
        GroupesDeTravailPort groupes = new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of(new Groupe("EST", "EST", "Opérations Est"), new Groupe("DIR", "DIR", "Direction"));
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                if ("DIR".equals(groupeId)) {
                    return List.of(new Membre("dir", "Diane Roy"));
                }
                return "EST".equals(groupeId) ? List.of(new Membre("julie", "Julie Roy"), new Membre("marc", "Marc Dubé"), new Membre("parti", "Parti")) : List.of();
            }
        };
        DepotAlertes depot = new DepotAlertes(jdbc);
        AdaptateurAnnuaireAlertes annuaire = new AdaptateurAnnuaireAlertes(groupes, listes, jdbc);
        AtomicInteger n = new AtomicInteger();
        Clock horloge = Clock.fixed(Instant.parse("2026-11-14T14:02:00Z"), ZoneOffset.UTC);
        PublicationAvis avis = a -> {
            avisPublies.add(a);
            return List.copyOf(envoisRendus);
        };
        ControleurAlertes.Cas cas = new ControleurAlertes.Cas(new GestionnaireParametresAlertes(new GestionnaireParametresAlertes.Dependances(depot, depot, annuaire, horloge),
                () -> "id-" + n.incrementAndGet()),
                new GestionnaireEnvoiAlertes(new GestionnaireEnvoiAlertes.Dependances(depot, depot, annuaire, avis), () -> "al-" + n.incrementAndGet(), horloge),
                new GestionnaireReceptionAlertes(depot, depot, horloge), new LectureAlertes(depot, annuaire));
        GardeAlertes garde = new GardeAlertes(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc));
        http = new ClientHttpEssai(new ControleurAlertes(garde, cas, horloge));
    }

    private static String corps(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private String creerReponse(String libelle, String semantique) throws Exception {
        return JsonPath.read(corps(http.post(A + "/reponses", ADMIN, "{\"libelle\":\"" + libelle + "\",\"semantique\":\"" + semantique + "\"}")
                .andExpect(status().isCreated())), "$.id");
    }

    @Test
    @DisplayName("l'administration tient réponses et modèles ; un planificateur les lit mais ne les change pas ; un code inconnu répond 400 nommé")
    void parametres() throws Exception {
        String arrive = creerReponse("J'arrive", "prise_en_charge");
        String indispo = creerReponse("Indisponible", "refus");
        http.post(A + "/reponses", ADMIN, "{\"libelle\":\"Peut-être\",\"semantique\":\"oui\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« semantique » : prise_en_charge, refus ou information"));
        http.post(A + "/reponses", CHEF, "{\"libelle\":\"X\",\"semantique\":\"refus\"}").andExpect(status().isForbidden());
        http.post(A + "/modeles", ADMIN, "{\"titre\":\"Code bleu\",\"corps\":\"Chambre 512\",\"priorite\":\"critique\",\"reponseIds\":[\"" + arrive + "\",\"" + indispo + "\"]}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.reponseIds.length()").value(2));
        http.post(A + "/modeles", ADMIN, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"haute\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« priorite » : information, urgente ou critique"));
        http.post(A + "/modeles", ADMIN, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"urgente\",\"reponseIds\":[\"inconnue\"]}").andExpect(status().isBadRequest());
        http.post(A + "/modeles", ADMIN, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"urgente\",\"reponseIds\":\"pas-une-liste\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« reponseIds » doit être une liste de textes"));
        String modele = JsonPath.read(corps(http.get(A + "/modeles", CHEF).andExpect(status().isOk())), "$[0].id");
        http.patch(A + "/modeles/" + modele, ADMIN, "{\"actif\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false));
        http.patch(A + "/modeles/inconnu", ADMIN, "{\"actif\":false}").andExpect(status().isNotFound());
        http.patch(A + "/modeles/" + modele, ADMIN, "{\"actif\":\"non\"}").andExpect(status().isBadRequest());
        http.patch(A + "/reponses/" + indispo, ADMIN, "{\"active\":false}").andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        http.patch(A + "/reponses/inconnue", ADMIN, "{\"active\":true}").andExpect(status().isNotFound());
        http.get(A + "/reponses", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        http.get(A + "/groupes", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$[0].nom").value("Opérations Est"));
        http.get(A + "/groupes/EST/membres", CHEF).andExpect(status().isOk()).andExpect(jsonPath("$[0].nom").value("Julie Roy"))
                .andExpect(jsonPath("$[0].personId").value("julie")).andExpect(jsonPath("$.length()").value(3));
        http.get(A + "/groupes/EST/membres", JULIE).andExpect(status().isForbidden());
        http.post(A + "/reponses", ADMIN, "{\"libelle\":42,\"semantique\":\"refus\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« libelle » doit être un texte"));
    }

    @Test
    @DisplayName("envoyer à un groupe et à une personne : snapshot nommé, expéditeur exclu ; la priorité critique impose tous les canaux")
    void envoyer() throws Exception {
        String arrive = creerReponse("J'arrive", "prise_en_charge");
        String codeBleu = "{\"titre\":\"Code bleu 512\",\"corps\":\"5e étage\",\"priorite\":\"critique\",\"reponseIds\":[\"" + arrive + "\"],"
                + "\"groupes\":[\"EST\"],\"personnes\":[\"chef\",\"lea\"]}";
        // ST-G01 : la priorité critique se réserve ; un chef sans la capacité est refusé, et rien ne part.
        http.post(A, CHEF, codeBleu).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("la priorité critique est réservée aux personnes qui en ont le droit (alertes.critique)"));
        assertThat(avisPublies).isEmpty();
        http.post(A, DIRECTRICE, codeBleu.replace("\"personnes\":[\"chef\",\"lea\"]", "\"personnes\":[\"chef\",\"lea\",\"dir\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bilan.destinataires").value(5))
                .andExpect(jsonPath("$.remises[?(@.personId=='julie')].nom").value("Julie Roy"))
                .andExpect(jsonPath("$.remises[?(@.personId=='parti')].nom").value("Parti"))
                .andExpect(jsonPath("$.remises[?(@.personId=='dir')]").isEmpty());
        assertThat(avisPublies).hasSize(1);
        PublicationAvis.Avis a = avisPublies.get(0);
        assertThat(a.evenement()).isEqualTo("alerte.recue");
        assertThat(a.canaux()).containsExactly("courriel", "texto", "poussee");
        // Le compte à aviser : le dossier d'employé s'il existe, sinon le compte ; personne sans compte, personne à aviser.
        assertThat(a.destinataires()).containsExactlyInAnyOrder("E-JULIE", "u-marc", "u-lea", "E-CHEF");
        assertThat(a.sujet()).isEqualTo("[critique] Code bleu 512");
        assertThat(a.url()).isEqualTo("/admin/mesalertes");

        http.post(A, CHEF, "{\"titre\":\"Info\",\"corps\":\"x\",\"priorite\":\"information\",\"personnes\":[\"inconnu\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« personnes » : « inconnu » n'est pas une personne de l'organisme"));
        http.post(A, CHEF, "{\"titre\":\"Info\",\"corps\":\"x\",\"priorite\":\"information\",\"groupes\":[\"VIDE\"]}").andExpect(status().isBadRequest());
        http.post(A, CHEF, null).andExpect(status().isBadRequest());
        http.post(A, JULIE, "{\"titre\":\"x\",\"corps\":\"x\",\"priorite\":\"information\",\"personnes\":[\"marc\"]}").andExpect(status().isForbidden());
        http.post(A, CHEF, "{\"titre\":\"Info\",\"corps\":\"x\",\"priorite\":\"urgente\",\"personnes\":[\"julie\"]}").andExpect(status().isCreated());
        assertThat(avisPublies.get(1).canaux()).isNull();
    }

    @Test
    @DisplayName("lire et répondre : seul un destinataire le peut (404 sinon) ; une réponse, une fois ; le suivi partagé dit qui prend en charge")
    void recevoirEtSuivre() throws Exception {
        String arrive = creerReponse("J'arrive", "prise_en_charge");
        String indispo = creerReponse("Indisponible", "refus");
        String id = JsonPath.read(corps(http.post(A, CHEF, "{\"titre\":\"Code bleu\",\"corps\":\"5e\",\"priorite\":\"urgente\","
                + "\"reponseIds\":[\"" + arrive + "\",\"" + indispo + "\"],\"personnes\":[\"julie\",\"marc\"]}")), "$.id");

        http.get(A + "/recues", JULIE).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].maRemise.lueLe").isEmpty()).andExpect(jsonPath("$[0].remises").doesNotExist());
        http.post(A + "/recues/" + id + "/lue", JULIE, null).andExpect(status().isOk()).andExpect(jsonPath("$.maRemise.lueLe").value("2026-11-14T14:02:00Z"));
        http.post(A + "/recues/" + id + "/lue", JULIE, null).andExpect(status().isOk());
        http.post(A + "/recues/" + id + "/lue", LEA, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("alerte introuvable"));
        http.post(A + "/recues/inconnue/lue", JULIE, null).andExpect(status().isNotFound());

        http.post(A + "/recues/" + id + "/reponse", MARC, "{\"reponseId\":\"" + indispo + "\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.maRemise.reponseId").value(indispo));
        http.post(A + "/recues/" + id + "/reponse", JULIE, "{\"reponseId\":\"" + arrive + "\"}").andExpect(status().isOk());
        http.post(A + "/recues/" + id + "/reponse", JULIE, "{\"reponseId\":\"" + indispo + "\"}").andExpect(status().isConflict());
        http.post(A + "/recues/" + id + "/reponse", LEA, "{\"reponseId\":\"" + arrive + "\"}").andExpect(status().isNotFound());

        http.get(A + "/envoyees", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[0].bilan.repondues").value(2))
                .andExpect(jsonPath("$[0].bilan.priseEnChargePar").value("Julie Roy"));
        http.get(A + "/envoyees/" + id, CHEF).andExpect(status().isOk()).andExpect(jsonPath("$.bilan.lues").value(2));
        http.get(A + "/envoyees/inconnue", CHEF).andExpect(status().isNotFound());
        http.get(A + "/envoyees", JULIE).andExpect(status().isForbidden());
        http.get(A + "/recues", LEA).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("sans le module, tout est refusé (403) ; sans organisme, la garde le dit")
    void sansModule() throws Exception {
        jdbc.sql("UPDATE tenant_quotas SET modules = '[]' WHERE tenantId = ?").param(T).update();
        http.get(A + "/recues", JULIE).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'alertes' not included in plan"));
        http.get(A + "/modeles", CHEF).andExpect(status().isForbidden());
        http.get(A + "/recues", new ContexteRequete("x", "x", "employee", null, null)).andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("le câblage produit des gestionnaires branchés sur les mêmes dépôts")
    void cablage() {
        ConfigurationAlertes c = new ConfigurationAlertes();
        DepotAlertes depot = c.depotAlertes(jdbc);
        AdaptateurAnnuaireAlertes annuaire = c.annuaireAlertes(new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of();
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                return List.of();
            }
        }, listes, jdbc);
        ControleurAlertes.Cas cas = c.casAlertes(depot, annuaire, PublicationAvis.MUET, Clock.systemUTC());
        assertThat(cas.parametres().creerReponse(T, "Ok", ca.vina.nexu.alertes.domaine.Semantique.INFORMATION).id()).hasSize(36);
        assertThat(c.gardeAlertes(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc))).isNotNull();
    }

    @Test
    @DisplayName("ST-G02 : un modèle se modifie en versions datées et signées ; son périmètre borne qui le voit ; une alerte retient sa version")
    void modelesVersionnesEtPerimetre() throws Exception {
        String arrive = creerReponse("J'arrive", "prise_en_charge");
        String modele = JsonPath.read(corps(http.post(A + "/modeles", ADMIN, "{\"titre\":\"Code bleu\",\"corps\":\"Chambre 512\",\"priorite\":\"urgente\","
                + "\"reponseIds\":[\"" + arrive + "\"],\"groupeIds\":[\"DIR\"]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.groupeIds[0]").value("DIR"))), "$.id");
        http.post(A + "/modeles", ADMIN, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"urgente\",\"groupeIds\":[\"OUEST\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« groupeIds » : le groupe « OUEST » n'existe pas"));
        http.put(A + "/modeles/" + modele, ADMIN, "{\"titre\":\"Code bleu — 5e\",\"corps\":\"Chambre 512, 5e étage\",\"priorite\":\"urgente\","
                + "\"reponseIds\":[\"" + arrive + "\"],\"groupeIds\":[\"DIR\"]}").andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2));
        http.put(A + "/modeles/inconnu", ADMIN, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"urgente\"}").andExpect(status().isNotFound());
        http.put(A + "/modeles/" + modele, CHEF, "{\"titre\":\"X\",\"corps\":\"Y\",\"priorite\":\"urgente\"}").andExpect(status().isForbidden());
        // Désactiver ne fait pas de version ; l'historique garde chaque texte, avec son auteur et sa date.
        http.patch(A + "/modeles/" + modele, ADMIN, "{\"actif\":false}").andExpect(jsonPath("$.version").value(2));
        http.patch(A + "/modeles/" + modele, ADMIN, "{\"actif\":true}").andExpect(status().isOk());
        http.get(A + "/modeles/" + modele + "/versions", ADMIN).andExpect(jsonPath("$[*].version", org.hamcrest.Matchers.contains(2, 1)))
                .andExpect(jsonPath("$[1].titre").value("Code bleu")).andExpect(jsonPath("$[0].creePar").value("admin"))
                .andExpect(jsonPath("$[0].creeLe").value("2026-11-14T14:02:00Z")).andExpect(jsonPath("$[0].reponseIds[0]").value(arrive));
        http.get(A + "/modeles/inconnu/versions", ADMIN).andExpect(status().isNotFound());

        // Le modèle vaut pour la Direction : le chef n'en est pas, il ne le voit pas et ne peut pas en partir ; la directrice, si.
        http.get(A + "/modeles", CHEF).andExpect(jsonPath("$.length()").value(0));
        http.post(A, CHEF, "{\"titre\":\"T\",\"corps\":\"C\",\"priorite\":\"urgente\",\"personnes\":[\"julie\"],\"modeleId\":\"" + modele + "\"}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("modèle introuvable"));
        http.get(A + "/modeles", ADMIN).andExpect(jsonPath("$.length()").value(1));
        String id = JsonPath.read(corps(http.post(A, DIRECTRICE, "{\"titre\":\"Code bleu — 5e\",\"corps\":\"Chambre 512\",\"priorite\":\"urgente\","
                + "\"personnes\":[\"julie\"],\"modeleId\":\"" + modele + "\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.modeleVersion").value(2))), "$.id");
        http.get(A + "/envoyees/" + id, ADMIN).andExpect(jsonPath("$.modeleId").value(modele)).andExpect(jsonPath("$.modeleVersion").value(2));
    }

    @Test
    @DisplayName("ST-G01 : une liste de distribution se déplie à l'envoi ; la liste privée d'un autre n'existe pas ; la copie par courriel ne double personne")
    void listesEtCopieCourriel() throws Exception {
        http.get(A + "/listes", CHEF).andExpect(jsonPath("$[*].nom", org.hamcrest.Matchers.contains("Garde de nuit")));
        http.post(A, CHEF, "{\"titre\":\"Garde\",\"corps\":\"x\",\"priorite\":\"urgente\",\"listes\":[\"l-garde\"]}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.remises[*].personId", org.hamcrest.Matchers.containsInAnyOrder("lea", "julie")));
        http.post(A, CHEF, "{\"titre\":\"Garde\",\"corps\":\"x\",\"priorite\":\"urgente\",\"listes\":[\"l-lea\"]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« listes » : la liste « l-lea » n'existe pas ou ne vous est pas visible"));

        // Sans réglage, pas de copie : une seule publication.
        assertThat(avisPublies).hasSize(1);
        http.get(A + "/reglages", ADMIN).andExpect(jsonPath("$.copieCourriel").value(false));
        http.put(A + "/reglages", CHEF, "{\"copieCourriel\":true}").andExpect(status().isForbidden());
        http.put(A + "/reglages", ADMIN, "{\"copieCourriel\":\"oui\"}").andExpect(status().isBadRequest());
        http.put(A + "/reglages", ADMIN, "{\"copieCourriel\":true}").andExpect(status().isOk()).andExpect(jsonPath("$.copieCourriel").value(true));
        // Julie a déjà reçu un courriel par ses préférences : la copie ne part que vers Léa.
        envoisRendus.add(new PublicationAvis.Envoi("E-JULIE", "courriel", "sent", null));
        envoisRendus.add(new PublicationAvis.Envoi("u-lea", "poussee", "sent", null));
        http.post(A, CHEF, "{\"titre\":\"Garde\",\"corps\":\"x\",\"priorite\":\"urgente\",\"personnes\":[\"julie\",\"lea\"]}").andExpect(status().isCreated());
        PublicationAvis.Avis copie = avisPublies.get(avisPublies.size() - 1);
        assertThat(copie.canaux()).containsExactly("courriel");
        assertThat(copie.destinataires()).containsExactly("u-lea");
        // Tous déjà joints par courriel (la priorité critique impose ce canal) : aucune copie.
        envoisRendus.add(new PublicationAvis.Envoi("u-lea", "courriel", "sent", null));
        int avant = avisPublies.size();
        http.post(A, DIRECTRICE, "{\"titre\":\"Critique\",\"corps\":\"x\",\"priorite\":\"critique\",\"personnes\":[\"julie\",\"lea\"]}").andExpect(status().isCreated());
        assertThat(avisPublies).hasSize(avant + 1);
    }

    @Test
    @DisplayName("ST-G03 : le suivi se filtre par période, priorité, état et expéditeur ; il s'exporte au même filtre, sans formule exécutable")
    void filtresEtExport() throws Exception {
        String arrive = creerReponse("J'arrive", "prise_en_charge");
        String prise = JsonPath.read(corps(http.post(A, CHEF, "{\"titre\":\"=HYPERLINK(1)\",\"corps\":\"x\",\"priorite\":\"urgente\",\"reponseIds\":[\"" + arrive + "\"],"
                + "\"personnes\":[\"julie\"]}")), "$.id");
        http.post(A + "/recues/" + prise + "/reponse", JULIE, "{\"reponseId\":\"" + arrive + "\"}").andExpect(status().isOk());
        http.post(A, DIRECTRICE, "{\"titre\":\"Info\",\"corps\":\"x\",\"priorite\":\"information\",\"personnes\":[\"marc\"]}").andExpect(status().isCreated());

        http.get(A + "/envoyees?priorite=urgente", ADMIN).andExpect(jsonPath("$[*].id", org.hamcrest.Matchers.contains(prise)));
        http.get(A + "/envoyees?etat=prise_en_charge", ADMIN).andExpect(jsonPath("$.length()").value(1));
        http.get(A + "/envoyees?etat=sans_prise_en_charge", ADMIN).andExpect(jsonPath("$[0].titre").value("Info"));
        http.get(A + "/envoyees?etat=non_lue", ADMIN).andExpect(jsonPath("$[0].titre").value("Info"));
        http.get(A + "/envoyees?expediteur=dir", ADMIN).andExpect(jsonPath("$.length()").value(1));
        http.get(A + "/envoyees?du=2026-11-14&au=2026-11-14", ADMIN).andExpect(jsonPath("$.length()").value(2));
        http.get(A + "/envoyees?du=2026-11-15", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(A + "/envoyees?au=2026-11-13&priorite=&etat=", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(A + "/envoyees?du=14/11/2026", ADMIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« du » : une date AAAA-MM-JJ"));
        http.get(A + "/envoyees?etat=perdue", ADMIN).andExpect(status().isBadRequest());
        http.get(A + "/envoyees?priorite=haute", ADMIN).andExpect(status().isBadRequest());

        String csv = http.get(A + "/envoyees/export?format=csv&priorite=urgente", ADMIN).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"suivi-alertes.csv\""))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("'=HYPERLINK(1)").contains("Julie Roy").doesNotContain("Info");
        http.get(A + "/envoyees/export", ADMIN).andExpect(status().isOk());
        assertThat(http.get(A + "/envoyees/export?format=xlsx", ADMIN).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isNotEmpty();
        assertThat(http.get(A + "/envoyees/export?format=pdf", ADMIN).andReturn().getResponse().getContentType()).isEqualTo("application/pdf");
        // Hors CSV, le titre reste tel quel : l'apostrophe ne protège que le tableur.
        assertThat(http.get(A + "/envoyees/export?format=json&priorite=urgente", ADMIN).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("\"=HYPERLINK(1)\"");
        http.get(A + "/envoyees/export?format=doc", ADMIN).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« format » : csv, xlsx, pdf ou json"));
        http.get(A + "/envoyees/export?du=x", ADMIN).andExpect(status().isBadRequest());
        http.get(A + "/envoyees/export", JULIE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("les droits de l'écran : envoyer, critique, configurer — selon le rôle")
    void droits() throws Exception {
        http.get(A + "/droits", ADMIN).andExpect(jsonPath("$.critique").value(true)).andExpect(jsonPath("$.configurer").value(true));
        http.get(A + "/droits", DIRECTRICE).andExpect(jsonPath("$.critique").value(true)).andExpect(jsonPath("$.configurer").value(false));
        http.get(A + "/droits", CHEF).andExpect(jsonPath("$.envoyer").value(true)).andExpect(jsonPath("$.critique").value(false));
        http.get(A + "/droits", JULIE).andExpect(jsonPath("$.envoyer").value(false));
        assertThat(VuesAlertes.cellule(null, true)).isNull();
        assertThat(VuesAlertes.cellule("", true)).isEmpty();
        assertThat(List.of(VuesAlertes.cellule("+1", true), VuesAlertes.cellule("-1", true), VuesAlertes.cellule("@x", true), VuesAlertes.cellule("\tx", true),
                VuesAlertes.cellule("ok", true), VuesAlertes.cellule("=x", false))).containsExactly("'+1", "'-1", "'@x", "'\tx", "ok", "=x");
    }
}
