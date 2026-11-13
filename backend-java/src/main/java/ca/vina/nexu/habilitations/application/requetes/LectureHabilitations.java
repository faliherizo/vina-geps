package ca.vina.nexu.habilitations.application.requetes;

import ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort;
import ca.vina.nexu.habilitations.domaine.Habilitations;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/** Les lectures du contexte {@code habilitations} — Java nu, sans Spring. */
public class LectureHabilitations {

    private final DepotHabilitationsPort depot;
    private final Clock horloge;

    public LectureHabilitations(DepotHabilitationsPort depot, Clock horloge) {
        this.depot = depot;
        this.horloge = horloge;
    }

    public String aujourdhui() {
        return LocalDate.now(horloge).toString();
    }

    private String jour(String le) {
        return le == null || le.isBlank() ? aujourdhui() : le;
    }

    public List<Habilitations.Objet> objets(String tenantId) {
        return depot.listerObjets(tenantId);
    }

    public List<Habilitations.Octroi> octrois(String tenantId) {
        return depot.listerOctrois(tenantId);
    }

    public List<String> profilsDe(String tenantId, String utilisateurId) {
        return depot.profilsDe(tenantId, Habilitations.texte(utilisateurId, "utilisateurId"));
    }

    /** L'objet déclaré sous ce code, ou un 404 : un octroi sur un objet inconnu ne protège rien. */
    public Habilitations.Objet objet(String tenantId, String code) {
        return depot.listerObjets(tenantId).stream().filter(o -> o.code().equals(code)).findFirst()
                .orElseThrow(() -> new DepotHabilitationsPort.IntrouvableException("objet", code));
    }

    /** <strong>La permission d'une personne sur un objet, à une date</strong> (exigences 85, 86). */
    public Habilitations.Resolution permission(String tenantId, String utilisateurId, String objetCode, String le) {
        String code = Habilitations.texte(objetCode, "objetCode");
        Habilitations.Objet objet = objet(tenantId, code);
        return Habilitations.resoudre(utilisateurId, profilsDe(tenantId, utilisateurId),
                depot.listerOctrois(tenantId), objet.type(), code, jour(le));
    }

    /** <strong>Qui peut accéder à cet objet ?</strong> (exigence 90) */
    public Habilitations.DroitParObjet droits(String tenantId, String objetCode, String le) {
        return Habilitations.droitsSurObjet(depot.listerObjets(tenantId), depot.listerOctrois(tenantId),
                objetCode, jour(le));
    }

    /** <strong>Tous les droits d'une personne, dans une seule vue</strong> (exigence 91). */
    public Habilitations.FicheUtilisateur fiche(String tenantId, String utilisateurId, String le) {
        return Habilitations.ficheUtilisateur(depot.listerObjets(tenantId), depot.listerOctrois(tenantId),
                utilisateurId, profilsDe(tenantId, utilisateurId), jour(le));
    }
}
