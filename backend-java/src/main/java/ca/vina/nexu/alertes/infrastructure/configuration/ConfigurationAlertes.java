package ca.vina.nexu.alertes.infrastructure.configuration;

import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireAppelsAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireCascades;
import ca.vina.nexu.alertes.application.commandes.GestionnairePolitiques;
import ca.vina.nexu.alertes.application.commandes.GestionnaireTextosEntrants;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureCascades;
import ca.vina.nexu.alertes.infrastructure.persistance.AdaptateurAnnuaireAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotAppelsAlertes;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotCascades;
import ca.vina.nexu.alertes.infrastructure.persistance.DepotTextosEntrants;
import ca.vina.nexu.alertes.infrastructure.persistance.EcheancierOrdonnanceur;
import ca.vina.nexu.alertes.infrastructure.persistance.UniteDeTravailAlertesSpring;
import ca.vina.nexu.alertes.infrastructure.web.ControleurCascades;
import ca.vina.nexu.alertes.infrastructure.web.ControleurWebhooksAlertes;
import ca.vina.nexu.alertes.infrastructure.web.ControleurAlertes;
import ca.vina.nexu.alertes.infrastructure.web.GardeAlertes;
import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.domaine.LectureListesDistributionPort;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.DepotTaches;
import ca.vina.nexu.ordonnanceur.infrastructure.persistance.RegistreExecuteurs;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.disponibilite.DisponibiliteDestinatairesPort;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.web.GardeAcces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.UUID;

/** Le câblage du contexte des alertes (ADR 0074), en un seul endroit. */
@Configuration
public class ConfigurationAlertes {

    @Bean
    public DepotAlertes depotAlertes(JdbcClient jdbc) {
        return new DepotAlertes(jdbc);
    }

    @Bean
    public AdaptateurAnnuaireAlertes annuaireAlertes(GroupesDeTravailPort groupes, LectureListesDistributionPort listes, JdbcClient jdbc) {
        return new AdaptateurAnnuaireAlertes(groupes, listes, jdbc);
    }

    @Bean
    public GardeAlertes gardeAlertes(GardeAcces garde, ModulesActives modules) {
        return new GardeAlertes(garde, modules);
    }

    @Bean
    public DepotCascades depotCascades(JdbcClient jdbc) {
        return new DepotCascades(jdbc);
    }

    /** Les cascades (ST-G04) : l'échéancier durable, et ses exécuteurs branchés une fois le gestionnaire construit. */
    @Bean
    public GestionnaireCascades gestionnaireCascades(DepotCascades cascades, DepotAlertes depot, AdaptateurAnnuaireAlertes annuaire, Durables durables, Clock horloge) {
        EcheancierOrdonnanceur echeancier = new EcheancierOrdonnanceur(durables.taches(), durables.executeurs());
        GestionnaireCascades g = new GestionnaireCascades(new GestionnaireCascades.Dependances(cascades, cascades, depot, depot, annuaire),
                new GestionnaireCascades.Services(echeancier, durables.disponibilite(), new UniteDeTravailAlertesSpring(durables.transaction())), horloge);
        echeancier.brancher(g, durables.avis());
        return g;
    }

    /** L'appel automatique (ST-G06) : ses tâches durables, la voix des notifications, les rappels sous l'URL publique. */
    @Bean
    public GestionnaireAppelsAlertes gestionnaireAppelsAlertes(DepotAlertes depot, JdbcClient jdbc, ControleurAlertes.Cas cas, Durables durables,
                                                               ca.vina.nexu.partage.telephonie.AppelsVocauxPort voix, ControleurWebhooksAlertes.Reglages reglages, Clock horloge) {
        GestionnaireAppelsAlertes g = new GestionnaireAppelsAlertes(new GestionnaireAppelsAlertes.Dependances(depot, new DepotAppelsAlertes(jdbc), voix, cas.reception()),
                new GestionnaireAppelsAlertes.Rappels(reglages.urlPublique() == null ? "" : reglages.urlPublique()), () -> UUID.randomUUID().toString(), horloge);
        new EcheancierOrdonnanceur(durables.taches(), durables.executeurs()).brancherAppels(g);
        return g;
    }

    /** Ce que les cascades empruntent aux autres contextes, groupé pour garder la signature courte. */
    public record Durables(DepotTaches taches, RegistreExecuteurs executeurs, DisponibiliteDestinatairesPort disponibilite,
                           TransactionTemplate transaction, PublicationAvis avis) { }

    @Bean
    public Durables durablesAlertes(DepotTaches taches, RegistreExecuteurs executeurs, DisponibiliteDestinatairesPort disponibilite,
                                    TransactionTemplate transaction, PublicationAvis avis) {
        return new Durables(taches, executeurs, disponibilite, transaction, avis);
    }

    @Bean
    public ControleurCascades.Cas casCascades(DepotCascades cascades, DepotAlertes depot, GestionnaireCascades gestionnaire, JdbcClient jdbc, Clock horloge) {
        return new ControleurCascades.Cas(new GestionnairePolitiques(cascades, cascades, () -> UUID.randomUUID().toString(), horloge), gestionnaire,
                new LectureCascades(cascades, depot, new DepotAppelsAlertes(jdbc)));
    }

    /** La réponse par texto (ST-G05) : signée par le jeton Twilio, sous l'URL publique du déploiement s'il la connaît. */
    @Bean
    public ControleurWebhooksAlertes.Reglages reglagesWebhooksAlertes(org.springframework.core.env.Environment env) {
        String base = env.getProperty("PUBLIC_API_URL", "").trim().replaceAll("/+$", "");
        return new ControleurWebhooksAlertes.Reglages(base.isEmpty() ? null : base, env.getProperty("TWILIO_AUTH_TOKEN", ""));
    }

    @Bean
    public GestionnaireTextosEntrants gestionnaireTextosEntrants(DepotAlertes depot, JdbcClient jdbc, ControleurAlertes.Cas cas, Clock horloge) {
        return new GestionnaireTextosEntrants(depot, new DepotTextosEntrants(jdbc), cas.reception(), horloge);
    }

    /**
     * Le bordereau des utilisateurs actifs (ST-G08), lu dans les remises, les étapes de cascade et les appels ; le minimum
     * facturable du contrat (ST-H01) se règle par {@code alertes.facturation.minimum-utilisateurs}.
     */
    @Bean
    public ca.vina.nexu.alertes.application.requetes.LectureUtilisateursActifs lectureUtilisateursActifs(JdbcClient jdbc,
            @org.springframework.beans.factory.annotation.Value("${alertes.facturation.minimum-utilisateurs}") int minimumFacturable) {
        return new ca.vina.nexu.alertes.application.requetes.LectureUtilisateursActifs(new ca.vina.nexu.alertes.infrastructure.persistance.DepotTentatives(jdbc),
                new ca.vina.nexu.alertes.domaine.UtilisateursActifs.MinimumFacturable(minimumFacturable));
    }

    /** Les notes sur les destinataires (ST-G12). */
    @Bean
    public ca.vina.nexu.alertes.application.commandes.GestionnaireNotes gestionnaireNotes(JdbcClient jdbc, AdaptateurAnnuaireAlertes annuaire, Clock horloge) {
        return new ca.vina.nexu.alertes.application.commandes.GestionnaireNotes(new ca.vina.nexu.alertes.infrastructure.persistance.DepotNotes(jdbc), annuaire, horloge);
    }

    @Bean
    public ControleurAlertes.Cas casAlertes(DepotAlertes depot, AdaptateurAnnuaireAlertes annuaire, PublicationAvis avis, GestionnaireCascades cascades,
                                         DepotCascades relances, Clock horloge) {
        // L'identifiant est tiré ici, à la frontière : le domaine le reçoit, il ne le tire jamais.
        java.util.function.Supplier<String> ids = () -> UUID.randomUUID().toString();
        return new ControleurAlertes.Cas(new GestionnaireParametresAlertes(new GestionnaireParametresAlertes.Dependances(depot, depot, annuaire, horloge), ids),
                new GestionnaireEnvoiAlertes(new GestionnaireEnvoiAlertes.Dependances(depot, depot, annuaire, avis), ids, horloge, cascades),
                new GestionnaireReceptionAlertes(depot, depot, horloge, cascades), new LectureAlertes(depot, annuaire, relances));
    }
}
