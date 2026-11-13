package ca.vina.nexu.rapports.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.rapports.domaine.CatalogueRapports;
import ca.vina.nexu.rapports.domaine.FournisseurFaits;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotIndicateurs;
import ca.vina.nexu.rapports.infrastructure.persistance.DepotUtilisation;
import ca.vina.nexu.rapports.infrastructure.persistance.MoteurRapports;
import ca.vina.nexu.saas.infrastructure.persistance.DepotQuotas;
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
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/rapports} — listes d'utilisation, comparaison de périodes et indicateurs ; jumeau des
 * essais Node {@code rapports-utilisation}, {@code rapports-periodicites} et {@code rapports-indicateurs}.
 */
@DisplayName("API rapports — utilisation, comparaison de périodes, indicateurs de l'établissement")
class ControleurAnalyseRapportsHttpTest {

    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", "t-1");
    private static final ContexteRequete RH = ClientHttpEssai.session("rh", "t-1");
    private static final ContexteRequete EMPLOYE = ClientHttpEssai.session("employee", "t-1");
    private static final ContexteRequete RESTREINT = ClientHttpEssai.session("admin", "t-2");
    private static final String R = "/api/rapports";

    /** Un fait par jour et par unité : mars 2027 en soins, février en soins et urgence, mars 2026 en soins. */
    private static final List<Map<String, Object>> FAITS = List.of(
            Map.of("jour", "2027-03-10", "unite", "soins", "journees", 4, "heuresPlanifiees", 30, "heuresDeclarees", 28),
            Map.of("jour", "2027-03-11", "unite", "soins", "journees", 2, "heuresPlanifiees", 10, "heuresDeclarees", 12),
            Map.of("jour", "2027-03-12", "unite", "urgence", "journees", 1, "heuresPlanifiees", 0, "heuresDeclarees", 0),
            Map.of("jour", "2027-02-10", "unite", "soins", "journees", 3, "heuresPlanifiees", 20, "heuresDeclarees", 20),
            Map.of("jour", "2027-02-11", "unite", "radiologie", "journees", 5, "heuresPlanifiees", 40, "heuresDeclarees", 38),
            Map.of("jour", "2026-03-10", "unite", "soins", "journees", 8, "heuresPlanifiees", 60, "heuresDeclarees", 60));

    /** Le fournisseur borne à la période, comme le ferait celui des effectifs. */
    private static final FournisseurFaits FOURNISSEUR = new FournisseurFaits() {
        @Override
        public Set<String> sources() {
            return Set.of("effectifs_journees");
        }

        @Override
        public List<Map<String, Object>> faits(String source, Demandeur demandeur, CatalogueRapports.Periode periode) {
            return FAITS.stream().filter(f -> String.valueOf(f.get("jour")).compareTo(periode.du()) >= 0
                    && String.valueOf(f.get("jour")).compareTo(periode.au()) <= 0).toList();
        }
    };

    private ClientHttpEssai http;
    private JdbcClient jdbc;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenants (id, name, country) VALUES ('t-1', 'CIUSSS', 'CA'), ('t-2', 'Voisin', 'CA')").update();
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('t-1', 10, 10, '[\"effectifs_temps\",\"finance\"]'),"
                + " ('t-2', 10, 10, '[\"finance\"]')").update();
        jdbc.sql("CREATE TABLE IF NOT EXISTS horaire_periodes (id TEXT PRIMARY KEY, tenantId TEXT NOT NULL, statut TEXT NOT NULL)").update();
        http = new ClientHttpEssai(new ControleurAnalyseRapports(new MoteurRapports(List.of(FOURNISSEUR)), new GardeAcces(new RegistrePolitiqueAcces()),
                new ControleurAnalyseRapports.Ports(new DepotQuotas(jdbc), new DepotIndicateurs(jdbc), new DepotUtilisation(jdbc),
                        Clock.fixed(Instant.parse("2027-04-01T12:00:00Z"), ZoneOffset.UTC), ControleurRapportsHttpTest.sequence())));
    }

    private void compte(String id, String username, String role, int actif) {
        jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt, active) VALUES (?, ?, 't-1', 'x', ?, '2026-01-01', ?)")
                .params(id, username, role, actif).update();
    }

    @Test
    @DisplayName("l'utilisation distingue invité, actif, sans activité récente et fermé, et compte les quarts publiés seulement")
    void utilisation() throws Exception {
        compte("u1", "alice@@t-1", "employee", 1);
        compte("u2", "bruno@@t-1", "employee", 1);
        compte("u3", "carla@@t-1", "employee", 0);
        compte("u4", "denis@@t-1", "manager", 1);
        jdbc.sql("INSERT INTO audit_log (id, at, userId, action) VALUES ('a1', '2027-03-30T10:00:00.000Z', 'u1', 'login'),"
                + " ('a2', '2026-10-01T10:00:00.000Z', 'u2', 'login')").update();
        jdbc.sql("INSERT INTO auth_invitations (id, tenantId, courriel, username, role, empreinte, expireLe, creeLe, creePar) VALUES "
                + "('i1', 't-1', 'e@x.ca', 'emma', 'employee', 'h1', '2027-05-01T00:00:00.000Z', '2027-03-01', 'u4'),"
                + "('i2', 't-1', 'a@x.ca', 'alice', 'employee', 'h2', '2027-05-01T00:00:00.000Z', '2027-03-01', 'u4'),"
                + "('i3', 't-1', 'f@x.ca', 'fanny', 'employee', 'h3', '2027-01-01T00:00:00.000Z', '2026-12-01', 'u4')").update();
        jdbc.sql("INSERT INTO horaire_periodes (id, tenantId, statut) VALUES ('p1', 't-1', 'publiee'), ('p2', 't-1', 'brouillon')").update();
        jdbc.sql("INSERT INTO horaire_quarts (id, tenantId, periodeId, date, personId) VALUES ('q1', 't-1', 'p1', '2027-03-02', 'e1'),"
                + " ('q2', 't-1', 'p1', '2027-03-03', NULL), ('q3', 't-1', 'p1', '2027-03-04', 'e2'), ('q4', 't-1', 'p2', '2027-03-04', NULL),"
                + " ('q5', 't-1', 'p1', '2027-05-04', NULL)").update();

        http.get(R + "/utilisation?du=2027-03-01&au=2027-03-31", ADMIN).andExpect(status().isOk())
                .andExpect(jsonPath("$.periode.du").value("2027-03-01")).andExpect(jsonPath("$.seuilInactiviteJours").value(90))
                .andExpect(jsonPath("$.quarts.publies").value(3)).andExpect(jsonPath("$.quarts.combles").value(2))
                .andExpect(jsonPath("$.quarts.aCombler").value(1)).andExpect(jsonPath("$.quarts.tauxComblement").value(66.67))
                .andExpect(jsonPath("$.comptesObserves").value(5))
                .andExpect(jsonPath("$.comptes[0].etat").value("invite")).andExpect(jsonPath("$.comptes[0].comptes[0].identifiant").value("emma"))
                .andExpect(jsonPath("$.comptes[0].comptes[0].role").value("—"))
                .andExpect(jsonPath("$.comptes[1].nombre").value(1)).andExpect(jsonPath("$.comptes[1].comptes[0].identifiant").value("alice"))
                .andExpect(jsonPath("$.comptes[2].nombre").value(2)).andExpect(jsonPath("$.comptes[2].comptes[0].identifiant").value("bruno"))
                .andExpect(jsonPath("$.comptes[3].comptes[0].identifiant").value("carla"));
        http.get(R + "/utilisation?du=2027-03-01&au=2027-03-31&seuilJours=365", ADMIN).andExpect(jsonPath("$.comptes[1].nombre").value(2));
        http.get(R + "/utilisation?du=2027-06-01&au=2027-06-30", ADMIN).andExpect(jsonPath("$.quarts.tauxComblement").value(nullValue()));
        http.get(R + "/utilisation?du=2027-03-01&au=2027-03-31&seuilJours=0", ADMIN).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("entre 1 et 730")));
        http.get(R + "/utilisation?du=2027-03-31&au=2027-03-01", ADMIN).andExpect(jsonPath("$.errors[0]").value("la fin de la période précède son début"));
        http.get(R + "/utilisation?au=2027-03-01", ADMIN).andExpect(jsonPath("$.errors[0]").value("le début de la période attend une date AAAA-MM-JJ"));
        http.get(R + "/utilisation?du=2027-03-01", ADMIN).andExpect(jsonPath("$.errors[0]").value("la fin de la période attend une date AAAA-MM-JJ"));
        http.get(R + "/utilisation?du=2027-03-01&au=2027-03-31", EMPLOYE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("les périodicités sont servies ; la comparaison rend l'absence comme null et l'écart en pour cent")
    void comparer() throws Exception {
        http.get(R + "/periodicites", EMPLOYE).andExpect(jsonPath("$", hasSize(6))).andExpect(jsonPath("$[2].code").value("mensuel"))
                .andExpect(jsonPath("$[2].libelle").value("le mois"));
        String definition = "{\"source\":\"effectifs_journees\",\"colonnes\":[{\"champ\":\"unite\"},{\"champ\":\"journees\",\"agregat\":\"somme\"}],"
                + "\"groupes\":[\"unite\"],\"filtres\":[{\"champ\":\"jour\",\"operateur\":\"entre\",\"valeurs\":[\"2000-01-01\",\"2000-01-02\"]}]}";
        http.post(R + "/comparer", ADMIN, "{\"definition\":" + definition + ",\"periodicite\":\"mensuel\",\"reference\":\"2027-03-14\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.contre").value("precedente")).andExpect(jsonPath("$.libelle").value("le mois"))
                .andExpect(jsonPath("$.courante.du").value("2027-03-01")).andExpect(jsonPath("$.anterieure.au").value("2027-02-28"))
                .andExpect(jsonPath("$.cles[0]").value("unite")).andExpect(jsonPath("$.mesures[0]").value("journees"))
                .andExpect(jsonPath("$.lignes", hasSize(3)))
                .andExpect(jsonPath("$.lignes[?(@.cles.unite=='soins')].ecart.journees").value(org.hamcrest.Matchers.hasItem(3.0)))
                .andExpect(jsonPath("$.lignes[?(@.cles.unite=='soins')].ecartPourcent.journees").value(org.hamcrest.Matchers.hasItem(100.0)))
                .andExpect(jsonPath("$.lignes[?(@.cles.unite=='urgence')].precedent.journees").value(org.hamcrest.Matchers.hasItem(nullValue())))
                .andExpect(jsonPath("$.lignes[?(@.cles.unite=='radiologie')].courant.journees").value(org.hamcrest.Matchers.hasItem(nullValue())));
        http.post(R + "/comparer", ADMIN, "{\"definition\":" + definition + ",\"periodicite\":\"mensuel\",\"reference\":\"2027-03-14\",\"contre\":\"an_passe\"}")
                .andExpect(jsonPath("$.anterieure.du").value("2026-03-01"))
                .andExpect(jsonPath("$.lignes[?(@.cles.unite=='soins')].ecartPourcent.journees").value(org.hamcrest.Matchers.hasItem(-25.0)));
        http.post(R + "/comparer", ADMIN, "{\"definition\":" + definition + ",\"periodicite\":\"personnalise\",\"du\":\"2027-03-10\",\"au\":\"2027-03-12\"}")
                .andExpect(jsonPath("$.anterieure.du").value("2027-03-07")).andExpect(jsonPath("$.anterieure.au").value("2027-03-09"));
        http.post(R + "/comparer", ADMIN, "{\"definition\":" + definition + ",\"periodicite\":\"mensuel\",\"reference\":\"2027-03-14\",\"contre\":\"hier\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« contre » attend « precedente » ou « an_passe »"));
        http.post(R + "/comparer", ADMIN, "{\"definition\":{\"source\":\"ecritures\",\"colonnes\":[{\"champ\":\"statut\"}]},\"periodicite\":\"annuel\",\"reference\":\"2027-03-14\"}")
                .andExpect(jsonPath("$.errors[0]").value(containsString("source inconnue : « ecritures »")));
        http.post(R + "/comparer", ADMIN, "{\"definition\":" + definition + ",\"periodicite\":\"siecle\"}")
                .andExpect(jsonPath("$.errors[0]").value(containsString("périodicité inconnue « siecle »")));
        http.post(R + "/comparer", EMPLOYE, "{\"definition\":" + definition + ",\"periodicite\":\"mensuel\",\"reference\":\"2027-03-14\"}")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.capability").value("effectifs.manage"));
        http.post(R + "/comparer", RESTREINT, "{\"definition\":" + definition + ",\"periodicite\":\"mensuel\",\"reference\":\"2027-03-14\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value(containsString("n'est pas disponible")));
        http.post(R + "/comparer", ADMIN, null).andExpect(status().isBadRequest());
        http.get(R + "/periodicites", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un indicateur se définit contre sa source, se modifie sans changer de code, se retire, et se lit par l'employé")
    void indicateurs() throws Exception {
        http.get(R + "/indicateurs/formats", EMPLOYE).andExpect(jsonPath("$.formats[0]").value("taux"))
                .andExpect(jsonPath("$.directions[1]").value("au_plus")).andExpect(jsonPath("$.etats[3]").value("indetermine"));
        http.get(R + "/indicateurs/mesures?source=effectifs_journees", EMPLOYE).andExpect(jsonPath("$.mesures[0]").value("journees"))
                .andExpect(jsonPath("$.axes[0]").value("jour"));
        http.get(R + "/indicateurs/mesures?source=budget_portefeuille", EMPLOYE).andExpect(status().isBadRequest());
        http.get(R + "/indicateurs/mesures", EMPLOYE).andExpect(status().isBadRequest());

        String taux = "{\"code\":\"respect\",\"libelle\":\"Respect du planifié\",\"source\":\"effectifs_journees\",\"format\":\"taux\","
                + "\"numerateur\":\"heuresDeclarees\",\"denominateur\":\"heuresPlanifiees\",\"axe\":\"unite\",\"seuil\":95,\"direction\":\"au_moins\","
                + "\"filtres\":[{\"champ\":\"unite\",\"operateur\":\"parmi\",\"valeurs\":[\"soins\",\"urgence\"]}]}";
        http.post(R + "/indicateurs", ADMIN, taux).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("uuid-1"))
                .andExpect(jsonPath("$.format").value("taux")).andExpect(jsonPath("$.marge").value(5.0)).andExpect(jsonPath("$.actif").value(true))
                .andExpect(jsonPath("$.filtres[0].operateur").value("parmi"));
        http.post(R + "/indicateurs", ADMIN, taux).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("le code « respect » est déjà utilisé"));
        http.post(R + "/indicateurs", ADMIN, "{\"code\":\"volume\",\"source\":\"effectifs_journees\",\"format\":\"nombre\",\"numerateur\":\"journees\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.libelle").value("volume")).andExpect(jsonPath("$.direction").value("au_moins"))
                .andExpect(jsonPath("$.denominateur").value(nullValue()));
        http.post(R + "/indicateurs", EMPLOYE, taux).andExpect(status().isForbidden());
        http.post(R + "/indicateurs", RH, taux.replace("respect", "autre")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.capability").value("effectifs.manage"));
        http.post(R + "/indicateurs", ADMIN, "{\"code\":\"x\",\"source\":\"budget_portefeuille\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("n'est pas disponible")));

        http.get(R + "/indicateurs", EMPLOYE).andExpect(jsonPath("$", hasSize(2))).andExpect(jsonPath("$[0].code").value("respect"));
        http.patch(R + "/indicateurs/uuid-1", ADMIN, "{\"code\":\"change\",\"seuil\":90,\"libelle\":\"Respect\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("respect")).andExpect(jsonPath("$.seuil").value(90.0)).andExpect(jsonPath("$.libelle").value("Respect"))
                .andExpect(jsonPath("$.filtres[0].valeurs[1]").value("urgence"));
        http.patch(R + "/indicateurs/uuid-1", ADMIN, "{\"seuil\":140}").andExpect(jsonPath("$.errors[0]").value("le seuil d'un taux va de 0 à 100"));
        http.patch(R + "/indicateurs/absent", ADMIN, "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("indicateur introuvable"));
        http.patch(R + "/indicateurs/uuid-1", EMPLOYE, "{}").andExpect(status().isForbidden());

        http.delete(R + "/indicateurs/uuid-3", ADMIN).andExpect(jsonPath("$.actif").value(false));
        http.get(R + "/indicateurs", ADMIN).andExpect(jsonPath("$", hasSize(1)));
        http.get(R + "/indicateurs?retires=1", ADMIN).andExpect(jsonPath("$", hasSize(2)));
        http.delete(R + "/indicateurs/uuid-3", EMPLOYE).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("mesurer exige une période, découpe par axe, rend l'état nommé et le dénombrement")
    void mesurer() throws Exception {
        http.post(R + "/indicateurs", ADMIN, "{\"code\":\"respect\",\"source\":\"effectifs_journees\",\"format\":\"taux\",\"numerateur\":\"heuresDeclarees\","
                + "\"denominateur\":\"heuresPlanifiees\",\"axe\":\"unite\",\"seuil\":95,\"marge\":10}").andExpect(status().isCreated());
        http.post(R + "/indicateurs", ADMIN, "{\"code\":\"volume\",\"source\":\"effectifs_journees\",\"format\":\"nombre\",\"numerateur\":\"journees\"}")
                .andExpect(status().isCreated());

        http.post(R + "/indicateurs/mesurer", EMPLOYE, "{\"du\":\"2027-03-01\",\"au\":\"2027-03-31\"}").andExpect(status().isForbidden());
        http.post(R + "/indicateurs/mesurer", ADMIN, "{\"du\":\"2027-03-01\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("la mesure d'un indicateur exige une période (du, au)"));
        http.post(R + "/indicateurs/mesurer", ADMIN, null).andExpect(status().isBadRequest());
        http.post(R + "/indicateurs/mesurer", ADMIN, "{\"du\":\"2027-03-01\",\"au\":\"2027-03-31\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.du").value("2027-03-01")).andExpect(jsonPath("$.mesures", hasSize(2)))
                .andExpect(jsonPath("$.mesures[0].indicateur.code").value("respect"))
                .andExpect(jsonPath("$.mesures[0].valeurs[0].decoupage").value("soins")).andExpect(jsonPath("$.mesures[0].valeurs[0].valeur").value(100.0))
                .andExpect(jsonPath("$.mesures[0].valeurs[0].etat").value("atteint")).andExpect(jsonPath("$.mesures[0].valeurs[0].faits").value(2))
                .andExpect(jsonPath("$.mesures[0].valeurs[1].decoupage").value("urgence")).andExpect(jsonPath("$.mesures[0].valeurs[1].valeur").value(nullValue()))
                .andExpect(jsonPath("$.mesures[0].valeurs[1].etat").value("indetermine"))
                .andExpect(jsonPath("$.mesures[1].valeurs[0].decoupage").value(nullValue())).andExpect(jsonPath("$.mesures[1].valeurs[0].valeur").value(7.0));
        http.post(R + "/indicateurs/mesurer", ADMIN, "{\"du\":\"2027-02-01\",\"au\":\"2027-02-28\",\"indicateurId\":\"uuid-1\"}")
                .andExpect(jsonPath("$.mesures", hasSize(1))).andExpect(jsonPath("$.mesures[0].valeurs[0].decoupage").value("radiologie"))
                .andExpect(jsonPath("$.mesures[0].valeurs[0].valeur").value(95.0)).andExpect(jsonPath("$.mesures[0].valeurs[0].etat").value("atteint"));
        http.post(R + "/indicateurs/mesurer", ADMIN, "{\"du\":\"2027-02-01\",\"au\":\"2027-02-28\",\"indicateurId\":\"absent\"}")
                .andExpect(jsonPath("$.errors[0]").value("indicateur introuvable"));
    }

    @Test
    @DisplayName("un indicateur rangé sur une source sans fournisseur se refuse nommément à la mesure")
    void sourcesNonMesurables() throws Exception {
        jdbc.sql("INSERT INTO rap_indicateurs (id, tenantId, code, libelle, source, format, numerateur, filtresJson, direction, marge, actif, creeLe, creePar)"
                + " VALUES ('t1', 't-1', 'heures', 'Heures', 'effectifs_heures', 'nombre', 'x', '[]', 'au_moins', 5, 1, 'x', 'x')").update();
        jdbc.sql("UPDATE tenant_quotas SET modules = '[\"effectifs_temps\"]' WHERE tenantId = 't-1'").update();
        http.post(R + "/indicateurs/mesurer", ADMIN, "{\"du\":\"2027-02-01\",\"au\":\"2027-02-28\"}")
                .andExpect(jsonPath("$.errors[0]").value("la source « effectifs_heures » n'a pas de fournisseur de faits branché"));
        http.get(R + "/indicateurs", ADMIN).andExpect(jsonPath("$[0].filtres", hasSize(0)));
    }
}
