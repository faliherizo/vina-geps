package ca.vina.nexu.organisme.application.requetes;

import ca.vina.nexu.organisme.domaine.Circonstances;
import ca.vina.nexu.organisme.domaine.EvenementAlerte;
import ca.vina.nexu.organisme.domaine.PaysAutorises;
import ca.vina.nexu.organisme.domaine.PaysAutorisesPort;
import ca.vina.nexu.organisme.domaine.ReglagesPays;
import ca.vina.nexu.organisme.domaine.SignalementPort;
import ca.vina.nexu.partage.temps.Iso;

import java.time.Clock;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * <strong>La garde de la connexion par pays</strong> (P-TN-11) — jumeau de {@code refusParPays} du Node. Pour
 * un compte d'organisme, refuse hors des pays autorisés ; un compte global (sans organisme) n'est pas concerné.
 * Le pays vient de l'en-tête posé par la passerelle, et de lui seul : le client ne choisit pas d'où il vient.
 * Un refus déclenche l'alerte de l'organisme, sans retenir la réponse.
 */
public class GardeConnexionPays {

    private static final String PAYS_INCONNU = "un pays inconnu";

    private final PaysAutorisesPort pays;
    private final SignalementPort signalement;
    private final ReglagesPays reglages;
    private final Clock horloge;

    public GardeConnexionPays(PaysAutorisesPort pays, SignalementPort signalement, ReglagesPays reglages, Clock horloge) {
        this.pays = pays;
        this.signalement = signalement;
        this.reglages = reglages;
        this.horloge = horloge;
    }

    /** Le pays d'une requête, tel que la passerelle l'a posé ; {@code null} s'il manque. */
    public String paysDeLaRequete(UnaryOperator<String> entete) {
        return reglages.estConfigure() ? PaysAutorises.paysDeLEntete(entete.apply(reglages.nomEntete())) : null;
    }

    /**
     * Le motif du refus, ou vide si la connexion peut se poursuivre.
     *
     * @param entete la lecture d'un en-tête de la requête, par son nom
     * @param qui    le nom de connexion tenté — l'alerte dit qui a essayé
     */
    public Optional<String> refus(String tenantId, UnaryOperator<String> entete, String qui) {
        if (tenantId == null || tenantId.isEmpty()) {
            return Optional.empty();
        }
        String origine = paysDeLaRequete(entete);
        PaysAutorises.Verdict v = PaysAutorises.verdict(pays.lire(tenantId), origine);
        if (v.autorise()) {
            return Optional.empty();
        }
        signalement.signaler(tenantId, EvenementAlerte.CONNEXION_REFUSEE_PAYS,
                new Circonstances(qui, Iso.de(horloge.instant()), "connexion depuis " + (origine == null ? PAYS_INCONNU : origine)));
        return Optional.of(v.motif());
    }
}
