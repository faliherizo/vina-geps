package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.ClientHttpEssai;
import ca.vina.nexu.SchemaEssai;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import ca.vina.nexu.auth.infrastructure.persistance.DepotPhotosProfil;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.securite.domaine.Antivirus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code /api/auth/personnes/{id}/photo} — le visage d'un collègue (maquette P11).
 *
 * Trois réponses à éprouver ensemble, parce que la troisième est celle qu'on oublie : le collègue
 * du même établissement l'obtient, celui d'un autre établissement ne l'obtient pas, et le refus
 * prend la forme d'un <b>404</b> — répondre « interdit » apprendrait que le compte existe ailleurs.
 */
@DisplayName("/api/auth/personnes/{id}/photo — la photo d'un collègue")
class ControleurPhotoProfilHttpTest {

    /** Un PNG minuscule mais valide : l'essai porte sur les règles, pas sur le décodage d'image. */
    private static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==";

    private static final ContexteRequete ANA = new ContexteRequete("u-ana", "ana", "employee", "ana", "T1");
    private static final ContexteRequete BOB = new ContexteRequete("u-bob", "bob", "employee", "bob", "T1");
    private static final ContexteRequete AILLEURS = new ContexteRequete("u-zoe", "zoe", "employee", "zoe", "T2");
    /** Un compte global — sans établissement : il administre la plateforme, il ne regarde pas les visages. */
    private static final ContexteRequete GLOBAL = new ContexteRequete("u-sa", "sa", "superadmin", "sa", null);

    private JdbcClient jdbc;
    private ClientHttpEssai http;

    @BeforeEach
    void preparer(@TempDir Path dossier) {
        jdbc = SchemaEssai.base(dossier);
        for (ContexteRequete c : new ContexteRequete[] { ANA, BOB, AILLEURS, GLOBAL }) {
            jdbc.sql("INSERT INTO users (id, username, tenantId, passwordHash, role, createdAt) VALUES (?,?,?,?,?,?)")
                    .params(c.userId(), c.username(), c.tenantId(), "x", c.role(), "2026-11-01T00:00:00.000Z").update();
        }
        jdbc.sql("INSERT INTO auth_photos (userId, typeMime, taille, contenu, modifieLe) VALUES (?,?,?,?,?)")
                .params(BOB.userId(), "image/png", 70, PNG, "2026-11-01T00:00:00.000Z").update();
        http = new ClientHttpEssai(new ControleurPhotoProfil(new DepotPhotosProfil(jdbc), new GardeAcces(new RegistrePolitiqueAcces()),
                new Antivirus.AnalyseurSimule(), Clock.fixed(Instant.parse("2026-11-09T12:00:00Z"), ZoneOffset.UTC)));
    }

    @Test
    @DisplayName("un collègue du même établissement l'obtient, en octets et sans deviner le type")
    void collegue() throws Exception {
        byte[] servi = http.get("/api/auth/personnes/u-bob/photo", ANA).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        // Les octets eux-mêmes : un `<img>` ne sait pas lire du JSON.
        assertThat(servi).isEqualTo(Base64.getDecoder().decode(PNG));
    }

    @Test
    @DisplayName("sans session, rien")
    void anonyme() throws Exception {
        http.get("/api/auth/personnes/u-bob/photo", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("un autre établissement, un compte global, un compte sans photo : 404, jamais 403")
    void refus() throws Exception {
        // 403 apprendrait que le compte existe ailleurs ; 404 ne dit rien de plus que l'absence.
        http.get("/api/auth/personnes/u-bob/photo", AILLEURS).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errors[0]").value("aucune photo de profil"));
        http.get("/api/auth/personnes/u-bob/photo", GLOBAL).andExpect(status().isNotFound());
        // Un compte du bon établissement, mais sans photo : l'écran dessine alors les initiales.
        http.get("/api/auth/personnes/u-ana/photo", BOB).andExpect(status().isNotFound());
        http.get("/api/auth/personnes/inconnu/photo", ANA).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("la sienne : posée après l'antivirus, relue, remplacée, retirée ; sans session, rien")
    void laSienne() throws Exception {
        http.get("/api/auth/me/photo", ANA).andExpect(status().isNotFound()).andExpect(jsonPath("$.errors[0]").value("aucune photo de profil"));
        http.put("/api/auth/me/photo", ANA, "{\"typeMime\":\"image/png\",\"contenu\":\"data:image/png;base64," + PNG + "\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.typeMime").value("image/png")).andExpect(jsonPath("$.taille").value(70))
                .andExpect(jsonPath("$.modifieLe").value("2026-11-09T12:00:00.000Z")).andExpect(jsonPath("$.contenu").doesNotExist());
        http.get("/api/auth/me/photo", ANA).andExpect(jsonPath("$.contenu").value(PNG)).andExpect(jsonPath("$.taille").value(70));
        http.get("/api/auth/personnes/u-ana/photo", BOB).andExpect(status().isOk());
        http.put("/api/auth/me/photo", ANA, "{\"typeMime\":\"image/webp\",\"contenu\":\"" + PNG + "\"}").andExpect(status().isOk());
        http.get("/api/auth/me/photo", ANA).andExpect(jsonPath("$.typeMime").value("image/webp"));
        http.delete("/api/auth/me/photo", ANA).andExpect(jsonPath("$.retiree").value(true));
        http.delete("/api/auth/me/photo", ANA).andExpect(jsonPath("$.retiree").value(false));
        http.get("/api/auth/me/photo", null).andExpect(status().isUnauthorized());
        http.put("/api/auth/me/photo", null, "{}").andExpect(status().isUnauthorized());
        http.delete("/api/auth/me/photo", null).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("refusée en 400 nommé : type non admis, image absente, fichier infecté — et rien n'est écrit")
    void refusDePose() throws Exception {
        http.put("/api/auth/me/photo", ANA, "{\"typeMime\":\"image/gif\",\"contenu\":\"" + PNG + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("type d'image non admis « image/gif »")));
        http.put("/api/auth/me/photo", ANA, null).andExpect(status().isBadRequest());
        String eicar = Base64.getEncoder().encodeToString(Antivirus.EICAR.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        http.put("/api/auth/me/photo", ANA, "{\"typeMime\":\"image/png\",\"contenu\":\"" + eicar + "\"}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(org.hamcrest.Matchers.startsWith("fichier refusé par l'antivirus")));
        http.get("/api/auth/me/photo", ANA).andExpect(status().isNotFound());
    }
}
