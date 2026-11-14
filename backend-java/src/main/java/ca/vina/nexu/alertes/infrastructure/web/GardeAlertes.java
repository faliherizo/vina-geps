package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.web.JournalErreurs;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde des routes {@code /api/alertes} : le module {@code alertes} souscrit par l'organisme, puis la
 * capacité du geste (envoyer, configurer) ; recevoir n'en exige aucune — être destinataire suffit, et
 * c'est le domaine qui en juge (R6).
 */
public class GardeAlertes {

    public static final String MODULE = "alertes";
    public static final String CAPACITE_ENVOI = "alertes.envoyer";
    public static final String CAPACITE_CONFIGURATION = "alertes.configure";
    private static final int INTERDIT = 403;
    private static final int INVALIDE = 400;

    private final GardeAcces garde;
    private final ModulesActives modules;

    public GardeAlertes(GardeAcces garde, ModulesActives modules) {
        this.garde = garde;
        this.modules = modules;
    }

    static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    public GardeAcces.Verdict socle(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse() || modules.estActive(v.tenantId(), MODULE)) {
            return v;
        }
        return new GardeAcces.Verdict(null, erreur(INTERDIT, "module '" + MODULE + "' not included in plan"));
    }

    public GardeAcces.Verdict capacite(HttpServletRequest requete, String capacite) {
        GardeAcces.Verdict v = socle(requete);
        return v.refuse() ? v : garde.exigerCapacite(requete, capacite);
    }

    /** L'acteur est le nom de connexion : c'est lui que portent les remises. */
    static Acteur acteur(HttpServletRequest requete, GardeAcces.Verdict v) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Acteur(v.tenantId(), c.username() != null ? c.username() : c.userId());
    }

    public static ResponseEntity<Object> repondre(HttpServletRequest requete, GardeAcces.Verdict v, int code, Function<Acteur, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(acteur(requete, v)));
        } catch (RefusAlerte e) {
            JournalErreurs.refus(statutDe(e.nature()), e);
            return erreur(statutDe(e.nature()), e.getMessage());
        }
    }

    static int statutDe(RefusAlerte.Nature nature) {
        return switch (nature) {
            case INVALIDE -> INVALIDE;
            case INTROUVABLE -> 404;
            case CONFLIT -> 409;
        };
    }
}
