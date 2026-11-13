package ca.vina.nexu.superadmin.application.commandes;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.saas.domaine.Locataires;
import ca.vina.nexu.superadmin.domaine.AdministrationSysteme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Les gestes de l'administration du système : créer un établissement et son administrateur (ensemble ou
 * pas du tout), le modifier, l'activer, le suspendre, le résilier ; gérer ses administrateurs ; traiter .
 */
public final class GestionnaireAdministration {

    private final AdministrationSysteme.Etablissements etablissements;
    private final AdministrationSysteme.Administrateurs admins;
    private final AdministrationSysteme.Transactions transactions;

    public GestionnaireAdministration(AdministrationSysteme.Etablissements etablissements, AdministrationSysteme.Administrateurs admins, AdministrationSysteme.Transactions transactions) {
        this.etablissements = etablissements;
        this.admins = admins;
        this.transactions = transactions;
    }

    /** L'établissement et son administrateur naissent dans une même transaction : pas d'établissement orphelin si l'admin est refusé. */
    public Map<String, Object> creerEtablissement(Map<String, Object> corps) {
        String username = texte(corps, "adminUsername") == null || texte(corps, "adminUsername").trim().isEmpty() ? "admin" : texte(corps, "adminUsername").trim();
        String fourni = texte(corps, "adminPassword");
        String motDePasse = fourni == null || fourni.isEmpty() ? admins.motDePasseProvisoire() : fourni;
        Map<String, Object> r = transactions.executer(() -> {
            Map<String, Object> tenant = etablissements.creer(demande(corps));
            Map<String, Object> admin = admins.creer((String) tenant.get("id"), username, motDePasse, fourni == null || fourni.isEmpty());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("tenant", tenant);
            m.put("admin", admin);
            return m;
        });
        if (fourni == null || fourni.isEmpty()) {
            r.put("temporaryPassword", motDePasse);   // exposé une seule fois, jamais stocké en clair
        }
        return r;
    }

    public Map<String, Object> modifierEtablissement(String id, Map<String, Object> c) {
        return etablissements.modifier(id, new AdministrationSysteme.Modification(texte(c, "name"), texte(c, "region"), texte(c, "address"),
                texte(c, "city"), texte(c, "postalCode"), texte(c, "phone"), texte(c, "email"), texte(c, "slug")));
    }

    public Map<String, Object> activer(String id) {
        return etablissements.activer(id, 12);
    }

    public Map<String, Object> changerStatut(String id, String statut) {
        return etablissements.changerStatut(id, statut);
    }

    public Map<String, Object> creerAdministrateur(String tenantId, Map<String, Object> c) {
        return admins.creer(tenantId, texte(c, "username"), texte(c, "password"), Boolean.TRUE.equals(c.get("mustChangePassword")));
    }

    public Map<String, Object> reinitialiserMotDePasse(String tenantId, String userId) {
        return admins.reinitialiserMotDePasse(tenantId, userId);
    }

    public Map<String, Object> activerAdministrateur(String tenantId, String userId, boolean actif) {
        return admins.activer(tenantId, userId, actif);
    }

    private static Locataires.Demande demande(Map<String, Object> c) {
        return new Locataires.Demande(texte(c, "name"), texte(c, "type"), texte(c, "country"), texte(c, "region"), texte(c, "planCode"),
                c.get("trialDays") instanceof Number n ? n.intValue() : null, texte(c, "dataResidency"), texte(c, "address"), texte(c, "city"),
                texte(c, "postalCode"), texte(c, "phone"), texte(c, "email"), texte(c, "slug"));
    }

    private static String texte(Map<String, Object> c, String cle) {
        Object v = c.get(cle);
        return v == null ? null : String.valueOf(v);
    }
}
