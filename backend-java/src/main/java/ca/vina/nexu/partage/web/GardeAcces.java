package ca.vina.nexu.partage.web;

import ca.vina.nexu.acces.domaine.SurchargesAcces;
import ca.vina.nexu.acces.infrastructure.persistance.RegistrePolitiqueAcces;
import org.springframework.beans.factory.annotation.Autowired;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.securite.ResolutionTenant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Garde d'accès des contrôleurs — résout l'établissement, vérifie les droits.
 *
 * <p>Chaque module migré passe par ici. Recopier la résolution du tenant dans chaque contrôleur
 * garantirait qu'un jour l'un d'eux l'oublie, et ce jour-là un établissement lirait les données
 * d'un autre. Une seule implémentation, testée une fois.
 *
 * <p>Les corps d'erreur reprennent la forme du Node — {@code {"errors": [...]}} — et ses messages
 * mot pour mot : le front les affiche tels quels.
 */
@Component
public class GardeAcces {

    private final RegistrePolitiqueAcces acces;
    private final SurchargesAcces surcharges;

    /** Sans dépôt de surcharges : les défauts de la politique s'appliquent (essais des contrôleurs). */
    public GardeAcces(RegistrePolitiqueAcces acces) {
        this(acces, (tenantId, role) -> Map.of());
    }

    @Autowired
    public GardeAcces(RegistrePolitiqueAcces acces, SurchargesAcces surcharges) {
        this.acces = acces;
        this.surcharges = surcharges;
    }

    /** Le résultat d'une garde : soit l'établissement retenu, soit la réponse à renvoyer. */
    public record Verdict(String tenantId, ResponseEntity<Object> refus) {
        public boolean refuse() {
            return refus != null;
        }
    }

    public static ContexteRequete contexte(HttpServletRequest requete) {
        return (ContexteRequete) requete.getAttribute(ContexteRequete.ATTRIBUT);
    }

    private static ResponseEntity<Object> erreur(int statut, String message) {
        return ResponseEntity.status(statut).body(Map.of("errors", List.of(message)));
    }

    /** Exige une session et un établissement résolu, sans exiger de droit particulier. */
    public Verdict exigerTenant(HttpServletRequest requete) {
        ContexteRequete c = contexte(requete);
        ResolutionTenant.Resultat r = ResolutionTenant.resoudre(c, requete.getHeader("X-Tenant-Id"));

        if (r instanceof ResolutionTenant.Resultat.Retenu retenu) {
            return new Verdict(retenu.tenantId(), null);
        }
        if (r instanceof ResolutionTenant.Resultat.Discordance) {
            return new Verdict(null, erreur(403, "tenant mismatch"));
        }
        if (r instanceof ResolutionTenant.Resultat.Manquant) {
            return new Verdict(null, erreur(400, "X-Tenant-Id header required"));
        }
        return new Verdict(null, erreur(401, "authentication required"));
    }

    /**
     * Exige en plus l'un des rôles cités.
     *
     * <p>Le superadmin passe : il n'appartient à aucun établissement, et l'exclure le priverait
     * de toute administration.
     */
    public Verdict exigerRole(HttpServletRequest requete, String... roles) {
        Verdict v = exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        ContexteRequete c = contexte(requete);
        if (c.estSuperadmin() || List.of(roles).contains(c.role())) {
            return v;
        }
        return new Verdict(null, erreur(403, "forbidden"));
    }

    /** Exige seulement une session ({@code requireAuth}) : le verdict porte l'établissement du jeton, s'il y en a un. */
    public Verdict exigerSession(HttpServletRequest requete) {
        ContexteRequete c = contexte(requete);
        return c == null ? new Verdict(null, erreur(401, "unauthenticated")) : new Verdict(c.tenantId(), null);
    }

    /**
     * L'administration du système ({@code requireSystemAdmin}) : le superadmin, ou un admin sans
     * établissement — jamais l'admin d'un établissement, qui n'administre que le sien.
     */
    public Verdict exigerAdministrationSysteme(HttpServletRequest requete) {
        ContexteRequete c = contexte(requete);
        if (c == null) {
            return new Verdict(null, erreur(401, "unauthenticated"));
        }
        if (c.estSuperadmin() || ("admin".equals(c.role()) && c.tenantId() == null)) {
            return new Verdict(c.tenantId(), null);
        }
        return new Verdict(null, erreur(403, "réservé à l'administration du système"));
    }

    /** {@code requireRole("superadmin")} du Node sans exiger d'établissement : l'instance entière, pas un locataire. */
    public Verdict exigerSuperadmin(HttpServletRequest requete) {
        ContexteRequete c = contexte(requete);
        if (c == null) {
            return new Verdict(null, erreur(401, "unauthenticated"));
        }
        return c.estSuperadmin() ? new Verdict(c.tenantId(), null) : new Verdict(null, erreur(403, "forbidden"));
    }

    /** Exige une capacité, telle que la table des droits la définit pour cet établissement. */
    public Verdict exigerCapacite(HttpServletRequest requete, String capacite, Map<String, Boolean> surcharges) {
        Verdict v = exigerTenant(requete);
        if (v.refuse()) {
            return v;
        }
        ContexteRequete c = contexte(requete);
        if (acces.decision().peut(c.role(), capacite, surcharges)) {
            return v;
        }
        var regle = acces.politique().regle(capacite);
        Map<String, Object> corps = new java.util.LinkedHashMap<>();
        corps.put("errors", List.of("droit requis : " + (regle != null ? regle.libelle() : capacite)));
        corps.put("capability", capacite);
        return new Verdict(null, ResponseEntity.status(403).body(corps));
    }

    /**
     * {@code requireCapability} du Node : les droits se lisent dans l'établissement <strong>du
     * compte</strong> (le jeton), avec ce que cet établissement a décidé pour le rôle — jamais
     * d'après un en-tête client.
     */
    public Verdict exigerCapacite(HttpServletRequest requete, String capacite) {
        ContexteRequete c = contexte(requete);
        return exigerCapacite(requete, capacite, c == null ? Map.of() : surcharges.pour(c.tenantId(), c.role()));
    }
}
