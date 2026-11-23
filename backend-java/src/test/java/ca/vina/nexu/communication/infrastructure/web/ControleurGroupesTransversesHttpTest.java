package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.communication.application.commandes.GestionnaireGroupesTransverses;
import ca.vina.nexu.communication.infrastructure.persistance.DepotGroupesTransverses;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/communication/groupes-transverses} — l'administration des gardes, comités et spécialités (ST-G09). */
@DisplayName("/api/communication/groupes-transverses — créer, renommer, désactiver ; inscrire pour une période ; retirer")
class ControleurGroupesTransversesHttpTest {

    private static final String T = "T1";
    private static final String R = "/api/communication/groupes-transverses";
    private static final ContexteRequete ADMIN = new ContexteRequete("u-adm", "admin", "admin", null, T);
    private static final ContexteRequete CHEF = new ContexteRequete("u-chef", "chef", "manager", null, T);
    private static final ContexteRequete VOISIN = new ContexteRequete("u-v", "voisin", "admin", null, "T2");
    private static final ContexteRequete SANS_MODULE = new ContexteRequete("u-s", "sans", "admin", null, "T3");

    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        JdbcClient jdbc = SchemaEssai.base(dossier);
        for (String[] u : new String[][]{{"u-lea", "lea"}, {"u-marc", "marc"}}) {
            jdbc.sql("INSERT INTO users (id, username, loginName, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, ?, 'x', 'employee', 'x')")
                    .params(u[0], u[1] + "@@" + T, u[1], T).update();
        }
        new ca.vina.nexu.effectifs.infrastructure.persistance.SchemaPlanification(jdbc);
        jdbc.sql("INSERT INTO eff_ressources (id, tenantId, matricule, nom, utilisateurId, uniteCode, corpsEmploiCode, titreEmploiCode, statutEmploiCode, conventionCode, actif, creeLe) "
                + "VALUES ('rs1', ?, 'P1', 'Léa Morin', 'u-lea', 'EST', 'PARA', 'TECH', 'TC', 'FSSS', 1, 'x')").param(T).update();
        DepotGroupesTransverses depot = new DepotGroupesTransverses(jdbc);
        AtomicInteger n = new AtomicInteger();
        // T a les alertes, T2 la messagerie : chacun des deux modules suffit ; T3 n'a ni l'un ni l'autre.
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, '[\"alertes\"]')").param(T).update();
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES ('T2', -1, -1, '[\"effectifs_communication\"]')").update();
        http = new ClientHttpEssai(new ControleurGroupesTransverses(new GardeAcces(new RegistrePolitiqueAcces()),
                new ca.vina.nexu.partage.modules.infrastructure.DepotModulesActives(jdbc),
                new GestionnaireGroupesTransverses(depot, depot, () -> String.valueOf(n.incrementAndGet()))));
    }

    @Test
    @DisplayName("l'administration crée un groupe, y inscrit pour une période, nomme une modératrice, renomme, désactive, retire")
    void cycle() throws Exception {
        String id = JsonPath.read(http.post(R, ADMIN, "{\"nom\":\"Garde de nuit\",\"nature\":\"garde\"}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.nature").value("garde")).andExpect(jsonPath("$.membres").isEmpty())
                .andReturn().getResponse().getContentAsString(), "$.id");
        http.put(R + "/" + id + "/membres/lea", ADMIN, "{\"depuis\":\"2031-03-01\",\"jusqua\":\"2031-03-07\",\"moderateur\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.membres[0].nom").value("Léa Morin")).andExpect(jsonPath("$.membres[0].moderateur").value(true));
        http.put(R + "/" + id + "/membres/marc", ADMIN, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.membres[*].nom", Matchers.contains("Léa Morin", "marc")));
        // Réinscrire change la période, sans doubler la personne.
        http.put(R + "/" + id + "/membres/lea", ADMIN, "{\"depuis\":\"2031-03-02\"}").andExpect(jsonPath("$.membres.length()").value(2))
                .andExpect(jsonPath("$.membres[0].depuis").value("2031-03-02")).andExpect(jsonPath("$.membres[0].moderateur").value(false));
        http.patch(R + "/" + id, ADMIN, "{\"nom\":\"Garde de fin de semaine\",\"actif\":false}").andExpect(status().isOk())
                .andExpect(jsonPath("$.nom").value("Garde de fin de semaine")).andExpect(jsonPath("$.actif").value(false));
        http.get(R, ADMIN).andExpect(jsonPath("$[0].membres.length()").value(2));
        http.delete(R + "/" + id + "/membres/marc", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$.membres.length()").value(1));
        http.get(R, VOISIN).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("refusés : rôle, personne inconnue, dates fautives, groupe introuvable, membre absent, corps mal typé")
    void refus() throws Exception {
        http.get(R, CHEF).andExpect(status().isForbidden());
        http.post(R, ADMIN, "{\"nom\":\"X\",\"nature\":\"club\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« nature » : specialite, garde ou comite"));
        http.post(R, ADMIN, "{\"nom\":42,\"nature\":\"garde\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« nom » doit être un texte"));
        String id = JsonPath.read(http.post(R, ADMIN, "{\"nom\":\"Comité\",\"nature\":\"comite\"}").andReturn().getResponse().getContentAsString(), "$.id");
        http.put(R + "/" + id + "/membres/inconnu", ADMIN, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« personId » : « inconnu » n'est pas une personne de l'organisme"));
        http.put(R + "/" + id + "/membres/lea", ADMIN, "{\"depuis\":\"2031-03-10\",\"jusqua\":\"2031-03-01\"}").andExpect(status().isBadRequest());
        http.put(R + "/gt-inconnu/membres/lea", ADMIN, "{}").andExpect(status().isNotFound());
        http.patch(R + "/gt-inconnu", ADMIN, "{\"nom\":\"X\",\"actif\":true}").andExpect(status().isNotFound());
        http.patch(R + "/" + id, ADMIN, "{\"nom\":\"X\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« actif » doit valoir vrai ou faux"));
        http.patch(R + "/" + id, ADMIN, null).andExpect(status().isBadRequest());
        http.delete(R + "/" + id + "/membres/lea", ADMIN).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("« lea » n'est pas membre de ce groupe"));
    }

    @Test
    @DisplayName("chercher une personne à inscrire : par nom ou nom de connexion, dès trois caractères, jokers neutralisés")
    void personnes() throws Exception {
        http.get(R + "/personnes?q=mor", ADMIN).andExpect(jsonPath("$[0].id").value("lea")).andExpect(jsonPath("$[0].nom").value("Léa Morin"));
        http.get(R + "/personnes?q=MAR", ADMIN).andExpect(jsonPath("$[*].id", Matchers.contains("marc")));
        http.get(R + "/personnes?q=le", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(R + "/personnes", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(R + "/personnes?q=%25%25%25", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(R + "/personnes?q=___", ADMIN).andExpect(jsonPath("$.length()").value(0));
        http.get(R + "/personnes?q=mor", CHEF).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("un organisme qui n'a ni la messagerie ni les alertes n'atteint pas les groupes : ni lecture, ni recherche de personnes, ni écriture")
    void sansModuleUsager() throws Exception {
        http.get(R, SANS_MODULE).andExpect(status().isForbidden());
        http.get(R + "/personnes?q=lea", SANS_MODULE).andExpect(status().isForbidden());
        http.post(R, SANS_MODULE, "{\"nom\":\"Garde\",\"nature\":\"garde\"}").andExpect(status().isForbidden());
    }
}
