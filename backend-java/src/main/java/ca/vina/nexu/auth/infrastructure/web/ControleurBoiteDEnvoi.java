package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.partage.courrier.infrastructure.CourrierJournal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/auth/_outbox} — <strong>essais seulement</strong> : la boîte d'envoi du courrier
 * journalisé, où la campagne e2e lit un mot de passe provisoire ou un avis. Introuvable (404)
 * hors environnement d'essai, comme le Node en production.
 */
@RestController
public class ControleurBoiteDEnvoi {

    private final CourrierJournal journal;
    private final boolean essai;

    public ControleurBoiteDEnvoi(CourrierJournal journal, @Value("${nexu.essai}") boolean essai) {
        this.journal = journal;
        this.essai = essai;
    }

    @GetMapping("/api/auth/_outbox")
    public ResponseEntity<Object> boite() {
        return essai ? ResponseEntity.ok(journal.boite()) : ResponseEntity.notFound().build();
    }
}
