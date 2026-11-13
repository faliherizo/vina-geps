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
            creer(env.getProperty("ADMIN_USERNAME", "admin"), env.getProperty("ADMIN_PASSWORD", "admin"), "admin");
        }
        String superadmin = env.getProperty("SUPERADMIN_USERNAME", "superadmin");
        if (comptes.parNom(superadmin, null).isEmpty()) {
            creer(superadmin, env.getProperty("SUPERADMIN_PASSWORD", "superadmin"), "superadmin");
        }
    }

    private void creer(String nom, String motDePasse, String role) {
        comptes.creer(new DepotComptes.Creation(nom, motDePasse, role, null, null, false, false, null, false));
        audit.consigner(null, nom, "auth.provision", nom, Map.of());
    }
}
