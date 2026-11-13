package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.ProvisionnementComptes;
import ca.vina.nexu.auth.infrastructure.persistance.RemiseAZeroComptes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * {@code POST /api/auth/_reset} — <strong>essais seulement</strong> : supprime tous les comptes sauf
 * {@code admin} et le journal, puis re-provisionne. Refusé (403) hors environnement d'essai
 * ({@code NEXU_ESSAI=1}), avec le message du Node : une campagne lancée contre une base réelle ne
 * doit rien pouvoir effacer.
 */
@RestController
public class ControleurRemiseAZeroComptes {

    static final String REFUS = "_reset disabled outside test environment";

    private final RemiseAZeroComptes remise;
    private final ProvisionnementComptes provisionnement;
    private final boolean essai;

    public ControleurRemiseAZeroComptes(RemiseAZeroComptes remise, ProvisionnementComptes provisionnement, @Value("${nexu.essai}") boolean essai) {
        this.remise = remise;
        this.provisionnement = provisionnement;
        this.essai = essai;
    }

    @PostMapping("/api/auth/_reset")
    public ResponseEntity<Object> remettreAZero() {
        if (!essai) {
            return ResponseEntity.status(403).body(Map.of("errors", List.of(REFUS)));
        }
        remise.vider();
        provisionnement.assurer();
        return ResponseEntity.noContent().build();
    }
}
