package ca.vina.nexu.habilitations.infrastructure.web;

import ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort;
import ca.vina.nexu.habilitations.domaine.Habilitations;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde du contexte {@code habilitations} : session, établissement, et le rôle
 * d'administration pour ce qui octroie.
 *
 * <p>Les <strong>lectures restent ouvertes</strong> à l'établissement : « à quoi ai-je droit ? »
 * et « qui peut voir ce rapport ? » sont des questions que l'exigence 91 demande de rendre
 * consultables, pas de réserver.
 */
@Component
public class GardeHabilitations {

    private static final String[] ADMINISTRATION = { "admin", "superadmin" };

    private final GardeAcces garde;

    public GardeHabilitations(GardeAcces garde) {
        this.garde = garde;
    }

    public static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    public GardeAcces.Verdict base(HttpServletRequest requete) {
        return garde.exigerTenant(requete);
    }

    public GardeAcces.Verdict administrer(HttpServletRequest requete) {
        return garde.exigerRole(requete, ADMINISTRATION);
    }

    /**
     * L'auteur du geste. Pas de garde contre l'absence de session : {@code auteur} n'est lu que
     * dans le corps d'un geste, et un geste ne s'exécute qu'après un verdict favorable — qui exige
     * déjà une session. Un test de plus sur cette branche n'éprouverait rien qui puisse arriver.
     */
    public String auteur(HttpServletRequest requete) {
        return GardeAcces.contexte(requete).userId();
    }

    /** « Introuvable » est un 404 ; un refus du domaine est un <strong>400 nommé</strong>. */
    public static ResponseEntity<Object> repondre(GardeAcces.Verdict v, int code, Function<String, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(v.tenantId()));
        } catch (DepotHabilitationsPort.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (Habilitations.HabilitationInvalideException e) {
            return erreur(400, e.getMessage());
        } catch (RuntimeException e) {
            return erreur(400, e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }
}
