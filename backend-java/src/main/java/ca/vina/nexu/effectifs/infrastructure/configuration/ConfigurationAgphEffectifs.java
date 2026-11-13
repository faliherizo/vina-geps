package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.ClotureAgph;
import ca.vina.nexu.effectifs.application.commandes.DecisionRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.DisponibilitesCampagneAgph;
import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePostes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRemplacementsAgph;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireRessources;
import ca.vina.nexu.effectifs.application.commandes.RevisionAgph;
import ca.vina.nexu.effectifs.application.commandes.TourDeRoleAgph;
import ca.vina.nexu.effectifs.application.requetes.LectureAgph;
import ca.vina.nexu.effectifs.application.requetes.LectureEquipages;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePostes;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotCampagnesAgphPort;
import ca.vina.nexu.effectifs.domaine.DepotEquipagesPort;
import ca.vina.nexu.effectifs.domaine.DepotRemplacementsAgphPort;
import ca.vina.nexu.effectifs.domaine.DepotSemainesTypeAgphPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurAgphEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de l'AGPH et des remplacements long terme (lot R7) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationAgphEffectifs {

    @Bean
    public LectureAgph lectureAgphEffectifs(DepotCampagnesAgphPort depot, LecturePostes postes, LectureRessources ressources, LecturePerimetres perimetres) {
        return new LectureAgph(depot, postes, ressources, perimetres);
    }

    @Bean
    public TourDeRoleAgph tourDeRoleAgphEffectifs(DepotCampagnesAgphPort depot, LectureAgph lecture, LectureRessources ressources, EvaluateurRegles regles,
                                                  GestionnairePostes postes, JournalEffectifsPort journal) {
        return new TourDeRoleAgph(depot, new TourDeRoleAgph.Collaborateurs(lecture, ressources, regles, postes), journal);
    }

    @Bean
    public GestionnaireAgph gestionnaireAgphEffectifs(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, LectureReferentiels referentiels,
                                                      LecturePerimetres perimetres, LectureRessources ressources, JournalEffectifsPort journal,
                                                      Sequence sequence) {
        return new GestionnaireAgph(depot, lecture, tour, new GestionnaireAgph.Socle(referentiels, perimetres, ressources, journal, sequence));
    }

    @Bean
    public ClotureAgph clotureAgphEffectifs(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, GestionnaireRessources ressources,
                                            GestionnairePostes postes, LecturePostes lecturePostes, LectureReferentiels referentiels,
                                            LecturePerimetres perimetres, JournalEffectifsPort journal) {
        return new ClotureAgph(depot, lecture, tour, new ClotureAgph.Gestes(ressources, postes, lecturePostes, referentiels, perimetres, journal));
    }

    @Bean
    public DecisionRemplacementsAgph decisionRemplacementsAgphEffectifs(DepotRemplacementsAgphPort depot, LecturePostes postes, LectureRessources ressources,
                                                                        LectureReferentiels referentiels, EvaluateurRegles regles,
                                                                        LectureEquipages equipages, DepotEquipagesPort places, JournalEffectifsPort journal) {
        return new DecisionRemplacementsAgph(depot, new DecisionRemplacementsAgph.Lectures(postes, ressources, referentiels, regles),
                new DecisionRemplacementsAgph.Quarts(equipages, places), journal);
    }

    @Bean
    public GestionnaireRemplacementsAgph gestionnaireRemplacementsAgphEffectifs(DepotRemplacementsAgphPort depot, DecisionRemplacementsAgph decision,
                                                                                LecturePostes postes, LectureRessources ressources,
                                                                                LecturePerimetres perimetres, JournalEffectifsPort journal,
                                                                                Sequence sequence) {
        return new GestionnaireRemplacementsAgph(depot, decision, new GestionnaireRemplacementsAgph.Socle(postes, ressources, perimetres, journal, sequence));
    }

    @Bean
    public RevisionAgph revisionAgphEffectifs(DepotCampagnesAgphPort depot, LectureAgph lecture, TourDeRoleAgph tour, LectureRessources ressources,
                                              LecturePerimetres perimetres, GestionnairePostes postes, JournalEffectifsPort journal) {
        return new RevisionAgph(depot, lecture, tour, new RevisionAgph.Appuis(ressources, perimetres, postes, journal));
    }

    @Bean
    public DisponibilitesCampagneAgph disponibilitesCampagneAgphEffectifs(DepotSemainesTypeAgphPort depot, LectureAgph lecture, LectureRessources ressources,
                                                                          LecturePerimetres perimetres, LectureReferentiels referentiels,
                                                                          EvaluateurRegles regles, JournalEffectifsPort journal) {
        return new DisponibilitesCampagneAgph(depot, lecture, new DisponibilitesCampagneAgph.Appuis(ressources, perimetres, referentiels, regles, journal));
    }

    @Bean
    public ControleurAgphEffectifs.Gestes gestesAgphEffectifs(GestionnaireAgph campagnes, ClotureAgph cloture, GestionnaireRemplacementsAgph remplacements,
                                                              RevisionAgph revision, DisponibilitesCampagneAgph disponibilites) {
        return new ControleurAgphEffectifs.Gestes(campagnes, cloture, remplacements, revision, disponibilites);
    }
}
