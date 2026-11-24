package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.JournalEffectifs;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ReglagesJournalPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/effectifs/journal/reglages} — <strong>journaliser ou non les consultations</strong> (addenda 8, M-10), un
 * réglage d'organisme : la capacité de configurer les effectifs le lit et le pose, et le changement est lui-même journalisé.
 */
@RestController
@RequestMapping("/api/effectifs/journal/reglages")
@Transactional
public class ControleurReglagesJournal {

    private static final String MODULE = "effectifs";
    private static final String CLE = "consultations";
    private static final int OK = 200;

    private final GardeEffectifs garde;
    private final ReglagesJournalPort reglages;
    private final JournalEffectifsPort journal;

    public ControleurReglagesJournal(GardeEffectifs garde, ReglagesJournalPort reglages, JournalEffectifsPort journal) {
        this.garde = garde;
        this.reglages = reglages;
        this.journal = journal;
    }

    @GetMapping
    public ResponseEntity<Object> lire(HttpServletRequest r) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER), OK, ctx -> Map.of(CLE, reglages.consultations(ctx.tenantId())));
    }

    @PutMapping
    public ResponseEntity<Object> poser(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return garde.repondre(r, garde.capacite(r, MODULE, GardeEffectifs.Capacite.CONFIGURER), OK, ctx -> {
            if (!(c != null && c.get(CLE) instanceof Boolean actif)) {
                throw new EffectifsInvalideException("« consultations » : vrai ou faux");
            }
            boolean avant = reglages.consultations(ctx.tenantId());
            reglages.poserConsultations(ctx.tenantId(), actif);
            journal.ecrire(ctx, JournalEffectifs.Consignation.de("journal.reglages", "reglages", CLE, null, Map.of(CLE, avant), Map.of(CLE, actif)));
            return Map.of(CLE, actif);
        });
    }
}
