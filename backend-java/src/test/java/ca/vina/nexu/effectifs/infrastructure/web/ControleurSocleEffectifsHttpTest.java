package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireMilieux;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePerimetres;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureJournalEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurComptesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.AdaptateurModulesPourEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotJournalEffectifs;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotMilieux;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotPerimetres;
import ca.vina.nexu.effectifs.infrastructure.persistance.DepotReferentiels;
import ca.vina.nexu.effectifs.infrastructure.persistance.SchemaEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * <strong>Le socle des effectifs par HTTP</strong> (lot R13, jumeau de {@code effectifs-socle.integration.test.ts}) :
 * mêmes chemins, mêmes codes, mêmes messages que le Node, sur une vraie base SQLite, horloge figée au
 * 15 janvier 2031.
 */
@DisplayName("/api/effectifs — socle (référentiels, périmètres, milieux, journal)")
class ControleurSocleEffectifsHttpTest {

    private static final String T = "t-1";
    private static final String E = "/api/effectifs";
    private static final ContexteRequete ADMIN = ClientHttpEssai.session("admin", T);
    private static final ContexteRequete CHEF = new ContexteRequete("usr-chef", "chef", "manager", null, T);
    private static final ContexteRequete JULIE = new ContexteRequete("usr-julie", "julie", "employee", null, T);

    private JdbcClient jdbc;
    private ClientHttpEssai http;
    private LectureMilieux lectureMilieux;
    private DepotMilieux depotMilieux;
    private GardeEffectifs gardeEffectifs;

    /** Une séquence déterministe : les identifiants se lisent dans les essais. */
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
                return "secret-" + octets;
            }
        };
    }

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        jdbc.sql("INSERT INTO tenant_quotas (tenantId, maxTeachers, maxContractsPerYear, modules) VALUES (?, -1, -1, ?)").params(T, "[\"personnel\",\"effectifs\"]").update();
        for (String[] u : new String[][]{{"usr-chef", "chef", T}, {"usr-autre", "autre", "t-2"}}) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?, ?, ?, 'x', 'manager', 'x')").params(u[0], u[1], u[2]).update();
        }
        SchemaEffectifs schema = new SchemaEffectifs(jdbc);
        Sequence seq = sequence();
        DepotReferentiels depotRef = new DepotReferentiels(jdbc, schema);
        DepotPerimetres depotPer = new DepotPerimetres(jdbc, schema);
        DepotMilieux depotMil = new DepotMilieux(jdbc, schema);
        DepotJournalEffectifs journal = new DepotJournalEffectifs(jdbc, seq, schema);
        LectureReferentiels referentiels = new LectureReferentiels(depotRef);
        LecturePerimetres perimetres = new LecturePerimetres(depotPer, referentiels);
        LectureMilieux milieux = new LectureMilieux(depotMil, perimetres);
        lectureMilieux = milieux;
        depotMilieux = depotMil;
        GardeEffectifs garde = new GardeEffectifs(new GardeAcces(new RegistrePolitiqueAcces()), new AdaptateurModulesPourEffectifs(jdbc),
                Clock.fixed(Instant.parse("2031-01-15T10:00:00Z"), ZoneOffset.UTC));
        gardeEffectifs = garde;
        http = new ClientHttpEssai(new ControleurSocleEffectifs(garde, referentiels, new GestionnaireReferentiels(depotRef, referentiels, journal, seq), perimetres,
                new GestionnairePerimetres(depotPer, referentiels, new AdaptateurComptesPourEffectifs(jdbc), journal, seq), milieux,
                new GestionnaireMilieux(depotMil, milieux, referentiels, perimetres, journal, seq), new LectureJournalEffectifs(journal, perimetres)));
    }

    private void ref(String type, String json) throws Exception {
        http.post(E + "/referentiels/" + type, ADMIN, json).andExpect(status().isCreated());
    }

    private void socle() throws Exception {
        ref("unites", "{\"code\":\"US\",\"libelleFr\":\"Urgences-santé\",\"effectifDepuis\":\"2026-01-01\"}");
        ref("unites", "{\"code\":\"EST\",\"libelleFr\":\"Est\",\"effectifDepuis\":\"2026-01-01\",\"attributs\":{\"parent\":\"US\"}}");
        ref("unites", "{\"code\":\"OUEST\",\"libelleFr\":\"Ouest\",\"effectifDepuis\":\"2026-01-01\",\"attributs\":{\"parent\":\"US\"}}");
        ref("types_milieu", "{\"code\":\"CASERNE\",\"libelleFr\":\"Caserne\",\"comportement\":\"CASERNE\",\"effectifDepuis\":\"2026-01-01\"}");
    }

    @Test
    @DisplayName("les référentiels : registre, création, doublon, version datée, fermeture, historique")
    void referentiels() throws Exception {
        // L'écran d'administration des référentiels est réservé à l'administration : registre, historique, versions.
        http.get(E + "/referentiels", JULIE).andExpect(status().isForbidden());
        http.get(E + "/referentiels", CHEF).andExpect(status().isForbidden());
        http.get(E + "/referentiels", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$[0].type").value("unites"))
                .andExpect(jsonPath("$[0].attributs[0].type").value("reference")).andExpect(jsonPath("$[0].attributs[0].reference").value("unites"))
                .andExpect(jsonPath("$[3].comportements[0]").value("TEMPS_COMPLET")).andExpect(jsonPath("$[6].attributs[0].min").value(0));
        socle();
        http.post(E + "/referentiels/unites", JULIE, "{\"code\":\"X\",\"libelleFr\":\"X\"}").andExpect(status().isForbidden());
        http.post(E + "/referentiels/unites", ADMIN, "{\"code\":\"EST\",\"libelleFr\":\"Est\"}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0]").value("Unités organisationnelles : « EST » existe déjà"));
        http.post(E + "/referentiels/unites", ADMIN, "{\"code\":\"NORD\",\"libelleFr\":\"Nord\",\"attributs\":{\"parent\":\"SUD\"}}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("Unités organisationnelles : « SUD » n'est pas en vigueur le 2031-01-15"));
        http.post(E + "/referentiels/couleurs", ADMIN, "{}").andExpect(status().is(422));
        http.post(E + "/referentiels/unites/US/versions", ADMIN, "{\"libelleFr\":\"US\",\"depuis\":\"2031-01-20\",\"attributs\":{\"parent\":\"EST\"}}")
                .andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value(Matchers.containsString("cycle")));
        http.post(E + "/referentiels/unites/EST/versions", ADMIN, "{\"libelleFr\":\"Opérations Est\",\"depuis\":\"2031-01-20\",\"attributs\":{\"parent\":\"US\"}}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.effectifDepuis").value("2031-01-20"));
        http.post(E + "/referentiels/unites/INCONNUE/versions", ADMIN, "{\"libelleFr\":\"x\",\"depuis\":\"2031-01-20\"}").andExpect(status().isNotFound());
        http.post(E + "/referentiels/unites/EST/versions", ADMIN, "{\"libelleFr\":\"x\",\"depuis\":\"2030-01-01\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("une modification ne peut pas être rétroactive (2030-01-01 < 2031-01-15)"));
        http.get(E + "/referentiels/unites?date=2031-01-25", JULIE).andExpect(jsonPath("$[?(@.code == 'EST')].libelleFr").value("Opérations Est"));
        http.get(E + "/referentiels/unites?historique=1", JULIE).andExpect(status().isForbidden());
        http.get(E + "/referentiels/unites?historique=1", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(4)));
        http.get(E + "/referentiels/unites", JULIE).andExpect(status().isOk());
        http.get(E + "/referentiels/unites?date=31-01", JULIE).andExpect(status().is(422));
        http.get(E + "/referentiels/unites/EST/historique", JULIE).andExpect(status().isForbidden());
        http.get(E + "/referentiels/unites/EST/historique", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.get(E + "/referentiels/unites/NUL/historique", ADMIN).andExpect(status().isNotFound());
        http.post(E + "/referentiels/unites/OUEST/fermer", ADMIN, "{\"date\":\"2031-02-01\"}").andExpect(status().isOk()).andExpect(jsonPath("$.effectifJusqua").value("2031-02-01"));
        http.post(E + "/referentiels/unites/NUL/fermer", ADMIN, "{\"date\":\"2031-02-01\"}").andExpect(status().isNotFound());
        http.post(E + "/referentiels/unites/OUEST/fermer", ADMIN, "{\"date\":\"2031-03-01\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("cette valeur est déjà fermée"));
        http.post(E + "/referentiels/unites", ADMIN, null).andExpect(status().is(422));
    }

    @Test
    @DisplayName("les périmètres : refus par défaut, ajout, lecture, fermeture ; un compte d'un autre établissement est inconnu")
    void perimetres() throws Exception {
        socle();
        http.get(E + "/perimetres/moi", CHEF).andExpect(jsonPath("$.toutes").value(false)).andExpect(jsonPath("$.lecture", Matchers.hasSize(0)));
        http.get(E + "/perimetres/moi", ADMIN).andExpect(jsonPath("$.toutes").value(true)).andExpect(jsonPath("$.gestion", Matchers.hasSize(3)));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-autre\",\"uniteCode\":\"EST\",\"niveau\":\"GESTION\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("utilisateur inconnu dans cet établissement"));
        http.post(E + "/perimetres", ADMIN, "{\"uniteCode\":\"EST\",\"niveau\":\"GESTION\"}").andExpect(status().is(422));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"niveau\":\"TOUT\"}").andExpect(status().is(422));
        String id = com.jayway.jsonpath.JsonPath.read(http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.etendue").value("SOUS_ARBRE")).andReturn().getResponse().getContentAsString(), "$.id");
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"OUEST\",\"niveau\":\"LECTURE\",\"etendue\":\"UNITE\"}").andExpect(status().isCreated());
        http.get(E + "/perimetres/moi", CHEF).andExpect(jsonPath("$.gestion[0]").value("EST")).andExpect(jsonPath("$.lecture", Matchers.hasSize(2)));
        http.get(E + "/perimetres?utilisateurId=usr-chef", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.get(E + "/perimetres", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(E + "/perimetres?utilisateurId=usr-chef", CHEF).andExpect(status().isForbidden());
        http.post(E + "/perimetres/" + id + "/fermer", ADMIN, "{\"date\":\"2031-02-01\"}").andExpect(status().isOk()).andExpect(jsonPath("$.jusqua").value("2031-02-01"));
        http.post(E + "/perimetres/" + id + "/fermer", ADMIN, null).andExpect(status().is(422));
        http.post(E + "/perimetres/nul/fermer", ADMIN, "{}").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("les milieux : garde de périmètre GESTION, coordonnées, exigences de preuve configurées par milieu (E-GP-12) et secret conservé ; le module coupé ferme tout")
    void milieuxEtModule() throws Exception {
        socle();
        String creer = "{\"code\":\"C12\",\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\",\"latitude\":45.5,\"longitude\":-73.6,\"rayonMetres\":100}";
        http.post(E + "/milieux", CHEF, creer).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errors[0]").value("unité « EST » hors de votre périmètre (niveau GESTION requis)"));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}").andExpect(status().isCreated());
        String id = com.jayway.jsonpath.JsonPath.read(http.post(E + "/milieux", CHEF, creer).andExpect(status().isCreated())
                .andExpect(jsonPath("$.rayonMetres").value(100)).andReturn().getResponse().getContentAsString(), "$.id");
        http.post(E + "/milieux", CHEF, creer).andExpect(status().isConflict());
        http.post(E + "/milieux", CHEF, "{\"code\":\"a b\"}").andExpect(status().is(422));
        http.post(E + "/milieux", CHEF, "{\"code\":\"C13\",\"libelle\":\" \"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« libellé » est obligatoire"));
        http.put(E + "/milieux/" + id + "/preuves", CHEF, "{\"preuves\":[{\"mecanisme\":\"GEOREPERAGE\",\"exige\":true},{\"mecanisme\":\"CODE_QR_DE_LIEU\"}]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.preuves", Matchers.hasSize(2)));
        String secret = jdbc.sql("SELECT secret FROM eff_milieu_preuves WHERE milieuId = ? AND mecanisme = 'CODE_QR_DE_LIEU'").param(id).query(String.class).single();
        http.put(E + "/milieux/" + id + "/preuves", CHEF, "{\"preuves\":[{\"mecanisme\":\"CODE_QR_DE_LIEU\"},{\"mecanisme\":\"GEOREPERAGE\",\"exige\":true}]}").andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.sql("SELECT secret FROM eff_milieu_preuves WHERE milieuId = ? AND mecanisme = 'CODE_QR_DE_LIEU'").param(id).query(String.class).single()).isEqualTo(secret);
        http.put(E + "/milieux/" + id + "/preuves", CHEF, "{\"preuves\":\"x\"}").andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« preuves » doit être une liste"));
        http.put(E + "/milieux/" + id, CHEF, "{\"libelle\":\"Caserne 12\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}").andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("ce milieu exige le géorepérage : ses coordonnées ne peuvent pas être retirées"));
        http.put(E + "/milieux/" + id, CHEF, "{\"libelle\":\"Caserne douze\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\",\"adresse\":\"1 rue\",\"latitude\":45.5,\"longitude\":-73.6,\"rayonMetres\":150,\"actif\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.actif").value(false)).andExpect(jsonPath("$.adresse").value("1 rue"));
        http.put(E + "/milieux/nul", CHEF, "{}").andExpect(status().isNotFound());
        http.get(E + "/milieux", CHEF).andExpect(jsonPath("$", Matchers.hasSize(1)));
        http.get(E + "/milieux", JULIE).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.get(E + "/milieux", ADMIN).andExpect(jsonPath("$[0].preuves[1].exige").value(true));
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("[\"personnel\"]", T).update();
        http.get(E + "/milieux", ADMIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.errors[0]").value("module 'effectifs' non activé pour cet organisme"));
        jdbc.sql("UPDATE tenant_quotas SET modules = ? WHERE tenantId = ?").params("illisible", T).update();
        http.get(E + "/milieux", ADMIN).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("le journal : chaque geste tracé, filtres, borné au périmètre, export CSV sûr (E-RF-8)")
    void journal() throws Exception {
        socle();
        http.get(E + "/journal", JULIE).andExpect(status().isForbidden());
        http.get(E + "/journal?action=referentiel.cree", ADMIN).andExpect(status().isOk()).andExpect(jsonPath("$", Matchers.hasSize(4)))
                .andExpect(jsonPath("$[0].origine").value("UTILISATEUR")).andExpect(jsonPath("$[0].apres.code").value("CASERNE"));
        http.get(E + "/journal?objetType=referentiel.unites&objetId=EST&acteurId=usr-admin&origine=UTILISATEUR&du=2031-01-01&au=2031-12-31", ADMIN)
                .andExpect(status().isOk());
        http.get(E + "/journal?origine=robot", ADMIN).andExpect(status().is(422));
        http.get(E + "/journal?limite=0", ADMIN).andExpect(status().is(422)).andExpect(jsonPath("$.errors[0]").value("« limite » : entier entre 1 et 1000"));
        http.get(E + "/journal?limite=deux", ADMIN).andExpect(status().is(422));
        http.get(E + "/journal?limite=2", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.get(E + "/journal?format=xml", ADMIN).andExpect(status().is(422))
                .andExpect(jsonPath("$.errors[0]").value("format inconnu : « xml » (attendu : json, csv, xlsx, pdf)"));
        // P-TN-8 : le même résultat de recherche, emporté en tableur et en PDF — de vrais fichiers, pas du texte renommé.
        byte[] xlsx = http.get(E + "/journal?format=xlsx", ADMIN).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"journal-effectifs.xlsx\"")).andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(xlsx, 0, 2, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("PK");
        byte[] pdf = http.get(E + "/journal?format=pdf", ADMIN).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"journal-effectifs.pdf\"")).andReturn().getResponse().getContentAsByteArray();
        org.assertj.core.api.Assertions.assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // Le JSON demandé par son nom est la réponse ordinaire de l'écran.
        http.get(E + "/journal?format=json&limite=2", ADMIN).andExpect(jsonPath("$", Matchers.hasSize(2)));
        http.get(E + "/journal?format=csv", ADMIN).andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"journal-effectifs.csv\""))
                .andExpect(content().string(Matchers.startsWith("horodatage,origine,acteurId,action,objetType,objetId,uniteCode,avant,apres\n")));
        // Un manager ne lit que les unités de son périmètre ; sans périmètre, rien.
        http.get(E + "/journal", CHEF).andExpect(jsonPath("$", Matchers.hasSize(0)));
        http.post(E + "/perimetres", ADMIN, "{\"utilisateurId\":\"usr-chef\",\"uniteCode\":\"EST\",\"niveau\":\"GESTION\",\"depuis\":\"2026-01-01\"}").andExpect(status().isCreated());
        http.get(E + "/journal", CHEF).andExpect(jsonPath("$[*].uniteCode", Matchers.everyItem(Matchers.is("EST"))));
    }

    @Test
    @DisplayName("ce que les lots suivants empruntent : le milieu visible, son secret QR ; et une erreur imprévue reste un 400 nommé")
    void portesDesLotsSuivants() throws Exception {
        socle();
        String id = com.jayway.jsonpath.JsonPath.read(http.post(E + "/milieux", ADMIN, "{\"code\":\"C7\",\"libelle\":\"Caserne 7\",\"typeCode\":\"CASERNE\",\"uniteCode\":\"EST\"}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        ca.vina.nexu.effectifs.domaine.Contexte admin = new ca.vina.nexu.effectifs.domaine.Contexte(T, "usr-admin", "admin", "2031-01-15T10:00:00.000Z");
        ca.vina.nexu.effectifs.domaine.Contexte chef = new ca.vina.nexu.effectifs.domaine.Contexte(T, "usr-chef", "manager", "2031-01-15T10:00:00.000Z");
        org.assertj.core.api.Assertions.assertThat(lectureMilieux.exiger(admin, id).code()).isEqualTo("C7");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lectureMilieux.exiger(chef, id)).isInstanceOf(ca.vina.nexu.effectifs.domaine.HorsPerimetreException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> lectureMilieux.lire(T, "nul")).isInstanceOf(ca.vina.nexu.effectifs.domaine.IntrouvableException.class);
        org.assertj.core.api.Assertions.assertThat(depotMilieux.secretQr(T, id)).isEmpty();
        http.put(E + "/milieux/" + id + "/preuves", ADMIN, "{\"preuves\":[{\"mecanisme\":\"CODE_QR_DE_LIEU\"}]}").andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(depotMilieux.secretQr(T, id)).contains("secret-24");

        org.springframework.mock.web.MockHttpServletRequest requete = new org.springframework.mock.web.MockHttpServletRequest();
        requete.setAttribute(ContexteRequete.ATTRIBUT, ADMIN);
        GardeAcces.Verdict v = new GardeAcces.Verdict(T, null);
        org.assertj.core.api.Assertions.assertThat(gardeEffectifs.repondre(requete, v, 200, ctx -> { throw new IllegalStateException("panne"); }).getStatusCode().value()).isEqualTo(400);
        org.assertj.core.api.Assertions.assertThat(gardeEffectifs.repondre(requete, v, 200, ctx -> { throw new IllegalStateException(); }).getBody().toString()).contains("erreur inattendue");
    }
}
