package ca.vina.nexu.auth.domaine;

/**
 * <strong>Ce compte peut-il approuver ?</strong> — le {@code peutApprouver} que le Node rend à la
 * connexion et à {@code /me}. Il accompagne les capacités et ne s'en déduit pas : approuver dépend
 * aussi de l'organigramme (un chef d'équipe au compte « employé ») et des délégations reçues. Le
 * contexte des approbations le calcule ; l'authentification ne fait que le rendre.
 */
public interface DroitDApprouver {

    boolean peutApprouver(String tenantId, String userId, String role, String employeeId);

    /** Le repli des essais d'authentification qui ne visent pas l'approbation : personne n'approuve. */
    DroitDApprouver PERSONNE = (tenantId, userId, role, employeeId) -> false;
}
