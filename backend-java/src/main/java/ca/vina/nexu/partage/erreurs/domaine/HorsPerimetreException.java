package ca.vina.nexu.partage.erreurs.domaine;

/** Un dossier que l'acteur ne devrait pas voir ; le bord HTTP le rend 403 et l'audit le consigne. */
public class HorsPerimetreException extends RuntimeException {
    private final String employeeId;

    public HorsPerimetreException(String employeeId) {
        this(employeeId, "forbidden (out of scope)");
    }

    /** Un refus d'accès nommé autrement (profil manquant, droit réservé au directeur), sans dossier visé. */
    public HorsPerimetreException(String employeeId, String message) {
        super(message);
        this.employeeId = employeeId;
    }

    public String employeeId() {
        return employeeId;
    }
}
