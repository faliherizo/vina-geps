package ca.vina.nexu.effectifs.domaine;

import java.util.Arrays;
import java.util.List;

/**
 * <strong>Ce que le logiciel comprend</strong> d'une valeur de référentiel : des ensembles fermés du
 * code (document 03 § 3). L'organisme nomme ses statuts et ses codes ; il les rattache à l'un de ces
 * comportements, et c'est le comportement que le métier lit.
 */
public final class Comportements {

    private Comportements() {
    }

    public enum StatutEmploi { TEMPS_COMPLET, TEMPS_PARTIEL_REGULIER, TEMPS_PARTIEL_OCCASIONNEL }

    public enum NatureQuart { REGULIER, AD_HOC, REMPLACEMENT, HEURES_SUPPLEMENTAIRES }

    public enum TypeMilieu { CASERNE, CENTRE_OPERATIONNEL, POINT_DE_SERVICE, AUTRE }

    public enum CodeAbsence { PAYE, NON_PAYE, BANQUE }

    public enum TypeHeure { TRAVAILLEE, PAUSE_PAYEE, PAUSE_NON_PAYEE, PRESENCE_PREALABLE, ABSENCE_PAYEE, SUPPLEMENTAIRE }

    /**
     * L'activité d'un temps de travail (E-GP-15) : ce que la personne faisait, et pas seulement qu'elle
     * travaillait. Une garde de douze heures dont deux en intervention ne se dimensionne pas comme douze
     * heures d'intervention. Jumeau du descripteur {@code activites_presence} du Node.
     */
    public enum ActivitePresence { INTERVENTION, DEPLACEMENT, DISPONIBILITE, SOUTIEN, FORMATION }

    /** La catégorie de durée d'un code d'absence. */
    public enum DureeAbsence { PONCTUELLE, MOYEN_TERME, LONG_TERME }

    /** Le type de valeur d'un paramètre de poste. */
    public enum TypeValeurParametre { TEXTE, NOMBRE, BOOLEEN }

    static List<String> noms(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(Enum::name).toList();
    }
}
