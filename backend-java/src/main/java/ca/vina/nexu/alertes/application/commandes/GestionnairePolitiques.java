package ca.vina.nexu.alertes.application.commandes;

import ca.vina.nexu.alertes.domaine.Acteur;
import ca.vina.nexu.alertes.domaine.Cascades.CritereArret;
import ca.vina.nexu.alertes.domaine.Cascades.Etape;
import ca.vina.nexu.alertes.domaine.Cascades.Politique;
import ca.vina.nexu.alertes.domaine.EcritureCascadesPort;
import ca.vina.nexu.alertes.domaine.LectureCascadesPort;
import ca.vina.nexu.alertes.domaine.RefusAlerte;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/** <strong>Tenir les politiques de relance</strong> (ST-G04, critères 15, 17, 32) : créer, modifier en versions, désactiver. */
public final class GestionnairePolitiques {

    /** Ce qu'une politique dit : son nom, ses étapes, son critère d'arrêt. */
    public record Contenu(String nom, List<Etape> etapes, CritereArret arret) {
        public Contenu {
            etapes = List.copyOf(etapes);
        }
    }

    private final LectureCascadesPort lecture;
    private final EcritureCascadesPort ecriture;
    private final Supplier<String> identifiants;
    private final Clock horloge;

    public GestionnairePolitiques(LectureCascadesPort lecture, EcritureCascadesPort ecriture, Supplier<String> identifiants, Clock horloge) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Politique creer(Acteur acteur, Contenu c) {
        return ranger(acteur, Politique.nouvelle(identifiants.get(), c.nom(), c.etapes(), c.arret()));
    }

    /** Une modification fait une version nouvelle ; une cascade en cours garde celle avec laquelle elle a été armée. */
    public Politique modifier(Acteur acteur, String id, Contenu c) {
        return ranger(acteur, exiger(acteur, id).modifiee(c.nom(), c.etapes(), c.arret()));
    }

    public Politique activer(Acteur acteur, String id, boolean actif) {
        return ranger(acteur, exiger(acteur, id).avecActif(actif));
    }

    private Politique exiger(Acteur acteur, String id) {
        return lecture.politique(acteur.tenantId(), id).orElseThrow(() -> RefusAlerte.introuvable("politique introuvable"));
    }

    private Politique ranger(Acteur acteur, Politique p) {
        ecriture.enregistrer(acteur.tenantId(), p, Instant.now(horloge).toString(), acteur.personId());
        return p;
    }
}
