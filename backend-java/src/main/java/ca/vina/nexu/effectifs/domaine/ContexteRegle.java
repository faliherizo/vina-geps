package ca.vina.nexu.effectifs.domaine;

import java.util.List;
import java.util.stream.Collectors;

import static ca.vina.nexu.effectifs.domaine.TypeFaitRegle.BOOLEEN;
import static ca.vina.nexu.effectifs.domaine.TypeFaitRegle.DATE;
import static ca.vina.nexu.effectifs.domaine.TypeFaitRegle.NOMBRE;
import static ca.vina.nexu.effectifs.domaine.TypeFaitRegle.TEXTE;

/**
 * Le contexte d'une règle — le moment métier où elle s'évalue — et son <strong>catalogue de faits</strong>
 * (jumeau de {@code FAITS}, {@code backend/src/effectifs/regles/domaine.ts}). Ajouter un fait est un
 * développement (il faut le calculer) ; composer des règles avec les faits existants est du paramétrage.
 */
public enum ContexteRegle {
    DISPONIBILITE, ABSENCE, ATTRIBUTION, ECHANGE, POINTAGE, FEUILLE_DE_TEMPS, HEURES_SUPPLEMENTAIRES, CHOIX_AGPH, INTERET_REMPLACEMENT, DEPOT_AGPH,
    MODIFICATION_HEURES;

    public String code() {
        return name();
    }

    public static ContexteRegle de(Object v) {
        return Regles.enumDe(values(), ContexteRegle::code, v, "contexte");
    }

    /** Les faits que ce contexte expose, dans l'ordre du catalogue Node. */
    public List<FaitRegle> faits() {
        return switch (this) {
            case DISPONIBILITE -> List.of(f("quartsParSemaine", NOMBRE, "Quarts disponibles dans la semaine"),
                    f("heuresParPeriode", NOMBRE, "Heures disponibles dans la période"), f("dureeMinutes", NOMBRE, "Durée d'une plage (minutes)"),
                    f("finsDeSemaine", NOMBRE, "Fins de semaine disponibles dans la période"), f("nuitsConsecutives", NOMBRE, "Nuits consécutives disponibles"),
                    f("statutEmploi", TEXTE, "Comportement du statut d'emploi"), f("corpsEmploi", TEXTE, "Corps d'emploi"));
            case ABSENCE -> List.of(f("codeAbsence", TEXTE, "Code d'absence"), f("dureeAbsence", TEXTE, "Catégorie de durée du code"),
                    f("joursPreavis", NOMBRE, "Préavis en jours"), f("joursDemandes", NOMBRE, "Jours demandés"),
                    f("absencesSimultanees", NOMBRE, "Absences déjà accordées dans l'unité sur la période"),
                    f("tauxComblementApres", NOMBRE, "Taux de comblement après l'absence (%)"), f("titreEmploi", TEXTE, "Titre d'emploi"),
                    f("premierJour", DATE, "Premier jour de l'absence"), f("planifiable", BOOLEEN, "Code planifiable d'avance (attribut du code)"),
                    f("absencePlanifiee", BOOLEEN, "Déclarée d'avance, avant son premier jour"),
                    // AD-06b : le solde transmis par l'organisme pour la banque du code (addenda 5 c).
                    f("soldeBanqueConnu", BOOLEEN, "Solde de la banque reçu de l'organisme"), f("soldeBanqueHeures", NOMBRE, "Solde de la banque (heures)"),
                    f("heuresDemandees", NOMBRE, "Heures demandées (jours × heures par jour du code)"),
                    f("soldeBanqueApresHeures", NOMBRE, "Solde de la banque après la demande (heures)"));
            case ATTRIBUTION -> List.of(f("admissible", BOOLEEN, "Admissible au poste"), f("reposHeures", NOMBRE, "Heures de repos depuis le quart précédent"),
                    f("heuresSemaine", NOMBRE, "Heures déjà planifiées dans la semaine"), f("formationRequiseAcquise", BOOLEEN, "Formation requise acquise"),
                    f("rang", NOMBRE, "Rang dans le classement"),
                    f("heures24h", NOMBRE, "Heures planifiées sur 24 h, ce quart compris"),
                    // Addenda 8, M-04 : les compétences exigées du corps d'emploi, à la date du quart.
                    f("competencesManquantes", NOMBRE, "Compétences exigées manquantes ou échues au jour du quart"),
                    f("joursAvantEcheance", NOMBRE, "Jours avant l'échéance d'une compétence exigée"));
            case ECHANGE -> List.of(f("reposHeures", NOMBRE, "Heures de repos entre deux quarts"), f("heuresSemaine", NOMBRE, "Heures de la semaine après l'échange"),
                    f("contigu", BOOLEEN, "Quart contigu à un autre quart"), f("memePeriode", BOOLEEN, "Échange dans la même période"),
                    // Addenda 7, Q8 : la contiguïté vise aussi le nombre d'heures maximal par jour de travail (24 h).
                    f("heures24h", NOMBRE, "Heures planifiées sur 24 h après l'échange"));
            case POINTAGE -> List.of(f("ecartDebutMinutes", NOMBRE, "Écart au début prévu (minutes)"),
                    f("distanceMetres", NOMBRE, "Distance au milieu (mètres ; -1 sans position)"), f("mecanisme", TEXTE, "Mécanisme de preuve"),
                    f("preuveExigeePresente", BOOLEEN, "Preuve exigée présente"));
            case FEUILLE_DE_TEMPS -> List.of(f("heuresJour", NOMBRE, "Heures déclarées du jour"), f("heuresSemaine", NOMBRE, "Heures déclarées de la semaine"),
                    f("ecartPlanifieMinutes", NOMBRE, "Écart planifié / déclaré (minutes)"), f("sansPointage", BOOLEEN, "Journée sans pointage"));
            case HEURES_SUPPLEMENTAIRES -> List.of(f("heuresJour", NOMBRE, "Heures travaillées du jour"), f("heuresSemaine", NOMBRE, "Heures travaillées de la semaine"),
                    f("jourFerie", BOOLEEN, "Jour férié"),
                    f("heuresPeriodePaie", NOMBRE, "Heures travaillées de la période de paie, jusqu'à ce jour"));
            case CHOIX_AGPH -> List.of(f("statutOffert", TEXTE, "Statut d'emploi offert par le poste"), f("corpsRequis", TEXTE, "Corps d'emploi requis"),
                    f("corpsEmploi", TEXTE, "Corps d'emploi de la personne"), f("rang", NOMBRE, "Rang d'ancienneté"));
            case INTERET_REMPLACEMENT -> List.of(f("corpsRequis", TEXTE, "Corps d'emploi requis"), f("corpsEmploi", TEXTE, "Corps d'emploi de la personne"),
                    f("statutEmploi", TEXTE, "Comportement du statut d'emploi"));
            // E-CH-1 : les disponibilités déposées dans la campagne AGPH, sur la semaine type de l'année, et ce qu'elles
            // pèsent sur le budget d'heures que le service de planification a fixé à la campagne.
            case DEPOT_AGPH -> List.of(f("quartsParSemaine", NOMBRE, "Quarts disponibles dans la semaine type"),
                    f("heuresParSemaine", NOMBRE, "Heures disponibles dans la semaine type"), f("dureeMinutes", NOMBRE, "Durée d'une plage (minutes)"),
                    f("finsDeSemaine", NOMBRE, "Fins de semaine disponibles (0 ou 1)"), f("nuitsConsecutives", NOMBRE, "Nuits consécutives disponibles"),
                    f("statutEmploi", TEXTE, "Comportement du statut d'emploi"), f("corpsEmploi", TEXTE, "Corps d'emploi"),
                    f("heuresCampagne", NOMBRE, "Heures hebdomadaires offertes dans la campagne, ce dépôt compris"),
                    f("budgetHeures", NOMBRE, "Budget d'heures hebdomadaires de la campagne (0 = non fixé)"),
                    f("tauxBudget", NOMBRE, "Heures offertes / budget (%, 0 sans budget)"));
            // E-GP-3 : chaque ajout, correction ou retrait de segment et chaque saisie après coup passent par ces faits.
            case MODIFICATION_HEURES -> List.of(f("operation", TEXTE, "Opération (AJOUT, CORRECTION, RETRAIT, SAISIE_A_POSTERIORI)"),
                    f("heuresDepuisLeQuart", NOMBRE, "Heures écoulées depuis la fin du temps touché"),
                    f("periodeEtat", TEXTE, "État de la période de l'horaire (PREPARATION, COLLECTE, CONFECTION, PUBLIEE, CLOSE ; AUCUNE hors période)"),
                    f("parAutrui", BOOLEEN, "Modifié par une autre personne que l'employé"), f("roleActeur", TEXTE, "Rôle de qui modifie"),
                    f("niveauActeur", TEXTE, "Niveau de périmètre de qui modifie sur l'unité (AUCUN, LECTURE, PLANIFICATION, GESTION, ETABLISSEMENT)"),
                    f("sourceSegment", TEXTE, "Origine du temps touché (GENERE, POINTAGE, SAISIE, EXTERNE)"));
        };
    }

    /** Le fait nommé, ou le refus qui dit lesquels ce contexte expose. */
    public FaitRegle fait(String code) {
        List<FaitRegle> faits = faits();
        return faits.stream().filter(x -> x.code().equals(code)).findFirst()
                .orElseThrow(() -> new EffectifsInvalideException("fait inconnu pour " + name() + " : « " + code + " » (attendu : "
                        + faits.stream().map(FaitRegle::code).collect(Collectors.joining(", ")) + ")"));
    }

    private static FaitRegle f(String code, TypeFaitRegle type, String libelle) {
        return new FaitRegle(code, type, libelle);
    }
}
