package ca.vina.nexu.habilitations.application.commandes;

import ca.vina.nexu.habilitations.application.requetes.LectureHabilitations;
import ca.vina.nexu.habilitations.domaine.DepotHabilitationsPort;
import ca.vina.nexu.habilitations.domaine.Habilitations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Les écritures du contexte {@code habilitations} — Java nu, sans Spring. */
public class GestionnaireHabilitations {

    private final DepotHabilitationsPort depot;
    private final LectureHabilitations lecture;
    private final Clock horloge;

    public GestionnaireHabilitations(DepotHabilitationsPort depot, LectureHabilitations lecture, Clock horloge) {
        this.depot = depot;
        this.lecture = lecture;
        this.horloge = horloge;
    }

    private static String texte(Map<String, Object> corps, String cle) {
        Object v = corps.get(cle);
        return v == null ? "" : v.toString().trim();
    }

    /**
     * <strong>Déclarer un objet protégeable.</strong>
     *
     * <p>Une composante déclare son formulaire parent : sans ce lien, l'exigence 90 — « les droits
     * par composante d'un formulaire » — ne se répondrait qu'en devinant lequel.
     */
    public Habilitations.Objet declarerObjet(String tenantId, Map<String, Object> corps) {
        Habilitations.TypeObjet type = Habilitations.TypeObjet.de(Habilitations.texte(texte(corps, "type"), "type"));
        String code = Habilitations.texte(texte(corps, "code"), "code");
        String parent = texte(corps, "parentCode").isEmpty() ? null : texte(corps, "parentCode");

        if (parent != null) {
            if (parent.equals(code)) {
                throw new Habilitations.HabilitationInvalideException(
                        "« " + code + " » ne peut pas être son propre parent");
            }
            if (lecture.objets(tenantId).stream().noneMatch(x -> x.code().equals(parent))) {
                throw new DepotHabilitationsPort.IntrouvableException("objet parent", parent);
            }
        }
        if (type == Habilitations.TypeObjet.COMPOSANTE && parent == null) {
            throw new Habilitations.HabilitationInvalideException("une composante déclare le formulaire dont elle "
                    + "fait partie : sans ce lien, les droits par composante ne se répondent qu'en devinant");
        }

        String libelle = texte(corps, "libelle").isEmpty() ? code : texte(corps, "libelle");
        depot.poserObjet(tenantId, new Habilitations.Objet(type, code, libelle, parent),
                Instant.now(horloge).toString());
        return lecture.objet(tenantId, code);
    }

    /** Période d'effectivité par profil <strong>et</strong> par utilisateur (exigence 84). */
    public Habilitations.Octroi octroyer(String tenantId, Map<String, Object> corps, String auteurId) {
        String code = Habilitations.texte(texte(corps, "objetCode"), "objetCode");
        Habilitations.Objet objet = lecture.objet(tenantId, code);
        String du = texte(corps, "effectifDu").isEmpty() ? lecture.aujourdhui() : texte(corps, "effectifDu");
        String au = texte(corps, "effectifAu").isEmpty() ? null : texte(corps, "effectifAu");

        Habilitations.Octroi candidat = new Habilitations.Octroi(
                Habilitations.Sujet.de(texte(corps, "sujetType")),
                Habilitations.texte(texte(corps, "sujetId"), "sujetId"),
                objet.type(), code,
                Habilitations.Permission.de(texte(corps, "permission")),
                du, au, texte(corps, "motif").isEmpty() ? null : texte(corps, "motif"));
        // Le domaine refuse une fenêtre inversée ; le dépôt persiste ce qu'il a accepté.
        Habilitations.verifierOctroi(candidat);
        depot.poserOctroi(tenantId, candidat, auteurId, Instant.now(horloge).toString());
        return candidat;
    }

    public record Revocation(String revoque) { }

    public Revocation revoquer(String tenantId, String sujetType, String sujetId, String objetCode, String effectifDu) {
        String sujet = Habilitations.texte(sujetId, "sujetId");
        String code = Habilitations.texte(objetCode, "objetCode");
        boolean retire = depot.retirerOctroi(tenantId, Habilitations.Sujet.de(sujetType), sujet, code,
                Habilitations.texte(effectifDu, "effectifDu"));
        if (!retire) {
            throw new DepotHabilitationsPort.IntrouvableException("octroi", sujet + " → " + code);
        }
        return new Revocation(sujet + " → " + code);
    }

    public List<String> rattacherProfil(String tenantId, String utilisateurId, String profilId) {
        depot.rattacherProfil(tenantId, Habilitations.texte(utilisateurId, "utilisateurId"),
                Habilitations.texte(profilId, "profilId"), Instant.now(horloge).toString());
        return lecture.profilsDe(tenantId, utilisateurId);
    }

    public List<String> detacherProfil(String tenantId, String utilisateurId, String profilId) {
        boolean retire = depot.detacherProfil(tenantId, Habilitations.texte(utilisateurId, "utilisateurId"),
                Habilitations.texte(profilId, "profilId"));
        if (!retire) {
            throw new DepotHabilitationsPort.IntrouvableException("rattachement", utilisateurId + " → " + profilId);
        }
        return lecture.profilsDe(tenantId, utilisateurId);
    }
}
