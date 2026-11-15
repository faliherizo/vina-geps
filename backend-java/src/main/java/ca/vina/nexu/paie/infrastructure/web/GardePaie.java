package ca.vina.nexu.paie.infrastructure.web;

import ca.vina.nexu.paie.domaine.RefusPaie;
import ca.vina.nexu.paie.domaine.Trace;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.web.JournalErreurs;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde des routes {@code /api/paie} : le module {@code paie} souscrit par l'organisme, puis la capacité
 * {@code paie.gerer} pour tout sauf lire ses propres bulletins (ADR 0076).
 */
public class GardePaie {

    public static final String MODULE = "paie";
    public static final String CAPACITE_GESTION = "paie.gerer";
    private static final int INTERDIT = 403;

    private final GardeAcces garde;
    private final ModulesActives modules;
    private final Clock horloge;

    public GardePaie(GardeAcces garde, ModulesActives modules, Clock horloge) {
        this.garde = garde;
        this.modules = modules;
        this.horloge = horloge;
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

    public GardeAcces.Verdict gestion(HttpServletRequest requete) {
        GardeAcces.Verdict v = socle(requete);
        return v.refuse() ? v : garde.exigerCapacite(requete, CAPACITE_GESTION);
    }

    /** Qui agit : le nom de connexion, ou l'identifiant du compte à défaut ; l'heure est lue ici, à la frontière. */
    Trace trace(HttpServletRequest requete, GardeAcces.Verdict v) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Trace(v.tenantId(), c.username() != null ? c.username() : c.userId(), horloge.instant().toString());
    }

    static String utilisateur(HttpServletRequest requete) {
        return GardeAcces.contexte(requete).userId();
    }

    public ResponseEntity<Object> repondre(HttpServletRequest requete, GardeAcces.Verdict v, int code, Function<Trace, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(trace(requete, v)));
        } catch (RefusPaie e) {
            JournalErreurs.refus(statutDe(e.nature()), e);
            return erreur(statutDe(e.nature()), e.getMessage());
        }
    }

    static int statutDe(RefusPaie.Nature nature) {
        return switch (nature) {
            case INVALIDE -> 400;
            case INTROUVABLE -> 404;
            case CONFLIT -> 409;
        };
    }
}
