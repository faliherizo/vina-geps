package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.Oidc;
import ca.vina.nexu.auth.infrastructure.persistance.DepotAnnuaires;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.application.ServiceAnnuaires;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.securite.ResolutionHote;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P-TN-2 de bout en bout, sur SQLite : l'administrateur déclare deux annuaires, la page de connexion
 * les propose, l'usager part vers l'annuaire choisi, revient, et entre dans <em>son</em> organisme
 * avec le rôle que donnent ses groupes. Seul l'échange du code avec Microsoft est remplacé.
 */
@DisplayName("/api/auth/annuaires et /api/auth/sso/annuaires — plusieurs annuaires par organisme (P-TN-2)")
class ControleurAnnuairesHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String GUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLIENT = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final ContexteRequete ADMIN_T1 = ClientHttpEssai.session("admin", "T1");
    private static final ContexteRequete ADMIN_T2 = ClientHttpEssai.session("admin", "T2");

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotComptes comptes;
    private final Map<String, String> env = new HashMap<>();
    private final AtomicReference<Map<String, Object>> revendications = new AtomicReference<>();
    private final AtomicReference<Oidc.Configuration> configurationRecue = new AtomicReference<>();
    private String tenantDeLHote;
    private String adresse = "https://app.nexu.ca";

    private static Sequence sequence() {
        AtomicInteger n = new AtomicInteger();
        return new Sequence() {
            @Override
            public String suivant(String prefixe) {
                return prefixe + "-" + n.incrementAndGet();
            }

            @Override
            public String uuid() {
                return "uuid";
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
        comptes = new DepotComptes(jdbc, HORLOGE);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        DepotAnnuaires depot = new DepotAnnuaires(jdbc, HORLOGE);
        ServiceConnexion connexion = new ServiceConnexion(comptes, new DepotQuestionsSecretes(jdbc, HORLOGE), new JetonDeSession("secret-d-essai-de-trente-deux-octets-au-moins"), HORLOGE);
        Random alea = new Random(7);
        ServiceAnnuaires service = new ServiceAnnuaires(new ServiceAnnuaires.Ports(depot, new AdaptateurComptesAnnuaire(comptes, alea), connexion::emettre,
                (code, cfg, demande) -> {
                    configurationRecue.set(cfg);
                    if ("refuse".equals(code)) {
                        throw new IllegalStateException("Entra : invalid_grant — code expiré");
                    }
                    return revendications.get();
                }), env, HORLOGE, alea);
        HoteEtablissement hote = new HoteEtablissement() {
            @Override
            public ResolutionHote.Configuration configuration() {
                return ResolutionHote.Configuration.depuis(null, null, null);
            }

            @Override
            public Resolution de(HttpServletRequest requete) {
                return tenantDeLHote == null ? new Resolution.Selecteur() : new Resolution.Etablissement(tenantDeLHote, "Hôte", "hote");
            }
        };
        AdressePublique publique = t -> adresse;
        http = new ClientHttpEssai(new ControleurAnnuaires(new ControleurAnnuaires.Dependances(service, depot, depot,
                new DepotRolesEtablissement(jdbc, acces, HORLOGE), new GardeAcces(acces), hote, publique, new JournalAudit(jdbc), sequence(), env, false)));
        env.put("ANNUAIRE_SIEGE", "le-secret");
    }

    private static String annuaire(String nom, String variable, String domaine, String regles, String defaut) {
        return "{\"nom\":\"" + nom + "\",\"locataireEntra\":\"" + GUID + "\",\"clientId\":\"" + CLIENT + "\",\"variableSecret\":\"" + variable
                + "\",\"domaines\":[\"" + domaine + "\"],\"correspondances\":" + regles + (defaut == null ? "" : ",\"roleParDefaut\":\"" + defaut + "\"") + "}";
    }

    private static final String SIEGE = annuaire("Siège", "ANNUAIRE_SIEGE", "urgences-sante.qc.ca",
            "[{\"groupe\":\"g-admin\",\"role\":\"admin\"},{\"groupe\":\"g-gest\",\"role\":\"manager\"}]", null);
    private static final String PARTENAIRE = annuaire("Partenaire", "ANNUAIRE_PARTENAIRE", "partenaire.ca", "[]", "employee");

    private int audits(String action) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = ?").params(action).query(Integer.class).single();
    }

    /** Part vers l'annuaire, rend le state de l'URL d'autorisation. */
    private String partir(String id) throws Exception {
        MvcResult r = http.get("/api/auth/sso/annuaires/" + id + "/login", null).andExpect(status().isFound()).andReturn();
        String url = r.getResponse().getRedirectedUrl();
        assertThat(url).startsWith("https://login.microsoftonline.com/" + GUID + "/oauth2/v2.0/authorize?client_id=" + CLIENT)
                .contains("redirect_uri=https%3A%2F%2Fapp.nexu.ca%2Fapi%2Fauth%2Fsso%2Fannuaires%2Fcallback").contains("code_challenge_method=S256");
        return URI.create(url).getQuery().replaceAll(".*state=([^&]*).*", "$1");
    }

    @Test
    @DisplayName("l'administrateur déclare, lit, modifie et retire ses annuaires ; le secret n'est jamais rendu")
    void administration() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ann-1")).andExpect(jsonPath("$.secretDeploye").value(true))
                .andExpect(jsonPath("$.correspondances[1].role").value("manager")).andExpect(jsonPath("$.roleParDefaut").doesNotExist());
        http.post("/api/auth/annuaires", ADMIN_T1, PARTENAIRE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.secretDeploye").value(false)).andExpect(jsonPath("$.roleParDefaut").value("employee"));
        http.get("/api/auth/annuaires", ADMIN_T1).andExpect(status().isOk())
                .andExpect(jsonPath("$.annuaires.length()").value(2)).andExpect(jsonPath("$.annuaires[0].nom").value("Partenaire"))
                .andExpect(jsonPath("$.rappel").value("https://app.nexu.ca/api/auth/sso/annuaires/callback"))
                .andExpect(content -> assertThat(content.getResponse().getContentAsString()).doesNotContain("le-secret"));
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("un annuaire de l'organisme porte déjà le nom « Siège »"));
        http.put("/api/auth/annuaires/ann-2", ADMIN_T1, PARTENAIRE.replace("}", ",\"actif\":false}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.actif").value(false));
        // Un autre organisme ne voit, ne modifie ni ne retire rien de T1.
        http.get("/api/auth/annuaires", ADMIN_T2).andExpect(jsonPath("$.annuaires.length()").value(0));
        http.put("/api/auth/annuaires/ann-1", ADMIN_T2, SIEGE).andExpect(status().isNotFound());
        http.delete("/api/auth/annuaires/ann-1", ADMIN_T2).andExpect(status().isNotFound());
        http.delete("/api/auth/annuaires/ann-2", ADMIN_T1).andExpect(status().isNoContent());
        http.get("/api/auth/annuaires", ADMIN_T1).andExpect(jsonPath("$.annuaires.length()").value(1));
    }

    @Test
    @DisplayName("réservé à l'administrateur ; un corps mal typé ou invalide est refusé avec chaque motif")
    void gardesEtFrontiere() throws Exception {
        http.get("/api/auth/annuaires", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/annuaires", ClientHttpEssai.session("manager", "T1")).andExpect(status().isForbidden());
        http.post("/api/auth/annuaires", ADMIN_T1, "{\"nom\":3,\"domaines\":\"a.ca\",\"correspondances\":[{\"groupe\":1}],\"actif\":\"oui\",\"roleParDefaut\":\"employee\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", org.hamcrest.Matchers.contains("« nom » doit être un texte", "« domaines » doit être une liste de textes",
                        "« correspondances » doit être une liste de { groupe, role } textuels", "« actif » doit être vrai ou faux")));
        http.post("/api/auth/annuaires", ADMIN_T1, "{\"domaines\":[1],\"correspondances\":\"x\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("« domaines » doit être une liste de textes"));
        http.post("/api/auth/annuaires", ADMIN_T1, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value("le nom est requis (80 caractères au plus)"));
        http.envoyer(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/annuaires"), ADMIN_T1)
                .andExpect(status().isBadRequest());
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire("Siège", "ANNUAIRE_SIEGE", "urgences-sante.qc.ca", "[]", "superadmin"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("rôle par défaut non attribuable : « superadmin »"));
    }

    @Test
    @DisplayName("la page de connexion propose les annuaires actifs de l'organisme : de l'hôte d'abord, du paramètre sinon")
    void listeDeConnexion() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isCreated());
        http.post("/api/auth/annuaires", ADMIN_T1, PARTENAIRE.replace("}", ",\"actif\":false}")).andExpect(status().isCreated());
        http.get("/api/auth/sso/annuaires?tenantId=T1", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.annuaires.length()").value(1)).andExpect(jsonPath("$.annuaires[0].nom").value("Siège"))
                .andExpect(jsonPath("$.annuaires[0].clientId").doesNotExist());
        http.get("/api/auth/sso/annuaires", null).andExpect(jsonPath("$.annuaires.length()").value(0));
        http.get("/api/auth/sso/annuaires?tenantId=", null).andExpect(jsonPath("$.annuaires.length()").value(0));
        tenantDeLHote = "T2";
        http.get("/api/auth/sso/annuaires?tenantId=T1", null).andExpect(jsonPath("$.annuaires.length()").value(0));
    }

    @Test
    @DisplayName("aller-retour : compte créé dans l'organisme avec le rôle du premier groupe déclaré, session posée, puis rôle suivi")
    void allerRetour() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isCreated());
        revendications.set(Map.of("email", "julie@urgences-sante.qc.ca", "groups", List.of("g-gest", "g-admin")));
        http.get("/api/auth/sso/annuaires/callback?code=c1&state=" + partir("ann-1"), null).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://app.nexu.ca"))
                .andExpect(header().stringValues("Set-Cookie", org.hamcrest.Matchers.hasItem(containsString("HttpOnly"))));
        assertThat(configurationRecue.get().clientSecret()).isEqualTo("le-secret");
        Compte julie = comptes.lister().stream().filter(c -> "T1".equals(c.tenantId())).findFirst().orElseThrow();
        assertThat(julie.role()).isEqualTo("admin");
        assertThat(julie.email()).isEqualTo("julie@urgences-sante.qc.ca");
        assertThat(audits("auth.sso.login.ok")).isEqualTo(1);
        // Retirée du groupe des administrateurs dans l'annuaire : elle revient gestionnaire, sans compte de plus.
        revendications.set(Map.of("email", "JULIE@urgences-sante.qc.ca", "groups", List.of("g-gest")));
        http.get("/api/auth/sso/annuaires/callback?code=c2&state=" + partir("ann-1"), null).andExpect(status().isFound());
        assertThat(comptes.parId(julie.id()).orElseThrow().role()).isEqualTo("manager");
        // Même rôle : le compte n'est pas réécrit.
        http.get("/api/auth/sso/annuaires/callback?code=c3&state=" + partir("ann-1"), null).andExpect(status().isFound());
        assertThat(comptes.lister().stream().filter(c -> "T1".equals(c.tenantId())).count()).isEqualTo(1);
        // Parti avant que l'adresse publique ne disparaisse : le retour mène au tableau de bord.
        String state = partir("ann-1");
        adresse = "";
        http.get("/api/auth/sso/annuaires/callback?code=c4&state=" + state, null).andExpect(status().isFound())
                .andExpect(header().string("Location", "/admin/dashboard"));
        http.get("/api/auth/sso/annuaires/ann-1/login", null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0]").value("l'adresse publique de l'application n'est pas déclarée (PUBLIC_APP_URL)"));
    }

    @Test
    @DisplayName("refus consignés : domaine non admis, aucun groupe, compte désactivé, code refusé, state inconnu, secret absent")
    void refus() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isCreated());
        revendications.set(Map.of("email", "invite@gmail.com", "groups", List.of("g-admin")));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + partir("ann-1"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le domaine de « invite@gmail.com » n'est pas admis par l'annuaire « Siège »"));
        revendications.set(Map.of("email", "paul@urgences-sante.qc.ca", "groups", List.of("autre")));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + partir("ann-1"), null).andExpect(status().isForbidden());
        Compte paul = comptes.creer(new DepotComptes.Creation("paul@urgences-sante.qc.ca", "Passe!Test2026", "employee", null, "T1", false, false, "paul@urgences-sante.qc.ca", false));
        comptes.modifier(paul.id(), new DepotComptes.Modification(null, false, null, null, null, null, null));
        revendications.set(Map.of("email", "paul@urgences-sante.qc.ca", "groups", List.of("g-admin")));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + partir("ann-1"), null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le compte « paul@urgences-sante.qc.ca » est désactivé dans l'application"));
        assertThat(comptes.parId(paul.id()).orElseThrow().role()).isEqualTo("employee");
        http.get("/api/auth/sso/annuaires/callback?code=refuse&state=" + partir("ann-1"), null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errors[0]").value("l'annuaire n'a pas authentifié la connexion : Entra : invalid_grant — code expiré"));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=rejoue", null).andExpect(status().isNotFound());
        http.get("/api/auth/sso/annuaires/callback", null).andExpect(status().isNotFound());
        http.get("/api/auth/sso/annuaires/inconnu/login", null).andExpect(status().isNotFound());
        String state = partir("ann-1");
        env.remove("ANNUAIRE_SIEGE");
        http.get("/api/auth/sso/annuaires/ann-1/login", null).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.errors[0]").value("le secret de l'annuaire n'est pas déployé (variable ANNUAIRE_SIEGE)"));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + state, null).andExpect(status().isServiceUnavailable());
        assertThat(audits("auth.sso.annuaire.refus")).isEqualTo(7);
        assertThat(audits("auth.sso.login.ok")).isZero();
    }

    @Test
    @DisplayName("un annuaire désactivé entre le départ et le retour n'ouvre plus de session")
    void desactiveEnCoursDeRoute() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, SIEGE).andExpect(status().isCreated());
        String state = partir("ann-1");
        http.put("/api/auth/annuaires/ann-1", ADMIN_T1, SIEGE.replace("}", ",\"actif\":false}")).andExpect(status().isOk());
        revendications.set(Map.of("email", "julie@urgences-sante.qc.ca", "groups", List.of("g-admin")));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + state, null).andExpect(status().isNotFound());
        http.get("/api/auth/sso/annuaires/ann-1/login", null).andExpect(status().isNotFound());
    }
}
