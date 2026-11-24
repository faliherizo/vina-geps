package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <strong>Le registre des référentiels de l'organisme</strong> — jumeau de {@code DESCRIPTEURS}
 * ({@code backend/src/effectifs/socle/referentiels.ts}). Il est <strong>fermé</strong> : ajouter un type,
 * c'est ajouter un comportement que le logiciel doit savoir traiter, donc du code ; y ajouter une
 * <strong>ligne</strong>, c'est du paramétrage.
 */
public enum TypeReferentiel {
    UNITES("unites", "Unités organisationnelles", List.of()),
    CORPS_EMPLOI("corps_emploi", "Corps d'emploi", List.of()),
    TITRES_EMPLOI("titres_emploi", "Titres d'emploi", List.of()),
    STATUTS_EMPLOI("statuts_emploi", "Statuts d'emploi", Comportements.noms(Comportements.StatutEmploi.values())),
    CONVENTIONS("conventions", "Conventions collectives", List.of()),
    NATURES_QUART("natures_quart", "Natures de quart", Comportements.noms(Comportements.NatureQuart.values())),
    PLAGES_HORAIRES("plages_horaires", "Plages horaires", List.of()),
    TYPES_MILIEU("types_milieu", "Types de milieu physique", Comportements.noms(Comportements.TypeMilieu.values())),
    CODES_ABSENCE("codes_absence", "Codes d'absence", Comportements.noms(Comportements.CodeAbsence.values())),
    TYPES_HEURE("types_heure", "Types d'heures", Comportements.noms(Comportements.TypeHeure.values())),
    JOURS_FERIES("jours_feries", "Jours fériés", List.of()),
    PARAMETRES_POSTE("parametres_poste", "Paramètres de poste", List.of()),
    /** E-GP-15 : le catalogue des activités qu'un pointage ou une saisie peut déclarer. Absent du Java, aucune activité ne se validait. */
    ACTIVITES_PRESENCE("activites_presence", "Activités de présence", Comportements.noms(Comportements.ActivitePresence.values())),
    /** Addenda 8, M-04 : permis, accréditations, formations obligatoires ; exigées par corps d'emploi, avec leur échéance. */
    COMPETENCES("competences", "Compétences", List.of()),
    /** Addenda 8, M-08 : les types de demande opérationnelle et ce que chacun fait. */
    TYPES_DEMANDE("types_demande", "Types de demande", Comportements.noms(Comportements.NatureDemande.values()));

    private static final Pattern DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final String code;
    private final String libelle;
    private final List<String> comportements;

    TypeReferentiel(String code, String libelle, List<String> comportements) {
        this.code = code;
        this.libelle = libelle;
        this.comportements = comportements;
    }

    public String code() {
        return code;
    }

    public String libelle() {
        return libelle;
    }

    /** Les comportements admis ; vide = pur vocabulaire, sans comportement. */
    public List<String> comportements() {
        return comportements;
    }

    public boolean aDesComportements() {
        return !comportements.isEmpty();
    }

    /** Les attributs du type. Calculés, parce qu'un attribut peut viser un autre type de l'énumération. */
    public List<DescripteurAttribut> attributs() {
        return switch (this) {
            case UNITES -> List.of(DescripteurAttribut.reference("parent", "Unité parente", TypeAttribut.REFERENCE, UNITES, false));
            case TITRES_EMPLOI -> List.of(DescripteurAttribut.reference("corps", "Corps d'emploi", TypeAttribut.REFERENCE, CORPS_EMPLOI, true));
            // E-GP-9 (lot R8) : les minutes de présence préalable rémunérée avant un quart de cette nature.
            case NATURES_QUART -> List.of(DescripteurAttribut.simple("compteQuota", "Compte dans les quotas de comblement", TypeAttribut.BOOLEEN),
                    DescripteurAttribut.borne("presencePrealableMinutes", "Présence préalable rémunérée (minutes)", false, 0, 120));
            case PLAGES_HORAIRES -> List.of(DescripteurAttribut.borne("debut", "Début (minutes depuis minuit)", true, 0, 1439),
                    DescripteurAttribut.borne("fin", "Fin (minutes depuis minuit)", true, 0, 1440));
            case CODES_ABSENCE -> List.of(DescripteurAttribut.choix("duree", "Catégorie de durée", Comportements.noms(Comportements.DureeAbsence.values()), true),
                    DescripteurAttribut.simple("planifiable", "Peut être planifiée d'avance", TypeAttribut.BOOLEEN),
                    DescripteurAttribut.reference("titres", "Titres d'emploi admissibles (vide = tous)", TypeAttribut.REFERENCES, TITRES_EMPLOI, false),
                    // Addenda 7, Q7 : au moins ce nombre de jours → remplacement long terme ; sinon, gestion régulière.
                    DescripteurAttribut.borne(AbsencesEffectifs.SEUIL_LONG_TERME, "Seuil du remplacement long terme (jours)", false, 1, 365),
                    // Addenda 5 (c), AD-06b : la banque de BD Corpo que le code consomme, et ses heures par jour d'absence.
                    DescripteurAttribut.simple(SoldesBanques.ATTRIBUT_BANQUE, "Banque de congés (code BD Corpo)", TypeAttribut.TEXTE),
                    DescripteurAttribut.borne(SoldesBanques.ATTRIBUT_HEURES_PAR_JOUR, "Heures par jour d'absence", false, 0.25, 24));
            case TYPES_HEURE -> List.of(DescripteurAttribut.simple("codePaie", "Code de paie (PFT)", TypeAttribut.TEXTE),
                    DescripteurAttribut.borne("majorationPourcent", "Majoration (%)", false, 0, 300));
            // Addenda 8, M-04 : les corps d'emploi qui l'exigent ; une compétence sans corps est suivie sans être exigée.
            case COMPETENCES -> List.of(DescripteurAttribut.reference(Competences.ATTRIBUT_CORPS, "Exigée des corps d'emploi (vide = suivie seulement)",
                    TypeAttribut.REFERENCES, CORPS_EMPLOI, false));
            case PARAMETRES_POSTE -> List.of(DescripteurAttribut.choix("typeValeur", "Type de valeur", Comportements.noms(Comportements.TypeValeurParametre.values()), true));
            // `codePaie` et `facturable` sont des attributs parce qu'ils varient d'un organisme à l'autre.
            case ACTIVITES_PRESENCE -> List.of(DescripteurAttribut.simple("codePaie", "Code de paie (PFT)", TypeAttribut.TEXTE),
                    DescripteurAttribut.simple("facturable", "Refacturable à un tiers", TypeAttribut.BOOLEEN));
            case CORPS_EMPLOI, STATUTS_EMPLOI, CONVENTIONS, TYPES_MILIEU, JOURS_FERIES, TYPES_DEMANDE -> List.of();
        };
    }

    /** Un format de code propre au type (lot R9 : le code d'un jour férié est sa date) ; vide = le code générique. */
    public Optional<Pattern> formatCode() {
        return this == JOURS_FERIES ? Optional.of(DATE) : Optional.empty();
    }

    public String messageFormatCode() {
        return "« code » : la date du férié, AAAA-MM-JJ";
    }

    public static Optional<TypeReferentiel> de(String code) {
        return Arrays.stream(values()).filter(t -> t.code.equals(code)).findFirst();
    }

    /** Le type nommé, ou le refus qui dit lesquels existent. */
    public static TypeReferentiel exiger(String code) {
        return de(code).orElseThrow(() -> new EffectifsInvalideException("référentiel inconnu : « " + code + " » (attendu : "
                + Arrays.stream(values()).map(TypeReferentiel::code).collect(Collectors.joining(", ")) + ")"));
    }
}
