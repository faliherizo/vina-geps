package ca.vina.nexu.partage.securite;

/**
 * Ce que le serveur sait de l'appelant : son identité, son rôle, son établissement.
 *
 * <p><strong>Le tenant vient du jeton, jamais d'un en-tête client.</strong> C'est l'invariante
 * d'isolation du produit, et elle n'a qu'une exception : un compte <em>sans</em> établissement
 * — administrateur système ou superadmin — choisit le sien par {@code X-Tenant-Id}, faute d'en
 * avoir un dans son jeton. Pour tous les autres, un en-tête divergent est un refus, pas une
 * préférence : c'est ainsi qu'on empêche l'usurpation d'établissement.
 */
public record ContexteRequete(String userId, String username, String role,
                              String employeeId, String tenantId, String emprunteur, String empruntId) {

    public static final String ATTRIBUT = "ca.vina.nexu.contexte";

    /** Une session ordinaire : personne n'agit sous cette identité. */
    public ContexteRequete(String userId, String username, String role, String employeeId, String tenantId) {
        this(userId, username, role, employeeId, tenantId, null, null);
    }

    /** Session <strong>empruntée</strong> (CSSMB 25, ADR 0038) : un administrateur agit comme ce compte. */
    public boolean estEmpruntee() {
        return emprunteur != null;
    }

    public boolean estSuperadmin() {
        return roleIntegre().filter(r -> r == Role.SUPERADMIN).isPresent();
    }

    /** Le rôle intégré du compte, s'il en porte un ; vide pour un rôle défini par l'établissement. */
    public java.util.Optional<Role> roleIntegre() {
        return Role.de(role);
    }
}
