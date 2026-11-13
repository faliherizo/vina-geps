package ca.vina.nexu.superadmin.infrastructure.configuration;

import ca.vina.nexu.superadmin.application.commandes.GestionnaireAdministration;
import ca.vina.nexu.superadmin.application.requetes.LectureAdministration;
import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;
import ca.vina.nexu.superadmin.infrastructure.persistance.DepotAdminsEtablissement;

import ca.vina.nexu.auth.domaine.PolitiqueMotDePasse;
import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.saas.infrastructure.persistance.DepotLocataires;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.security.SecureRandom;
import java.time.Clock;

/** Configuration déclarative : l'aléa des mots de passe provisoires et l'URL publique viennent d'ici, pas du dépôt. */
@Configuration
public class ConfigurationSuperadmin {

    @Bean
    public DepotAdminsEtablissement depotAdminsEtablissement(JdbcClient jdbc, Clock horloge, DepotComptes comptes, DepotLocataires locataires, JournalAudit audit, Courrier courrier) {
        SecureRandom alea = new SecureRandom();
        return new DepotAdminsEtablissement(jdbc, horloge, comptes, locataires, audit, courrier,
                () -> PolitiqueMotDePasse.motDePasseProvisoire(16, alea), System.getenv("PUBLIC_APP_URL"));
    }

    @Bean
    public LectureAdministration lectureAdministration(AdministrationSysteme.Etablissements etablissements, AdministrationSysteme.Administrateurs admins) {
        return new LectureAdministration(etablissements, admins);
    }

    @Bean
    public GestionnaireAdministration gestionnaireAdministration(AdministrationSysteme.Etablissements etablissements, AdministrationSysteme.Administrateurs admins, AdministrationSysteme.Transactions transactions) {
        return new GestionnaireAdministration(etablissements, admins, transactions);
    }
}
