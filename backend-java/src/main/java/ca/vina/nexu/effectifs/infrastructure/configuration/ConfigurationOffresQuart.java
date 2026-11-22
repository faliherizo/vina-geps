package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.AvisDeComblement;
import ca.vina.nexu.effectifs.application.commandes.CascadeOffresQuart;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresOffres;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.ReponsesOffresQuart;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureOffresQuart;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import ca.vina.nexu.effectifs.domaine.DepotOffresQuartPort;
import ca.vina.nexu.effectifs.domaine.EcheancesOffresPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ParametresOffresPort;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurOffresQuart;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de l'offre des quarts par ancienneté et à tour de rôle (AD-07) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationOffresQuart {

    /** La cascade, et son exécuteur enregistré auprès de l'ordonnanceur dès sa création : une échéance planifiée n'est jamais sans exécuteur. */
    @Bean
    public CascadeOffresQuart cascadeOffresQuartEffectifs(DepotOffresQuartPort depot, ParametresOffresPort parametres, EcheancesOffresPort echeances,
                                                          LectureRessources ressources, DepotComblementsAbsencesPort comblements, PublicationAvis avis,
                                                          LectureEquipages equipages, JournalEffectifsPort journal, Sequence sequence) {
        CascadeOffresQuart cascade = new CascadeOffresQuart(new CascadeOffresQuart.Ports(depot, parametres, echeances),
                new CascadeOffresQuart.Collaborateurs(new AvisDeComblement(ressources, comblements, avis), equipages, journal, sequence));
        echeances.enregistrerExecuteur(cascade::executer);
        return cascade;
    }

    @Bean
    public ReponsesOffresQuart reponsesOffresQuartEffectifs(CascadeOffresQuart cascade, DepotOffresQuartPort depot, GestionnairePlaces places,
                                                            JournalEffectifsPort journal, LectureRessources ressources) {
        return new ReponsesOffresQuart(new ReponsesOffresQuart.Collaborateurs(cascade, depot, places, journal), ressources);
    }

    @Bean
    public GestionnaireParametresOffres gestionnaireParametresOffresEffectifs(ParametresOffresPort depot, JournalEffectifsPort journal) {
        return new GestionnaireParametresOffres(depot, journal);
    }

    @Bean
    public LectureOffresQuart lectureOffresQuartEffectifs(DepotOffresQuartPort depot, LectureRessources ressources, LectureAbsencesEffectifs absences,
                                                          LecturePerimetres perimetres) {
        return new LectureOffresQuart(depot, ressources, absences, perimetres);
    }

    @Bean
    public ControleurOffresQuart.Gestes gestesOffresQuartEffectifs(CascadeOffresQuart cascade, ReponsesOffresQuart reponses,
                                                                  GestionnaireParametresOffres parametres, LectureOffresQuart lecture) {
        return new ControleurOffresQuart.Gestes(cascade, reponses, parametres, lecture);
    }
}
