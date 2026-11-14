package ca.vina.nexu.partage.web;

import ca.vina.nexu.partage.erreurs.domaine.HorsPerimetreException;
import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.erreurs.domaine.RefusException;
import ca.vina.nexu.partage.erreurs.domaine.VerrouException;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Le bord HTTP commun à tous les contextes (ADR 0025) : après la garde, exécute le gestionnaire
 * et traduit les refus du domaine en codes — introuvable 404, refus ou argument 400, périmètre
 * 403, verrou 409, état incohérent 400 (403 s'il commence par « not authorized », comme le Node).
 * Un contrôleur ne fait pas de {@code try/catch} : il garde, construit la commande, rend le DTO.
 */
public final class Bord {

    private static final Object ABSENT = new Object();

    private Bord() {
    }

    public static ResponseEntity<Object> erreur(int statut, String message) {
        return ResponseEntity.status(statut).body(Map.of("errors", List.of(message == null ? "" : message)));
    }

    public static ResponseEntity<Object> repondre(GardeAcces.Verdict v, int statut, Function<String, Object> action) {
        return repondre(v, statut, action, e -> null);
    }

    /**
     * Même traduction, précédée d'une règle propre au contexte : {@code particulier} rend la réponse
     * d'une exception qu'il connaît, ou {@code null} pour laisser faire la traduction commune.
     */
    public static ResponseEntity<Object> repondre(GardeAcces.Verdict v, int statut, Function<String, Object> action,
                                                  Function<RuntimeException, ResponseEntity<Object>> particulier) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Object corps = action.apply(v.tenantId());
            return corps == null ? ResponseEntity.status(statut).build() : ResponseEntity.status(statut).body(corps);
        } catch (RuntimeException e) {
            ResponseEntity<Object> propre = particulier.apply(e);
            if (propre != null) {
                return propre;
            }
            return traduire(e);
        }
    }

    private static ResponseEntity<Object> traduire(RuntimeException ex) {
        ResponseEntity<Object> r = reponseDe(ex);
        JournalErreurs.refus(r.getStatusCode().value(), ex);
        return r;
    }

    /** Une exception que le bord ne connaît pas n'est pas traduite : elle remonte, et le filtre du journal la consigne avec sa pile. */
    private static ResponseEntity<Object> reponseDe(RuntimeException ex) {
        try {
            throw ex;
        } catch (IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (HorsPerimetreException e) {
            return erreur(403, e.getMessage());
        } catch (VerrouException e) {
            return erreur(409, e.getMessage());
        } catch (IllegalStateException e) {
            return erreur(e.getMessage() != null && e.getMessage().startsWith("not authorized") ? 403 : 400, e.getMessage());
        } catch (RefusException e) {
            return ResponseEntity.status(400).body(Map.of("errors", e.erreurs()));
        } catch (IllegalArgumentException e) {
            return erreur(400, e.getMessage());
        }
    }

    public static ResponseEntity<Object> repondre(GardeAcces.Verdict v, Function<String, Object> action) {
        return repondre(v, 200, action);
    }

    /** Une route publique (catalogue, jeton) : pas de garde, la même traduction. */
    public static ResponseEntity<Object> publique(java.util.function.Supplier<Object> action) {
        return repondre(new GardeAcces.Verdict(null, null), 200, t -> action.get());
    }

    /** 204 si supprimé, 404 sinon — après la garde. */
    public static ResponseEntity<Object> suppression(GardeAcces.Verdict v, Function<String, Boolean> action) {
        ResponseEntity<Object> r = repondre(v, 204, t -> action.apply(t) ? null : ABSENT);
        return r.getBody() == ABSENT ? ResponseEntity.notFound().build() : r;
    }

    /** Le corps JSON d'une requête, jamais nul. */
    public static Map<String, Object> corps(Map<String, Object> c) {
        return c == null ? Map.of() : c;
    }
}
