package ca.vina.nexu.organisme.infrastructure.configuration;

import ca.vina.nexu.auth.domaine.RestrictionPaysConnexion;
import ca.vina.nexu.organisme.application.commandes.DeclencheurAlertes;
import ca.vina.nexu.organisme.application.commandes.GestionnaireCharteOrganisme;
import ca.vina.nexu.organisme.application.commandes.GestionnairePaysAutorises;
import ca.vina.nexu.organisme.application.commandes.GestionnaireReglesAlerte;
import ca.vina.nexu.organisme.application.requetes.GardeConnexionPays;
import ca.vina.nexu.organisme.application.requetes.LectureOptionsOrganisme;
import ca.vina.nexu.organisme.domaine.ChartesPort;
import ca.vina.nexu.organisme.domaine.JournalAlertesPort;
import ca.vina.nexu.organisme.domaine.PaysAutorisesPort;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.domaine.ReglesAlertePort;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Executors;

/**
 * Le câblage du contexte {@code organisme}. Les cas d'utilisation sont du Java nu ; {@code PAYS_ENTETE} se lit
 * ici et nulle part ailleurs. La garde de connexion est offerte au contexte {@code auth} par son port
 * {@link RestrictionPaysConnexion} : c'est l'organisme qui dépend de l'authentification, jamais l'inverse.
 */
@Configuration
public class ConfigurationOrganisme {

    @Bean
    public ReglagesPays organismeReglagesPays(@Value("${PAYS_ENTETE:}") String nomEntete) {
        return new ReglagesPays(nomEntete);
    }

    @Bean
    public LectureOptionsOrganisme organismeLectureOptions(ChartesPort chartes, PaysAutorisesPort pays, ReglesAlertePort regles,
                                                          JournalAlertesPort journal, ReglagesPays reglages) {
        return new LectureOptionsOrganisme(chartes, pays, regles, journal, reglages);
    }

    @Bean
    public GestionnaireCharteOrganisme organismeGestionnaireCharte(ChartesPort chartes, LectureOptionsOrganisme lecture) {
        return new GestionnaireCharteOrganisme(chartes, lecture);
    }

    @Bean
    public GestionnairePaysAutorises organismeGestionnairePays(PaysAutorisesPort pays, LectureOptionsOrganisme lecture, ReglagesPays reglages) {
        return new GestionnairePaysAutorises(pays, lecture, reglages);
    }

    @Bean
    public GestionnaireReglesAlerte organismeGestionnaireRegles(ReglesAlertePort regles, Sequence sequence) {
        return new GestionnaireReglesAlerte(regles, sequence);
    }

    /** Un fil virtuel par alerte : l'envoi attend le transport, jamais la requête qui l'a déclenché. */
    @Bean
    public SignalementDiffere organismeSignalement(ReglesAlertePort regles, JournalAlertesPort journal, Courrier courrier, Sequence sequence) {
        return new SignalementDiffere(new DeclencheurAlertes(regles, journal, courrier, sequence), Executors.newVirtualThreadPerTaskExecutor());
    }

    @Bean
    public GardeConnexionPays organismeGardeConnexionPays(PaysAutorisesPort pays, SignalementDiffere signalement, ReglagesPays reglages, Clock horloge) {
        return new GardeConnexionPays(pays, signalement, reglages, horloge);
    }

    @Bean
    public RestrictionPaysConnexion organismeRestrictionPaysConnexion(GardeConnexionPays garde) {
        return garde::refus;
    }
}
