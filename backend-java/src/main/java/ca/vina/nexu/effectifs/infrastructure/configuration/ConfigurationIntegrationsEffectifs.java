package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.BoiteEnvoiIntegration;
import ca.vina.nexu.effectifs.application.requetes.LectureEchangesApi;
import ca.vina.nexu.effectifs.domaine.EchangesApiPort;
import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GestesEntrantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestesSortantsIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireConnecteursIntegration;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
import ca.vina.nexu.effectifs.application.commandes.LivraisonIntegration;
import ca.vina.nexu.effectifs.application.commandes.RelaisJournalIntegration;
import ca.vina.nexu.effectifs.application.requetes.LectureIntegrations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.AnciennetesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.BoiteEnvoiIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DepotConnecteursIntegrationPort;
import ca.vina.nexu.effectifs.domaine.DonneesIntegrationPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OrdonnanceurIntegrationPort;
import ca.vina.nexu.effectifs.domaine.SecretsIntegrationPort;
import ca.vina.nexu.effectifs.infrastructure.persistance.ClientHttpIntegration;
import ca.vina.nexu.effectifs.infrastructure.persistance.RegistreConnecteursIntegration;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurIntegrationsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

/**
 * Le câblage des intégrations des effectifs (lot R10) : du Java nu, assemblé ici. Le registre choisit, par la configuration
 * de chaque organisme, le simulateur ou le client HTTP ; le livreur est enregistré auprès de l'ordonnanceur partagé.
 */
@Configuration
public class ConfigurationIntegrationsEffectifs {

    @Bean
    public RegistreConnecteursIntegration registreConnecteursIntegrationEffectifs(SecretsIntegrationPort secrets) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(ClientHttpIntegration.DELAI).build();
        return new RegistreConnecteursIntegration(secrets, ClientHttpIntegration.reseau(client));
    }

    /** L'API d'échange de l'organisme (AD-06) : lectures machine des quarts, correctifs et relevés. */
    @Bean
    public LectureEchangesApi lectureEchangesApi(EchangesApiPort port) {
        return new LectureEchangesApi(port);
    }

    @Bean
    public LectureIntegrations lectureIntegrationsEffectifs(DepotConnecteursIntegrationPort connecteurs, BoiteEnvoiIntegrationPort boite) {
        return new LectureIntegrations(connecteurs, boite);
    }

    @Bean
    public GestionnaireConnecteursIntegration gestionnaireConnecteursIntegrationEffectifs(LectureIntegrations lecture, DepotConnecteursIntegrationPort depot,
                                                                                         JournalEffectifsPort journal) {
        return new GestionnaireConnecteursIntegration(lecture, depot, journal);
    }

    @Bean
    public BoiteEnvoiIntegration boiteEnvoiIntegrationEffectifs(LectureIntegrations lecture, BoiteEnvoiIntegrationPort boite,
                                                                OrdonnanceurIntegrationPort ordonnanceur, JournalEffectifsPort journal, Sequence sequence) {
        return new BoiteEnvoiIntegration(lecture, new BoiteEnvoiIntegration.Ecritures(boite, ordonnanceur, journal, sequence));
    }

    /** Le livreur, enregistré auprès de l'ordonnanceur dès sa création : une tâche planifiée ne reste jamais sans exécuteur. */
    @Bean
    public LivraisonIntegration livraisonIntegrationEffectifs(LectureIntegrations lecture, BoiteEnvoiIntegrationPort boite,
                                                              RegistreConnecteursIntegration registre, OrdonnanceurIntegrationPort ordonnanceur) {
        LivraisonIntegration livraison = new LivraisonIntegration(lecture, boite, registre);
        ordonnanceur.enregistrerLivreur(livraison);
        return livraison;
    }

    @Bean
    public GestesSortantsIntegration gestesSortantsIntegrationEffectifs(ExportPaieTemps paie, LecturePerimetres perimetres, LectureRessources ressources,
                                                                        DonneesIntegrationPort donnees, BoiteEnvoiIntegration boite, LectureIntegrations lecture,
                                                                        JournalEffectifsPort journal) {
        return new GestesSortantsIntegration(new GestesSortantsIntegration.Appuis(paie, perimetres, ressources, donnees), boite, lecture, journal);
    }

    @Bean
    public RelaisJournalIntegration relaisJournalIntegrationEffectifs(LectureIntegrations lecture, BoiteEnvoiIntegration boite, DonneesIntegrationPort donnees,
                                                                      DepotConnecteursIntegrationPort connecteurs, JournalEffectifsPort journal) {
        return new RelaisJournalIntegration(lecture, donnees, new RelaisJournalIntegration.Ecritures(boite, connecteurs, journal));
    }

    @Bean
    public GestesEntrantsIntegration gestesEntrantsIntegrationEffectifs(RegistreConnecteursIntegration registre, GestionnaireHorodatagesExternes horodatages,
                                                                        AnciennetesIntegrationPort anciennetes, LecturePerimetres perimetres,
                                                                        LectureIntegrations lecture, DepotConnecteursIntegrationPort connecteurs,
                                                                        JournalEffectifsPort journal) {
        return new GestesEntrantsIntegration(new GestesEntrantsIntegration.Appuis(registre, horodatages, anciennetes, perimetres), lecture, connecteurs, journal);
    }

    @Bean
    public ControleurIntegrationsEffectifs.Gestes gestesIntegrationsEffectifs(GestionnaireConnecteursIntegration connecteurs, BoiteEnvoiIntegration boite,
                                                                              GestesSortantsIntegration sortants, RelaisJournalIntegration relais,
                                                                              GestesEntrantsIntegration entrants) {
        return new ControleurIntegrationsEffectifs.Gestes(connecteurs, boite, sortants, relais, entrants);
    }
}
