package ca.vina.nexu.communication.infrastructure.configuration;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import ca.vina.nexu.communication.application.commandes.GestionnaireGroupesTransverses;
import ca.vina.nexu.communication.application.requetes.GroupesDeCommunication;
import ca.vina.nexu.communication.infrastructure.persistance.DepotGroupesTransverses;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import ca.vina.nexu.communication.application.commandes.BalayageSonneries;
import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsDirects;
import ca.vina.nexu.communication.application.commandes.GestionnaireAppelsGroupe;
import ca.vina.nexu.communication.application.commandes.GestionnaireBrouillonsMessages;
import ca.vina.nexu.communication.application.commandes.GestionnaireListesDistribution;
import ca.vina.nexu.communication.application.commandes.GestionnaireMessagerie;
import ca.vina.nexu.communication.application.commandes.GestionnaireReglagesAcheminement;
import ca.vina.nexu.communication.application.requetes.AcheminementParUnite;
import ca.vina.nexu.communication.application.requetes.LectureReglagesAcheminement;
import ca.vina.nexu.communication.application.requetes.LectureAppelsDirects;
import ca.vina.nexu.communication.application.requetes.LectureAppelsGroupe;
import ca.vina.nexu.communication.application.requetes.LectureBrouillonsMessages;
import ca.vina.nexu.communication.application.requetes.LectureListesDistribution;
import ca.vina.nexu.communication.application.requetes.LectureMessagerie;
import ca.vina.nexu.communication.domaine.AcheminementPort;
import ca.vina.nexu.communication.domaine.AnalysePieces;
import ca.vina.nexu.communication.infrastructure.persistance.CoffreEnBase;
import ca.vina.nexu.communication.infrastructure.persistance.CoffrePiecesJointes;
import ca.vina.nexu.communication.infrastructure.persistance.CoffreSurVolume;
import ca.vina.nexu.communication.infrastructure.persistance.ModeStockagePieces;
import ca.vina.nexu.securite.domaine.Antivirus;
import org.springframework.beans.factory.annotation.Value;
import ca.vina.nexu.communication.domaine.ConversationsPourListesPort;
import ca.vina.nexu.communication.domaine.JournalCommunicationPort;
import ca.vina.nexu.communication.infrastructure.persistance.AdaptateurConversationsPourListes;
import ca.vina.nexu.communication.infrastructure.persistance.DepotAppelsDirects;
import ca.vina.nexu.communication.infrastructure.persistance.DepotAppelsGroupe;
import ca.vina.nexu.communication.infrastructure.persistance.DepotBrouillonsMessages;
import ca.vina.nexu.communication.infrastructure.persistance.DepotListesDistribution;
import ca.vina.nexu.communication.infrastructure.persistance.DepotMessagerie;
import ca.vina.nexu.communication.infrastructure.persistance.DepotReglagesAcheminement;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.nio.file.Path;
import java.time.Clock;

/** Le câblage de la communication : messagerie, appels entre collègues, brouillons, listes de distribution. Du Java nu, assemblé ici. */
@Configuration
public class ConfigurationCommunication {

    /** Le bean des unités d'effectifs, désigné par son nom : aucune dépendance de code vers le contexte des effectifs. */
    public static final String UNITES = "adaptateurGroupesDeTravail";

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
    public DepotAppelsGroupe depotAppelsGroupe(JdbcClient jdbc) {
        return new DepotAppelsGroupe(jdbc);
    }

    @Bean
    public GestionnaireAppelsGroupe gestionnaireAppelsGroupe(LectureMessagerie messagerie, DepotAppelsGroupe depot, JournalCommunicationPort journal,
                                                             Sequence sequence, Clock horloge) {
        return new GestionnaireAppelsGroupe(messagerie, depot, new GestionnaireAppelsGroupe.Ecritures(depot, journal, sequence, horloge));
    }

    @Bean
    public LectureAppelsGroupe lectureAppelsGroupe(DepotAppelsGroupe depot, Clock horloge) {
        return new LectureAppelsGroupe(depot, horloge);
    }

    /**
     * <strong>Les groupes où l'on communique</strong> (ST-G09) : les unités et les groupes transverses. Principal : la
     * messagerie, les listes et les alertes le reçoivent. L'acheminement des appels, lui, demande les unités seules
     * ({@link #UNITES}) : on ne route pas un appel vers une garde transverse.
     */
    @Bean
    @Primary
    public GroupesDeCommunication groupesDeCommunication(@Qualifier(UNITES) GroupesDeTravailPort unites, DepotGroupesTransverses transverses, Clock horloge) {
        return new GroupesDeCommunication(unites, transverses, horloge);
    }

    @Bean
    public DepotGroupesTransverses depotGroupesTransverses(JdbcClient jdbc) {
        return new DepotGroupesTransverses(jdbc);
    }

    @Bean
    public GestionnaireGroupesTransverses gestionnaireGroupesTransverses(DepotGroupesTransverses depot) {
        // L'identifiant est tiré ici, à la frontière : le domaine le reçoit, il ne le tire jamais.
        return new GestionnaireGroupesTransverses(depot, depot, () -> java.util.UUID.randomUUID().toString());
    }

    @Bean
    public AcheminementPort acheminement(@Qualifier(UNITES) GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages) {
        return new AcheminementParUnite(groupes, reglages);
    }

    @Bean
    public LectureReglagesAcheminement lectureReglagesAcheminement(@Qualifier(UNITES) GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages) {
        return new LectureReglagesAcheminement(groupes, reglages);
    }

    @Bean
    public GestionnaireReglagesAcheminement gestionnaireReglagesAcheminement(@Qualifier(UNITES) GroupesDeTravailPort groupes, DepotReglagesAcheminement reglages,
                                                                             JournalCommunicationPort journal, Clock horloge) {
        return new GestionnaireReglagesAcheminement(new GestionnaireReglagesAcheminement.Dependances(groupes, reglages, journal), horloge);
    }

    @Bean
    public LectureMessagerie lectureMessagerie(DepotMessagerie depot, AcheminementPort acheminement, JournalCommunicationPort journal, Clock horloge) {
        return new LectureMessagerie(depot, acheminement, journal, horloge);
    }

    @Bean
    public GestionnaireMessagerie gestionnaireMessagerie(LectureMessagerie lecture, DepotMessagerie depot, JournalCommunicationPort journal,
                                                         Sequence sequence, Clock horloge, AnalysePieces antivirus) {
        return new GestionnaireMessagerie(new GestionnaireMessagerie.Dependances(lecture, depot, journal, sequence::uuid, antivirus), horloge);
    }

    /** Le sous-dossier des pièces jointes sous le volume de données, quand elles y vivent. */
    static final String DOSSIER_PIECES = "pieces-jointes";

    /**
     * Où se range le contenu des pièces jointes : un choix d'hébergement ({@code PIECES_JOINTES_STOCKAGE}). Un mode
     * inconnu arrête le démarrage — jamais un repli silencieux qui rangerait les fichiers là où personne ne les sauvegarde.
     */
    @Bean
    public CoffrePiecesJointes coffrePiecesJointes(@Value("${nexu.pieces-jointes.stockage:}") String mode, @Value("${nexu.donnees.dossier:data}") String donnees) {
        return switch (ModeStockagePieces.de(mode)) {
            case BASE -> new CoffreEnBase();
            case VOLUME -> new CoffreSurVolume(Path.of(donnees, DOSSIER_PIECES));
        };
    }

    /** L'antivirus de l'établissement, sur chaque pièce avant qu'elle ne rejoigne un fil ; injoignable, il fait échouer l'envoi. */
    @Bean
    public AnalysePieces analysePieces(Antivirus.AnalyseurFichier analyseur) {
        return contenu -> Antivirus.exigerSain(analyseur, contenu);
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
