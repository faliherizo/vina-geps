package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import ca.vina.nexu.alertes.domaine.Alertes.Destinataire;
import ca.vina.nexu.alertes.domaine.Alertes.Modele;
import ca.vina.nexu.alertes.domaine.Alertes.Remise;
import ca.vina.nexu.alertes.domaine.AnnuaireAlertes;
import ca.vina.nexu.alertes.domaine.EcritureAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.Priorite;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.partage.avis.domaine.PublicationAvis;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * <strong>Envoyer une alerte</strong> (CU-3 ; critères 1, 5, 7, 8, 18, 22). Les destinataires — personnes, groupes,
 * listes — sont résolus maintenant et figés (R2) ; l'alerte est écrite, puis annoncée par le port d'avis, qui choisit
 * les canaux selon les préférences — sauf la priorité critique, qui les impose tous (R8), et qui se réserve à qui en a
 * le droit (ST-G01). Si l'organisme l'a demandé, chacun reçoit aussi une copie par courriel (critère 8).
 */
public final class GestionnaireEnvoiAlertes {

    /** L'événement du catalogue des avis ; essentiel : une personne ne ferme pas tous ses canaux écrits. */
    public static final String EVENEMENT = "alerte.recue";
    /** L'écran où le destinataire lit et répond. */
    public static final String URL_MES_ALERTES = "/admin/mesalertes";
    private static final String COURRIEL = Priorite.Canal.COURRIEL.code();

    /** Qui viser : des personnes, des groupes et des listes de distribution, résolus à l'envoi. */
    public record Cibles(List<String> personnes, List<String> groupes, List<String> listes) {
        public Cibles {
            personnes = List.copyOf(personnes);
            groupes = List.copyOf(groupes);
            listes = List.copyOf(listes);
        }
    }

    /** La demande d'envoi : ce qu'on dit, à qui, depuis quel modèle (nul : aucun), et si l'acteur a le droit du critique. */
    public record Demande(Composition composition, Cibles cibles, String modeleId, boolean critiqueAutorisee) {
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

    public Alerte envoyer(Acteur acteur, Demande demande) {
        Composition c = avecModele(acteur, demande.composition(), demande.modeleId());
        Alertes.exigerDroit(c.priorite(), demande.critiqueAutorisee());
        List<Destinataire> resolus = resoudre(acteur, demande.cibles());
        Alerte a = Alertes.composer(identifiants.get(), acteur.tenantId(), acteur.personId(), Instant.now(horloge).toString(),
                c, resolus, d.lecture().reponses(acteur.tenantId()));
        d.ecriture().creer(a);
        aviser(a);
        return a;
    }

    /** Partie d'un modèle, l'alerte retient sa version courante ; un modèle inactif ou hors du périmètre est introuvable. */
    private Composition avecModele(Acteur acteur, Composition c, String modeleId) {
        if (modeleId == null || modeleId.isBlank()) {
            return c;
        }
        List<String> groupes = d.annuaire().groupesDe(acteur.tenantId(), acteur.personId());
        Modele m = d.lecture().modele(acteur.tenantId(), modeleId).filter(Modele::actif).filter(x -> x.visiblePour(groupes))
                .orElseThrow(() -> RefusAlerte.introuvable("modèle introuvable"));
        return new Composition(c.titre(), c.corps(), c.priorite(), c.reponseIds(), m.reference());
    }

    private List<Destinataire> resoudre(Acteur acteur, Cibles cibles) {
        String tenantId = acteur.tenantId();
        List<Destinataire> l = new ArrayList<>();
        for (String p : cibles.personnes()) {
            l.add(d.annuaire().personne(tenantId, p).orElseThrow(() -> RefusAlerte.invalide("« personnes » : « " + p + " » n'est pas une personne de l'organisme")));
        }
        for (String g : cibles.groupes()) {
            l.addAll(d.annuaire().membres(tenantId, g));
        }
        for (String liste : cibles.listes()) {
            l.addAll(d.annuaire().membresDeListe(tenantId, acteur.personId(), liste)
                    .orElseThrow(() -> RefusAlerte.invalide("« listes » : la liste « " + liste + " » n'existe pas ou ne vous est pas visible")));
        }
        return l;
    }

    /** Une panne d'avis n'annule pas l'alerte : elle est écrite, et l'écran « Mes alertes » la montre. */
    private void aviser(Alerte a) {
        List<String> comptes = a.remises().stream().map(Remise::destinataire).map(Destinataire::compteAvis).filter(Objects::nonNull).toList();
        String sujet = "[" + a.priorite().code() + "] " + a.titre();
        List<PublicationAvis.Envoi> envois = d.avis().publier(new PublicationAvis.Avis(a.tenantId(), EVENEMENT, comptes, sujet, a.corps(), URL_MES_ALERTES,
                Map.of(), a.priorite().canauxImposes()));
        copieParCourriel(a, comptes, envois, sujet);
    }

    /**
     * La copie par courriel (critère 8) : seulement à ceux que l'envoi n'a pas déjà joints par courriel — la priorité
     * critique les y a tous joints, et une préférence « courriel » aussi. Personne ne la reçoit deux fois.
     */
    private void copieParCourriel(Alerte a, List<String> comptes, List<PublicationAvis.Envoi> envois, String sujet) {
        if (!d.lecture().reglages(a.tenantId()).copieCourriel()) {
            return;
        }
        Set<String> dejaJoints = (envois == null ? List.<PublicationAvis.Envoi>of() : envois).stream()
                .filter(e -> COURRIEL.equals(e.canal())).map(PublicationAvis.Envoi::employeeId).collect(Collectors.toSet());
        List<String> restants = comptes.stream().filter(c -> !dejaJoints.contains(c)).toList();
        if (!restants.isEmpty()) {
            d.avis().publier(new PublicationAvis.Avis(a.tenantId(), EVENEMENT, restants, sujet, a.corps(), URL_MES_ALERTES, Map.of(), List.of(COURRIEL)));
        }
    }
}
