package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.domaine.Appelant;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.partage.web.JournalErreurs;
import ca.vina.nexu.partage.modules.ModulesActives;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde des routes de la communication : session, établissement, puis **module**
 * {@code effectifs_communication} (refus par défaut, ADR 0063). Les refus du domaine se traduisent
 * en statut par leur <strong>nature</strong>, jamais par leur texte.
 */
@Component
public class GardeCommunication {

    public static final String MODULE = "effectifs_communication";
    /** La capacité qui règle l'acheminement d'une unité : celle qui planifie et gère ses effectifs. */
    public static final String CAPACITE_GESTION = "effectifs.manage";
    private static final int INTERDIT = 403;
    private static final int INTROUVABLE = 404;
    private static final int CONFLIT = 409;
    private static final int INVALIDE = 400;

    private final GardeAcces garde;
    private final ModulesActives modules;

    public GardeCommunication(GardeAcces garde, ModulesActives modules) {
        this.garde = garde;
        this.modules = modules;
    }

    public static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    /** Session, établissement et module de la communication. */
    public GardeAcces.Verdict socle(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse() || modules.estActive(v.tenantId(), MODULE)) {
            return v;
        }
        return new GardeAcces.Verdict(null, erreur(INTERDIT, "module '" + MODULE + "' not included in plan"));
    }

    /** Le socle, puis la gestion des effectifs : régler ce qui touche toute une unité n'est pas l'affaire de chacun. */
    public GardeAcces.Verdict gestion(HttpServletRequest requete) {
        GardeAcces.Verdict v = socle(requete);
        return v.refuse() ? v : garde.exigerCapacite(requete, CAPACITE_GESTION);
    }

    /** L'acteur : le nom de connexion, l'identifiant de compte seulement pour une session qui n'en a pas. */
    public static String acteurDe(ContexteRequete c) {
        return c.username() != null ? c.username() : c.userId();
    }

    public static Appelant appelant(HttpServletRequest requete, GardeAcces.Verdict v) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Appelant(v.tenantId(), acteurDe(c), c.role(), c.employeeId());
    }

    /** Un geste derrière un verdict ; un refus du domaine devient son statut, toute autre erreur un 400 nommé. */
    public static ResponseEntity<Object> repondre(HttpServletRequest requete, GardeAcces.Verdict v, int code, Function<Appelant, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(appelant(requete, v)));
        } catch (RefusCommunication e) {
            JournalErreurs.refus(statutDe(e.nature()), e);
            return erreur(statutDe(e.nature()), e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            JournalErreurs.refus(INVALIDE, e);
            return erreur(INVALIDE, e.getMessage() == null ? "requête invalide" : e.getMessage());
        }
    }

    static int statutDe(RefusCommunication.Nature nature) {
        return switch (nature) {
            case INVALIDE -> INVALIDE;
            case INTERDIT -> INTERDIT;
            case INTROUVABLE -> INTROUVABLE;
            case CONFLIT -> CONFLIT;
        };
    }
}
