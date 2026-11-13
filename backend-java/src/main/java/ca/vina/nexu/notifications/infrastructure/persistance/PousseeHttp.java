package ca.vina.nexu.notifications.infrastructure.persistance;

import ca.vina.nexu.notifications.domaine.AbonnementsPoussee.Abonnement;
import ca.vina.nexu.notifications.domaine.PousseeWeb;

import java.security.SecureRandom;
import java.time.Clock;

/** L'adaptateur réel : chiffre, signe, livre au service de poussée du navigateur ; 404 et 410 disent « expiré ». */
public class PousseeHttp implements PousseeWeb.PorteurPoussee {

    private final ClientFournisseur client;
    private final PousseeWeb.ClesVapid cles;
    private final Clock horloge;
    private final SecureRandom alea;

    public PousseeHttp(ClientFournisseur client, PousseeWeb.ClesVapid cles, Clock horloge, SecureRandom alea) {
        this.client = client;
        this.cles = cles;
        this.horloge = horloge;
        this.alea = alea;
    }

    @Override
    public PousseeWeb.Resultat envoyer(Abonnement abonnement, PousseeWeb.Charge charge) {
        byte[] sel = new byte[16];
        alea.nextBytes(sel);
        PousseeWeb.MessageChiffre m = PousseeWeb.chiffrer(abonnement, charge, cles, sel, PousseeWeb.pairePhemere(alea), horloge.instant());
        try {
            ClientFournisseur.Reponse r = client.appeler(abonnement.endpoint(), m.enTetes(), "application/octet-stream", m.corps(), "service de poussée", b -> b);
            if (r.statut() == 404 || r.statut() == 410) {
                return new PousseeWeb.Resultat(PousseeWeb.EXPIRE, r.statut(), "abonnement expiré");
            }
            if (r.ok()) {
                return new PousseeWeb.Resultat(PousseeWeb.ENVOYE, r.statut(), null);
            }
            return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, r.statut(), r.extrait());
        } catch (IllegalStateException e) {
            return new PousseeWeb.Resultat(PousseeWeb.ECHOUE, null, e.getMessage());
        }
    }
}
