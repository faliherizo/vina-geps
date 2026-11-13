package ca.vina.nexu.effectifs.infrastructure.persistance;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.infrastructure.web.FluxEssai;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.application.requetes.SourcesFeuillesTemps;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotRapportsEnregistres;
import ca.vina.nexu.rapports.infrastructure.persistance.MoteurRapports;
import ca.vina.nexu.rapports.infrastructure.web.ControleurRapports;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Relevés statistiques paramétrés par l'organisation</strong> (AO 26-015-DO, P-RF-1), par HTTP — jumeau de
 * {@code effectifs-releves.integration.test.ts}.
 *
 * <p>Semaine du 13 janvier 2031, deux unités sous la racine US : EST (Julie) et OUEST (Olivier). Le 15, Julie a un quart
 * pointé (12 h) ; le 16, un équipage de deux places au minimum de deux, une seule pourvue (elle) ; le 18 et le 19, elle est
 * absente. Le chef ne voit que l'EST ; une employée est refusée.
 */
@DisplayName("/api/rapports — sources d'effectifs : heures, journées, pointages, comblement, absences ; périmètre ; rapports enregistrés")
class FaitsRelevesEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String MODULES = "[\"personnel\",\"effectifs\",\"effectifs_regles\",\"effectifs_planification\",\"effectifs_presence\","
            + "\"effectifs_temps\",\"effectifs_absences\"]";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);
    private static final String SEMAINE = "{\"champ\":\"jour\",\"operateur\":\"entre\",\"valeurs\":[\"2031-01-13\",\"2031-01-19\"]}";
    private static final String CREE = "2026-01-01T00:00:00.000Z";

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private FaitsRelevesEffectifs faits;

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
                return "secret";
            }
        };
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, MODULES).update();
        cabler();
        socle();
        planification();
        tempsPresenceAbsences();
    }

    private void cabler() {
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        SchemaPlanification schemaPlanif = new SchemaPlanification(jdbc);
        SchemaPresenceEffectifs schemaPresence = new SchemaPresenceEffectifs(jdbc);
        new SchemaAbsencesEffectifs(jdbc);
        Sequence seq = sequence();
        LectureReferentiels referentiels = new LectureReferentiels(new DepotReferentiels(jdbc, schema));
        LecturePerimetres perimetres = new LecturePerimetres(new DepotPerimetres(jdbc, schema), referentiels);
        LectureRessources ressources = new LectureRessources(new DepotRessources(jdbc, schemaPlanif), perimetres);
        DepotFeuillesTemps depot = new DepotFeuillesTemps(jdbc, new SchemaTempsEffectifs(jdbc), schemaPresence, schemaPlanif);
        LectureFeuillesTemps lecture = new LectureFeuillesTemps(depot, ressources, perimetres, referentiels);
        LectureQuartsPresence quarts = new LectureQuartsPresence(new DepotPresenceEffectifs(jdbc, schemaPresence, schemaPlanif, schema), referentiels);
        EvaluateurRegles regles = new EvaluateurRegles(new DepotRegles(jdbc, new SchemaReglesEffectifs(jdbc)), referentiels,
                new DepotJournalEffectifs(jdbc, seq, schema), seq);
        Clock horloge = Clock.fixed(Instant.parse("2031-01-20T12:00:00Z"), ZoneOffset.UTC);
        FluxEssai.Cablage flux = FluxEssai.cabler(new FluxEssai.Socle(jdbc, horloge, perimetres, regles, new DepotJournalEffectifs(jdbc, seq, schema)));
        CalculFeuillesTemps calcul = new CalculFeuillesTemps(lecture, new SourcesFeuillesTemps(depot, quarts), regles, flux.lecture());
        faits = new FaitsRelevesEffectifs(jdbc, new FaitsRelevesEffectifs.Lectures(lecture, calcul, perimetres, referentiels));
        http = new ClientHttpEssai(new ControleurRapports(new MoteurRapports(List.of(faits)), new GardeAcces(new RegistrePolitiqueAcces()),
                new ControleurRapports.Ports(new ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas(jdbc), new DepotRapportsEnregistres(jdbc),
                        Clock.fixed(Instant.parse("2031-01-20T12:00:00Z"), ZoneOffset.UTC), seq)));
    }

    private void ref(String type, String code, String comportement, String attributs) {
        jdbc.sql("INSERT INTO eff_referentiels (id, tenantId, type, code, libelleFr, comportement, attributs, effectifDepuis, creeLe, creePar) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, '2026-01-01', ?, 'adm')").params("r-" + type + "-" + code, T, type, code, code, comportement, attributs, CREE).update();
    }

    private void socle() {
        ref("unites", "US", null, "{}");
        ref("unites", "EST", null, "{\"parent\":\"US\"}");
        ref("unites", "OUEST", null, "{\"parent\":\"US\"}");
        ref("types_heure", "REG", "TRAVAILLEE", "{\"codePaie\":\"100\"}");
        ref("codes_absence", "MAL", "PAYE", "{\"duree\":\"PONCTUELLE\"}");
        jdbc.sql("INSERT INTO eff_milieux (id, tenantId, code, libelle, typeCode, uniteCode, creeLe) VALUES ('m-12', ?, 'C12', 'Caserne 12', 'CASERNE', 'EST', ?)")
                .params(T, CREE).update();
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, titreEmploiCode, statutEmploiCode, "
                + "conventionCode, creeLe) VALUES ('rs-1', ?, 'P1', 'Julie', 'usr-julie', 'EST', 'PARA', 'TECH', 'TC', 'FSSS', ?), "
                + "('rs-5', ?, 'P5', 'Olivier', NULL, 'OUEST', 'PARA', NULL, NULL, NULL, ?)").params(T, CREE, T, CREE).update();
        jdbc.sql("INSERT INTO eff_perimetres (id, tenantId, utilisateurId, uniteCode, etendue, niveau, depuis, creeLe) "
                + "VALUES ('per-1', ?, 'usr-chef', 'EST', 'UNITE', 'GESTION', '2026-01-01', ?)").params(T, CREE).update();
    }

    private void equipage(String id, String periode, String unite, String date, int minimum) {
        jdbc.sql("INSERT INTO eff_equipages (id, tenantId, periodeId, uniteCode, date, debut, fin, natureCode, milieuId, modeAttribution, minimumOperationnel, creeLe) "
                + "VALUES (?, ?, ?, ?, ?, 360, 1080, 'REG', 'm-12', 'MANUEL', ?, ?)").params(id, T, periode, unite, date, minimum, CREE).update();
    }

    private void place(String id, String equipage, int rang, String ressource) {
        jdbc.sql("INSERT INTO eff_places (id, tenantId, equipageId, rang, corpsEmploiCode, ressourceId) VALUES (?, ?, ?, ?, 'PARA', ?)")
                .params(id, T, equipage, rang, ressource).update();
    }

    private void planification() {
        for (String[] p : new String[][]{{"pr-est", "EST"}, {"pr-ouest", "OUEST"}}) {
            jdbc.sql("INSERT INTO eff_periodes (id, tenantId, uniteCode, nom, debut, fin, etat, creeLe) VALUES (?, ?, ?, 'Semaine 3', '2031-01-13', '2031-01-19', 'PUBLIEE', ?)")
                    .params(p[0], T, p[1], CREE).update();
        }
        equipage("e-1", "pr-est", "EST", "2031-01-15", 1);
        place("pl-1", "e-1", 1, "rs-1");
        equipage("e-2", "pr-est", "EST", "2031-01-16", 2);
        place("pl-2", "e-2", 1, "rs-1");
        place("pl-3", "e-2", 2, null);
        equipage("e-3", "pr-ouest", "OUEST", "2031-01-15", 1);
        place("pl-4", "e-3", 1, "rs-5");
    }

    private void segment(String id, String feuille, String place, String date, String debut, String fin, String source) {
        jdbc.sql("INSERT INTO eff_segments_temps (id, tenantId, feuilleId, placeId, date, debut, fin, typeHeureCode, source, version, actif, creeLe, creePar) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'REG', ?, 1, 1, ?, 'adm')").params(id, T, feuille, place, date, debut, fin, source, CREE).update();
    }

    private void tempsPresenceAbsences() {
        jdbc.sql("INSERT INTO eff_feuilles (id, tenantId, ressourceId, uniteCode, debut, fin, statut, palier, paliersRequis, creeLe, creePar) "
                + "VALUES ('f-1', ?, 'rs-1', 'EST', '2031-01-13', '2031-01-19', 'BROUILLON', 0, 1, ?, 'adm'), "
                + "('f-5', ?, 'rs-5', 'OUEST', '2031-01-13', '2031-01-19', 'BROUILLON', 0, 1, ?, 'adm')").params(T, CREE, T, CREE).update();
        segment("s-1", "f-1", "pl-1", "2031-01-15", "2031-01-15T11:00:00.000Z", "2031-01-15T23:00:00.000Z", "POINTAGE");
        segment("s-5", "f-5", "pl-4", "2031-01-15", "2031-01-15T11:00:00.000Z", "2031-01-15T23:00:00.000Z", "GENERE");
        for (String[] p : new String[][]{{"po-1", "ENTREE", "2031-01-15T11:00:00.000Z", "CONFORME", null}, {"po-2", "SORTIE", "2031-01-15T23:00:00.000Z", "ECART", "HORS_MILIEU"}}) {
            jdbc.sql("INSERT INTO eff_pointages (id, tenantId, ressourceId, placeId, milieuId, uniteCode, sens, horodatage, origine, resultat, ecart, actionRegles, "
                    + "statut, saisiLe, saisiPar) VALUES (?, ?, 'rs-1', 'pl-1', 'm-12', 'EST', ?, ?, 'APPAREIL', ?, ?, 'ACCEPTER', 'ENREGISTRE', ?, 'usr-julie')")
                    .params(p[0], T, p[1], p[2], p[3], p[4], CREE).update();
        }
        jdbc.sql("INSERT INTO eff_horodatages_externes (id, tenantId, source, cle, ressourceId, sens, horodatage, importeLe, importePar) "
                + "VALUES ('h-1', ?, 'borne', 'b1', 'rs-1', 'ENTREE', '2031-01-15T11:02:00.000Z', ?, 'adm')").params(T, CREE).update();
        jdbc.sql("INSERT INTO eff_absences (id, tenantId, ressourceId, uniteCode, codeAbsence, du, au, statut, actionRegles, declareeLe, declareePar) "
                + "VALUES ('a-1', ?, 'rs-1', 'EST', 'MAL', '2031-01-18', '2031-01-19', 'ACCEPTEE', 'ACCEPTER', ?, 'usr-julie')").params(T, CREE).update();
    }

    private String executer(ContexteRequete qui, String definition) throws Exception {
        return http.post("/api/rapports/executer", qui, definition).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private ResultActions produire(ContexteRequete qui, String definition) throws Exception {
        return http.post("/api/rapports/executer", qui, definition);
    }

    @Test
    @DisplayName("les cinq sources sont offertes avec leur période et leur capacité ; l'agrégat « taux » est annoncé")
    void catalogue() throws Exception {
        http.get("/api/rapports/sources", ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("effectifs_heures")).andExpect(jsonPath("$[0].faits.periode").value("jour"))
                .andExpect(jsonPath("$[0].capacite").value("effectifs.manage")).andExpect(jsonPath("$[4].code").value("effectifs_absences"));
        http.get("/api/rapports/agregats", ADMIN).andExpect(jsonPath("$[5]").value("taux"));
    }

    @Test
    @DisplayName("heures par unité racine et unité ; journées : heures supplémentaires et écart au planifié par personne")
    void temps() throws Exception {
        produire(ADMIN, "{\"source\":\"effectifs_heures\",\"groupes\":[\"uniteNiveau1\",\"unite\"],\"colonnes\":[{\"champ\":\"uniteNiveau1\"},"
                + "{\"champ\":\"unite\"},{\"champ\":\"heures\",\"agregat\":\"somme\"}],\"filtres\":[" + SEMAINE + "],\"tri\":{\"champ\":\"unite\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].uniteNiveau1").value("US")).andExpect(jsonPath("$.lignes[0].unite").value("EST"))
                .andExpect(jsonPath("$.lignes[0].heures").value(12)).andExpect(jsonPath("$.lignes[1].unite").value("OUEST"))
                .andExpect(jsonPath("$.lignes[1].heures").value(12)).andExpect(jsonPath("$.colonnes[0].libelle").value("Unité de niveau 1 (racine)"));
        produire(ADMIN, "{\"source\":\"effectifs_journees\",\"groupes\":[\"ressource\",\"titreEmploi\",\"statutEmploi\",\"convention\"],"
                + "\"colonnes\":[{\"champ\":\"ressource\"},{\"champ\":\"titreEmploi\"},{\"champ\":\"statutEmploi\"},{\"champ\":\"convention\"},"
                + "{\"champ\":\"heuresSupplementaires\",\"agregat\":\"somme\"},{\"champ\":\"ecartHeures\",\"agregat\":\"somme\"},{\"champ\":\"journees\",\"agregat\":\"somme\"}],"
                + "\"filtres\":[" + SEMAINE + ",{\"champ\":\"unite\",\"operateur\":\"egal\",\"valeurs\":[\"EST\"]}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignes.length()").value(1))
                .andExpect(jsonPath("$.lignes[0].ressource").value("P1 — Julie")).andExpect(jsonPath("$.lignes[0].titreEmploi").value("TECH"))
                .andExpect(jsonPath("$.lignes[0].convention").value("FSSS")).andExpect(jsonPath("$.lignes[0].heuresSupplementaires").value(4))
                .andExpect(jsonPath("$.lignes[0].ecartHeures").value(-12)).andExpect(jsonPath("$.lignes[0].journees").value(2));
    }

    @Test
    @DisplayName("pointages : taux d'écarts, corroboration, milieu et nature du quart ; comblement : taux et équipages sous le minimum")
    void presenceEtComblement() throws Exception {
        produire(ADMIN, "{\"source\":\"effectifs_pointages\",\"groupes\":[\"unite\",\"milieu\",\"natureQuart\"],\"colonnes\":[{\"champ\":\"unite\"},"
                + "{\"champ\":\"milieu\"},{\"champ\":\"natureQuart\"},{\"champ\":\"pointages\",\"agregat\":\"somme\"},"
                + "{\"champ\":\"ecarts\",\"agregat\":\"taux\",\"denominateur\":\"pointages\"},{\"champ\":\"corrobores\",\"agregat\":\"somme\"}],\"filtres\":[" + SEMAINE + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].milieu").value("C12")).andExpect(jsonPath("$.lignes[0].natureQuart").value("REG"))
                .andExpect(jsonPath("$.lignes[0].pointages").value(2)).andExpect(jsonPath("$.lignes[0].ecarts").value(50))
                .andExpect(jsonPath("$.lignes[0].corrobores").value(1)).andExpect(jsonPath("$.colonnes[4].libelle").value("Pointages en écart / Pointages (%)"));
        produire(ADMIN, "{\"source\":\"effectifs_places\",\"groupes\":[\"unite\"],\"colonnes\":[{\"champ\":\"unite\"},"
                + "{\"champ\":\"pourvues\",\"agregat\":\"taux\",\"denominateur\":\"places\"},{\"champ\":\"equipagesSousMinimum\",\"agregat\":\"somme\"},"
                + "{\"champ\":\"equipages\",\"agregat\":\"somme\"}],\"filtres\":[" + SEMAINE + "],\"tri\":{\"champ\":\"unite\"}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].unite").value("EST")).andExpect(jsonPath("$.lignes[0].pourvues").value(66.67))
                .andExpect(jsonPath("$.lignes[0].equipagesSousMinimum").value(1)).andExpect(jsonPath("$.lignes[0].equipages").value(2))
                .andExpect(jsonPath("$.lignes[1].pourvues").value(100));
    }

    @Test
    @DisplayName("absences : jours et absences par mois, code et type")
    void absences() throws Exception {
        produire(ADMIN, "{\"source\":\"effectifs_absences\",\"groupes\":[\"mois\",\"codeAbsence\",\"typeAbsence\"],\"colonnes\":[{\"champ\":\"mois\"},"
                + "{\"champ\":\"codeAbsence\"},{\"champ\":\"typeAbsence\"},{\"champ\":\"jours\",\"agregat\":\"somme\"},{\"champ\":\"absences\",\"agregat\":\"somme\"}],"
                + "\"filtres\":[{\"champ\":\"jour\",\"operateur\":\"entre\",\"valeurs\":[\"2031-01-01\",\"2031-01-31\"]}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.lignes[0].mois").value("2031-01")).andExpect(jsonPath("$.lignes[0].typeAbsence").value("PAYE"))
                .andExpect(jsonPath("$.lignes[0].jours").value(2)).andExpect(jsonPath("$.lignes[0].absences").value(1));
    }

    @Test
    @DisplayName("le périmètre borne le relevé ; une employée est refusée (403) ; la période est obligatoire ; sans demandeur, rien")
    void perimetreEtRefus() throws Exception {
        String definition = "{\"source\":\"effectifs_heures\",\"groupes\":[\"unite\"],\"colonnes\":[{\"champ\":\"unite\"},{\"champ\":\"heures\",\"agregat\":\"somme\"}],"
                + "\"filtres\":[" + SEMAINE + "]}";
        List<String> unites = JsonPath.read(executer(CHEF, definition), "$.lignes[*].unite");
        assertThat(unites).containsExactly("EST");
        produire(JULIE, definition).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("droit requis : Planifier et gérer les effectifs"))
                .andExpect(jsonPath("$.capability").value("effectifs.manage"));
        http.post("/api/rapports/exporter", JULIE, "{\"definition\":" + definition + ",\"format\":\"csv\"}").andExpect(status().isForbidden());
        produire(ADMIN, "{\"source\":\"effectifs_heures\",\"colonnes\":[{\"champ\":\"heures\",\"agregat\":\"somme\"}]}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("la source « effectifs_heures » exige sa période : un filtre « entre » sur « jour » (deux dates AAAA-MM-JJ)"));
        CatalogueRapports.Definition d = new CatalogueRapports.Definition("effectifs_heures", List.of(new CatalogueRapports.Colonne("heures", CatalogueRapports.Agregat.SOMME)),
                null, List.of(new CatalogueRapports.Filtre("jour", CatalogueRapports.Operateur.ENTRE, List.of("2031-01-13", "2031-01-19"))), null, false, null);
        assertThatThrownBy(() -> new MoteurRapports(List.of(faits)).executer(d, null))
                .hasMessage("la source « effectifs_heures » exige l'identité du demandeur");
        assertThatThrownBy(() -> new MoteurRapports(List.of()).executer(d, new ca.vina.nexu.rapports.domaine.FournisseurFaits.Demandeur(T, "u", "admin", "2031-01-20T12:00:00Z")))
                .hasMessage("la source « effectifs_heures » n'a pas de fournisseur de faits branché");
        assertThatThrownBy(() -> faits.faits("users", null, null)).hasMessageContaining("users");
        // Le câblage Spring rend les mêmes objets que ceux que l'essai assemble à la main.
        assertThat(new ca.vina.nexu.effectifs.infrastructure.configuration.ConfigurationRelevesEffectifs()
                .lecturesRelevesEffectifs(null, null, null, null).feuilles()).isNull();
        assertThat(new ca.vina.nexu.rapports.infrastructure.configuration.ConfigurationRapports()
                .portsRapports(null, null, Clock.systemUTC(), null).horloge()).isNotNull();
    }

    @Test
    @DisplayName("l'organisme enregistre son relevé, le produit, l'exporte (CSV, Excel, PDF), le modifie et le retire")
    void enregistres() throws Exception {
        String definition = "{\"source\":\"effectifs_pointages\",\"groupes\":[\"unite\"],\"filtres\":[" + SEMAINE + "],"
                + "\"colonnes\":[{\"champ\":\"unite\"},{\"champ\":\"ecarts\",\"agregat\":\"taux\",\"denominateur\":\"pointages\"}]}";
        http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\" \",\"definition\":" + definition + "}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« nom » : de 1 à 120 caractères"));
        http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\"Taux\"}").andExpect(jsonPath("$.errors[0]").value("« definition » : la définition du rapport est requise"));
        http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\"Taux\",\"description\":\"" + "x".repeat(1001) + "\",\"definition\":" + definition + "}")
                .andExpect(jsonPath("$.errors[0]").value("« description » : 1000 caractères au plus"));
        http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\"Taux\",\"definition\":{\"source\":\"effectifs_pointages\",\"colonnes\":[{\"champ\":\"pointages\"}]}}")
                .andExpect(jsonPath("$.errors[0]", containsString("exige sa période")));

        String cree = http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\"Taux d'écarts de pointage\",\"description\":\"Hebdomadaire\",\"definition\":" + definition + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.definition.source").value("effectifs_pointages"))
                .andExpect(jsonPath("$.description").value("Hebdomadaire")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(cree, "$.id");
        http.post("/api/rapports/enregistres", ADMIN, "{\"nom\":\"Taux d'écarts de pointage\",\"definition\":" + definition + "}")
                .andExpect(jsonPath("$.errors[0]").value("un rapport « Taux d'écarts de pointage » est déjà enregistré dans cet organisme"));
        http.get("/api/rapports/enregistres", ADMIN).andExpect(jsonPath("$[0].nom").value("Taux d'écarts de pointage"));
        http.get("/api/rapports/enregistres/" + id, ADMIN).andExpect(jsonPath("$.creePar").value(ADMIN.userId()));

        http.post("/api/rapports/enregistres/" + id + "/executer", CHEF, "{}").andExpect(status().isOk())
                .andExpect(jsonPath("$.lignes[0].unite").value("EST")).andExpect(jsonPath("$.lignes[0].ecarts").value(50));
        String csv = http.post("/api/rapports/enregistres/" + id + "/exporter", ADMIN, "{\"format\":\"csv\"}").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).isEqualTo("﻿Unité;Pointages en écart / Pointages (%)\r\nEST;50");
        for (String[] f : new String[][]{{"xlsx", "PK"}, {"pdf", "%PDF-"}}) {
            byte[] octets = http.post("/api/rapports/enregistres/" + id + "/exporter", ADMIN, "{\"format\":\"" + f[0] + "\"}").andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(octets, 0, f[1].length(), StandardCharsets.ISO_8859_1)).isEqualTo(f[1]);
        }
        http.post("/api/rapports/enregistres/" + id + "/exporter", JULIE, "{\"format\":\"csv\"}").andExpect(status().isForbidden());
        // Un employé produit un rapport de l'organisme ; il ne le définit, ne le change ni ne le retire pour tous.
        http.post("/api/rapports/enregistres", JULIE, "{\"nom\":\"Le mien\",\"definition\":" + definition + "}").andExpect(status().isForbidden());
        http.put("/api/rapports/enregistres/" + id, JULIE, "{\"nom\":\"x\",\"definition\":" + definition + "}").andExpect(status().isForbidden());
        http.delete("/api/rapports/enregistres/" + id, JULIE).andExpect(status().isForbidden());

        http.put("/api/rapports/enregistres/" + id, ADMIN, "{\"nom\":\"Écarts par milieu\",\"definition\":{\"source\":\"effectifs_pointages\",\"groupes\":[\"milieu\"],"
                        + "\"filtres\":[" + SEMAINE + "],\"colonnes\":[{\"champ\":\"milieu\"},{\"champ\":\"pointages\",\"agregat\":\"somme\"}]}}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Écarts par milieu"));
        http.post("/api/rapports/enregistres/" + id + "/executer", ADMIN, "{}").andExpect(jsonPath("$.lignes[0].milieu").value("C12"))
                .andExpect(jsonPath("$.lignes[0].pointages").value(2));
        http.delete("/api/rapports/enregistres/" + id, ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.nom").value("Écarts par milieu"));
        http.get("/api/rapports/enregistres/" + id, ADMIN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("rapport enregistré « " + id + " » introuvable"));
        http.post("/api/rapports/enregistres/" + id + "/exporter", ADMIN, "{\"format\":\"csv\"}").andExpect(status().isNotFound());
        http.post("/api/rapports/enregistres/" + id + "/executer", ADMIN, "{}").andExpect(status().isNotFound());
        http.delete("/api/rapports/enregistres/" + id, ADMIN).andExpect(status().isNotFound());
    }
}
