package ca.vina.nexu.rapports.domaine;

import ca.vina.nexu.rapports.domaine.CatalogueRapports.Champ;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Faits;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.Source;
import ca.vina.nexu.rapports.domaine.CatalogueRapports.TypeChamp;

import java.util.List;
import java.util.stream.Stream;

/**
 * <b>Les sources des relevés statistiques d'effectifs</b> (AO 26-015-DO, P-RF-1) — jumeau de
 * {@code backend/src/rapports/sourcesEffectifs.ts}.
 *
 * <p>Ce qu'un gestionnaire d'effectifs veut compter n'est pas stocké : heures supplémentaires, écart
 * au planifié, corroboration, statut opérationnel se <b>calculent</b>. Chaque source est donc servie par
 * un fournisseur de faits ({@link FournisseurFaits}) qui appelle les mêmes calculs que les écrans et
 * applique le périmètre d'unités de l'acteur. Toutes partagent les mêmes axes : la période, l'unité et
 * ses ancêtres, la personne et ses attributs d'emploi.
 */
public final class SourcesEffectifs {

    private SourcesEffectifs() {
    }

    /** Un relevé d'effectifs expose des personnes : il exige la capacité de gérer les effectifs. */
    public static final String CAPACITE = "effectifs.manage";

    private static final Faits PERIODE_JOUR = new Faits("jour");

    private static Champ axe(String code, String libelle) {
        return new Champ(code, libelle, TypeChamp.TEXTE, true);
    }

    private static Champ mesure(String code, String libelle) {
        return new Champ(code, libelle, TypeChamp.NOMBRE, false);
    }

    private static final List<Champ> COMMUNS = List.of(
            new Champ("jour", "Jour", TypeChamp.DATE, true),
            new Champ("semaine", "Semaine (lundi)", TypeChamp.DATE, true),
            axe("mois", "Mois"),
            axe("unite", "Unité"),
            axe("uniteParente", "Unité parente"),
            axe("uniteNiveau1", "Unité de niveau 1 (racine)"),
            axe("uniteNiveau2", "Unité de niveau 2"),
            axe("uniteNiveau3", "Unité de niveau 3"),
            axe("ressource", "Personne"),
            axe("corpsEmploi", "Corps d'emploi"),
            axe("titreEmploi", "Titre d'emploi"),
            axe("statutEmploi", "Statut d'emploi"),
            axe("convention", "Convention collective"));

    private static final List<Champ> QUART = List.of(axe("milieu", "Milieu"), axe("natureQuart", "Nature de quart"));

    @SafeVarargs
    private static Source source(String code, String libelle, String module, List<Champ>... champs) {
        return new Source(code, libelle, module, Stream.of(champs).flatMap(List::stream).toList(), PERIODE_JOUR, CAPACITE);
    }

    public static final List<Source> SOURCES = List.of(
            source("effectifs_heures", "Effectifs — heures par type", "effectifs_temps", COMMUNS, QUART, List.of(
                    axe("typeHeure", "Type d'heure"),
                    axe("comportementHeure", "Comportement du type d'heure"),
                    axe("origine", "Origine du temps"),
                    axe("statutFeuille", "Statut de la feuille"),
                    mesure("heures", "Heures"))),
            source("effectifs_journees", "Effectifs — journées de temps (planifié, déclaré, supplémentaire)", "effectifs_temps",
                    COMMUNS, QUART, List.of(
                    axe("statutFeuille", "Statut de la feuille"),
                    mesure("journees", "Journées"),
                    mesure("heuresPlanifiees", "Heures planifiées"),
                    mesure("heuresDeclarees", "Heures déclarées"),
                    mesure("ecartHeures", "Écart déclaré moins planifié (h)"),
                    mesure("heuresSupplementaires", "Heures supplémentaires"),
                    mesure("anomalies", "Anomalies"))),
            source("effectifs_pointages", "Effectifs — pointages", "effectifs_presence", COMMUNS, QUART, List.of(
                    axe("sens", "Sens"),
                    axe("resultat", "Résultat de la vérification"),
                    axe("ecart", "Écart constaté"),
                    axe("origine", "Origine du pointage"),
                    axe("statut", "Statut du pointage"),
                    axe("activite", "Activité"),
                    mesure("pointages", "Pointages"),
                    mesure("ecarts", "Pointages en écart"),
                    mesure("corrobores", "Pointages corroborés"))),
            source("effectifs_places", "Effectifs — comblement des quarts", "effectifs_planification", COMMUNS, QUART, List.of(
                    axe("plageHoraire", "Plage horaire"),
                    axe("modeAttribution", "Mode d'attribution"),
                    axe("statutOperationnel", "Statut opérationnel"),
                    axe("etatPeriode", "État de la période"),
                    mesure("places", "Places"),
                    mesure("pourvues", "Places pourvues"),
                    mesure("vacantes", "Places vacantes"),
                    mesure("equipages", "Équipages"),
                    mesure("equipagesSousMinimum", "Équipages sous le minimum"))),
            source("effectifs_absences", "Effectifs — absences", "effectifs_absences", COMMUNS, List.of(
                    axe("codeAbsence", "Code d'absence"),
                    axe("typeAbsence", "Type d'absence (payée, non payée, banque)"),
                    axe("statut", "Statut de l'absence"),
                    mesure("jours", "Jours d'absence"),
                    mesure("absences", "Absences"))),
            // Addenda 8, M-18 : les rapports attendus dès la mise en production — l'horaire et le budget en heures.
            source("effectifs_horaires", "Effectifs — horaires (quarts tenus)", "effectifs_planification", COMMUNS, QUART, List.of(
                    axe("plageHoraire", "Plage horaire"),
                    axe("debut", "Début (HH:MM)"),
                    axe("fin", "Fin (HH:MM)"),
                    mesure("quarts", "Quarts"),
                    mesure("heures", "Heures"))),
            source("effectifs_budget", "Effectifs — budget en heures (AGPH) et heures planifiées", "effectifs_agph", COMMUNS, List.of(
                    mesure("budgetHeures", "Budget de la semaine (h)"),
                    mesure("heuresPlanifiees", "Heures planifiées (toutes les places)"),
                    mesure("heuresPourvues", "Heures pourvues"),
                    mesure("ecartHeures", "Écart planifié moins budget (h)"))));
}
