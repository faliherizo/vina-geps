package ca.vina.nexu.effectifs.domaine;

/** Une période de planification et son état dans le cycle préparation → close. */
public record Periode(String id, String uniteCode, String nom, String debut, String fin, Planification.EtatPeriode etat,
                      String finCollecte, String creeLe, String publieeLe, String closeLe) {
}
