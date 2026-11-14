package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * <strong>Envoyer une alerte</strong> (CU-3 ; critères 1, 5, 7, 8, 18, 22). Les destinataires sont résolus
 * maintenant et figés (R2) ; l'alerte est écrite, puis annoncée par le port d'avis, qui choisit les canaux
 * selon les préférences — sauf la priorité critique, qui les impose tous (R8).
 */
public final class GestionnaireEnvoiAlertes {

    /** L'événement du catalogue des avis ; essentiel : une personne ne ferme pas tous ses canaux écrits. */
    public static final String EVENEMENT = "alerte.recue";
    /** L'écran où le destinataire lit et répond. */
    public static final String URL_MES_ALERTES = "/admin/mesalertes";

    /** Qui viser : des personnes et des groupes, résolus à l'envoi. */
    public record Cibles(List<String> personnes, List<String> groupes) {

        public Cibles {
            personnes = List.copyOf(personnes);
            groupes = List.copyOf(groupes);
        }
    }

    /** Les dépendances du geste, groupées pour garder le constructeur court. */
    public record Dependances(LectureAlertesPort lecture, EcritureAlertesPort ecriture, AnnuaireAlertes annuaire, PublicationAvis avis) {
    }

    private final Dependances d;
    private final Supplier<String> identifiants;
    private final Clock horloge;

    public GestionnaireEnvoiAlertes(Dependances d, Supplier<String> identifiants, Clock horloge) {
        this.d = d;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Alerte envoyer(Acteur acteur, Composition composition, Cibles cibles) {
        List<Destinataire> resolus = resoudre(acteur.tenantId(), cibles);
        Alerte a = Alertes.composer(identifiants.get(), acteur.tenantId(), acteur.personId(), Instant.now(horloge).toString(),
                composition, resolus, d.lecture().reponses(acteur.tenantId()));
        d.ecriture().creer(a);
        aviser(a);
        return a;
    }

    private List<Destinataire> resoudre(String tenantId, Cibles cibles) {
        List<Destinataire> l = new ArrayList<>();
        for (String p : cibles.personnes()) {
            l.add(d.annuaire().personne(tenantId, p).orElseThrow(() -> RefusAlerte.invalide("« personnes » : « " + p + " » n'est pas une personne de l'organisme")));
        }
        for (String g : cibles.groupes()) {
            l.addAll(d.annuaire().membres(tenantId, g));
        }
        return l;
    }

    /** Une panne d'avis n'annule pas l'alerte : elle est écrite, et l'écran « Mes alertes » la montre. */
    private void aviser(Alerte a) {
        List<String> comptes = a.remises().stream().map(Remise::destinataire).map(Destinataire::compteAvis).filter(Objects::nonNull).toList();
        String sujet = "[" + a.priorite().code() + "] " + a.titre();
        d.avis().publier(new PublicationAvis.Avis(a.tenantId(), EVENEMENT, comptes, sujet, a.corps(), URL_MES_ALERTES,
                java.util.Map.of(), a.priorite().canauxImposes()));
    }
}
