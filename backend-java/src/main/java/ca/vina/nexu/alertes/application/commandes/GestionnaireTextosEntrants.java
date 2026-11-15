package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.TextosEntrants.Entrant;
import ca.vina.nexu.alertes.domaine.TextosEntrants.Intention;
import ca.vina.nexu.alertes.domaine.TextosEntrants.Issue;
import ca.vina.nexu.alertes.domaine.TextosEntrants.Recu;
import ca.vina.nexu.alertes.domaine.TextosEntrants.RemiseOuverte;
import ca.vina.nexu.alertes.domaine.TextosEntrantsPort;
import ca.vina.nexu.partage.telephonie.Telephonie;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * <strong>Recevoir une réponse par texto</strong> (ST-G05 ; critères 4, 9, 10). Le texto se rattache à la dernière
 * alerte qui attend une réponse de ce numéro ; la réponse passe par la même porte qu'à l'écran
 * ({@link GestionnaireReceptionAlertes}), donc arrête une cascade comme elle. Un message rejoué ne fait rien.
 */
public final class GestionnaireTextosEntrants {

    /** Ce qui est arrivé, et le texto à renvoyer (nul : rien à renvoyer). */
    public record Resultat(Issue issue, String message) { }

    private final LectureAlertesPort lecture;
    private final TextosEntrantsPort port;
    private final GestionnaireReceptionAlertes reception;
    private final Clock horloge;

    public GestionnaireTextosEntrants(LectureAlertesPort lecture, TextosEntrantsPort port, GestionnaireReceptionAlertes reception, Clock horloge) {
        this.lecture = lecture;
        this.port = port;
        this.reception = reception;
        this.horloge = horloge;
    }

    public Resultat recevoir(Entrant e) {
        if (port.dejaRecu(e.messageSid())) {
            return new Resultat(Issue.REJOUE, null);
        }
        String numero = Telephonie.e164(e.de());
        Optional<RemiseOuverte> remise = numero == null ? Optional.empty() : port.derniereRemiseOuverte(numero);
        if (remise.isEmpty()) {
            return consigner(e, null, Issue.SANS_ALERTE, "");
        }
        Alerte a = lecture.alerte(remise.get().tenantId(), remise.get().alerteId()).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        Intention intention = Intention.de(e.corps());
        Issue issue = intention == Intention.INCONNUE ? Issue.AIDE : repondre(a, remise.get(), intention);
        return consigner(e, remise.get(), issue, a.titre());
    }

    private Issue repondre(Alerte a, RemiseOuverte r, Intention intention) {
        Optional<ReponsePredefinie> reponse = a.reponses().stream().filter(x -> x.semantique() == intention.semantique()).findFirst();
        if (reponse.isEmpty()) {
            return Issue.NON_PROPOSEE;
        }
        try {
            reception.repondre(new Acteur(r.tenantId(), r.personId()), a.id(), reponse.get().id());
        } catch (RefusAlerte refus) {
            if (refus.nature() != RefusAlerte.Nature.CONFLIT) {
                throw refus;
            }
            return Issue.DEJA_REPONDU;
        }
        return intention == Intention.PRISE_EN_CHARGE ? Issue.PRISE_EN_CHARGE : Issue.REFUS;
    }

    private Resultat consigner(Entrant e, RemiseOuverte r, Issue issue, String titre) {
        port.consigner(new Recu(e.messageSid(), r == null ? null : r.tenantId(), r == null ? null : r.alerteId(), r == null ? null : r.personId(),
                issue, Instant.now(horloge).toString()));
        return new Resultat(issue, issue.message(titre));
    }
}
