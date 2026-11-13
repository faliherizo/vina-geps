package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotComptes;
import ca.vina.nexu.auth.infrastructure.persistance.DepotProfil;

import ca.vina.nexu.auth.domaine.CodeQr;
import ca.vina.nexu.auth.domaine.Compte;
import ca.vina.nexu.auth.domaine.Profil;
import ca.vina.nexu.auth.domaine.Totp;
import ca.vina.nexu.partage.audit.infrastructure.JournalAudit;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Le profil de la personne connectée ({@code /profile}), les approbateurs qu'elle peut désigner
 * ({@code /approvers}) et l'amorce d'un second facteur TOTP ({@code /security/totp/init}) : le
 * secret reste <em>en attente</em> tant que {@code /security/setup} ne l'a pas vérifié.
 */
@RestController
@RequestMapping("/api/auth")
public class ControleurProfil {

    private final DepotComptes comptes;
    private final DepotProfil profils;
    private final JournalAudit audit;
    private final RandomGenerator alea;
    private final String emetteurTotp;

    @Autowired
    public ControleurProfil(DepotComptes comptes, DepotProfil profils, JournalAudit audit, @Value("${TOTP_ISSUER:NexuVINA}") String emetteurTotp) {
        this(comptes, profils, audit, new SecureRandom(), emetteurTotp);
    }

    public ControleurProfil(DepotComptes comptes, DepotProfil profils, JournalAudit audit, RandomGenerator alea, String emetteurTotp) {
        this.comptes = comptes;
        this.profils = profils;
        this.audit = audit;
        this.alea = alea;
        this.emetteurTotp = emetteurTotp == null || emetteurTotp.isEmpty() ? "NexuVINA" : emetteurTotp;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static ResponseEntity<Object> erreurs(List<String> messages) {
        return ResponseEntity.badRequest().body(Map.of("errors", messages));
    }

    private Optional<Compte> compte(HttpServletRequest requete) {
        ContexteRequete c = GardeAcces.contexte(requete);
        return c == null ? Optional.empty() : comptes.parId(c.userId());
    }

    @GetMapping("/profile")
    public ResponseEntity<Object> profil(HttpServletRequest requete) {
        if (GardeAcces.contexte(requete) == null) {
            return erreur(401, "unauthenticated");
        }
        Optional<Compte> u = compte(requete);
        if (u.isEmpty()) {
            return erreur(404, "user not found");
        }
        Optional<Profil.Employe> emp = profils.employe(u.get().employeeId());
        Map<String, Object> r = new LinkedHashMap<>(u.get().vue());
        r.put("profile", emp.map(Profil::modifiable).orElse(null));
        r.put("readOnly", emp.map(e -> Profil.lectureSeule(e, profils.nomDuDepartement(e.departmentId()), profils.nomDeLEmploye(e.managerId()))).orElse(null));
        // Encadre-t-elle des personnes ? L'information vient de la hiérarchie, pas du rôle du compte.
        r.put("manages", Profil.subordonnes(u.get().employeeId(), profils.liens()));
        return ResponseEntity.ok(r);
    }

    @PatchMapping("/profile")
    public ResponseEntity<Object> modifierProfil(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        if (GardeAcces.contexte(requete) == null) {
            return erreur(401, "unauthenticated");
        }
        Optional<Compte> u = compte(requete);
        if (u.isEmpty()) {
            return erreur(404, "user not found");
        }
        Optional<Profil.Employe> emp = profils.employe(u.get().employeeId());
        if (emp.isEmpty()) {
            return erreur(409, "no employee profile linked to this account");
        }
        Map<String, Object> patch = corps == null ? Map.of() : corps;
        List<String> erreurs = Profil.validerModification(patch);
        if (!erreurs.isEmpty()) {
            return erreurs(erreurs);
        }
        Profil.Employe modifie = Profil.appliquer(emp.get(), patch);
        profils.enregistrerIdentite(modifie);
        audit.consigner(u.get().id(), u.get().nomDeConnexion(), "auth.profile.update", null, null);
        return ResponseEntity.ok(Profil.modifiable(modifie));
    }

    /** Liste des approbateurs — visible à tout utilisateur authentifié, dans son établissement. */
    @GetMapping("/approvers")
    public ResponseEntity<Object> approbateurs(HttpServletRequest requete, @RequestParam(name = "employeeId", required = false) String employeeId) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null) {
            return erreur(401, "unauthenticated");
        }
        List<Compte> perimetre = c.estSuperadmin() ? comptes.lister() : comptes.listerParTenant(ControleurAuth.tenantDuDemandeur(requete));
        List<String> chaine = employeeId == null || employeeId.isEmpty() ? List.of() : Profil.chaineHierarchique(employeeId, profils.gestionnaireDe());
        return ResponseEntity.ok(Profil.approbateurs(perimetre, employeeId, chaine));
    }

    /** Initialise un secret TOTP en attente (n'active pas la méthode tant que la vérification n'a pas eu lieu). */
    @PostMapping("/security/totp/init")
    public ResponseEntity<Object> initialiserTotp(HttpServletRequest requete) {
        if (GardeAcces.contexte(requete) == null) {
            return erreur(401, "unauthenticated");
        }
        Optional<Compte> u = compte(requete);
        if (u.isEmpty()) {
            return erreur(404, "user not found");
        }
        String secret = Totp.secretAleatoire(alea);
        comptes.mettreTotpEnAttente(u.get().id(), secret);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("secret", secret);
        r.put("otpauthUrl", Totp.urlOtpauth(u.get().username(), secret, emetteurTotp));
        // Le code QR (SVG) à scanner avec Google Authenticator, Microsoft Authenticator ou 1Password.
        r.put("qrSvg", CodeQr.svg(Totp.urlOtpauthCompacte(u.get().username(), secret, emetteurTotp), 4, 4));
        return ResponseEntity.ok(r);
    }
}
