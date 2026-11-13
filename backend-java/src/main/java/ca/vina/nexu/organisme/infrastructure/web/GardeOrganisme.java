package ca.vina.nexu.organisme.infrastructure.web;

import ca.vina.nexu.organisme.domaine.AccesEtablissement;
import ca.vina.nexu.organisme.domaine.EtablissementsPort;
import ca.vina.nexu.organisme.domaine.Modification;
import ca.vina.nexu.organisme.domaine.OptionInvalideException;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.Role;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde des options de l'organisme — jumelle de {@code requireAuth, requireTenant} puis
 * {@code requireRole("admin", "superadmin")} : session, établissement <strong>existant et ouvert</strong>
 * (un établissement suspendu ou résilié n'a plus d'options à poser), rôle le cas échéant. Puis la traduction
 * des erreurs par leur <strong>type</strong> : 422 pour une option refusée, 400 pour le reste.
 */
@Component("organismeGarde")
public class GardeOrganisme {

    private static final int INTROUVABLE = 404;
    private static final int PAIEMENT_REQUIS = 402;
    private static final int RESILIE = 410;
    private static final int INTERDIT = 403;
    private static final int INVALIDE = 422;
    private static final int REQUETE = 400;
    // `res.json(null)` écrit le littéral JSON ; un corps vide casserait le `res.json()` du front.
    private static final String JSON_NUL = "null";

    private final GardeAcces garde;
    private final EtablissementsPort etablissements;
    private final Clock horloge;

    public GardeOrganisme(GardeAcces garde, EtablissementsPort etablissements, Clock horloge) {
        this.garde = garde;
        this.etablissements = etablissements;
        this.horloge = horloge;
    }

    static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static GardeAcces.Verdict refus(int code, String message) {
        return new GardeAcces.Verdict(null, erreur(code, message));
    }

    /** Session et établissement ouvert : tout membre de l'organisme. */
    public GardeAcces.Verdict membre(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        return switch (AccesEtablissement.de(etablissements.statut(v.tenantId()))) {
            case OUVERT -> v;
            case INTROUVABLE -> refus(INTROUVABLE, "tenant not found");
            case SUSPENDU -> refus(PAIEMENT_REQUIS, "tenant suspended (payment required)");
            case RESILIE -> refus(RESILIE, "tenant terminated");
        };
    }

    /** En plus, l'administration de l'organisme — ou le superadmin, qui administre tout le système. */
    public GardeAcces.Verdict administration(HttpServletRequest requete) {
        GardeAcces.Verdict v = membre(requete);
        if (v.refuse()) {
            return v;
        }
        boolean administre = Role.de(GardeAcces.contexte(requete).role()).filter(r -> r == Role.ADMIN || r == Role.SUPERADMIN).isPresent();
        return administre ? v : refus(INTERDIT, "insufficient role");
    }

    /** Qui agit, et quand — l'heure vient de l'horloge injectée, jamais du client. */
    public Modification modification(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Modification(Iso.de(horloge.instant()), c.userId());
    }

    /** Un geste derrière un verdict ; un résultat {@code null} s'écrit {@code null}, comme {@code res.json(null)}. */
    public ResponseEntity<Object> repondre(GardeAcces.Verdict v, int code, Function<String, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Object corps = geste.apply(v.tenantId());
            if (corps == null) {
                return ResponseEntity.status(code).contentType(MediaType.APPLICATION_JSON).body(JSON_NUL);
            }
            return ResponseEntity.status(code).body(corps);
        } catch (OptionInvalideException e) {
            return erreur(INVALIDE, e.getMessage());
        } catch (RuntimeException e) {
            return erreur(REQUETE, e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }
}
