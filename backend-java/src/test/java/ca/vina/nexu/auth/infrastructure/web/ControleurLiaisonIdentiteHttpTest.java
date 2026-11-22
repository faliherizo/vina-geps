package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.application.ServiceAnnuaires;
import ca.vina.nexu.auth.application.ServiceApprobationsLiaison;
import ca.vina.nexu.auth.application.ServiceResolutionIdentite;
import ca.vina.nexu.auth.domaine.Annuaires;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.IdentitesExternes;
import ca.vina.nexu.auth.domaine.Invitations;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesAnnuaire;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurInvitationsLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotAnnuaires;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotDemandesLiaison;
import ca.vina.nexu.auth.infrastructure.persistance.DepotIdentitesExternes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotInvitations;
import ca.vina.nexu.auth.infrastructure.persistance.DepotQuestionsSecretes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.infrastructure.services.ServiceConnexion;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.crypto.Primitives;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.HoteEtablissement;
import ca.vina.nexu.partage.securite.JetonDeSession;
import ca.vina.nexu.partage.securite.ResolutionHote;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.PareFeu;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La première connexion de Jean par son annuaire (ADR 0093), de bout en bout sur SQLite : l'annuaire l'authentifie,
 * {@code (iss, sub)} n'est relié à rien, <strong>aucun second compte</strong> ne naît, le courriel ne sert que
 * d'indice ; Jean prouve la possession de son compte (mot de passe, invitation ou approbation d'un administrateur),
 * l'identité est reliée, et la connexion suivante entre directement. Seul l'échange du code est remplacé.
 */
@DisplayName("« Relier mon compte » — résolution par (iss, sub), preuves de possession, approbation par l'administration")
class ControleurLiaisonIdentiteHttpTest {

    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC);
    private static final String GUID = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CLIENT = "7c9e6679-7425-40de-944b-e07fc1f90ae7";
    private static final String EMETTEUR = "https://login.microsoftonline.com/" + GUID + "/v2.0";
    private static final ContexteRequete ADMIN_T1 = ClientHttpEssai.session("admin", "T1");
    private static final ContexteRequete ADMIN_T2 = ClientHttpEssai.session("admin", "T2");
    private static final String MOT_DE_PASSE = "Passe!Jean2031";
    private static final String DOMAINE = "urgences-sante.qc.ca";

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private DepotComptes comptes;
    private DepotInvitations invitations;
    private final Map<String, String> env = new HashMap<>();
    private final AtomicReference<Map<String, Object>> revendications = new AtomicReference<>();

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
        invitations = new DepotInvitations(jdbc);
        RegistrePolitiqueAcces acces = new RegistrePolitiqueAcces();
        DepotAnnuaires depot = new DepotAnnuaires(jdbc, HORLOGE);
        ServiceConnexion connexion = new ServiceConnexion(comptes, new DepotQuestionsSecretes(jdbc, HORLOGE), new JetonDeSession("secret-d-essai-de-trente-deux-octets-au-moins"), HORLOGE);
        Random alea = new Random(11);
        DepotIdentitesExternes identites = new DepotIdentitesExternes(jdbc);
        DepotDemandesLiaison demandes = new DepotDemandesLiaison(jdbc);
        ServiceResolutionIdentite.Ports ports = new ServiceResolutionIdentite.Ports(identites, identites, new AdaptateurComptesAnnuaire(comptes, alea),
                new AdaptateurInvitationsLiaison(invitations, comptes, HORLOGE, alea), demandes, demandes);
        ServiceResolutionIdentite resolution = new ServiceResolutionIdentite(ports, HORLOGE, alea);
        ServiceAnnuaires service = new ServiceAnnuaires(new ServiceAnnuaires.Ports(depot, resolution, connexion::emettre,
                (code, cfg, demande) -> revendications.get(),
                emetteur -> new Annuaires.Decouverte.Points(emetteur + "/auth", emetteur + "/token"),
                (annuaire, secret, identifiant, motDePasse) -> Optional.of(new Annuaires.Personne(identifiant + "@" + DOMAINE, List.of("g-gest")))),
                env, HORLOGE, alea);
        HoteEtablissement hote = new HoteEtablissement() {
            @Override
            public ResolutionHote.Configuration configuration() {
                return ResolutionHote.Configuration.depuis(null, null, null);
            }

            @Override
            public Resolution de(HttpServletRequest requete) {
                return new Resolution.Selecteur();
            }
        };
        JournalAudit audit = new JournalAudit(jdbc);
        GardeAcces garde = new GardeAcces(acces);
        PareFeu pareFeu = new PareFeu(new PareFeu.Reglages(PareFeu.Mode.BLOQUER, 0, 3, 15), HORLOGE::millis, (a, d) -> { });
        AdressePublique adresse = AdressePublique.fixe("https://app.nexu.ca");
        http = new ClientHttpEssai(
                new ControleurAnnuaires(new ControleurAnnuaires.Dependances(service, depot, depot, new DepotRolesEtablissement(jdbc, acces, HORLOGE), garde, hote,
                        adresse, audit, sequence(), env, true, pareFeu)),
                new ControleurLiaisonIdentite(new ControleurLiaisonIdentite.Dependances(resolution, new ServiceApprobationsLiaison(ports, HORLOGE), connexion,
                        audit, pareFeu, garde, adresse, true)));
        env.put("ANNUAIRE_SIEGE", "le-secret");
    }

    private static String annuaire(boolean jat) {
        return "{\"nom\":\"Siège\",\"locataireEntra\":\"" + GUID + "\",\"clientId\":\"" + CLIENT + "\",\"variableSecret\":\"ANNUAIRE_SIEGE\","
                + "\"domaines\":[\"" + DOMAINE + "\"],\"correspondances\":[{\"groupe\":\"g-gest\",\"role\":\"manager\"}],\"provisionnementJat\":" + jat + "}";
    }

    private Compte compte(String nom, String tenant) {
        return comptes.creer(new DepotComptes.Creation(nom, MOT_DE_PASSE, "employee", null, tenant, false, false, nom + "@" + DOMAINE, false));
    }

    private int audits(String action) {
        return jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE action = ?").params(action).query(Integer.class).single();
    }

    private int identites() {
        return jdbc.sql("SELECT COUNT(*) FROM auth_identites_externes").query(Integer.class).single();
    }

    /** L'aller-retour par l'annuaire : départ, puis retour avec ces revendications (iss, sub, courriel, groupes). */
    private ResultActions connecter(String sujet, String courriel) throws Exception {
        String url = http.get("/api/auth/sso/annuaires/ann-1/login", null).andExpect(status().isFound()).andReturn().getResponse().getRedirectedUrl();
        String state = URI.create(url).getQuery().replaceAll(".*state=([^&]*).*", "$1");
        revendications.set(Map.of("iss", EMETTEUR, "sub", sujet, "email", courriel, "groups", List.of("g-gest")));
        return http.get("/api/auth/sso/annuaires/callback?code=c&state=" + state, null);
    }

    /** Le témoin de liaison posé par le retour. */
    private Cookie liaison(ResultActions retour) throws Exception {
        String brut = retour.andExpect(status().isFound()).andExpect(header().string("Location", "/connexion/relier")).andReturn().getResponse()
                .getHeader("Set-Cookie");
        assertThat(brut).startsWith("liaison_sso=").contains("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/auth/sso/liaison", "Max-Age=600");
        return new Cookie("liaison_sso", brut.substring("liaison_sso=".length(), brut.indexOf(';')));
    }

    private ResultActions poster(String chemin, Cookie c, String json) throws Exception {
        var b = MockMvcRequestBuilders.post(chemin).cookie(c);
        if (json != null) {
            b.contentType("application/json").content(json);
        }
        return http.envoyer(b, null);
    }

    private ResultActions etat(Cookie c) throws Exception {
        return http.envoyer(MockMvcRequestBuilders.get("/api/auth/sso/liaison").cookie(c), null);
    }

    @Test
    @DisplayName("Jean : aucune identité reliée → aucun compte créé, « relier » ; mot de passe juste → relié, session ; la connexion suivante entre directement")
    void parMotDePasse() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        Compte jean = compte("jean", "T1");
        Cookie c = liaison(connecter("sub-jean", "jean@" + DOMAINE));
        assertThat(comptes.lister()).hasSize(1);
        assertThat(identites()).isZero();
        assertThat(audits("auth.sso.liaison.requise")).isOne();
        assertThat(jdbc.sql("SELECT username FROM audit_log WHERE action = 'auth.sso.liaison.requise'").query(String.class).single()).isEqualTo("j***@" + DOMAINE);
        etat(c).andExpect(status().isOk()).andExpect(jsonPath("$.indice").value("j***@" + DOMAINE)).andExpect(jsonPath("$.candidat").value(true))
                .andExpect(jsonPath("$.demandeEnAttente").value(false)).andExpect(jsonPath("$.essaisRestants").value(5));

        poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"motDePasse\":\"mauvais\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("la preuve ne permet pas de relier ce compte"));
        etat(c).andExpect(jsonPath("$.essaisRestants").value(4));
        poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"identifiant\":\"jean\"}").andExpect(status().isBadRequest());
        poster("/api/auth/sso/liaison/mot-de-passe", c, null).andExpect(status().isBadRequest());
        MvcResult ok = poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"identifiant\":\" jean \",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.retour").value("https://app.nexu.ca")).andExpect(jsonPath("$.role").value("manager"))
                .andExpect(jsonPath("$.preuve").value("mot-de-passe"))
                .andExpect(header().stringValues("Set-Cookie", hasItem(allOf(containsString("session="), containsString("HttpOnly"), containsString("Secure"),
                        containsString("SameSite=Lax"))))).andReturn();
        assertThat(ok.getResponse().getHeaders("Set-Cookie")).anySatisfy(v -> assertThat(v).startsWith("liaison_sso=;").contains("Max-Age=0"));
        assertThat(jdbc.sql("SELECT userId || '|' || tenantId || '|' || annuaireId || '|' || preuve || '|' || lieePar FROM auth_identites_externes")
                .query(String.class).single()).isEqualTo(jean.id() + "|T1|ann-1|mot-de-passe|" + jean.id());
        assertThat(comptes.parId(jean.id()).orElseThrow().role()).isEqualTo("manager");
        assertThat(audits("auth.sso.lie")).isOne();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_log WHERE details LIKE ?").params("%" + MOT_DE_PASSE + "%").query(Integer.class).single()).isZero();
        // La liaison est consommée.
        etat(c).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value(containsString("aucune liaison en cours")));

        // Seconde connexion : (iss, sub) trouvé — droit dans l'application, même si le courriel a changé chez le fournisseur.
        connecter("sub-jean", "jean.tremblay@" + DOMAINE).andExpect(status().isFound()).andExpect(header().string("Location", "https://app.nexu.ca"));
        assertThat(comptes.lister()).hasSize(1);
        assertThat(jdbc.sql("SELECT derniereConnexion FROM auth_identites_externes").query(String.class).single()).isEqualTo("2031-01-15T10:00:00.000Z");
    }

    @Test
    @DisplayName("refus : identité inconnue sans compte, mot de passe d'un autre organisme, compte désactivé, essais épuisés, aucune liaison")
    void refus() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        compte("marc", "T2");
        // Un inconnu : rien n'est créé, et la demande à l'administration n'a pas de compte candidat.
        Cookie inconnu = liaison(connecter("sub-inconnu", "inconnu@" + DOMAINE));
        etat(inconnu).andExpect(jsonPath("$.candidat").value(false));
        poster("/api/auth/sso/liaison/demande", inconnu, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value(containsString("aucun compte de l'organisme ne porte ce courriel")));
        assertThat(comptes.lister()).hasSize(1);
        // Le mot de passe juste d'un compte d'un autre organisme ne relie rien dans celui-ci.
        Cookie marc = liaison(connecter("sub-marc", "marc@" + DOMAINE));
        etat(marc).andExpect(jsonPath("$.candidat").value(false));
        poster("/api/auth/sso/liaison/mot-de-passe", marc, "{\"identifiant\":\"marc\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}").andExpect(status().isForbidden());
        // Un compte désactivé n'est ni authentifié ni proposé à l'approbation.
        Compte paul = compte("paul", "T1");
        comptes.modifier(paul.id(), new DepotComptes.Modification(null, false, null, null, null, null, null));
        Cookie p = liaison(connecter("sub-paul", "paul@" + DOMAINE));
        poster("/api/auth/sso/liaison/mot-de-passe", p, "{\"identifiant\":\"paul\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}").andExpect(status().isForbidden());
        poster("/api/auth/sso/liaison/demande", p, null).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le compte de l'application est désactivé"));
        // Cinq essais au plus par liaison : le sixième la referme.
        Cookie e = liaison(connecter("sub-essais", "essais@" + DOMAINE));
        for (int i = 0; i < 5; i++) {
            poster("/api/auth/sso/liaison/invitation", e, "{\"invitation\":\"faux-" + i + "\"}").andExpect(status().isForbidden());
        }
        poster("/api/auth/sso/liaison/invitation", e, "{\"invitation\":\"encore\"}").andExpect(status().isTooManyRequests());
        poster("/api/auth/sso/liaison/invitation", e, "{\"invitation\":\"encore\"}").andExpect(status().isNotFound());
        poster("/api/auth/sso/liaison/invitation", e, "{\"invitation\":\" \"}").andExpect(status().isBadRequest());
        // Sans témoin : aucune liaison.
        http.get("/api/auth/sso/liaison", null).andExpect(status().isNotFound());
        http.post("/api/auth/sso/liaison/demande", null, null).andExpect(status().isNotFound());
        assertThat(identites()).isZero();
        assertThat(comptes.lister()).hasSize(2);
        assertThat(audits("auth.sso.refus")).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("le verrou du pare-feu s'applique à la preuve par mot de passe, par identifiant")
    void verrou() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        compte("jean", "T1");
        Cookie c = liaison(connecter("sub-jean", "jean@" + DOMAINE));
        for (int i = 0; i < 3; i++) {
            poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"identifiant\":\"jean\",\"motDePasse\":\"x\"}").andExpect(status().isForbidden());
        }
        poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"identifiant\":\"jean\",\"motDePasse\":\"" + MOT_DE_PASSE + "\"}").andExpect(status().isTooManyRequests());
        // Sans identifiant saisi, le courriel vérifié en tient lieu — sous une autre clé de verrou.
        poster("/api/auth/sso/liaison/mot-de-passe", c, "{\"motDePasse\":\"" + MOT_DE_PASSE + "\"}").andExpect(status().isOk());
    }

    @Test
    @DisplayName("invitation : le lien reçu par courriel relie l'identité au compte invité, qui naît ; un lien d'un autre courriel ne prouve rien")
    void parInvitation() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        inviter("i1", "nadia@" + DOMAINE, "jeton-nadia-0123456789", "rh");
        inviter("i2", "autre@" + DOMAINE, "jeton-autre-0123456789", "rh");
        Cookie c = liaison(connecter("sub-nadia", "nadia@" + DOMAINE));
        poster("/api/auth/sso/liaison/invitation", c, "{\"invitation\":\"https://app.nexu.ca/admin/invitation/jeton-autre-0123456789\"}").andExpect(status().isForbidden());
        poster("/api/auth/sso/liaison/invitation", c, "{\"invitation\":\"https://app.nexu.ca/admin/invitation/jeton-nadia-0123456789?x=1\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.preuve").value("invitation")).andExpect(jsonPath("$.role").value("manager"));
        Compte nadia = comptes.lister().stream().filter(x -> ("nadia@" + DOMAINE).equals(x.email())).findFirst().orElseThrow();
        assertThat(List.of(nadia.nomDeConnexion(), nadia.tenantId())).containsExactly("nadia", "T1");
        assertThat(invitations.lire("T1", "i1").orElseThrow().accepteeLe()).isNotNull();
        assertThat(invitations.lire("T1", "i2").orElseThrow().accepteeLe()).isNull();
        connecter("sub-nadia", "nadia@" + DOMAINE).andExpect(status().isFound()).andExpect(header().string("Location", "https://app.nexu.ca"));
    }

    private void inviter(String id, String courriel, String jeton, String role) {
        invitations.poser(new Invitations.Invitation(id, "T1", courriel, courriel.substring(0, courriel.indexOf('@')), role, null, "2031-02-01T00:00:00.000Z",
                null, null, "2031-01-10T00:00:00.000Z", "usr-admin", null), HexFormat.of().formatHex(Primitives.sha256(jeton.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("approbation : la demande attend l'administrateur de l'organisme, qui seul la voit ; approuvée, l'identité est reliée ; refusée, rien")
    void parApprobation() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        Compte jean = compte("jean", "T1");
        compte("lea", "T1");
        Cookie c = liaison(connecter("sub-jean", "jean@" + DOMAINE));
        String id = poster("/api/auth/sso/liaison/demande", c, null).andExpect(status().isAccepted()).andExpect(jsonPath("$.message").value(containsString("transmise")))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"demandeId\":\"([^\"]+)\".*", "$1");
        poster("/api/auth/sso/liaison/demande", c, null).andExpect(jsonPath("$.demandeId").value(id));
        etat(c).andExpect(jsonPath("$.demandeEnAttente").value(true));
        assertThat(identites()).isZero();

        http.get("/api/auth/liaisons", null).andExpect(status().isUnauthorized());
        http.get("/api/auth/liaisons", ClientHttpEssai.session("manager", "T1")).andExpect(status().isForbidden());
        http.get("/api/auth/liaisons", ADMIN_T2).andExpect(jsonPath("$.demandes.length()").value(0));
        http.post("/api/auth/liaisons/" + id + "/approuver", ADMIN_T2, null).andExpect(status().isNotFound());
        http.get("/api/auth/liaisons", ADMIN_T1).andExpect(status().isOk()).andExpect(jsonPath("$.demandes[0].id").value(id))
                .andExpect(jsonPath("$.demandes[0].courriel").value("jean@" + DOMAINE)).andExpect(jsonPath("$.demandes[0].compteId").value(jean.id()))
                .andExpect(jsonPath("$.demandes[0].emetteur").value(EMETTEUR)).andExpect(jsonPath("$.demandes[0].statut").value("en_attente"));
        http.post("/api/auth/liaisons/" + id + "/approuver", ADMIN_T1, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("approuvee"));
        http.post("/api/auth/liaisons/" + id + "/refuser", ADMIN_T1, null).andExpect(status().isNotFound());
        assertThat(jdbc.sql("SELECT preuve || '|' || lieePar || '|' || userId FROM auth_identites_externes").query(String.class).single())
                .isEqualTo("approbation|usr-admin|" + jean.id());
        assertThat(audits("auth.sso.liaison.approuvee")).isOne();
        connecter("sub-jean", "jean@" + DOMAINE).andExpect(status().isFound()).andExpect(header().string("Location", "https://app.nexu.ca"));

        // Léa : refusée, rien n'est relié ; sa connexion suivante redemande la liaison.
        Cookie l = liaison(connecter("sub-lea", "lea@" + DOMAINE));
        String idLea = poster("/api/auth/sso/liaison/demande", l, null).andReturn().getResponse().getContentAsString().replaceAll(".*\"demandeId\":\"([^\"]+)\".*", "$1");
        http.post("/api/auth/liaisons/" + idLea + "/refuser", ADMIN_T1, null).andExpect(status().isOk()).andExpect(jsonPath("$.statut").value("refusee"));
        http.post("/api/auth/liaisons/" + idLea + "/approuver", ADMIN_T1, null).andExpect(status().isNotFound());
        assertThat(audits("auth.sso.liaison.refusee")).isOne();
        liaison(connecter("sub-lea", "lea@" + DOMAINE));
        assertThat(identites()).isOne();
    }

    @Test
    @DisplayName("organisme : une identité reliée à un compte d'un autre organisme que l'annuaire est refusée")
    void autreOrganisme() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(false)).andExpect(status().isCreated());
        Compte marc = compte("marc", "T2");
        new DepotIdentitesExternes(jdbc).lier(new IdentitesExternes.Identite(IdentitesExternes.Cle.de(EMETTEUR, "sub-marc"), marc.id(),
                IdentitesExternes.Provenance.annuaire("ann-x", "T2"), HORLOGE.instant(), "x", IdentitesExternes.Preuve.APPROBATION, Optional.empty()));
        connecter("sub-marc", "marc@" + DOMAINE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le compte n'appartient pas à l'organisme de cet annuaire"));
        // Le compte relié a disparu : refus nommé, pas de second compte.
        jdbc.sql("DELETE FROM users WHERE id = ?").params(marc.id()).update();
        connecter("sub-marc", "marc@" + DOMAINE).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("le compte relié à cette identité n'existe plus"));
        // Un jeton sans sujet n'identifie personne.
        String url = http.get("/api/auth/sso/annuaires/ann-1/login", null).andReturn().getResponse().getRedirectedUrl();
        revendications.set(Map.of("iss", EMETTEUR, "email", "x@" + DOMAINE, "groups", List.of("g-gest")));
        http.get("/api/auth/sso/annuaires/callback?code=c&state=" + URI.create(url).getQuery().replaceAll(".*state=([^&]*).*", "$1"), null)
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errors[0]").value(containsString("sujet")));
        assertThat(comptes.lister()).isEmpty();
    }

    @Test
    @DisplayName("juste-à-temps, sur option de l'annuaire : l'inconnu naît relié ; un courriel déjà porté exige quand même la preuve")
    void justeATemps() throws Exception {
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(true)).andExpect(status().isCreated()).andExpect(jsonPath("$.provisionnementJat").value(true));
        connecter("sub-neuf", "neuf@" + DOMAINE).andExpect(status().isFound()).andExpect(header().string("Location", "https://app.nexu.ca"));
        assertThat(jdbc.sql("SELECT preuve || '|' || lieePar FROM auth_identites_externes").query(String.class).single()).isEqualTo("provisionnement-jat|systeme");
        compte("jean", "T1");
        liaison(connecter("sub-jean", "jean@" + DOMAINE));
        assertThat(comptes.lister()).hasSize(2);
        http.post("/api/auth/annuaires", ADMIN_T1, annuaire(true).replace("\"provisionnementJat\":true", "\"provisionnementJat\":\"oui\""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").value("« provisionnementJat » doit être vrai ou faux"));
    }

    @Test
    @DisplayName("LDAPS : la connexion par identifiant et mot de passe rend la liaison en JSON quand l'identité n'est reliée à rien")
    void ldapsEnJson() throws Exception {
        env.put("ANNUAIRE_AD", "s");
        http.post("/api/auth/annuaires", ADMIN_T1, "{\"nom\":\"AD\",\"fournisseur\":\"ldaps\",\"emetteur\":\"ldaps://ad.us.local:636/OU=P,DC=us,DC=local\","
                + "\"clientId\":\"CN=svc,DC=us,DC=local\",\"variableSecret\":\"ANNUAIRE_AD\",\"domaines\":[\"" + DOMAINE + "\"],"
                + "\"correspondances\":[{\"groupe\":\"g-gest\",\"role\":\"manager\"}]}").andExpect(status().isCreated());
        compte("jean", "T1");
        http.post("/api/auth/sso/annuaires/ann-1/connexion", null, "{\"identifiant\":\"Jean\",\"motDePasse\":\"ad\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.liaisonRequise").value(true)).andExpect(jsonPath("$.retour").value("/connexion/relier"))
                .andExpect(header().string("Set-Cookie", containsString("liaison_sso=")));
        assertThat(comptes.lister()).hasSize(1);
    }
}
