package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.domaine.SoldesBanquesPort;
import ca.vina.nexu.effectifs.application.commandes.CascadeOffresQuart;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAttributionFinDeCollecte;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireComblementAbsences;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.SuiviComblementAbsence;
import ca.vina.nexu.effectifs.application.requetes.LectureAbsencesEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureDisponibilites;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotAbsencesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.DepotComblementsAbsencesPort;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.ModulesPort;
import ca.vina.nexu.effectifs.domaine.PlanningAbsencesEffectifsPort;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage des absences d'effectifs (lot R5) et de leur comblement (lot U5) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationAbsencesEffectifs {

    @Bean
    public LectureAbsencesEffectifs lectureAbsencesEffectifs(DepotAbsencesEffectifsPort depot, PlanningAbsencesEffectifsPort planning,
                                                             LectureRessources ressources, LecturePerimetres perimetres) {
        return new LectureAbsencesEffectifs(depot, planning, ressources, perimetres);
    }

    @Bean
    public GestionnaireComblementAbsences gestionnaireComblementAbsencesEffectifs(LectureEquipages equipages, LectureDisponibilites disponibilites,
                                                                                  LectureRessources ressources, LectureReferentiels referentiels,
                                                                                  DepotComblementsAbsencesPort depot, GestionnaireAttributionFinDeCollecte attribution,
                                                                                  PublicationAvis avis, JournalEffectifsPort journal, Sequence sequence,
                                                                                  CascadeOffresQuart cascade) {
        return new GestionnaireComblementAbsences(new GestionnaireComblementAbsences.Lectures(equipages, disponibilites, ressources, referentiels),
                new GestionnaireComblementAbsences.Ecritures(depot, attribution, avis, journal, sequence, cascade));
    }

    @Bean
    public SuiviComblementAbsence suiviComblementAbsenceEffectifs(DepotComblementsAbsencesPort comblements, LecturePerimetres perimetres, LectureRessources ressources,
                                                                  LecturePostes postes, DepotRemplacementsAgphPort remplacements,
                                                                  GestionnaireRemplacementsAgph gestionnaire, ModulesPort modules, JournalEffectifsPort journal,
                                                                  GestionnaireComblementAbsences comblement) {
        return new SuiviComblementAbsence(new SuiviComblementAbsence.Lectures(comblements, perimetres, ressources, postes),
                new SuiviComblementAbsence.Remplacements(remplacements, gestionnaire, modules, journal), comblement);
    }

    @Bean
    public GestionnaireAbsencesEffectifs gestionnaireAbsencesEffectifs(DepotAbsencesEffectifsPort depot, PlanningAbsencesEffectifsPort planning,
                                                                       DepotEquipagesPort equipages, GestionnaireComblementAbsences comblement,
                                                                       LectureAbsencesEffectifs absences, LectureRessources ressources,
                                                                       LectureReferentiels referentiels, LecturePerimetres perimetres, EvaluateurRegles regles,
                                                                       JournalEffectifsPort journal, Sequence sequence, SoldesBanquesPort soldes) {
        return new GestionnaireAbsencesEffectifs(new GestionnaireAbsencesEffectifs.Ports(depot, planning, equipages, comblement),
                new GestionnaireAbsencesEffectifs.Lectures(absences, ressources, referentiels, perimetres, soldes), regles, journal, sequence);
    }
}
