package ca.vina.nexu.partage.web;

import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.erreurs.domaine.RefusException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Bord — la traduction des refus du domaine en codes HTTP, une fois pour tous les contextes")
class BordTest {

    private static final GardeAcces.Verdict OK = new GardeAcces.Verdict("t-1", null);

    @Test
    void traduitChaqueRefus() {
        assertThat(Bord.repondre(OK, t -> Map.of("t", t)).getBody()).isEqualTo(Map.of("t", "t-1"));
        assertThat(Bord.repondre(OK, 201, t -> "x").getStatusCode().value()).isEqualTo(201);
        assertThat(Bord.repondre(OK, 204, t -> null).getStatusCode().value()).isEqualTo(204);
        assertThat(Bord.repondre(new GardeAcces.Verdict(null, Bord.erreur(401, "unauthenticated")), t -> "x").getStatusCode().value()).isEqualTo(401);
        assertThat(Bord.repondre(OK, t -> { throw new IntrouvableException("absent"); }).getStatusCode().value()).isEqualTo(404);
        assertThat(Bord.repondre(OK, t -> { throw new HorsPerimetreException("e-1"); }).getStatusCode().value()).isEqualTo(403);
        assertThat(Bord.repondre(OK, t -> { throw new VerrouException("verrou"); }).getStatusCode().value()).isEqualTo(409);
        assertThat(Bord.repondre(OK, t -> { throw new IllegalStateException("not authorized: role"); }).getStatusCode().value()).isEqualTo(403);
        assertThat(Bord.repondre(OK, t -> { throw new IllegalStateException("incohérent"); }).getStatusCode().value()).isEqualTo(400);
        assertThat(Bord.repondre(OK, t -> { throw new IllegalStateException(); }).getStatusCode().value()).isEqualTo(400);
        ResponseEntity<Object> refus = Bord.repondre(OK, t -> { throw new RefusException(List.of("a", "b")); });
        assertThat(refus.getStatusCode().value()).isEqualTo(400);
        assertThat(refus.getBody()).isEqualTo(Map.of("errors", List.of("a", "b")));
        assertThat(Bord.repondre(OK, t -> { throw new IllegalArgumentException("nom requis"); }).getBody()).isEqualTo(Map.of("errors", List.of("nom requis")));
        assertThat(Bord.suppression(OK, t -> true).getStatusCode().value()).isEqualTo(204);
        assertThat(Bord.suppression(OK, t -> false).getStatusCode().value()).isEqualTo(404);
        assertThat(Bord.publique(() -> "ok").getBody()).isEqualTo("ok");
        // une règle propre au contexte passe avant la traduction commune, et la laisse faire sinon
        java.util.function.Function<RuntimeException, ResponseEntity<Object>> particulier = e -> e instanceof UnsupportedOperationException ? Bord.erreur(501, "pas porté") : null;
        assertThat(Bord.repondre(OK, 200, t -> { throw new UnsupportedOperationException(); }, particulier).getStatusCode().value()).isEqualTo(501);
        assertThat(Bord.repondre(OK, 200, t -> { throw new IntrouvableException("x"); }, particulier).getStatusCode().value()).isEqualTo(404);
        assertThat(Bord.publique(() -> { throw new IntrouvableException("unknown country"); }).getStatusCode().value()).isEqualTo(404);
        assertThat(Bord.corps(null)).isEmpty();
        assertThat(Bord.corps(Map.of("a", 1))).containsEntry("a", 1);
    }
}
