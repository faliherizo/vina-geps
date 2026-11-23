package ca.vina.nexu.alertes.domaine;

import java.util.regex.Pattern;

/**
 * <strong>Aucun renseignement de santé dans ce qui transite</strong> (ST-H04, ADR 0085). FX-AO-20260810, addenda 1, Q1 :
 * la solution « n'est pas destinée à transmettre des renseignements de santé ou de services sociaux », et la certification
 * TGV n'est retirée du contrat qu'à cette condition.
 *
 * <p>On refuse l'identifiant qui ne laisse aucun doute : le numéro d'assurance maladie du Québec, quatre lettres puis huit
 * chiffres, avec ou sans séparateurs. Une détection plus large (diagnostics, médicaments) produirait de faux refus au cœur
 * d'un message urgent ; le reste relève de la formation et du rappel affiché à l'écran.
 */
public final class ContenuSansRenseignementDeSante {

    private static final Pattern NUMERO_ASSURANCE_MALADIE = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{4}[\\s-]?\\d{4}[\\s-]?\\d{4}(?![0-9])");

    private ContenuSansRenseignementDeSante() {
    }

    /** Rend le texte tel quel, ou refuse (400) en nommant le champ — jamais le texte, qui ne doit aller nulle part. */
    public static String verifier(String champ, String texte) {
        if (texte != null && NUMERO_ASSURANCE_MALADIE.matcher(texte).find()) {
            throw RefusAlerte.invalide("« " + champ + " » : un numéro d'assurance maladie ne peut pas y figurer — "
                    + "les communications ne transmettent aucun renseignement de santé");
        }
        return texte;
    }
}
