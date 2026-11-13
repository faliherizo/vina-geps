package ca.vina.nexu.effectifs.infrastructure.web;

import ca.vina.nexu.effectifs.domaine.ConflitException;
import ca.vina.nexu.effectifs.domaine.Contexte;
import ca.vina.nexu.effectifs.domaine.EffectifsInvalideException;
import ca.vina.nexu.effectifs.domaine.HorsPerimetreException;
import ca.vina.nexu.effectifs.domaine.IntrouvableException;
import ca.vina.nexu.effectifs.domaine.RefusParRegleException;
import ca.vina.nexu.effectifs.domaine.SourceIndisponibleException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ca.vina.nexu.effectifs.domaine.ModulesPort;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.temps.Iso;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * La garde des effectifs : session, établissement, <strong>module du lot</strong> (couper un module ferme
 * ses routes), capacité le cas échéant ; puis la traduction des erreurs du contexte en statuts — par leur
 * <strong>type</strong>, jamais par leur texte (jumeau de {@code http.ts} et {@code erreurs.ts}).
 */
@Component("effectifsGarde")
public class GardeEffectifs {

    /** Les capacités des effectifs (politique d'accès, identiques au Node). */
    public enum Capacite {
        CONFIGURER("effectifs.configure"), GERER("effectifs.manage");

        private final String code;

        Capacite(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    private final GardeAcces garde;
    private final ModulesPort modules;
    private final Clock horloge;

    public GardeEffectifs(GardeAcces garde, ModulesPort modules, Clock horloge) {
        this.garde = garde;
        this.modules = modules;
        this.horloge = horloge;
    }

    public static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private GardeAcces.Verdict avecModule(GardeAcces.Verdict v, String module) {
        if (v.refuse() || modules.estActive(v.tenantId(), module)) {
            return v;
        }
        return new GardeAcces.Verdict(null, erreur(403, "module '" + module + "' non activé pour cet organisme"));
    }

    /** Session, établissement et module. */
    public GardeAcces.Verdict module(HttpServletRequest requete, String module) {
        return avecModule(garde.exigerTenant(requete), module);
    }

    /** Session, établissement, module et capacité. */
    public GardeAcces.Verdict capacite(HttpServletRequest requete, String module, Capacite capacite) {
        GardeAcces.Verdict v = module(requete, module);
        return v.refuse() ? v : garde.exigerCapacite(requete, capacite.code());
    }

    /** Le contexte d'un appel : l'acteur de la session, l'établissement du verdict, l'heure de l'horloge. */
    public Contexte contexte(HttpServletRequest requete, GardeAcces.Verdict v) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return new Contexte(v.tenantId(), c.userId(), c.role(), Iso.de(horloge.instant()));
    }

    /** Un geste derrière un verdict, et ses erreurs traduites par leur type. */
    public ResponseEntity<Object> repondre(HttpServletRequest requete, GardeAcces.Verdict v, int code, Function<Contexte, Object> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(contexte(requete, v)));
        } catch (RefusParRegleException e) {
            // Le refus garde la trace de l'évaluation, comme le Node, qui la valide avant de refuser.
            return ResponseEntity.status(409).body(Map.of("errors", List.of(e.getMessage()), "verdict", e.verdict()));
        } catch (RuntimeException e) {
            annulerLaTransaction();
            return erreur(statutDe(e), e.getMessage() == null ? "erreur inattendue" : e.getMessage());
        }
    }

    /** Une erreur rendue n'est pas une erreur levée : sans ceci, la transaction de l'appel validerait un geste à moitié fait. */
    private static void annulerLaTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /** Le statut HTTP d'une erreur du contexte, par son type ; 400 pour une erreur venue d'ailleurs. */
    static int statutDe(RuntimeException e) {
        return switch (e) {
            case EffectifsInvalideException x -> 422;
            case IntrouvableException x -> 404;
            case ConflitException x -> 409;
            case HorsPerimetreException x -> 403;
            // Un système externe (RAO, BD Corpo) n'a pas répondu : ce n'est pas la demande qui est fautive.
            case SourceIndisponibleException x -> 502;
            default -> 400;
        };
    }
}
