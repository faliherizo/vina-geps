package ca.vina.nexu.organisation.infrastructure.web;

import ca.vina.nexu.organisation.domaine.DepotOrganisationPort;
import ca.vina.nexu.organisation.domaine.Organisation;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde du contexte {@code organisation} : session, établissement, et {@code accounting.govern}
 * pour ce qui réorganise.
 *
 * <p>L'organigramme se <strong>lit</strong> par tout compte de l'établissement — savoir de qui
 * l'on relève n'est pas un privilège.
 */
@Component
public class GardeOrganisation {

    static final String GOUVERNER = "accounting.govern";

    private final GardeAcces garde;

    public GardeOrganisation(GardeAcces garde) {
        this.garde = garde;
    }

    public static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    public GardeAcces.Verdict base(HttpServletRequest requete) {
        return garde.exigerTenant(requete);
    }

    public GardeAcces.Verdict gouverner(HttpServletRequest requete) {
        return garde.exigerCapacite(requete, GOUVERNER);
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
        } catch (DepotOrganisationPort.IntrouvableException e) {
            return erreur(404, e.getMessage());
        } catch (Organisation.OrganisationInvalideException e) {
            return erreur(400, e.getMessage());
        } catch (RuntimeException e) {
            return erreur(400, e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }
}
