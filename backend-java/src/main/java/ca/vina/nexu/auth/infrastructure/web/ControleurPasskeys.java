package ca.vina.nexu.auth.infrastructure.web;

import ca.vina.nexu.auth.infrastructure.persistance.DepotPasskeys;

import ca.vina.nexu.auth.domaine.Passkeys;
import ca.vina.nexu.partage.securite.ContexteRequete;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Passkeys (#93) : gestion des identifiants et cérémonies d'enregistrement et d'authentification, pour le compte de la session. */
@RestController
@RequestMapping("/api/auth")
public class ControleurPasskeys {

    private final DepotPasskeys passkeys;

    public ControleurPasskeys(DepotPasskeys passkeys) {
        this.passkeys = passkeys;
    }

    private static ResponseEntity<Object> erreur(int code, String message) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(message)));
    }

    private static ResponseEntity<Object> pour(HttpServletRequest requete, int code, Function<String, Object> geste) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null) {
            return erreur(401, "unauthenticated");
        }
        try {
            return ResponseEntity.status(code).body(geste.apply(c.userId()));
        } catch (RuntimeException e) {
            return erreur(400, e.getMessage());
        }
    }

    @GetMapping("/webauthn/credentials")
    public ResponseEntity<Object> identifiants(HttpServletRequest requete) {
        return pour(requete, 200, u -> passkeys.lister(u).stream().map(Passkeys.Identifiant::vue).toList());
    }

    @DeleteMapping("/webauthn/credentials/{id}")
    public ResponseEntity<Object> supprimer(HttpServletRequest requete, @PathVariable("id") String id) {
        ContexteRequete c = GardeAcces.contexte(requete);
        if (c == null) {
            return erreur(401, "unauthenticated");
        }
        return ResponseEntity.status(passkeys.supprimer(c.userId(), id) ? 204 : 404).build();
    }

    @PostMapping("/webauthn/register/begin")
    public ResponseEntity<Object> debuterEnregistrement(HttpServletRequest requete) {
        return pour(requete, 200, passkeys::debuterEnregistrement);
    }

    @PostMapping("/webauthn/register/finish")
    public ResponseEntity<Object> terminerEnregistrement(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return pour(requete, 201, u -> passkeys.terminerEnregistrement(u, corps == null ? Map.of() : corps).vue());
    }

    @PostMapping("/webauthn/authenticate/begin")
    public ResponseEntity<Object> debuterAuthentification(HttpServletRequest requete) {
        return pour(requete, 200, passkeys::debuterAuthentification);
    }

    @PostMapping("/webauthn/authenticate/finish")
    public ResponseEntity<Object> terminerAuthentification(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        return pour(requete, 200, u -> passkeys.terminerAuthentification(u, corps == null ? Map.of() : corps));
    }
}
