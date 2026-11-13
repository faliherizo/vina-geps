package ca.vina.nexu.approbations.infrastructure.configuration;

import ca.vina.nexu.approbations.application.commandes.GestionnaireApprobations;
import ca.vina.nexu.approbations.application.commandes.GestionnaireTypesDeDocument;
import ca.vina.nexu.approbations.application.requetes.LectureApprobations;
import ca.vina.nexu.approbations.application.requetes.LectureTypesDeDocument;
import ca.vina.nexu.approbations.domaine.Approbations;
import ca.vina.nexu.approbations.domaine.Delegataires;
import ca.vina.nexu.approbations.domaine.TypesDeDocument;
import ca.vina.nexu.partage.temps.domaine.Horloge;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Le câblage du moteur d'approbation (ADR 0025) et de son catalogue de types de document. */
@Configuration
public class ConfigurationApprobations {

    @Bean
    public LectureTypesDeDocument lectureTypesDeDocument(TypesDeDocument.Depot types, Delegataires.Comptes comptes, Horloge horloge) {
        return new LectureTypesDeDocument(types, comptes, horloge);
    }

    @Bean
    public GestionnaireTypesDeDocument gestionnaireTypesDeDocument(TypesDeDocument.Depot types, Horloge horloge) {
        return new GestionnaireTypesDeDocument(types, horloge);
    }

    @Bean
    public LectureApprobations lectureApprobations(Approbations.Depot depot, Delegataires.Comptes comptes) {
        return new LectureApprobations(depot, comptes);
    }

    @Bean
    public GestionnaireApprobations gestionnaireApprobations(Approbations.Depot depot, LectureTypesDeDocument catalogue, Delegataires.Comptes comptes) {
        return new GestionnaireApprobations(depot, catalogue::actif, comptes);
    }
}
