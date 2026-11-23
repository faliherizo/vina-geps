package ca.vina.nexu.paie.infrastructure.configuration;

import ca.vina.nexu.paie.application.commandes.GestionnaireCyclesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireDossiersPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireReglesPaie;
import ca.vina.nexu.paie.application.commandes.GestionnaireValidationTemps;
import ca.vina.nexu.paie.application.requetes.DocumentsPaie;
import ca.vina.nexu.paie.application.requetes.LecturePaie;
import ca.vina.nexu.paie.application.requetes.LectureVariablesPaie;
import ca.vina.nexu.paie.infrastructure.persistance.BaremesFichiers;
import ca.vina.nexu.paie.infrastructure.persistance.DepotPaie;
import ca.vina.nexu.paie.infrastructure.persistance.DepotReglesPaie;
import ca.vina.nexu.paie.infrastructure.persistance.DepotValidationsTemps;
import ca.vina.nexu.paie.infrastructure.persistance.UniteDeTravailSpring;
import ca.vina.nexu.paie.infrastructure.web.ControleurPaie;
import ca.vina.nexu.paie.infrastructure.web.GardePaie;
import ca.vina.nexu.paie.infrastructure.web.RenduPdfPaie;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.modules.ModulesActives;
import ca.vina.nexu.partage.paie.HeuresApprouveesPort;
import ca.vina.nexu.partage.paie.PersonnesPourPaiePort;
import ca.vina.nexu.partage.web.GardeAcces;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

/** Le câblage du contexte de la paie (ADR 0076, 0084), en un seul endroit. */
@Configuration
public class ConfigurationPaie {

    @Bean
    public DepotPaie depotPaie(JdbcClient jdbc) {
        return new DepotPaie(jdbc);
    }

    @Bean
    public DepotReglesPaie depotReglesPaie(JdbcClient jdbc) {
        return new DepotReglesPaie(jdbc);
    }

    @Bean
    public DepotValidationsTemps depotValidationsTemps(JdbcClient jdbc) {
        return new DepotValidationsTemps(jdbc);
    }

    @Bean
    public GardePaie gardePaie(GardeAcces garde, ModulesActives modules, Clock horloge) {
        return new GardePaie(garde, modules, horloge);
    }

    @Bean
    public LecturePaie lecturePaie(DepotPaie depot, PersonnesPourPaiePort personnes) {
        return new LecturePaie(depot, new BaremesFichiers(), personnes);
    }

    @Bean
    public LectureVariablesPaie lectureVariablesPaie(DepotReglesPaie regles, DepotValidationsTemps validations, HeuresApprouveesPort heures,
                                                     PersonnesPourPaiePort personnes, LecturePaie lecture) {
        return new LectureVariablesPaie(new LectureVariablesPaie.Dependances(regles, validations, heures, personnes, lecture));
    }

    @Bean
    public GestionnaireReglesPaie gestionnaireReglesPaie(LectureVariablesPaie lecture, DepotReglesPaie regles, Sequence sequence) {
        return new GestionnaireReglesPaie(lecture, regles, sequence);
    }

    @Bean
    public GestionnaireValidationTemps gestionnaireValidationTemps(LectureVariablesPaie lecture, HeuresApprouveesPort heures,
                                                                   DepotValidationsTemps validations, TransactionTemplate transaction, Sequence sequence) {
        return new GestionnaireValidationTemps(new GestionnaireValidationTemps.Dependances(lecture, heures, validations,
                new UniteDeTravailSpring(transaction), sequence));
    }

    @Bean
    public ControleurPaie.Cas casPaie(DepotPaie depot, LecturePaie lecture, LectureVariablesPaie variables, HeuresApprouveesPort heures,
                                      PersonnesPourPaiePort personnes, TransactionTemplate transaction, Sequence sequence) {
        GestionnaireCyclesPaie cycles = new GestionnaireCyclesPaie(new GestionnaireCyclesPaie.Dependances(lecture, variables, depot, heures,
                new UniteDeTravailSpring(transaction), sequence));
        return new ControleurPaie.Cas(new GestionnaireDossiersPaie(depot, personnes), cycles, lecture,
                new DocumentsPaie(lecture, depot, new RenduPdfPaie()));
    }
}
