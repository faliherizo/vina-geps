package ca.vina.nexu.effectifs.infrastructure.configuration;

import ca.vina.nexu.effectifs.application.commandes.EvaluateurRegles;
import ca.vina.nexu.effectifs.application.commandes.ExportPaieTemps;
import ca.vina.nexu.effectifs.application.commandes.GenerationSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireApprobationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireFluxApprobations;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireParametresTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnairePreparationTemps;
import ca.vina.nexu.effectifs.application.commandes.GestionnaireSegmentsTemps;
import ca.vina.nexu.effectifs.application.commandes.RestrictionModificationHeures;
import ca.vina.nexu.effectifs.application.requetes.CalculFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.FeuillesPourPaie;
import ca.vina.nexu.effectifs.application.requetes.LectureFeuillesTemps;
import ca.vina.nexu.effectifs.application.requetes.LectureFluxApprobations;
import ca.vina.nexu.effectifs.application.requetes.LecturePerimetres;
import ca.vina.nexu.effectifs.application.requetes.LectureQuartsPresence;
import ca.vina.nexu.effectifs.application.requetes.LectureReferentiels;
import ca.vina.nexu.effectifs.application.requetes.LectureRessources;
import ca.vina.nexu.effectifs.application.requetes.RapportsTemps;
import ca.vina.nexu.effectifs.application.requetes.SourcesFeuillesTemps;
import ca.vina.nexu.effectifs.domaine.DepotRessourcesPort;
import ca.vina.nexu.effectifs.domaine.EcritureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.JournalEffectifsPort;
import ca.vina.nexu.effectifs.domaine.LectureFeuillesTempsPort;
import ca.vina.nexu.effectifs.domaine.SourcesTempsEffectifsPort;
import ca.vina.nexu.effectifs.infrastructure.web.ControleurTempsEffectifs;
import ca.vina.nexu.partage.identifiants.domaine.Sequence;
import ca.vina.nexu.partage.temps.Iso;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Le câblage des feuilles de temps des effectifs (lot R9) : du Java nu, assemblé ici. */
@Configuration
public class ConfigurationTempsEffectifs {

    @Bean
    public LectureFeuillesTemps lectureFeuillesTempsEffectifs(LectureFeuillesTempsPort depot, LectureRessources ressources, LecturePerimetres perimetres,
                                                              LectureReferentiels referentiels) {
        return new LectureFeuillesTemps(depot, ressources, perimetres, referentiels);
    }

    @Bean
    public SourcesFeuillesTemps sourcesFeuillesTempsEffectifs(SourcesTempsEffectifsPort depot, LectureQuartsPresence quarts) {
        return new SourcesFeuillesTemps(depot, quarts);
    }

    @Bean
    public CalculFeuillesTemps calculFeuillesTempsEffectifs(LectureFeuillesTemps lecture, SourcesFeuillesTemps sources, EvaluateurRegles regles,
                                                            LectureFluxApprobations flux) {
        return new CalculFeuillesTemps(lecture, sources, regles, flux);
    }

    @Bean
    public GenerationSegmentsTemps generationSegmentsTempsEffectifs(LectureFeuillesTemps lecture, SourcesFeuillesTemps sources) {
        return new GenerationSegmentsTemps(lecture, sources);
    }

    @Bean
    public RapportsTemps rapportsTempsEffectifs(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul) {
        return new RapportsTemps(lecture, calcul);
    }

    @Bean
    public ExportPaieTemps exportPaieTempsEffectifs(LectureFeuillesTemps lecture, JournalEffectifsPort journal) {
        return new ExportPaieTemps(lecture, journal);
    }

    /** Les feuilles approuvées et le verrou PAYEE, offerts à la paie par les ports de {@code partage.paie} (ADR 0076). */
    @Bean
    public FeuillesPourPaie feuillesPourPaie(LectureFeuillesTemps lecture, ExportPaieTemps export, EcritureFeuillesTempsPort depot,
                                             JournalEffectifsPort journal, DepotRessourcesPort ressources, Clock horloge) {
        return new FeuillesPourPaie(new FeuillesPourPaie.Dependances(lecture, export, depot, journal, ressources), () -> Iso.de(horloge.instant()));
    }

    @Bean
    public GestionnaireParametresTemps gestionnaireParametresTempsEffectifs(LectureFeuillesTemps lecture, EcritureFeuillesTempsPort depot, JournalEffectifsPort journal) {
        return new GestionnaireParametresTemps(lecture, depot, journal);
    }

    @Bean
    public GestionnairePreparationTemps gestionnairePreparationTempsEffectifs(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul,
                                                                              GenerationSegmentsTemps generation, LecturePerimetres perimetres,
                                                                              EcritureFeuillesTempsPort depot, JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnairePreparationTemps(new GestionnairePreparationTemps.Lectures(lecture, calcul, generation, perimetres),
                new GestionnairePreparationTemps.Ecritures(depot, journal, sequence));
    }

    @Bean
    public GestionnaireSegmentsTemps gestionnaireSegmentsTempsEffectifs(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul,
                                                                        RestrictionModificationHeures restriction, EcritureFeuillesTempsPort depot,
                                                                        JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireSegmentsTemps(lecture, calcul, restriction, new GestionnaireSegmentsTemps.Ecritures(depot, journal, sequence));
    }

    @Bean
    public GestionnaireApprobationTemps gestionnaireApprobationTempsEffectifs(LectureFeuillesTemps lecture, CalculFeuillesTemps calcul,
                                                                             GestionnaireFluxApprobations flux, EcritureFeuillesTempsPort depot,
                                                                             JournalEffectifsPort journal, Sequence sequence) {
        return new GestionnaireApprobationTemps(lecture, calcul, flux, new GestionnaireApprobationTemps.Ecritures(depot, journal, sequence));
    }

    @Bean
    public ControleurTempsEffectifs.Lectures lecturesTempsEffectifs(LectureFeuillesTemps feuilles, CalculFeuillesTemps calcul, RapportsTemps rapports,
                                                                    LectureFluxApprobations flux) {
        return new ControleurTempsEffectifs.Lectures(feuilles, calcul, rapports, flux);
    }

    @Bean
    public ControleurTempsEffectifs.Gestes gestesTempsEffectifs(GestionnaireParametresTemps parametres, GestionnairePreparationTemps preparation,
                                                                GestionnaireSegmentsTemps segments, GestionnaireApprobationTemps approbation, ExportPaieTemps export,
                                                                GestionnaireFluxApprobations flux) {
        return new ControleurTempsEffectifs.Gestes(parametres, preparation, segments, approbation, export, flux);
    }
}
