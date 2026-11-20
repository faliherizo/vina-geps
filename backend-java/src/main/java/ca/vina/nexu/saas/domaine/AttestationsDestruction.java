package ca.vina.nexu.saas.domaine;

import java.util.Optional;

/** Port de lecture : l'attestation de destruction d'un organisme, si ses données ont été détruites. */
public interface AttestationsDestruction {

    Optional<Destruction.Attestation> lireAttestation(String tenantId);
}
