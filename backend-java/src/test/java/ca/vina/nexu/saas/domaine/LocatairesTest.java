package ca.vina.nexu.saas.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Locataires — validation, slug, essai, abonnement")
class LocatairesTest {

    private static final Instant T = Instant.parse("2026-09-13T12:00:00Z");
    private static final List<String> PAYS = List.of("CA", "FR");

    private static Locataires.Demande demande(String nom, String type, String pays, String plan, String slug, String courriel) {
        return new Locataires.Demande(nom, type, pays, "QC", plan, null, "ca-central-1", " 12 rue X ", "Québec", "G1A 1A1", "418 555-0100", courriel, slug);
    }

    @Test
    void uneDemandeValideEstNormaliseeEtDateeDeTrenteJours() {
        Locataires.Creation c = Locataires.valider(demande("  CSS des Chênes ", "public_institution", "ca", "enterprise", " CSS des Chênes! ", " a@b.qc "), T, PAYS::contains, PAYS);
        assertThat(c.name()).isEqualTo("CSS des Chênes");
        assertThat(c.country()).isEqualTo("CA");
        assertThat(c.slug()).isEqualTo("css-des-ch-nes");
        assertThat(c.email()).isEqualTo("a@b.qc");
        assertThat(c.address()).isEqualTo("12 rue X");
        assertThat(c.trialEndsAt()).isEqualTo("2026-10-13T12:00:00.000Z");
        assertThat(c.dataResidency()).isEqualTo("ca-central-1");
        Locataires.Creation court = Locataires.valider(new Locataires.Demande("X", "corporate", "FR", null, "starter", 7, "francecentral", null, null, null, null, null, null), T, PAYS::contains, PAYS);
        assertThat(court.trialEndsAt()).isEqualTo("2026-09-20T12:00:00.000Z");
        assertThat(court.dataResidency()).isEqualTo("francecentral");
        assertThat(court.slug()).isNull();
        assertThat(court.email()).isNull();
    }

    @Test
    void tousLesMotifsSontRendusDansLOrdreDuNode() {
        assertThatThrownBy(() -> Locataires.valider(demande(" ", "club", "Canada", "or", null, "pas-un-courriel"), T, PAYS::contains, PAYS))
                .isInstanceOf(Locataires.ValidationException.class)
                .extracting(e -> ((Locataires.ValidationException) e).motifs())
                .isEqualTo(List.of("le nom de l'établissement est requis",
                        "type invalide (attendu : government, public_institution, private_institution, corporate, country)",
                        "le pays doit être un code ISO à 2 lettres",
                        "plan invalide (attendu : starter, professional, enterprise, sovereign)",
                        "courriel invalide « pas-un-courriel »"));
        assertThatThrownBy(() -> Locataires.valider(demande("X", "corporate", "zz", "starter", null, null), T, PAYS::contains, PAYS))
                .hasMessage("pays « ZZ » non pris en charge (disponibles : CA, FR)");
        assertThatThrownBy(() -> Locataires.valider(demande("X", null, null, null, null, null), T, PAYS::contains, PAYS))
                .hasMessageContaining("type invalide").hasMessageContaining("2 lettres").hasMessageContaining("plan invalide");
    }

    @Test
    @DisplayName("E-TN-19 : la résidence non déclarée devient la région d'hébergement ; sans l'une ni l'autre, rien n'est inventé")
    void laResidenceNEstJamaisInventee() {
        Locataires.Demande sans = new Locataires.Demande("X", "corporate", "CA", null, "starter", null, "  ", null, null, null, null, null, null);
        assertThat(Locataires.valider(sans.avecResidenceParDefaut("ca-central-1"), T, PAYS::contains, PAYS).dataResidency()).isEqualTo("ca-central-1");
        Locataires.Demande declaree = new Locataires.Demande("X", "corporate", "FR", null, "starter", null, " francecentral ", null, null, null, null, null, null);
        assertThat(declaree.avecResidenceParDefaut("ca-central-1")).isSameAs(declaree);
        assertThat(Locataires.valider(declaree, T, PAYS::contains, PAYS).dataResidency()).isEqualTo("francecentral");
        assertThatThrownBy(() -> Locataires.valider(sans.avecResidenceParDefaut(""), T, PAYS::contains, PAYS))
                .isInstanceOf(Locataires.ValidationException.class)
                .hasMessage("la résidence des données est requise : la déclarer, ou déclarer la région d'hébergement de la plateforme (REGION_DONNEES)");
    }

    @Test
    void leSlugEstBorneEtLePaysCorrigeVautDeuxLettres() {
        assertThatThrownBy(() -> Locataires.normaliserSlug("a")).hasMessage("le slug d'URL doit compter de 2 à 40 caractères (a-z, 0-9, tirets)");
        assertThatThrownBy(() -> Locataires.valider(demande("X", "corporate", "CA", "starter", "!", null), T, PAYS::contains, PAYS)).hasMessageContaining("slug");
        assertThat(Locataires.normaliserSlug("--École--A--")).isEqualTo("cole-a");
        assertThat(Locataires.paysValide("fr")).isEqualTo("FR");
        assertThatThrownBy(() -> Locataires.paysValide("FRA")).hasMessage("country must be 2-letter ISO code");
        assertThatThrownBy(() -> Locataires.paysValide(null)).hasMessage("country must be 2-letter ISO code");
    }

    @Test
    void lAbonnementCompteTrenteJoursParMois() {
        assertThat(Locataires.finAbonnement(T, 12)).isEqualTo("2027-09-08T12:00:00.000Z");
        assertThat(Locataires.iso(Instant.parse("2026-09-13T12:00:00.250Z"))).isEqualTo("2026-09-13T12:00:00.250Z");
    }
}
