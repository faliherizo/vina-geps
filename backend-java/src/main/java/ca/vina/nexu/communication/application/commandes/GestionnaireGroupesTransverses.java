package ca.vina.nexu.communication.application.commandes;

import ca.vina.nexu.communication.domaine.GroupesTransverses;
import ca.vina.nexu.communication.domaine.GroupesTransversesPort;
import ca.vina.nexu.communication.domaine.RefusCommunication;

import java.util.List;
import java.util.function.Supplier;

/**
 * <strong>Tenir les groupes transverses</strong> (ST-G09) : en créer, les renommer, les désactiver ; y inscrire une
 * personne pour une période, comme membre ou modératrice ; l'en retirer. Une personne inconnue de l'organisme est refusée.
 */
public final class GestionnaireGroupesTransverses {

    /** Un groupe et ses appartenances, tel que l'administration le lit. */
    public record Fiche(GroupesTransverses.Groupe groupe, List<GroupesTransverses.Appartenance> appartenances) { }

    /** Une inscription demandée ; les dates sont validées par le domaine. */
    public record Inscription(String personId, String depuis, String jusqua, boolean moderateur) { }

    private final GroupesTransversesPort.Lecture lecture;
    private final GroupesTransversesPort.Ecriture ecriture;
    private final Supplier<String> identifiants;

    public GestionnaireGroupesTransverses(GroupesTransversesPort.Lecture lecture, GroupesTransversesPort.Ecriture ecriture, Supplier<String> identifiants) {
        this.lecture = lecture;
        this.ecriture = ecriture;
        this.identifiants = identifiants;
    }

    /** En deçà de trois caractères, on ne cherche pas : sans ce seuil, la suggestion énumère l'organisme. */
    public static final int SEUIL = 3;
    public static final int LIMITE = 20;

    public List<GroupesTransversesPort.Personne> personnes(String tenantId, String texte) {
        String t = texte == null ? "" : texte.strip();
        return t.length() < SEUIL ? List.of() : lecture.personnes(tenantId, t, LIMITE);
    }

    public List<Fiche> fiches(String tenantId) {
        return lecture.groupes(tenantId).stream().map(g -> new Fiche(g, lecture.appartenances(tenantId, g.id()))).toList();
    }

    public Fiche creer(String tenantId, String nom, String nature) {
        GroupesTransverses.Groupe g = GroupesTransverses.nouveau(identifiants.get(), nom, nature);
        ecriture.enregistrer(tenantId, g);
        return new Fiche(g, List.of());
    }

    public Fiche modifier(String tenantId, String id, String nom, boolean actif) {
        GroupesTransverses.Groupe g = exiger(tenantId, id).avec(nom, actif);
        ecriture.enregistrer(tenantId, g);
        return new Fiche(g, lecture.appartenances(tenantId, id));
    }

    public Fiche inscrire(String tenantId, String groupeId, Inscription i) {
        GroupesTransverses.Groupe g = exiger(tenantId, groupeId);
        String nom = lecture.nomDe(tenantId, i.personId())
                .orElseThrow(() -> RefusCommunication.invalide("« personId » : « " + i.personId() + " » n'est pas une personne de l'organisme"));
        ecriture.enregistrer(tenantId, groupeId, GroupesTransverses.appartenance(i.personId(), nom, i.depuis(), i.jusqua(), i.moderateur()));
        return new Fiche(g, lecture.appartenances(tenantId, groupeId));
    }

    public Fiche retirer(String tenantId, String groupeId, String personId) {
        GroupesTransverses.Groupe g = exiger(tenantId, groupeId);
        if (!ecriture.retirer(tenantId, groupeId, personId)) {
            throw RefusCommunication.introuvable("« " + personId + " » n'est pas membre de ce groupe");
        }
        return new Fiche(g, lecture.appartenances(tenantId, groupeId));
    }

    private GroupesTransverses.Groupe exiger(String tenantId, String id) {
        return lecture.groupe(tenantId, id).orElseThrow(() -> RefusCommunication.introuvable("groupe introuvable"));
    }
}
