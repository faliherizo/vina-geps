package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.FinDeCollecteAutomatique;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAlignementOrganigramme;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDisponibilites;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureAlignementOrganigramme;
import ca.vina.nexu.effectifs.application.requetes.LectureAttributionAutomatique;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotDisponibilitesPort;
import ca.vina.nexu.effectifs.domaine.FinDeCollectePort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.OrganigrammePourEffectifsPort;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage des disponibilités (lot R4) et de la liaison avec l'organigramme (lot R12) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationDisponibilitesEffectifs {

    @Bean
    public LectureDisponibilites.Lectures lecturesPlanificationPourDisponibilitesEffectifs(LectureRessources ressources, LecturePeriodes periodes,
                                                                                           LectureEquipages equipages, LecturePerimetres perimetres) {
        return new LectureDisponibilites.Lectures(ressources, periodes, equipages, perimetres);
    }

    @Bean
    public LectureDisponibilites lectureDisponibilitesEffectifs(DepotDisponibilitesPort depot, LectureDisponibilites.Lectures lectures,
                                                                EvaluateurRegles regles) {
        return new LectureDisponibilites(depot, lectures, regles);
    }

    @Bean
    public GestionnaireDisponibilites gestionnaireDisponibilitesEffectifs(LectureDisponibilites disponibilites, LectureDisponibilites.Lectures lectures,
                                                                          LectureReferentiels referentiels, DepotDisponibilitesPort depot, EvaluateurRegles regles,
                                                                          JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireDisponibilites(new GestionnaireDisponibilites.Lectures(disponibilites, lectures, referentiels),
                new GestionnaireDisponibilites.Ecritures(depot, regles, journal, sequence));
    }

    @Bean
    public GestionnaireAttributionFinDeCollecte gestionnaireAttributionFinDeCollecteEffectifs(LectureDisponibilites.Lectures lectures,
                                                                                              LectureDisponibilites disponibilites, GestionnairePlaces places,
                                                                                              JournalEffectifsPort journal) {
        return new GestionnaireAttributionFinDeCollecte(lectures, disponibilites, places, journal);
    }

    /** L'exécuteur de la fin de collecte, enregistré auprès de l'ordonnanceur dès sa création : une tâche planifiée ne reste jamais sans exécuteur. */
    @Bean
    public FinDeCollecteAutomatique finDeCollecteAutomatiqueEffectifs(GestionnairePeriodes periodes, GestionnaireAttributionFinDeCollecte attribution,
                                                                      FinDeCollectePort ordonnanceur) {
        FinDeCollecteAutomatique fin = new FinDeCollecteAutomatique(periodes, attribution);
        ordonnanceur.enregistrerExecuteur(fin::executer);
        return fin;
    }

    @Bean
    public LectureAttributionAutomatique lectureAttributionAutomatiqueEffectifs(LecturePeriodes periodes, FinDeCollectePort ordonnanceur) {
        return new LectureAttributionAutomatique(periodes, ordonnanceur);
    }

    @Bean
    public LectureAlignementOrganigramme lectureAlignementOrganigrammeEffectifs(OrganigrammePourEffectifsPort organigramme, LectureReferentiels referentiels) {
        return new LectureAlignementOrganigramme(organigramme, referentiels);
    }

    @Bean
    public GestionnaireAlignementOrganigramme gestionnaireAlignementOrganigrammeEffectifs(LectureAlignementOrganigramme lecture,
                                                                                          GestionnaireReferentiels referentiels) {
        return new GestionnaireAlignementOrganigramme(lecture, referentiels);
    }
}
