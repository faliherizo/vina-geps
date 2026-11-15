package ca.vina.nexu.alertes.domaine;

import java.util.List;
import java.util.Optional;

/** Port : la trace des appels d'alerte, et le numéro d'une personne (jamais exposé hors du contexte). */
public interface AppelsAlertesPort {

    void enregistrer(AppelsAlertes.Appel appel);

    Optional<AppelsAlertes.Appel> appel(String id);

    List<AppelsAlertes.Appel> appelsDe(String tenantId, String alerteId);

    /** Le numéro E.164 de la fiche de cette personne ({@code compteAvis}), s'il est lisible. */
    Optional<String> numeroDe(String tenantId, String compteAvis);
}
