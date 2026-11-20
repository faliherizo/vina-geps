package ca.vina.nexu.effectifs.domaine;

/** Une période de planification et son état dans le cycle préparation → close. */
public record Periode(String id, String uniteCode, String nom, String debut, String fin, Planification.EtatPeriode etat,
                      String finCollecte, String creeLe, String publieeLe, String closeLe) {

    /**
     * La période horaire est-elle <strong>en cours</strong> ce jour-là : publiée, commencée et non finie ? (addenda 7, Q7 : une absence
     * longue ne relève du remplacement long terme qu'« à part si la période horaire est en cours ».)
     */
    public boolean estEnCours(String aujourdhui) {
        return etat == Planification.EtatPeriode.PUBLIEE && debut.compareTo(aujourdhui) <= 0 && aujourdhui.compareTo(fin) <= 0;
    }
}
