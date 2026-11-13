package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireEquipages;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePeriodes;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePlaces;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireQuotas;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.partage.personnel.FichesPersonnelPort;
import ca.vina.nexu.effectifs.application.commandes.RepercussionPostes;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePeriodes;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureQuotas;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.DepotPeriodesPort;
import ca.vina.nexu.effectifs.domaine.DepotPostesPort;
import ca.vina.nexu.effectifs.domaine.DepotQuotasPort;
import ca.vina.nexu.effectifs.domaine.DepotRepercussionPort;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.FinDeCollectePort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurPlanificationEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de la planification des effectifs (lot R3) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationPlanificationEffectifs {

    @Bean
    public LectureRessources lectureRessourcesEffectifs(DepotRessourcesPort depot, LecturePerimetres perimetres) {
        return new LectureRessources(depot, perimetres);
    }

    @Bean
    public LecturePeriodes lecturePeriodesEffectifs(DepotPeriodesPort depot, LecturePerimetres perimetres) {
        return new LecturePeriodes(depot, perimetres);
    }

    @Bean
    public LecturePostes lecturePostesEffectifs(DepotPostesPort depot, LecturePerimetres perimetres) {
        return new LecturePostes(depot, perimetres);
    }

    @Bean
    public LectureQuotas lectureQuotasEffectifs(DepotQuotasPort depot, LecturePerimetres perimetres) {
        return new LectureQuotas(depot, perimetres);
    }

    @Bean
    public LectureEquipages lectureEquipagesEffectifs(DepotEquipagesPort depot, LecturePeriodes periodes, LectureQuotas quotas) {
        return new LectureEquipages(depot, periodes, quotas);
    }

    @Bean
    public GestionnaireRessources gestionnaireRessourcesEffectifs(FichesPersonnelPort fiches, DepotRessourcesPort depot, LectureRessources lecture,
                                                                  LectureReferentiels referentiels, LecturePerimetres perimetres, JournalEffectifsPort journal,
                                                                  Sequence sequence) {
        return new GestionnaireRessources(fiches, depot, lecture, referentiels, perimetres, journal, sequence);
    }

    /** L'ouverture de la collecte planifie sa fin à l'ordonnanceur ; la publication l'annule (P-GH-4). */
    @Bean
    public GestionnairePeriodes gestionnairePeriodesEffectifs(DepotPeriodesPort depot, LecturePeriodes lecture, LectureReferentiels referentiels,
                                                              LecturePerimetres perimetres, JournalEffectifsPort journal, Sequence sequence,
                                                              FinDeCollectePort finDeCollecte) {
        return new GestionnairePeriodes(depot, lecture, new GestionnairePeriodes.Socle(referentiels, perimetres, journal, sequence), finDeCollecte);
    }

    /** Modifier un poste répercute sa forme sur ses quarts futurs déjà générés (E-CH-3). */
    @Bean
    public RepercussionPostes repercussionPostesEffectifs(DepotRepercussionPort repercussion, DepotEquipagesPort equipages, LectureReferentiels referentiels,
                                                          JournalEffectifsPort journal, Sequence sequence) {
        return new RepercussionPostes(new RepercussionPostes.Depots(repercussion, equipages), referentiels, journal, sequence);
    }

    @Bean
    public GestionnairePostes gestionnairePostesEffectifs(DepotPostesPort depot, LecturePostes postes, LectureReferentiels referentiels,
                                                          LecturePerimetres perimetres, LectureMilieux milieux, LectureRessources ressources,
                                                          JournalEffectifsPort journal, Sequence sequence, RepercussionPostes repercussion) {
        return new GestionnairePostes(depot, new GestionnairePostes.Lectures(postes, referentiels, perimetres, milieux, ressources),
                new GestionnairePostes.Ecritures(journal, sequence, repercussion));
    }

    @Bean
    public GestionnaireQuotas gestionnaireQuotasEffectifs(DepotQuotasPort depot, LectureQuotas lecture, LectureReferentiels referentiels,
                                                          LecturePerimetres perimetres, JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireQuotas(depot, lecture, referentiels, perimetres, journal, sequence);
    }

    @Bean
    public GestionnaireEquipages gestionnaireEquipagesEffectifs(DepotEquipagesPort depot, LectureEquipages equipages, LecturePeriodes periodes,
                                                                LecturePostes postes, LectureReferentiels referentiels, LectureMilieux milieux,
                                                                JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireEquipages(depot, new GestionnaireEquipages.Lectures(equipages, periodes, postes, referentiels, milieux), journal, sequence);
    }

    @Bean
    public GestionnairePlaces gestionnairePlacesEffectifs(DepotEquipagesPort depot, LectureEquipages equipages, LectureRessources ressources,
                                                          LecturePerimetres perimetres, GestionnaireEquipages gestion, EvaluateurRegles regles,
                                                          JournalEffectifsPort journal) {
        return new GestionnairePlaces(depot, equipages, ressources, perimetres, gestion, regles, journal);
    }

    @Bean
    public ControleurPlanificationEffectifs.Lectures lecturesPlanificationEffectifs(LectureRessources ressources, LecturePeriodes periodes, LecturePostes postes,
                                                                                    LectureEquipages equipages, LectureQuotas quotas) {
        return new ControleurPlanificationEffectifs.Lectures(ressources, periodes, postes, equipages, quotas);
    }

    @Bean
    public ControleurPlanificationEffectifs.Gestes gestesPlanificationEffectifs(GestionnaireRessources ressources, GestionnairePeriodes periodes,
                                                                                GestionnairePostes postes, GestionnaireEquipages equipages,
                                                                                GestionnairePlaces places, GestionnaireQuotas quotas) {
        return new ControleurPlanificationEffectifs.Gestes(ressources, periodes, postes, equipages, places, quotas);
    }
}
