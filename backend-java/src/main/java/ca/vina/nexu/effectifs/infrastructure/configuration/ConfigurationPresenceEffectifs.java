package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireDecisionsPresence;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireHorodatagesExternes;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireFluxApprobations;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePointagesEffectifs;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireProfilsPointage;
import ca.vina.nexu.effectifs.application.commandes.QualificationPointages;
import ca.vina.nexu.effectifs.application.requetes.LectureAuditLocalisations;
import ca.vina.nexu.effectifs.application.requetes.LectureProfilsPointage;
import ca.vina.nexu.effectifs.application.commandes.RestrictionModificationHeures;
import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.LectureMilieux;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LecturePointagesADecider;
import ca.vina.nexu.effectifs.application.requetes.LecturePresenceEffectifs;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.domaine.DepotMilieuxPort;
import ca.vina.nexu.effectifs.domaine.EcriturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.EcritureProfilsPointagePort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LecturePointagesEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureProfilsPointagePort;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurPresenceEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage de la présence des effectifs (lot R8) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationPresenceEffectifs {

    @Bean
    public LectureQuartsPresence lectureQuartsPresenceEffectifs(LecturePointagesEffectifsPort depot, LectureReferentiels referentiels) {
        return new LectureQuartsPresence(depot, referentiels);
    }

    @Bean
    public LecturePresenceEffectifs lecturePresenceEffectifs(LecturePointagesEffectifsPort depot, LectureQuartsPresence quarts, LectureRessources ressources,
                                                             LecturePerimetres perimetres, LectureMilieux milieux, DepotMilieuxPort secrets) {
        return new LecturePresenceEffectifs(depot, quarts, new LecturePresenceEffectifs.Socle(ressources, perimetres, milieux, secrets));
    }

    @Bean
    public GestionnairePointagesEffectifs gestionnairePointagesEffectifs(LecturePresenceEffectifs presence, LectureQuartsPresence quarts,
                                                                         LectureRessources ressources, LecturePerimetres perimetres, LectureMilieux milieux,
                                                                         DepotMilieuxPort secrets, EcriturePointagesEffectifsPort depot, EvaluateurRegles regles,
                                                                         JournalEffectifsPort journal, Sequence sequence,
                                                                         GestionnairePointagesEffectifs.Approbation approbation,
                                                                         QualificationPointages qualification) {
        return new GestionnairePointagesEffectifs(new GestionnairePointagesEffectifs.Lectures(presence, quarts, ressources, perimetres, milieux, secrets,
                qualification),
                new GestionnairePointagesEffectifs.Ecritures(depot, regles, journal, sequence), approbation);
    }

    @Bean
    public GestionnairePointagesEffectifs.Approbation approbationPointagesEffectifs(RestrictionModificationHeures restriction, GestionnaireFluxApprobations flux) {
        return new GestionnairePointagesEffectifs.Approbation(restriction, flux);
    }

    @Bean
    public LecturePointagesADecider lecturePointagesADeciderEffectifs(LecturePresenceEffectifs presence, LectureFluxApprobations flux) {
        return new LecturePointagesADecider(presence, flux);
    }

    @Bean
    public GestionnaireDecisionsPresence gestionnaireDecisionsPresenceEffectifs(LecturePointagesADecider lecture, LectureRessources ressources,
                                                                                GestionnaireFluxApprobations flux, EcriturePointagesEffectifsPort depot,
                                                                                JournalEffectifsPort journal) {
        return new GestionnaireDecisionsPresence(lecture, ressources, flux, new GestionnaireDecisionsPresence.Ecritures(depot, journal));
    }

    @Bean
    public GestionnaireHorodatagesExternes gestionnaireHorodatagesExternesEffectifs(LecturePointagesEffectifsPort pointages, LectureRessources ressources,
                                                                                    LecturePerimetres perimetres, EcriturePointagesEffectifsPort depot,
                                                                                    JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireHorodatagesExternes(new GestionnaireHorodatagesExternes.Lectures(pointages, ressources, perimetres), depot, journal, sequence);
    }

    @Bean
    public ControleurPresenceEffectifs.Gestes gestesPresenceEffectifs(GestionnairePointagesEffectifs pointages, GestionnaireDecisionsPresence decisions,
                                                                      GestionnaireHorodatagesExternes horodatages, GestionnaireProfilsPointage profils) {
        return new ControleurPresenceEffectifs.Gestes(pointages, decisions, horodatages, profils);
    }

    @Bean
    public LectureProfilsPointage lectureProfilsPointageEffectifs(LectureProfilsPointagePort depot, EvaluateurRegles regles, LectureRessources ressources) {
        return new LectureProfilsPointage(depot, regles, ressources);
    }

    @Bean
    public GestionnaireProfilsPointage gestionnaireProfilsPointageEffectifs(LectureProfilsPointage lire, EcritureProfilsPointagePort depot,
                                                                            JournalEffectifsPort journal) {
        return new GestionnaireProfilsPointage(lire, depot, journal);
    }

    @Bean
    public QualificationPointages qualificationPointagesEffectifs(LectureProfilsPointage profils, LectureReferentiels referentiels) {
        return new QualificationPointages(profils, referentiels);
    }

    @Bean
    public ControleurPresenceEffectifs.Lectures lecturesPresenceEffectifs(LecturePresenceEffectifs presence, LectureProfilsPointage profils) {
        return new ControleurPresenceEffectifs.Lectures(presence, profils, new LectureAuditLocalisations(presence));
    }
}
