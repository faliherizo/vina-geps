package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_CONFIGURATION;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_ENVOI;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.repondre;
import static ca.vina.nexu.alertes.infrastructure.web.SaisiesAlertes.corps;

/**
 * {@code /api/alertes} — les communications cliniques (ADR 0074). Le contrôleur traduit HTTP en
 * commande ou requête, rien de plus : les règles vivent dans le domaine, la garde dans {@link GardeAlertes}.
 */
@RestController
@RequestMapping("/api/alertes")
public class ControleurAlertes {

    private static final int OK = 200;
    private static final int CREE = 201;

    /** Les gestes et la lecture du contexte, groupés pour garder le constructeur court. */
    public record Cas(GestionnaireParametresAlertes parametres, GestionnaireEnvoiAlertes envoi, GestionnaireReceptionAlertes reception, LectureAlertes lecture) {
    }

    private final GardeAlertes garde;
    private final Cas cas;

    public ControleurAlertes(GardeAlertes garde, Cas cas) {
        this.garde = garde;
        this.cas = cas;
    }

    // ── Paramètres (administration) ─────────────────────────────────────────

    @GetMapping("/reponses")
    public ResponseEntity<Object> reponses(HttpServletRequest r) {
        return repondre(r, garde.socle(r), OK, a -> cas.lecture().reponses(a.tenantId()).stream().map(VuesAlertes::reponse).toList());
    }

    @PostMapping("/reponses")
    public ResponseEntity<Object> creerReponse(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), CREE, a -> VuesAlertes.reponse(cas.parametres()
                .creerReponse(a.tenantId(), SaisiesAlertes.texte(corps(c), "libelle"), SaisiesAlertes.semantique(corps(c)))));
    }

    @PatchMapping("/reponses/{id}")
    public ResponseEntity<Object> activerReponse(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesAlertes.reponse(cas.parametres().activerReponse(a.tenantId(), id, SaisiesAlertes.booleen(corps(c), "active"))));
    }

    @GetMapping("/modeles")
    public ResponseEntity<Object> modeles(HttpServletRequest r) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().modeles(a.tenantId()).stream().map(VuesAlertes::modele).toList());
    }

    @PostMapping("/modeles")
    public ResponseEntity<Object> creerModele(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> d = corps(c);
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), CREE, a -> VuesAlertes.modele(cas.parametres().creerModele(a.tenantId(),
                SaisiesAlertes.texte(d, "titre"), SaisiesAlertes.texte(d, "corps"), SaisiesAlertes.priorite(d), SaisiesAlertes.textes(d, "reponseIds"))));
    }

    @PatchMapping("/modeles/{id}")
    public ResponseEntity<Object> activerModele(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesAlertes.modele(cas.parametres().activerModele(a.tenantId(), id, SaisiesAlertes.booleen(corps(c), "actif"))));
    }

    @GetMapping("/groupes")
    public ResponseEntity<Object> groupes(HttpServletRequest r) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().groupes(a.tenantId()).stream().map(VuesAlertes::groupe).toList());
    }

    /** Les personnes d'un groupe, nommées : l'écran d'envoi les propose une à une. */
    @GetMapping("/groupes/{id}/membres")
    public ResponseEntity<Object> membres(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().membres(a.tenantId(), id).stream().map(VuesAlertes::personne).toList());
    }

    // ── Envoi et suivi (expéditeurs) ────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Object> envoyer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> d = corps(c);
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), CREE, a -> VuesAlertes.suivi(cas.envoi().envoyer(a,
                new Composition(SaisiesAlertes.texte(d, "titre"), SaisiesAlertes.texte(d, "corps"), SaisiesAlertes.priorite(d), SaisiesAlertes.textes(d, "reponseIds")),
                new GestionnaireEnvoiAlertes.Cibles(SaisiesAlertes.textes(d, "personnes"), SaisiesAlertes.textes(d, "groupes")))));
    }

    @GetMapping("/envoyees")
    public ResponseEntity<Object> envoyees(HttpServletRequest r) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().envoyees(a.tenantId()).stream().map(VuesAlertes::suivi).toList());
    }

    @GetMapping("/envoyees/{id}")
    public ResponseEntity<Object> fiche(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> VuesAlertes.suivi(cas.lecture().fiche(a.tenantId(), id)));
    }

    // ── Réception (destinataires) ───────────────────────────────────────────

    @GetMapping("/recues")
    public ResponseEntity<Object> recues(HttpServletRequest r) {
        return repondre(r, garde.socle(r), OK, a -> VuesAlertes.recues(cas.lecture().recues(a), a.personId()));
    }

    @PostMapping("/recues/{id}/lue")
    public ResponseEntity<Object> lire(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.socle(r), OK, a -> VuesAlertes.recue(cas.reception().lire(a, id), a.personId()));
    }

    @PostMapping("/recues/{id}/reponse")
    public ResponseEntity<Object> repondreA(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.socle(r), OK,
                a -> VuesAlertes.recue(cas.reception().repondre(a, id, SaisiesAlertes.texte(corps(c), "reponseId")), a.personId()));
    }
}
