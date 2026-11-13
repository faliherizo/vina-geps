package ca.vina.nexu.personnel.application.requetes;

import ca.vina.nexu.personnel.domaine.RepertoirePersonnes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lecture des personnes — le seuil de trois caractères et la limite des suggestions")
class LecturePersonnesTest {

    /** Un répertoire qui retient ce qu'on lui demande : le seuil doit l'épargner, la limite lui parvenir. */
    private static final class RepertoireTemoin implements RepertoirePersonnes {
        final List<String> demandes = new ArrayList<>();

        @Override
        public List<Personne> parTexte(String tenantId, String texte, int limite) {
            demandes.add(tenantId + "|" + texte + "|" + limite);
            return List.of(new Personne("e-1", null, "Seul", null, null, "active"));
        }

        @Override
        public Optional<Personne> lire(String tenantId, String id) {
            return Optional.empty();
        }

        @Override
        public List<Personne> tous(String tenantId) {
            return List.of();
        }
    }

    @Test
    @DisplayName("sous le seuil, aucune requête ; au seuil, la saisie nettoyée part avec la limite")
    void seuil() {
        RepertoireTemoin temoin = new RepertoireTemoin();
        LecturePersonnes lecture = new LecturePersonnes(temoin);
        assertThat(lecture.suggerer("t-1", null)).isEmpty();
        assertThat(lecture.suggerer("t-1", "  ab  ")).isEmpty();
        assertThat(temoin.demandes).isEmpty();
        assertThat(lecture.suggerer("t-1", "  abc ")).extracting(RepertoirePersonnes.Personne::nomComplet).containsExactly("Seul");
        assertThat(temoin.demandes).containsExactly("t-1|abc|" + LecturePersonnes.LIMITE_SUGGESTIONS);
        assertThat(lecture.lire("t-1", "x")).isEmpty();
        assertThat(lecture.tous("t-1")).isEmpty();
        assertThat(new RepertoirePersonnes.Personne("e", "Léa", null, null, null, null).nomComplet()).isEqualTo("Léa");
    }
}
