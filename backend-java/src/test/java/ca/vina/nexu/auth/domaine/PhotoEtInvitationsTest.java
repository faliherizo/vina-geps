package ca.vina.nexu.auth.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Auth — photo de profil et invitations")
class PhotoEtInvitationsTest {

    private static final String PNG = Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});

    @Test
    @DisplayName("la photo : trois types, data URL admise, contenu vide ou trop lourd refusé")
    void photo() {
        PhotoProfil.Saisie s = PhotoProfil.valider(" IMAGE/PNG ", "data:image/png;base64," + PNG);
        assertThat(s.typeMime()).isEqualTo("image/png");
        assertThat(s.octets()).hasSize(3);
        assertThat(PhotoProfil.valider("image/webp", PNG).contenu()).isEqualTo(PNG);
        assertThatThrownBy(() -> PhotoProfil.valider("image/gif", PNG)).hasMessageStartingWith("type d'image non admis « image/gif »");
        assertThatThrownBy(() -> PhotoProfil.valider(null, PNG)).hasMessageStartingWith("type d'image non admis « inconnu »");
        assertThatThrownBy(() -> PhotoProfil.valider("image/png", " ")).hasMessage("aucune image reçue");
        assertThatThrownBy(() -> PhotoProfil.valider("image/png", "data:image/png;base64")).hasMessage("aucune image reçue");
        assertThatThrownBy(() -> PhotoProfil.valider("image/png", "====")).hasMessage("l'image reçue est vide");
        assertThat(PhotoProfil.valider("image/png", "QU-_Q\nxQ").octets()).hasSize(5);
        String lourde = Base64.getEncoder().encodeToString(new byte[PhotoProfil.TAILLE_MAX_OCTETS + 1]);
        assertThatThrownBy(() -> PhotoProfil.valider("image/jpeg", lourde)).hasMessageStartingWith("l'image pèse 512 Ko ; la limite est de 512 Ko");
    }

    @Test
    @DisplayName("l'invitation : courriel, identifiant, rôle permis, validité de 1 à 30 jours")
    void validation() {
        Map<String, Object> e = new HashMap<>(Map.of("courriel", " Julie@Ex.CA ", "username", "julie.r", "role", "employee", "employeeId", " e-1 "));
        Invitations.Saisie s = Invitations.valider(e, List.of("employee", "manager"));
        assertThat(s.courriel()).isEqualTo("julie@ex.ca");
        assertThat(s.employeeId()).isEqualTo("e-1");
        assertThat(s.jours()).isEqualTo(7);
        e.put("jours", "30");
        assertThat(Invitations.valider(e, List.of("employee")).jours()).isEqualTo(30);
        e.put("jours", 31);
        assertThatThrownBy(() -> Invitations.valider(e, List.of("employee"))).hasMessage("la validité d'une invitation va de 1 à 30 jours");
        e.put("jours", "abc");
        assertThatThrownBy(() -> Invitations.valider(e, List.of("employee"))).isInstanceOf(Invitations.InvitationInvalideException.class);
        e.remove("jours");
        e.put("employeeId", "");
        assertThat(Invitations.valider(e, List.of("employee")).employeeId()).isNull();
        assertThatThrownBy(() -> Invitations.valider(Map.of("courriel", "x"), List.of())).hasMessageStartingWith("un courriel valide est obligatoire");
        assertThatThrownBy(() -> Invitations.valider(Map.of("courriel", "a@b.cc", "username", "x"), List.of())).hasMessageStartingWith("le nom d'utilisateur");
        assertThatThrownBy(() -> Invitations.valider(Map.of("courriel", "a@b.cc", "username", "abc"), List.of("employee")))
                .hasMessage("rôle inconnu «  » — attendu : employee");
    }

    @Test
    @DisplayName("l'état se déduit des dates ; le lien public refuse ce qui a servi, été retiré ou expiré")
    void etats() {
        Instant maintenant = Instant.parse("2026-06-01T12:00:00Z");
        Invitations.Invitation offerte = new Invitations.Invitation("i", "t", "a@b.cc", "abc", "employee", null, "2026-06-05T00:00:00Z", null, null,
                "c", "p", null);
        assertThat(Invitations.etatDe(offerte, maintenant)).isEqualTo(Invitations.Etat.OFFERTE);
        assertThat(Invitations.publique(offerte, maintenant)).isEqualTo(new Invitations.Publique("abc", "a@b.cc", "2026-06-05T00:00:00Z"));
        assertThat(Invitations.expiration(maintenant, 2)).isEqualTo(Instant.parse("2026-06-03T12:00:00Z"));
        Invitations.Invitation expiree = new Invitations.Invitation("i", "t", "a@b.cc", "abc", "employee", null, "2026-06-01T12:00:00Z", null, null,
                "c", "p", null);
        assertThatThrownBy(() -> Invitations.publique(expiree, maintenant)).hasMessageStartingWith("ce lien a expiré");
        Invitations.Invitation acceptee = new Invitations.Invitation("i", "t", "a@b.cc", "abc", "employee", null, "2026-06-05T00:00:00Z", "x", null,
                "c", "p", "u");
        assertThatThrownBy(() -> Invitations.publique(acceptee, maintenant)).hasMessage("ce lien a déjà servi : connectez-vous");
        Invitations.Invitation retiree = new Invitations.Invitation("i", "t", "a@b.cc", "abc", "employee", null, "2026-06-05T00:00:00Z", null, "x",
                "c", "p", null);
        assertThatThrownBy(() -> Invitations.publique(retiree, maintenant)).hasMessage("cette invitation a été retirée");
    }
}
