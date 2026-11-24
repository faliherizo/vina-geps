package ca.vina.nexu.effectifs.domaine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Consultations (addenda 8, M-10) — quel chemin lu est un dossier ou un horaire")
class ConsultationsTest {

    private static Optional<Consultations.Objet> o(String chemin) {
        return Consultations.objetDe(chemin);
    }

    @Test
    @DisplayName("dossiers et horaires reconnus, avec leur identifiant ; le reste n'est pas une consultation")
    void objets() {
        assertThat(o("/api/effectifs/planification/ressources")).contains(new Consultations.Objet(Consultations.TypeObjet.DOSSIER, "liste"));
        assertThat(o("/api/effectifs/planification/ressources/rs-1/competences")).contains(new Consultations.Objet(Consultations.TypeObjet.DOSSIER, "rs-1"));
        assertThat(o("/api/effectifs/planification/ressources/rs-1/rattachements")).contains(new Consultations.Objet(Consultations.TypeObjet.DOSSIER, "rs-1"));
        assertThat(o("/api/effectifs/ressources/rs-2/quarts")).contains(new Consultations.Objet(Consultations.TypeObjet.DOSSIER, "rs-2"));
        assertThat(o("/api/effectifs/ressources/rs-2/pointages")).contains(new Consultations.Objet(Consultations.TypeObjet.DOSSIER, "rs-2"));
        assertThat(o("/api/effectifs/planification/periodes/pr-1/equipages")).contains(new Consultations.Objet(Consultations.TypeObjet.HORAIRE, "pr-1"));
        assertThat(o("/api/effectifs/disponibilites/periodes/pr-1")).contains(new Consultations.Objet(Consultations.TypeObjet.HORAIRE, "pr-1"));
        assertThat(o("/api/effectifs/planification/ressources/rs-1")).isEmpty();
        assertThat(o("/api/effectifs/journal")).isEmpty();
        assertThat(o(null)).isEmpty();
        assertThat(Consultations.TypeObjet.HORAIRE.code()).isEqualTo("horaire");
        assertThat(Consultations.ACTION).isEqualTo("consultation");
    }
}
