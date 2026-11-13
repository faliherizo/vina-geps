package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.domaine.GroupesDeTravailPort;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/communication/groupes} — le périmètre de la communication : les groupes de travail et
 * leurs membres, pour nommer les participants plutôt que de montrer leurs identifiants.
 */
@RestController
@RequestMapping("/api/communication")
public class ControleurGroupesDeTravail {

    private static final int OK = 200;

    private final GardeCommunication garde;
    private final GroupesDeTravailPort groupes;

    public ControleurGroupesDeTravail(GardeCommunication garde, GroupesDeTravailPort groupes) {
        this.garde = garde;
        this.groupes = groupes;
    }

    @GetMapping("/groupes")
    public ResponseEntity<Object> groupes(HttpServletRequest requete) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> groupes.groupes(a.tenantId()));
    }

    @GetMapping("/groupes/{id}/membres")
    public ResponseEntity<Object> membres(HttpServletRequest requete, @PathVariable String id) {
        return GardeCommunication.repondre(requete, garde.socle(requete), OK, a -> groupes.membres(a.tenantId(), id));
    }
}
