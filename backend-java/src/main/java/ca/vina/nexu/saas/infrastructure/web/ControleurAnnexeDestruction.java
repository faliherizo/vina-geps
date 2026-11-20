package ca.vina.nexu.saas.infrastructure.web;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.saas.domaine.AnnexeDestruction;
import ca.vina.nexu.saas.domaine.AttestationsDestruction;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * {@code GET /api/saas/tenants/{id}/attestation-destruction/annexe-c?finContrat=AAAA-MM-JJ} — l'attestation de
 * destruction au gabarit de l'annexe 10.18.04 C (FX-AO-20260810), à signer et transmettre dans les 60 jours.
 * Réservée, comme l'attestation elle-même, à l'administration du système.
 */
@RestController
@RequestMapping("/api/saas/tenants/{id}/attestation-destruction")
public class ControleurAnnexeDestruction {

    private static final int OK = 200;
    /** La date de l'attestation est celle du Québec, où elle est signée. */
    private static final ZoneId FUSEAU = ZoneId.of("America/Montreal");
    /** Posé sur la seule réponse réussie : un refus reste en JSON ({@code errors}), comme partout ailleurs. */
    private static final MediaType TEXTE = MediaType.parseMediaType("text/plain;charset=UTF-8");

    private final GardeAcces garde;
    private final AttestationsDestruction attestations;
    private final AnnexeDestruction.Signataire signataire;
    private final Clock horloge;

    public ControleurAnnexeDestruction(GardeAcces garde, AttestationsDestruction attestations, AnnexeDestruction.Signataire signataire, Clock horloge) {
        this.garde = garde;
        this.attestations = attestations;
        this.signataire = signataire;
        this.horloge = horloge;
    }

    @GetMapping("/annexe-c")
    public ResponseEntity<Object> annexeC(HttpServletRequest r, @PathVariable("id") String id, @RequestParam(name = "finContrat", required = false) String finContrat) {
        ResponseEntity<Object> reponse = Bord.repondre(garde.exigerAdministrationSysteme(r), OK, t -> {
            LocalDate fin = AnnexeDestruction.finContrat(finContrat);
            LocalDate aujourdhui = LocalDate.now(horloge.withZone(FUSEAU));
            return attestations.lireAttestation(id)
                    .map(a -> AnnexeDestruction.texte(a, signataire, AnnexeDestruction.Echeance.de(fin, aujourdhui), aujourdhui))
                    .orElseThrow(() -> new IntrouvableException("aucune attestation : les données n'ont pas été détruites"));
        });
        return reponse.getStatusCode().value() == OK ? ResponseEntity.ok().contentType(TEXTE).body(reponse.getBody()) : reponse;
    }
}
