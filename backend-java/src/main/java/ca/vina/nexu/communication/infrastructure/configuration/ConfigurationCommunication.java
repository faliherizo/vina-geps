package ca.vina.nexu.communication.infrastructure.configuration;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.application.commandes.BalayageSonneries;
import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsDirects;
import ca.vina.nexu.communication.application.commandes.GestionnaireBrouillonsMessages;
import ca.vina.nexu.communication.application.commandes.GestionnaireListesDistribution;
import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.commandes.GestionnaireReglagesAcheminement;
import ca.vina.nexu.communication.application.requetes.AcheminementParUnite;
import ca.vina.nexu.communication.application.requetes.LectureReglagesAcheminement;
import ca.vina.nexu.communication.application.requetes.LectureAppelsDirects;
import ca.vina.nexu.communication.application.requetes.LectureBrouillonsMessages;
import ca.vina.nexu.communication.application.requetes.LectureListesDistribution;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.infrastructure.persistance.AdaptateurConversationsPourListes;
import ca.vina.nexu.communication.infrastructure.persistance.DepotAppelsDirects;
import ca.vina.nexu.communication.infrastructure.persistance.DepotBrouillonsMessages;
import ca.vina.nexu.communication.infrastructure.persistance.DepotListesDistribution;
import ca.vina.nexu.communication.infrastructure.persistance.DepotMessagerie;
import ca.vina.nexu.communication.infrastructure.persistance.DepotReglagesAcheminement;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;

/** Le câblage de la communication : messagerie, appels entre collègues, brouillons, listes de distribution. Du Java nu, assemblé ici. */
@Configuration
public class ConfigurationCommunication {

    @Bean
    public DepotAppelsDirects depotAppelsDirects(JdbcClient jdbc) {
        return new DepotAppelsDirects(jdbc);
    }

    @Bean
    public BalayageSonneries balayageSonneries(DepotAppelsDirects depot, JournalCommunicationPort journal) {
        return new BalayageSonneries(depot, depot, journal);
    }

    @Bean
    public GestionnaireAppelsDirects gestionnaireAppelsDirects(DepotAppelsDirects depot, BalayageSonneries balayage, JournalCommunicationPort journal,
                                                                      Sequence sequence, Clock horloge) {
        return new GestionnaireAppelsDirects(depot, balayage, new GestionnaireAppelsDirects.Ecritures(depot, journal, sequence, horloge));
    }

    @Bean
    public LectureAppelsDirects lectureAppelsDirects(DepotAppelsDirects depot, BalayageSonneries balayage, Clock horloge) {
        return new LectureAppelsDirects(depot, balayage, horloge);
    }

    @Bean
    public AcheminementPort acheminement(GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages) {
        return new AcheminementParUnite(groupes, reglages);
    }

    @Bean
    public LectureReglagesAcheminement lectureReglagesAcheminement(GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages) {
        return new LectureReglagesAcheminement(groupes, reglages);
    }

    @Bean
    public GestionnaireReglagesAcheminement gestionnaireReglagesAcheminement(GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages,
                                                                             JournalCommunicationPort journal, Clock horloge) {
        return new GestionnaireReglagesAcheminement(new GestionnaireReglagesAcheminement.Dependances(groupes, reglages, journal), horloge);
    }

    @Bean
    public LectureMessagerie lectureMessagerie(DepotMessagerie depot, AcheminementPort acheminement, JournalCommunicationPort journal, Clock horloge) {
        return new LectureMessagerie(depot, acheminement, journal, horloge);
    }

    @Bean
    public GestionnaireMessagerie gestionnaireMessagerie(LectureMessagerie lecture, DepotMessagerie depot, JournalCommunicationPort journal,
                                                         Sequence sequence, Clock horloge) {
        return new GestionnaireMessagerie(new GestionnaireMessagerie.Dependances(lecture, depot, journal, sequence::uuid), horloge);
    }

    @Bean
    public ConversationsPourListesPort conversationsPourListes(LectureMessagerie lecture, GestionnaireMessagerie gestionnaire) {
        return new AdaptateurConversationsPourListes(lecture, gestionnaire);
    }

    @Bean
    public DepotBrouillonsMessages depotBrouillonsMessages(JdbcClient jdbc) {
        return new DepotBrouillonsMessages(jdbc);
    }

    @Bean
    public GestionnaireBrouillonsMessages gestionnaireBrouillonsMessages(ConversationsPourListesPort conversations, DepotBrouillonsMessages depot,
                                                                                Sequence sequence, Clock horloge) {
        return new GestionnaireBrouillonsMessages(conversations, depot, sequence, horloge);
    }

    @Bean
    public LectureBrouillonsMessages lectureBrouillonsMessages(DepotBrouillonsMessages depot) {
        return new LectureBrouillonsMessages(depot);
    }

    @Bean
    public DepotListesDistribution depotListesDistribution(JdbcClient jdbc, Sequence sequence, GroupesDeTravailPort groupes) {
        return new DepotListesDistribution(jdbc, sequence, groupes);
    }

    @Bean
    public LectureListesDistribution lectureListesDistribution(DepotListesDistribution depot) {
        return new LectureListesDistribution(depot);
    }

    @Bean
    public GestionnaireListesDistribution gestionnaireListesDistribution(DepotListesDistribution depot, ConversationsPourListesPort conversations,
                                                                                JournalCommunicationPort journal, LectureListesDistribution lecture,
                                                                                Sequence sequence, Clock horloge) {
        return new GestionnaireListesDistribution(new GestionnaireListesDistribution.Dependances(depot, depot, conversations, journal), lecture, sequence, horloge);
    }
}
