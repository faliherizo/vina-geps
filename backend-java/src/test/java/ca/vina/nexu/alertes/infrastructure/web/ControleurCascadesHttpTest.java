package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireCascades;
import ca.vina.nexu.alertes.application.commandes.GestionnaireTextosEntrants;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAppelsAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotTextosEntrants;
import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnairePolitiques;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureCascades;
import ca.vina.nexu.alertes.domaine.Cascades;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotCascades;
import ca.vina.nexu.alertes.infrastructure.persistance.EcheancierOrdonnanceur;
import ca.vina.nexu.alertes.infrastructure.persistance.UniteDeTravailAlertesSpring;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.communication.domaine.ListesDistribution;
import ca.vina.nexu.ordonnanceur.domaine.PolitiqueRetentative;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ST-G04 de bout en bout, sans serveur : la vraie base, le vrai ordonnanceur (ses tâches et son tic), de vraies
 * transactions, et une horloge que l'essai avance. Les critères 15, 16, 17, 19, 25 et 32.
 */
@DisplayName("/api/alertes — politiques de relance et cascades : armement, étapes, arrêt, redémarrage, reprise")
class ControleurCascadesHttpTest {

    private static final String T = "T1";
    private static final String A = "/api/alertes";
    private static final String T0 = "2026-11-16T14:00:00Z";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "adm", "admin", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("u-julie", "julie", "employee", null, T);
    private static final ContexteRequete LEA = new ContexteRequete("u-lea", "lea", "employee", null, T);

    /** Une horloge que l'essai règle : l'envoi et la reprise lisent l'heure ici. */
    private static final class Horloge extends Clock {
        private Instant maintenant = Instant.parse(T0);

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

    private final Horloge horloge = new Horloge();
    private final List<PublicationAvis.Avis> avis = new ArrayList<>();
    private final Set<String> indisponibles = new HashSet<>();
    private static final String URL = "https://geps.exemple";
    private static final String JETON = "jeton-essai";
    private final List<ca.vina.nexu.partage.telephonie.AppelsVocauxPort.Appel> voix = new ArrayList<>();
    /** Simule une exécution concurrente qui a fait avancer la cascade entre la lecture et l'écriture. */
    private boolean devancee;
    private JdbcClient jdbc;
    private DepotTaches taches;
    private GestionnaireCascades cascades;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        SimpleDriverDataSource source = SchemaEssai.source(dossier);
        jdbc = SchemaEssai.base(source);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, 1, 1, '[\"alertes\"]')").param(T).update();
        for (String login : List.of("adm", "chef", "julie", "marc", "lea", "remi")) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, ?, 'x', 'employee', 'x')")
                    .params("u-" + login, login + "@@" + T, login, T).update();
        }
        new ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification(jdbc);
        // Les numéros des fiches : celui de Julie et celui de Léa ; Marc n'en a pas.
        jdbc.sql("INSERT INTO employees (id, firstName, lastName, tenantId, phone) VALUES ('u-julie', 'Julie', 'Roy', ?, '(514) 555-0101'), "
                + "('u-lea', 'Léa', 'Côté', ?, '514-555-0103'), ('u-marc', 'Marc', 'Dubé', ?, NULL)").params(T, T, T).update();
        GroupesDeTravailPort groupes = new GroupesDeTravailPort() {
            @Override
            public List<Groupe> groupes(String tenantId) {
                return List.of(new Groupe("EST", "EST", "Opérations Est"), new Groupe("GARDE", "GARDE", "Garde"));
            }

            @Override
            public List<Membre> membres(String tenantId, String groupeId) {
                return "EST".equals(groupeId) ? List.of(new Membre("julie", "Julie Roy"))
                        : List.of(new Membre("lea", "Léa Côté"), new Membre("marc", "Marc Dubé"), new Membre("remi", "Rémi Roy"));
            }
        };
        AdaptateurAnnuaireAlertes annuaire = new AdaptateurAnnuaireAlertes(groupes, sansListe(), jdbc);
        DepotAlertes depot = new DepotAlertes(jdbc);
        DepotCascades depotCascades = new DepotCascades(jdbc);
        RegistreExecuteurs registre = new RegistreExecuteurs();
        taches = new DepotTaches(jdbc, registre);
        EcheancierOrdonnanceur echeancier = new EcheancierOrdonnanceur(taches, registre);
        ca.vina.nexu.alertes.domaine.EcritureCascadesPort ecriture = new ca.vina.nexu.alertes.domaine.EcritureCascadesPort() {
            @Override
            public void enregistrer(String tenantId, Cascades.Politique politique, String instant, String auteur) {
                depotCascades.enregistrer(tenantId, politique, instant, auteur);
            }

            @Override
            public void armer(String tenantId, Cascades.Cascade cascade) {
                depotCascades.armer(tenantId, cascade);
            }

            @Override
            public boolean avancer(String tenantId, Cascades.Cascade avant, Cascades.Cascade apres) {
                return !devancee && depotCascades.avancer(tenantId, avant, apres);
            }

            @Override
            public void tracer(String tenantId, Cascades.Trace trace) {
                depotCascades.tracer(tenantId, trace);
            }
        };
        cascades = new GestionnaireCascades(new GestionnaireCascades.Dependances(depotCascades, ecriture, depot, depot, annuaire),
                new GestionnaireCascades.Services(echeancier, (t, personne, instant) -> !indisponibles.contains(personne),
                        new UniteDeTravailAlertesSpring(new TransactionTemplate(new DataSourceTransactionManager(source)))), horloge);
        PublicationAvis publication = a -> {
            avis.add(a);
            return List.of();
        };
        echeancier.brancher(cascades, publication);
        int[] n = {0};
        java.util.function.Supplier<String> ids = () -> "id-" + ++n[0];
        ControleurAlertes.Cas cas = new ControleurAlertes.Cas(new GestionnaireParametresAlertes(new GestionnaireParametresAlertes.Dependances(depot, depot, annuaire, horloge), ids),
                new GestionnaireEnvoiAlertes(new GestionnaireEnvoiAlertes.Dependances(depot, depot, annuaire, publication), ids, horloge, cascades),
                new GestionnaireReceptionAlertes(depot, depot, horloge, cascades), new LectureAlertes(depot, annuaire, depotCascades));
        GardeAlertes garde = new GardeAlertes(new GardeAcces(new RegistrePolitiqueAcces()), new DepotModulesActives(jdbc));
        // ST-G06 : la voix simulée garde ce qu'on lui demande ; les appels sont tracés en base.
        GestionnaireAppelsAlertes appels = new GestionnaireAppelsAlertes(new GestionnaireAppelsAlertes.Dependances(depot, new DepotAppelsAlertes(jdbc), a -> {
            voix.add(a);
            return "CA-" + voix.size();
        }, cas.reception()), new GestionnaireAppelsAlertes.Rappels(URL), ids, horloge);
        echeancier.brancherAppels(appels);
        GestionnaireTextosEntrants textos = new GestionnaireTextosEntrants(depot, new DepotTextosEntrants(jdbc), cas.reception(), horloge);
        http = new ClientHttpEssai(new ControleurAlertes(garde, cas, horloge), new ControleurCascades(garde, new ControleurCascades.Cas(
                new GestionnairePolitiques(depotCascades, depotCascades, ids, horloge), cascades, new LectureCascades(depotCascades, depot, new DepotAppelsAlertes(jdbc)))),
                new ControleurWebhooksAlertes(textos, appels, new ControleurWebhooksAlertes.Reglages(URL, JETON)));
    }

    private static LectureListesDistributionPort sansListe() {
        return new LectureListesDistributionPort() {
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
        };
    }

    private static String corps(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private void tic(String instant) {
        taches.tic(instant, PolitiqueRetentative.PAR_DEFAUT, "essai");
    }

    private String politique(String etapes, String arret) throws Exception {
        return JsonPath.read(corps(http.post(A + "/politiques", ADMIN, "{\"nom\":\"Code bleu\",\"arret\":\"" + arret + "\",\"etapes\":[" + etapes + "]}")
                .andExpect(status().isCreated())), "$.id");
    }

    private static final String TROIS_ETAPES = "{\"cible\":\"initiaux\",\"canaux\":[\"poussee\"],\"delaiMinutes\":5},"
            + "{\"cible\":\"groupe\",\"cibleId\":\"GARDE\",\"canaux\":[\"texto\"],\"delaiMinutes\":10},"
            + "{\"cible\":\"personne\",\"cibleId\":\"adm\",\"canaux\":[\"courriel\"],\"delaiMinutes\":15}";

    private String reponse() throws Exception {
        return JsonPath.read(corps(http.post(A + "/reponses", ADMIN, "{\"libelle\":\"J'arrive\",\"semantique\":\"prise_en_charge\"}").andExpect(status().isCreated())), "$.id");
    }

    private String envoyer(String politiqueId, String reponseId) throws Exception {
        String p = politiqueId == null ? "" : ",\"politiqueId\":\"" + politiqueId + "\"";
        return JsonPath.read(corps(http.post(A, CHEF, "{\"titre\":\"Code bleu 512\",\"corps\":\"5e\",\"priorite\":\"urgente\",\"reponseIds\":[\"" + reponseId
                + "\"],\"groupes\":[\"EST\"]" + p + "}").andExpect(status().isCreated())), "$.id");
    }

    private List<String> evenements(String alerteId) throws Exception {
        return JsonPath.read(corps(http.get(A + "/envoyees/" + alerteId + "/cascade", CHEF).andExpect(status().isOk())), "$.traces[*].evenement");
    }

    private int taches(String type, String statut) {
        return jdbc.sql("SELECT COUNT(*) FROM taches_differees WHERE type = ? AND statut = ?").params(type, statut).query(Integer.class).single();
    }

    @Test
    @DisplayName("critère 32 : l'administration tient les politiques en versions ; un expéditeur ne voit que les actives ; les saisies sont vérifiées")
    void politiques() throws Exception {
        String id = politique(TROIS_ETAPES, "prise_en_charge");
        http.get(A + "/politiques", ADMIN).andExpect(jsonPath("$[0].etapes.length()").value(3)).andExpect(jsonPath("$[0].etapes[1].cibleId").value("GARDE"))
                .andExpect(jsonPath("$[0].version").value(1));
        http.put(A + "/politiques/" + id, ADMIN, "{\"nom\":\"Code bleu — nuit\",\"arret\":\"toutes_lues\",\"etapes\":[{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":3}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.arret").value("toutes_lues"));
        http.get(A + "/politiques", CHEF).andExpect(jsonPath("$.length()").value(1));
        http.patch(A + "/politiques/" + id, ADMIN, "{\"actif\":false}").andExpect(jsonPath("$.actif").value(false));
        http.get(A + "/politiques", CHEF).andExpect(jsonPath("$.length()").value(0));
        http.get(A + "/politiques", ADMIN).andExpect(jsonPath("$.length()").value(1));
        http.post(A + "/politiques", CHEF, "{}").andExpect(status().isForbidden());
        http.put(A + "/politiques/inconnue", ADMIN, "{\"nom\":\"X\",\"arret\":\"toutes_lues\",\"etapes\":[{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":3}]}")
                .andExpect(status().isNotFound());
        http.post(A + "/politiques", ADMIN, "{\"nom\":\"X\",\"arret\":\"jamais\",\"etapes\":[]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« arret » : prise_en_charge ou toutes_lues"));
        http.post(A + "/politiques", ADMIN, "{\"nom\":\"X\",\"arret\":\"toutes_lues\",\"etapes\":\"non\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« etapes » doit être une liste d'étapes"));
        http.post(A + "/politiques", ADMIN, "{\"nom\":\"X\",\"etapes\":[{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":2.5}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« delaiMinutes » doit être un nombre entier"));
        http.post(A + "/politiques", ADMIN, "{\"nom\":\"X\",\"arret\":\"toutes_lues\",\"etapes\":[{\"canaux\":[\"texto\"],\"delaiMinutes\":2}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« cible » : initiaux, groupe, personne ou prochain_disponible"));
        // Une alerte ne s'arme pas sur une politique désactivée, ni inconnue.
        String rp = reponse();
        http.post(A, CHEF, "{\"titre\":\"T\",\"corps\":\"C\",\"priorite\":\"urgente\",\"reponseIds\":[\"" + rp + "\"],\"groupes\":[\"EST\"],\"politiqueId\":\"" + id + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("la politique « Code bleu — nuit » est désactivée"));
        http.post(A, CHEF, "{\"titre\":\"T\",\"corps\":\"C\",\"priorite\":\"urgente\",\"reponseIds\":[\"" + rp + "\"],\"groupes\":[\"EST\"],\"politiqueId\":\"x\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« politiqueId » : politique introuvable"));
    }

    @Test
    @DisplayName("critères 15, 16, 19 : une cascade de trois étapes relance, escalade, puis s'arrête à la première prise en charge")
    void cascadeArreteeParPriseEnCharge() throws Exception {
        String alerte = envoyer(politique(TROIS_ETAPES, "prise_en_charge"), reponse());
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("armee"))
                .andExpect(jsonPath("$.cascade.etape").value(1)).andExpect(jsonPath("$.cascade.echeanceLe").value("2026-11-16T14:05:00Z"));
        avis.clear();
        tic("2026-11-16T14:04:59Z");
        assertThat(avis).isEmpty();
        // Étape 1 : la relance de Julie, qui n'a pas répondu ; l'envoi part par sa propre tâche durable.
        tic("2026-11-16T14:05:00Z");
        assertThat(taches(EcheancierOrdonnanceur.TACHE_RELANCE, "en_attente")).isOne();
        tic("2026-11-16T14:05:01Z");
        assertThat(avis).singleElement().satisfies(a -> {
            assertThat(a.sujet()).isEqualTo("[relance] [urgente] Code bleu 512");
            assertThat(a.destinataires()).containsExactly("u-julie");
            assertThat(a.canaux()).containsExactly("poussee");
        });
        // Étape 2 : l'escalade au groupe de garde, qui rejoint l'alerte.
        tic("2026-11-16T14:15:01Z");
        tic("2026-11-16T14:15:02Z");
        assertThat(avis.getLast().destinataires()).containsExactlyInAnyOrder("u-lea", "u-marc", "u-remi");
        http.get(A + "/envoyees", CHEF).andExpect(jsonPath("$[0].bilan.destinataires").value(4)).andExpect(jsonPath("$[0].bilan.relances").value(2));
        // Léa prend en charge : la cascade s'arrête aussitôt, et l'étape 3 ne partira jamais.
        http.post(A + "/recues/" + alerte + "/reponse", LEA, "{\"reponseId\":\"" + JsonPath.read(corps(http.get(A + "/recues", LEA)), "$[0].reponses[0].id") + "\"}")
                .andExpect(status().isOk());
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("arretee"))
                .andExpect(jsonPath("$.traces[1].personnes[0]").value("julie")).andExpect(jsonPath("$.traces[1].par").value("cascade"));
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "etape", "arretee");
        int avant = avis.size();
        tic("2026-11-16T15:00:00Z");
        assertThat(avis).hasSize(avant);
        assertThat(taches(EcheancierOrdonnanceur.TACHE_ETAPE, "annulee")).isOne();
    }

    @Test
    @DisplayName("redémarrage en cours de cascade : repris après la panne, l'étape ne se perd pas, et ne part pas deux fois")
    void redemarrage() throws Exception {
        String alerte = envoyer(politique(TROIS_ETAPES, "prise_en_charge"), reponse());
        // Panne avant l'écriture : la tâche a été prise, puis le processus est tombé ; son verrou se périme.
        jdbc.sql("UPDATE taches_differees SET statut = 'en_cours', verrouLe = '2026-11-16T14:05:00Z', verrouPar = 'mort' WHERE type = ?")
                .param(EcheancierOrdonnanceur.TACHE_ETAPE).update();
        tic("2026-11-16T14:10:00Z");
        assertThat(evenements(alerte)).containsExactly("armee");
        tic("2026-11-16T14:16:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "etape");
        // Panne après l'écriture : l'étape 2 a été faite, mais la tâche est restée « en cours ». Reprise, elle ne refait rien.
        cascades.executerEtape(T, alerte, 1, "2026-11-16T14:26:00Z");
        String id2 = jdbc.sql("SELECT id FROM taches_differees WHERE type = ? AND statut = 'en_attente'").param(EcheancierOrdonnanceur.TACHE_ETAPE).query(String.class).single();
        jdbc.sql("INSERT INTO taches_differees (id, tenantId, type, cibleId, charge, echeanceLe, statut, createdAt, creePar, verrouLe, verrouPar) "
                + "VALUES ('rejouee', ?, ?, ?, '{\"etape\":1}', '2026-11-16T14:26:00Z', 'en_cours', 'x', 'x', '2026-11-16T14:26:00Z', 'mort')")
                .params(T, EcheancierOrdonnanceur.TACHE_ETAPE, alerte).update();
        tic("2026-11-16T14:40:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "etape");
        assertThat(jdbc.sql("SELECT statut FROM taches_differees WHERE id = 'rejouee'").query(String.class).single()).isEqualTo("faite");
        assertThat(jdbc.sql("SELECT statut FROM taches_differees WHERE id = ?").param(id2).query(String.class).single()).isEqualTo("en_attente");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM taches_differees WHERE type = ?").param(EcheancierOrdonnanceur.TACHE_RELANCE).query(Integer.class).single()).isEqualTo(2);
    }

    @Test
    @DisplayName("concurrence : deux exécutions de la même étape — une seule avance la cascade")
    void concurrence() throws Exception {
        String alerte = envoyer(politique(TROIS_ETAPES, "prise_en_charge"), reponse());
        cascades.executerEtape(T, alerte, 0, "2026-11-16T14:05:00Z");
        cascades.executerEtape(T, alerte, 0, "2026-11-16T14:05:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "etape");
        DepotCascades depot = new DepotCascades(jdbc);
        Cascades.Cascade perimee = Cascades.Cascade.armer(alerte, depot.politiques(T).getFirst(), T0);
        assertThat(depot.avancer(T, perimee, perimee.apresEtape(T0))).isFalse();
    }

    @Test
    @DisplayName("critère 17 : la reprise relance l'étape choisie tout de suite, au nom de l'acteur ; épuisée, la cascade le dit")
    void reprise() throws Exception {
        String alerte = envoyer(politique("{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "prise_en_charge"), reponse());
        tic("2026-11-16T14:05:00Z");
        tic("2026-11-16T14:05:01Z");
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("epuisee"));
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "epuisee");
        horloge.maintenant = Instant.parse("2026-11-16T14:30:00Z");
        http.post(A + "/envoyees/" + alerte + "/cascade/reprendre", CHEF, "{\"etape\":1}").andExpect(status().isOk())
                .andExpect(jsonPath("$.cascade.etat").value("armee")).andExpect(jsonPath("$.cascade.etape").value(1));
        tic("2026-11-16T14:30:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "epuisee", "reprise", "etape", "epuisee");
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.traces[3].par").value("chef"));
        http.post(A + "/envoyees/" + alerte + "/cascade/reprendre", CHEF, "{\"etape\":4}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« etape » : de 1 à 1"));
        http.post(A + "/envoyees/" + alerte + "/cascade/reprendre", CHEF, "{\"etape\":\"un\"}").andExpect(status().isBadRequest());
        http.post(A + "/envoyees/" + alerte + "/cascade/reprendre", JULIE, "{\"etape\":1}").andExpect(status().isForbidden());
        // Une alerte sans politique n'a pas de cascade ; une alerte inconnue n'existe pas.
        String libre = envoyer(null, reponse());
        http.get(A + "/envoyees/" + libre + "/cascade", CHEF).andExpect(jsonPath("$.cascade").isEmpty()).andExpect(jsonPath("$.traces.length()").value(0));
        http.post(A + "/envoyees/" + libre + "/cascade/reprendre", CHEF, "{\"etape\":1}").andExpect(status().isNotFound());
        http.get(A + "/envoyees/inconnue/cascade", CHEF).andExpect(status().isNotFound());
        // Critère 11 : l'expéditeur voit les relances — au bilan, à l'export, et par l'état « relancée ».
        http.get(A + "/envoyees/" + alerte, CHEF).andExpect(jsonPath("$.bilan.relances").value(2));
        http.get(A + "/envoyees/" + libre, CHEF).andExpect(jsonPath("$.bilan.relances").value(0));
        http.get(A + "/envoyees?etat=relancee", CHEF).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(alerte));
        assertThat(corps(http.get(A + "/envoyees/export?format=csv&etat=relancee", CHEF).andExpect(status().isOk()))).contains("relances");
    }

    @Test
    @DisplayName("critère 25 : « prochain disponible » saute la personne absente selon le calendrier interne")
    void prochainDisponible() throws Exception {
        indisponibles.add("lea");
        String alerte = envoyer(politique("{\"cible\":\"prochain_disponible\",\"cibleId\":\"GARDE\",\"canaux\":[\"texto\"],\"delaiMinutes\":5},"
                + "{\"cible\":\"prochain_disponible\",\"cibleId\":\"GARDE\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "toutes_lues"), reponse());
        avis.clear();
        tic("2026-11-16T14:05:00Z");
        tic("2026-11-16T14:05:01Z");
        assertThat(avis.getLast().destinataires()).containsExactly("u-marc");
        tic("2026-11-16T14:10:01Z");
        tic("2026-11-16T14:10:02Z");
        assertThat(avis.getLast().destinataires()).containsExactly("u-remi");
    }

    @Test
    @DisplayName("critère « toutes lues » : la dernière lecture arrête la cascade ; une lecture qui ne l'atteint pas la laisse armée")
    void toutesLues() throws Exception {
        String alerte = envoyer(politique("{\"cible\":\"groupe\",\"cibleId\":\"GARDE\",\"canaux\":[\"texto\"],\"delaiMinutes\":5},"
                + "{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "toutes_lues"), reponse());
        tic("2026-11-16T14:05:00Z");
        http.post(A + "/recues/" + alerte + "/lue", LEA, null).andExpect(status().isOk());
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("armee"));
        for (ContexteRequete c : List.of(JULIE, new ContexteRequete("u-marc", "marc", "employee", null, T), new ContexteRequete("u-remi", "remi", "employee", null, T))) {
            http.post(A + "/recues/" + alerte + "/lue", c, null).andExpect(status().isOk());
        }
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("arretee"));
        // Déjà lue : relire ne déclenche rien.
        http.post(A + "/recues/" + alerte + "/lue", LEA, null).andExpect(status().isOk());
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "arretee");
        // L'étape attendue, arrivée après coup, voit l'arrêt et ne fait rien.
        cascades.executerEtape(T, alerte, 1, "2026-11-16T14:20:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "etape", "arretee");
    }

    @Test
    @DisplayName("le critère atteint avant l'échéance : l'étape qui arrive l'arrête au lieu de relancer")
    void arretALEcheance() throws Exception {
        String alerte = envoyer(politique(TROIS_ETAPES, "toutes_lues"), reponse());
        jdbc.sql("UPDATE alr_remises SET lueLe = ? WHERE alerteId = ?").params(T0, alerte).update();
        avis.clear();
        tic("2026-11-16T14:05:00Z");
        assertThat(evenements(alerte)).containsExactly("armee", "arretee");
        assertThat(avis).isEmpty();
    }

    @Test
    @DisplayName("devancée par une exécution concurrente : l'étape ne fait rien, et la reprise répond 409 sans rien écrire")
    void devancee() throws Exception {
        String alerte = envoyer(politique(TROIS_ETAPES, "prise_en_charge"), reponse());
        devancee = true;
        cascades.executerEtape(T, alerte, 0, "2026-11-16T14:05:00Z");
        http.post(A + "/envoyees/" + alerte + "/cascade/reprendre", CHEF, "{\"etape\":2}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("la cascade vient de changer : rechargez, puis reprenez"));
        assertThat(evenements(alerte)).containsExactly("armee");
        assertThat(taches(EcheancierOrdonnanceur.TACHE_RELANCE, "en_attente")).isZero();
    }

    @Test
    @DisplayName("une étape « personne » vise la personne nommée ; sous « toutes lues », la relance vise ceux qui n'ont pas lu")
    void personneEtSansLecture() throws Exception {
        String alerte = envoyer(politique("{\"cible\":\"personne\",\"cibleId\":\"adm\",\"canaux\":[\"courriel\"],\"delaiMinutes\":5},"
                + "{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "toutes_lues"), reponse());
        avis.clear();
        tic("2026-11-16T14:05:00Z");
        tic("2026-11-16T14:05:01Z");
        assertThat(avis.getLast().destinataires()).containsExactly("u-adm");
        // Julie lit après l'escalade : l'administratrice, elle, n'a pas lu — la cascade continue.
        http.post(A + "/recues/" + alerte + "/lue", JULIE, null).andExpect(status().isOk());
        tic("2026-11-16T14:10:01Z");
        tic("2026-11-16T14:10:02Z");
        // Julie a lu : seule l'administratrice, escaladée sans avoir lu, est relancée.
        assertThat(avis.getLast().destinataires()).containsExactly("u-adm");
        assertThat(avis.getLast().canaux()).containsExactly("texto");
        // Une personne qui n'existe pas : l'étape passe, tracée sans personne visée.
        String autre = envoyer(politique("{\"cible\":\"personne\",\"cibleId\":\"fantome\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "toutes_lues"), reponse());
        cascades.executerEtape(T, autre, 0, "2026-11-16T14:05:00Z");
        http.get(A + "/envoyees/" + autre + "/cascade", CHEF).andExpect(jsonPath("$.traces[1].evenement").value("etape"))
                .andExpect(jsonPath("$.traces[1].personnes.length()").value(0));
    }

    private ResultActions rappel(String chemin, String appelId, Map<String, String> corps) throws Exception {
        StringBuilder donnees = new StringBuilder(URL + chemin + "?appel=" + appelId);
        new java.util.TreeMap<>(corps).forEach((k, v) -> donnees.append(k).append(v));
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(chemin).queryParam("appel", appelId).header("X-Twilio-Signature", ca.vina.nexu.partage.telephonie.Telephonie.signer(donnees.toString(), JETON));
        corps.forEach(b::param);
        return http.envoyer(b, null);
    }

    @Test
    @DisplayName("ST-G06, critères 13 et 14 : l'étape « appel » fait sonner la personne ; « 1 » prend en charge et arrête la cascade ; résultat et durée tracés")
    void appelAutomatique() throws Exception {
        String alerte = envoyer(politique("{\"cible\":\"initiaux\",\"canaux\":[\"appel\"],\"delaiMinutes\":5},"
                + "{\"cible\":\"groupe\",\"cibleId\":\"GARDE\",\"canaux\":[\"appel\",\"texto\"],\"delaiMinutes\":10}", "prise_en_charge"), reponse());
        avis.clear();
        tic("2026-11-16T14:05:00Z");
        assertThat(taches(EcheancierOrdonnanceur.TACHE_RELANCE, "en_attente")).isZero();
        tic("2026-11-16T14:05:01Z");
        assertThat(avis).isEmpty();
        assertThat(voix).singleElement().satisfies(a -> {
            assertThat(a.numero()).isEqualTo("+15145550101");
            assertThat(a.message()).startsWith("Alerte urgente de VINA GePS : Code bleu 512. 5e.").endsWith("Appuyez sur 1 pour prendre en charge, sur 2 pour refuser.");
            assertThat(a.urlChiffre()).isEqualTo(URL + "/api/alertes/webhooks/voix?appel=" + a.reference());
        });
        String appel = voix.getFirst().reference();
        // Rejouée après une panne, la tâche d'appel ne rappelle pas.
        new GestionnaireAppelsAlertes(new GestionnaireAppelsAlertes.Dependances(new DepotAlertes(jdbc), new DepotAppelsAlertes(jdbc), a -> {
            voix.add(a);
            return "doublon";
        }, null), new GestionnaireAppelsAlertes.Rappels(URL), () -> "x", horloge)
                .lancer(T, new ca.vina.nexu.alertes.domaine.AppelsAlertes.AAppeler(alerte, 0, "julie", "u-julie"));
        assertThat(voix).hasSize(1);
        // Une touche inconnue ne compte pas ; une signature fausse est refusée ; « 1 » prend en charge.
        rappel("/api/alertes/webhooks/voix", appel, Map.of("Digits", "9", "CallSid", "CA-1")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Touche non reconnue")));
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/alertes/webhooks/voix").queryParam("appel", appel)
                .param("Digits", "1").header("X-Twilio-Signature", "faux"), null).andExpect(status().isForbidden());
        rappel("/api/alertes/webhooks/voix", appel, Map.of("Digits", "1", "CallSid", "CA-1")).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Prise en charge enregistrée")));
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("arretee"));
        // La fin d'appel ne fait pas oublier la touche : répondu, 42 secondes.
        rappel("/api/alertes/webhooks/voix/statut", appel, Map.of("CallStatus", "completed", "CallDuration", "42", "CallSid", "CA-1")).andExpect(status().isOk());
        http.get(A + "/envoyees/" + alerte + "/cascade", CHEF).andExpect(jsonPath("$.appels[0].resultat").value("repondu"))
                .andExpect(jsonPath("$.appels[0].touche").value("1")).andExpect(jsonPath("$.appels[0].dureeSecondes").value(42))
                .andExpect(jsonPath("$.appels[0].etape").value(1)).andExpect(jsonPath("$.appels[0].personId").value("julie"));
        // Répondre deux fois au téléphone : la seconde touche le dit.
        rappel("/api/alertes/webhooks/voix", appel, Map.of("Digits", "1", "CallSid", "CA-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("déjà enregistrée")));
        rappel("/api/alertes/webhooks/voix", "inconnu", Map.of("Digits", "1")).andExpect(status().isNotFound());
        rappel("/api/alertes/webhooks/voix/statut", "inconnu", Map.of("CallStatus", "completed")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("ST-G06 : escalade par appel à un groupe — sans numéro, l'appel est tracé en échec ; la messagerie et le refus sont distingués")
    void appelsDuGroupe() throws Exception {
        String alerte = envoyer(politique("{\"cible\":\"groupe\",\"cibleId\":\"GARDE\",\"canaux\":[\"appel\",\"texto\"],\"delaiMinutes\":5}", "prise_en_charge"), reponse());
        avis.clear();
        tic("2026-11-16T14:05:00Z");
        tic("2026-11-16T14:05:01Z");
        assertThat(avis).singleElement().satisfies(a -> assertThat(a.canaux()).containsExactly("texto"));
        assertThat(voix).extracting(ca.vina.nexu.partage.telephonie.AppelsVocauxPort.Appel::numero).containsExactly("+15145550103");
        String lea = voix.getFirst().reference();
        rappel("/api/alertes/webhooks/voix/statut", lea, Map.of("CallStatus", "completed", "AnsweredBy", "machine_end_beep", "CallDuration", "18")).andExpect(status().isOk());
        String json = corps(http.get(A + "/envoyees/" + alerte + "/cascade", CHEF));
        assertThat(JsonPath.<List<String>>read(json, "$.appels[*].resultat")).containsExactlyInAnyOrder("messagerie", "echec", "echec");
        assertThat(JsonPath.<List<String>>read(json, "$.appels[?(@.personId == 'marc')].resultat")).containsExactly("echec");
        // Un refus au téléphone n'arrête pas la cascade (critère « prise en charge »).
        String alerte2 = envoyer(politique("{\"cible\":\"personne\",\"cibleId\":\"lea\",\"canaux\":[\"appel\"],\"delaiMinutes\":5},"
                + "{\"cible\":\"initiaux\",\"canaux\":[\"texto\"],\"delaiMinutes\":5}", "prise_en_charge"),
                JsonPath.read(corps(http.post(A + "/reponses", ADMIN, "{\"libelle\":\"Non\",\"semantique\":\"refus\"}")), "$.id"));
        voix.clear();
        cascades.executerEtape(T, alerte2, 0, "2026-11-16T14:05:00Z");
        tic("2026-11-16T14:06:00Z");
        rappel("/api/alertes/webhooks/voix", voix.getFirst().reference(), Map.of("Digits", "2")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .string(org.hamcrest.Matchers.containsString("Refus enregistré")));
        http.get(A + "/envoyees/" + alerte2 + "/cascade", CHEF).andExpect(jsonPath("$.cascade.etat").value("armee"));
        // Un « 1 » sur une alerte qui ne propose que le refus : la voix renvoie vers l'application.
        rappel("/api/alertes/webhooks/voix", voix.getFirst().reference(), Map.of("Digits", "1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(org.hamcrest.Matchers.containsString("Touche non reconnue")));
    }
}
