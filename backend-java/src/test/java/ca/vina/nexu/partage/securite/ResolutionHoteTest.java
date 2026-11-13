package ca.vina.nexu.partage.securite;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ResolutionHote — le tenant vient de l'hôte, jamais du client (ADR 0033)")
class ResolutionHoteTest {

    private static final ResolutionHote.Configuration SOUS_DOMAINE = ResolutionHote.Configuration.depuis("sous_domaine", " .Nexus-VinaITsolution.com. ", " www, Vitrine ,,");

    @Test
    @DisplayName("la configuration se valide à la construction : mode inconnu, sous-domaine sans racine ; les réservés comprennent toujours admin")
    void configuration() {
        ResolutionHote.Configuration defaut = ResolutionHote.Configuration.depuis(null, null, null);
        assertThat(defaut.mode()).isEqualTo(ModeResolution.SELECTEUR);
        assertThat(defaut.domaineRacine()).isNull();
        assertThat(defaut.parSousDomaine()).isFalse();
        assertThat(defaut.reserves()).containsExactlyInAnyOrder("www", "admin", "api", "vitrine", "app");
        assertThat(ResolutionHote.Configuration.depuis(" ", "", "").mode()).isEqualTo(ModeResolution.SELECTEUR);
        assertThat(SOUS_DOMAINE.domaineRacine()).isEqualTo("nexus-vinaitsolution.com");
        assertThat(SOUS_DOMAINE.reserves()).containsExactlyInAnyOrder("www", "vitrine", "admin");
        assertThat(SOUS_DOMAINE.parSousDomaine()).isTrue();
        // Sélecteur avec une racine posée : la racine ne sert pas, le mode fait foi.
        assertThat(ResolutionHote.Configuration.depuis("selecteur", "nexus.example", null).parSousDomaine()).isFalse();
        assertThatThrownBy(() -> ResolutionHote.Configuration.depuis("dns", null, null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("TENANT_RESOLUTION inconnu");
        assertThatThrownBy(() -> ResolutionHote.Configuration.depuis("sous_domaine", " ", null)).isInstanceOf(IllegalStateException.class).hasMessageContaining("DOMAINE_RACINE");
        assertThat(ModeResolution.de("sous_domaine")).contains(ModeResolution.SOUS_DOMAINE);
        assertThat(ModeResolution.SOUS_DOMAINE.code()).isEqualTo("sous_domaine");
    }

    @Test
    @DisplayName("l'hôte : X-Forwarded-Host d'abord (première valeur), sinon Host ; sans port, en minuscules")
    void hote() {
        assertThat(ResolutionHote.hoteDe("CSSDC.nexus-vinaitsolution.com:443, proxy.interne", "backend:4000")).isEqualTo("cssdc.nexus-vinaitsolution.com");
        assertThat(ResolutionHote.hoteDe(" ", "Localhost:5173")).isEqualTo("localhost");
        assertThat(ResolutionHote.hoteDe(null, null)).isEmpty();
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.addHeader("Host", "admin.nexus-vinaitsolution.com");
        assertThat(ResolutionHote.hoteDe(r)).isEqualTo("admin.nexus-vinaitsolution.com");
    }

    @Test
    @DisplayName("le genre de l'hôte : racine, réservé, établissement, hors domaine ; sélecteur partout hors sous-domaine")
    void genre() {
        assertThat(ResolutionHote.genreDe("n-importe-quoi", ResolutionHote.Configuration.depuis(null, null, null))).isEqualTo(new ResolutionHote.Genre.Selecteur());
        assertThat(ResolutionHote.genreDe("nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.Racine());
        assertThat(ResolutionHote.genreDe("www.nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.Reserve("www"));
        assertThat(ResolutionHote.genreDe("admin.nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.Reserve("admin"));
        assertThat(ResolutionHote.genreDe("cssdc.nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.Etablissement("cssdc"));
        assertThat(ResolutionHote.genreDe("a.b.nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.HorsDomaine("a.b.nexus-vinaitsolution.com"));
        assertThat(ResolutionHote.genreDe(".nexus-vinaitsolution.com", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.HorsDomaine(".nexus-vinaitsolution.com"));
        assertThat(ResolutionHote.genreDe("localhost", SOUS_DOMAINE)).isEqualTo(new ResolutionHote.Genre.HorsDomaine("localhost"));
    }

    @Test
    @DisplayName("l'adresse publique : https://slug.racine (admin. sans établissement) en sous-domaine, l'adresse commune sinon")
    void urlPublique() {
        assertThat(SOUS_DOMAINE.urlPublique("cssdc", "https://commune")).isEqualTo("https://cssdc.nexus-vinaitsolution.com");
        assertThat(SOUS_DOMAINE.urlPublique(null, "https://commune")).isEqualTo("https://admin.nexus-vinaitsolution.com");
        assertThat(SOUS_DOMAINE.urlPublique(" ", null)).isEqualTo("https://admin.nexus-vinaitsolution.com");
        ResolutionHote.Configuration selecteur = ResolutionHote.Configuration.depuis(null, null, null);
        assertThat(selecteur.urlPublique("cssdc", "https://commune")).isEqualTo("https://commune");
        assertThat(selecteur.urlPublique("cssdc", null)).isEmpty();
        assertThat(AdressePublique.fixe("https://nexu.example//").pour("t")).isEqualTo("https://nexu.example");
        assertThat(AdressePublique.fixe(null).pour("t")).isEmpty();
    }
}
