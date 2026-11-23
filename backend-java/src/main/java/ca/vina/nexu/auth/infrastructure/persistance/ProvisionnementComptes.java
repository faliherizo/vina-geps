package ca.vina.nexu.auth.infrastructure.persistance;

import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Les comptes que le Node assure au démarrage ({@code provisionDefaultAdmin}) : un {@code admin}
 * sur une base vide, et un {@code superadmin} toujours — idempotent. Sans lui, un Java démarré
 * seul sur une base neuve n'a personne pour se connecter.
 */
@Component
public class ProvisionnementComptes {

    /** Le mode d'essai (campagnes e2e, BDD, banc de charge) garde des comptes amorcés prêts à l'emploi. */
    static final String PROPRIETE_ESSAI = "nexu.essai";

    private final DepotComptes comptes;
    private final JournalAudit audit;
    private final Environment env;

    public ProvisionnementComptes(DepotComptes comptes, JournalAudit audit, Environment env) {
        this.comptes = comptes;
        this.audit = audit;
        this.env = env;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        assurer();
    }

    public void assurer() {
        if (comptes.lister().isEmpty()) {
            creer("admin", env.getProperty("ADMIN_USERNAME", "admin"), env.getProperty("ADMIN_PASSWORD"));
        }
        String superadmin = env.getProperty("SUPERADMIN_USERNAME", "superadmin");
        if (comptes.parNom(superadmin, null).isEmpty()) {
            creer("superadmin", superadmin, env.getProperty("SUPERADMIN_PASSWORD"));
        }
    }

    /**
     * Sans mot de passe fourni, le compte reçoit celui de son rôle — connu de tous (tests d'intrusion du 22 novembre 2026,
     * PT-03). Hors du mode d'essai, il doit alors être changé à la première connexion : une pile montée sans ses secrets
     * n'offre plus un accès administrateur à qui devine {@code admin/admin}.
     */
    private void creer(String role, String nom, String motDePasse) {
        boolean parDefaut = motDePasse == null || motDePasse.isBlank() || motDePasse.equals(role);
        boolean aChanger = parDefaut && !env.getProperty(PROPRIETE_ESSAI, Boolean.class, false);
        comptes.creer(new DepotComptes.Creation(nom, parDefaut ? role : motDePasse, role, null, null, aChanger, false, null, false));
        audit.consigner(null, nom, "auth.provision", nom, Map.of());
    }
}
