package ca.vina.nexu.soutien.infrastructure.web;

import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.soutien.application.commandes.FixerEngagement;
import ca.vina.nexu.soutien.application.requetes.LectureEngagements;
import ca.vina.nexu.soutien.domaine.EngagementsDeService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * {@code /api/soutien/engagements} — la politique de soutien de chaque organisme, celle de son
 * contrat (AO 26-015-DO, clauses 5.02 et 6.01). Réservé au soutien (superadmin).
 */
@RestController
@RequestMapping("/api/soutien/engagements")
public class ControleurEngagementsSoutien {

    private static final String CHAMP_POLITIQUE = "politique";

    private final LectureEngagements lecture;
    private final FixerEngagement fixer;
    private final GardeAcces garde;

    public ControleurEngagementsSoutien(LectureEngagements lecture, FixerEngagement fixer, GardeAcces garde) {
        this.lecture = lecture;
        this.fixer = fixer;
        this.garde = garde;
    }

    private GardeAcces.Verdict soutien(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerSession(requete);
        if (v.refuse()) {
            return v;
        }
        return GardeAcces.contexte(requete).estSuperadmin() ? v : new GardeAcces.Verdict(null, Bord.erreur(403, "forbidden"));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest requete) {
        return Bord.repondre(soutien(requete), t -> lecture.lire());
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<Object> fixer(HttpServletRequest requete, @PathVariable String tenantId, @RequestBody(required = false) Map<String, Object> c) {
        return Bord.repondre(soutien(requete), t -> fixer.fixer(tenantId, politiqueDe(Bord.corps(c)), GardeAcces.contexte(requete).userId()));
    }

    /** Le corps porte un code de politique, en texte : tout autre forme est refusée avant d'entrer. */
    private static EngagementsDeService.Politique politiqueDe(Map<String, Object> corps) {
        if (!(corps.get(CHAMP_POLITIQUE) instanceof String code) || code.isBlank()) {
            throw new IllegalArgumentException("politique requise (texte)");
        }
        return EngagementsDeService.Politique.de(code.trim());
    }
}
