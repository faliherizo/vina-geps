package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Alertes.Alerte;
import ca.vina.nexu.alertes.domaine.Alertes.ReponsePredefinie;
import ca.vina.nexu.alertes.domaine.AppelsAlertes;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.AAppeler;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.Appel;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.Resultat;
import ca.vina.nexu.alertes.domaine.AppelsAlertes.Touche;
import ca.vina.nexu.alertes.domaine.AppelsAlertesPort;
import ca.vina.nexu.alertes.domaine.LectureAlertesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.partage.telephonie.AppelsVocauxPort;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * <strong>L'appel automatique d'une alerte</strong> (ST-G06 ; critères 13, 14) : lancer l'appel d'une étape de
 * cascade, recevoir la touche pressée — « 1 » prend en charge par la même porte qu'à l'écran, donc arrête la cascade —,
 * et recevoir le résultat et la durée de l'appel.
 */
public final class GestionnaireAppelsAlertes {

    /** Les URL publiques que le fournisseur rappelle ; {@code base} : l'adresse publique du déploiement. */
    public record Rappels(String base) {
        public static final String CHEMIN_CHIFFRE = "/api/alertes/webhooks/voix";
        public static final String CHEMIN_STATUT = "/api/alertes/webhooks/voix/statut";
        public static final String PARAMETRE = "appel";

        String chiffre(String appelId) {
            return base + CHEMIN_CHIFFRE + "?" + PARAMETRE + "=" + appelId;
        }

        String statut(String appelId) {
            return base + CHEMIN_STATUT + "?" + PARAMETRE + "=" + appelId;
        }
    }

    /** Ce que le gestionnaire lit et emploie. */
    public record Dependances(LectureAlertesPort lecture, AppelsAlertesPort appels, AppelsVocauxPort voix, GestionnaireReceptionAlertes reception) { }

    private final Dependances d;
    private final Rappels rappels;
    private final Supplier<String> identifiants;
    private final Clock horloge;

    public GestionnaireAppelsAlertes(Dependances d, Rappels rappels, Supplier<String> identifiants, Clock horloge) {
        this.d = d;
        this.rappels = rappels;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    /**
     * Lance l'appel d'une étape. Rien si la personne a déjà répondu, ou si cet appel a déjà été lancé (tâche reprise
     * après une panne) ; sans numéro lisible, l'appel est tracé en échec, sans rien composer.
     */
    public void lancer(String tenantId, AAppeler a) {
        Optional<Alerte> alerte = d.lecture().alerte(tenantId, a.alerteId());
        if (alerte.isEmpty() || alerte.get().remiseDe(a.personId()).repondue() || dejaLance(tenantId, a)) {
            return;
        }
        String maintenant = Instant.now(horloge).toString();
        Optional<String> numero = d.appels().numeroDe(tenantId, a.compteAvis());
        Appel appel = new Appel(identifiants.get(), tenantId, a.alerteId(), a.etape(), a.personId(), null,
                numero.isPresent() ? Resultat.LANCE : Resultat.ECHEC, null, null, maintenant, numero.isPresent() ? null : maintenant);
        d.appels().enregistrer(appel);
        numero.ifPresent(n -> d.appels().enregistrer(appel.avecFournisseur(d.voix().appeler(new AppelsVocauxPort.Appel(tenantId, appel.id(), n,
                AppelsAlertes.message(alerte.get()), rappels.chiffre(appel.id()), rappels.statut(appel.id()))))));
    }

    private boolean dejaLance(String tenantId, AAppeler a) {
        return d.appels().appelsDe(tenantId, a.alerteId()).stream().anyMatch(x -> x.etape() == a.etape() && x.personId().equals(a.personId()));
    }

    /** La touche pressée ; rend ce que la voix répond. */
    public String touche(String appelId, String chiffres) {
        Appel appel = d.appels().appel(appelId).orElseThrow(() -> RefusAlerte.introuvable("appel introuvable"));
        Touche t = Touche.de(chiffres);
        if (t == Touche.AUTRE) {
            return AppelsAlertes.reponseVocale(t, true);
        }
        d.appels().enregistrer(appel.touchee(chiffres));
        Alerte a = d.lecture().alerte(appel.tenantId(), appel.alerteId()).orElseThrow(() -> RefusAlerte.introuvable("alerte introuvable"));
        Optional<ReponsePredefinie> reponse = a.reponses().stream().filter(r -> r.semantique() == t.semantique()).findFirst();
        if (reponse.isEmpty()) {
            return AppelsAlertes.reponseVocale(Touche.AUTRE, true);
        }
        return AppelsAlertes.reponseVocale(t, repondre(appel, reponse.get()));
    }

    private boolean repondre(Appel appel, ReponsePredefinie reponse) {
        try {
            d.reception().repondre(new Acteur(appel.tenantId(), appel.personId()), appel.alerteId(), reponse.id());
            return true;
        } catch (RefusAlerte refus) {
            if (refus.nature() != RefusAlerte.Nature.CONFLIT) {
                throw refus;
            }
            return false;
        }
    }

    /** La fin d'appel : son résultat et sa durée. Un appel inconnu est ignoré — le fournisseur ne doit pas retenter. */
    public void statut(String appelId, String statutFournisseur, String reponduPar, Integer dureeSecondes) {
        d.appels().appel(appelId).ifPresent(a -> d.appels().enregistrer(a.termine(Resultat.duStatut(statutFournisseur, reponduPar), dureeSecondes,
                Instant.now(horloge).toString())));
    }
}
