package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotInvitations;
import ca.vina.nexu.auth.infrastructure.persistance.DepotRolesEtablissement;
import ca.vina.nexu.auth.application.ServiceInvitations;
import ca.vina.nexu.auth.infrastructure.persistance.AdaptateurComptesInvitations;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.courrier.Courrier;
import ca.vina.nexu.partage.securite.AdressePublique;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code /api/auth/invitations} — l'administration invite, la personne choisit elle-même son mot
 * de passe par un lien à usage unique. Jumeau de {@code backend/src/auth/routes.ts} : la pose, la
 * liste et la révocation sont réservées à l'administration ; la lecture du lien et l'acceptation
 * sont publiques — le lien est le secret.
 */
@RestController
@RequestMapping("/api/auth/invitations")
@Transactional
public class ControleurInvitations {

    /** Sans adresse publique déclarée, le lien vise le front de développement, comme au Node. */
    static final String BASE_PAR_DEFAUT = "http://localhost:8080";
    private static final String ADMIN = "admin";
    private static final String SUJET = "Votre accès à Nexu VINA — lien d'activation";

    /** Les collaborateurs d'une invitation : où écrire, qui aviser, et qui consigne. */
    public record Collaborateurs(DepotRolesEtablissement roles, AdressePublique adresses, Courrier courrier, JournalAudit audit) {
    }

    private final GardeAcces garde;
    private final ServiceInvitations service;
    private final Collaborateurs avec;

    @Autowired
    public ControleurInvitations(GardeAcces garde, DepotInvitations depot, DepotComptes comptes, Clock horloge,
                                 DepotRolesEtablissement roles, AdressePublique adresses, Courrier courrier, JournalAudit audit) {
        this(garde, new ServiceInvitations(depot, new AdaptateurComptesInvitations(comptes), horloge), new Collaborateurs(roles, adresses, courrier, audit));
    }

    ControleurInvitations(GardeAcces garde, ServiceInvitations service, Collaborateurs avec) {
        this.garde = garde;
        this.service = service;
        this.avec = avec;
    }

    private GardeAcces.Verdict administration(HttpServletRequest r) {
        GardeAcces.Verdict v = garde.exigerSession(r);
        if (v.refuse()) {
            return v;
        }
        ContexteRequete c = GardeAcces.contexte(r);
        return c.estSuperadmin() || ADMIN.equals(c.role()) ? v : new GardeAcces.Verdict(null, Bord.erreur(403, "insufficient role"));
    }

    private static ResponseEntity<Object> repondre(GardeAcces.Verdict v, int statut, Supplier<Object> geste) {
        return Bord.repondre(v, statut, t -> geste.get());
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r) {
        return repondre(administration(r), 200, () -> service.lister(ControleurAuth.tenantDuDemandeur(r)));
    }

    @PostMapping
    public ResponseEntity<Object> inviter(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> corps) {
        return repondre(administration(r), 201, () -> {
            ContexteRequete c = GardeAcces.contexte(r);
            String tenantId = ControleurAuth.tenantDuDemandeur(r);
            String adresse = avec.adresses().pour(tenantId);
            ServiceInvitations.Pose pose = service.inviter(tenantId, c.userId(), adresse == null || adresse.isEmpty() ? BASE_PAR_DEFAUT : adresse,
                    Bord.corps(corps), avec.roles().codesAttribuables(c.tenantId()));
            avec.courrier().envoyer(new Courrier.Message(pose.courriel(), SUJET, texte(pose)));
            avec.audit().consigner(c.userId(), c.username(), "auth.invitation.pose", (String) pose.invitation().get("id"),
                    Map.of("username", pose.invitation().get("username")));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("invitation", pose.invitation());
            m.put("lien", pose.lien());
            m.put("courrielEnvoyeA", pose.courriel());
            return m;
        });
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> revoquer(HttpServletRequest r, @PathVariable String id) {
        return repondre(administration(r), 200, () -> {
            ContexteRequete c = GardeAcces.contexte(r);
            Map<String, Object> retiree = service.revoquer(ControleurAuth.tenantDuDemandeur(r), id);
            avec.audit().consigner(c.userId(), c.username(), "auth.invitation.revoque", id, Map.of());
            return retiree;
        });
    }

    @GetMapping("/jeton/{jeton}")
    public ResponseEntity<Object> lireParJeton(@PathVariable String jeton) {
        return Bord.publique(() -> service.lireParJeton(jeton));
    }

    @PostMapping("/jeton/{jeton}/accepter")
    public ResponseEntity<Object> accepter(@PathVariable String jeton, @RequestBody(required = false) Map<String, Object> corps) {
        return repondre(new GardeAcces.Verdict(null, null), 201, () -> {
            Map<String, Object> compte = new LinkedHashMap<>(service.accepter(jeton, Bord.corps(corps)));
            avec.audit().consigner((String) compte.get("compteId"), (String) compte.get("username"), "auth.invitation.acceptee",
                    (String) compte.get("compteId"), Map.of());
            compte.put("prochaineEtape", "connexion");
            return compte;
        });
    }

    private static String texte(ServiceInvitations.Pose pose) {
        return "Bonjour,\n\nUn accès vous est ouvert. Suivez ce lien pour choisir votre mot de passe :\n" + pose.lien()
                + "\n\nLe lien est à usage unique et expire le " + String.valueOf(pose.invitation().get("expireLe")).substring(0, 10)
                + ".\n\nÀ la première connexion, la double authentification et trois questions de sécurité vous seront demandées.\n";
    }
}
