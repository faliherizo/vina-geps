package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.commandes.GestionnaireEnvoiAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireParametresAlertes;
import ca.vina.nexu.alertes.application.commandes.GestionnaireReceptionAlertes;
import ca.vina.nexu.alertes.application.requetes.LectureAlertes;
import ca.vina.nexu.alertes.domaine.AlerteSuivie;
import ca.vina.nexu.alertes.domaine.Alertes;
import ca.vina.nexu.alertes.domaine.Alertes.Composition;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.partage.export.infrastructure.SerialiseursLiasse;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_CONFIGURATION;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_CRITIQUE;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_ENVOI;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_SUIVI;
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
    private static final String TITRE_EXPORT = "Suivi des communications";

    /** Les gestes et la lecture du contexte, groupés pour garder le constructeur court. */
    public record Cas(GestionnaireParametresAlertes parametres, GestionnaireEnvoiAlertes envoi, GestionnaireReceptionAlertes reception, LectureAlertes lecture) {
    }

    private final GardeAlertes garde;
    private final Cas cas;
    private final Clock horloge;

    public ControleurAlertes(GardeAlertes garde, Cas cas, Clock horloge) {
        this.garde = garde;
        this.cas = cas;
        this.horloge = horloge;
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

    /** L'administration voit tous les modèles ; un expéditeur, ceux de son périmètre, actifs (ST-G02). */
    @GetMapping("/modeles")
    public ResponseEntity<Object> modeles(HttpServletRequest r) {
        boolean tous = garde.peut(r, CAPACITE_CONFIGURATION);
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> (tous ? cas.lecture().modeles(a.tenantId()) : cas.lecture().modelesPour(a))
                .stream().map(VuesAlertes::modele).toList());
    }

    @PostMapping("/modeles")
    public ResponseEntity<Object> creerModele(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), CREE,
                a -> VuesAlertes.modele(cas.parametres().creerModele(a, SaisiesAlertes.contenuModele(corps(c)))));
    }

    /** Modifier le contenu fait une version nouvelle (ST-G02). */
    @PutMapping("/modeles/{id}")
    public ResponseEntity<Object> modifierModele(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesAlertes.modele(cas.parametres().modifierModele(a, id, SaisiesAlertes.contenuModele(corps(c)))));
    }

    @PatchMapping("/modeles/{id}")
    public ResponseEntity<Object> activerModele(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> VuesAlertes.modele(cas.parametres().activerModele(a, id, SaisiesAlertes.booleen(corps(c), "actif"))));
    }

    @GetMapping("/modeles/{id}/versions")
    public ResponseEntity<Object> versions(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK,
                a -> cas.parametres().versions(a.tenantId(), id).stream().map(VuesAlertes::version).toList());
    }

    @GetMapping("/reglages")
    public ResponseEntity<Object> reglages(HttpServletRequest r) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK, a -> VuesAlertes.reglages(cas.parametres().reglages(a.tenantId())));
    }

    @PutMapping("/reglages")
    public ResponseEntity<Object> definirReglages(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK, a -> VuesAlertes.reglages(cas.parametres()
                .definirReglages(a.tenantId(), new Alertes.Reglages(SaisiesAlertes.booleen(corps(c), "copieCourriel")))));
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

    /** Les listes de distribution que l'expéditeur voit : partagées, ou les siennes. */
    @GetMapping("/listes")
    public ResponseEntity<Object> listes(HttpServletRequest r) {
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), OK, a -> cas.lecture().listes(a).stream().map(VuesAlertes::liste).toList());
    }

    /** Ce que l'écran doit offrir à cette personne : la priorité critique, la configuration. */
    @GetMapping("/droits")
    public ResponseEntity<Object> droits(HttpServletRequest r) {
        return repondre(r, garde.socle(r), OK, a -> Map.of("envoyer", garde.peut(r, CAPACITE_ENVOI), "suivre", garde.peut(r, CAPACITE_SUIVI), "critique", garde.peut(r, CAPACITE_CRITIQUE),
                "configurer", garde.peut(r, CAPACITE_CONFIGURATION)));
    }

    // ── Envoi et suivi (expéditeurs) ────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Object> envoyer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        Map<String, Object> d = corps(c);
        boolean critique = garde.peut(r, CAPACITE_CRITIQUE);
        return repondre(r, garde.capacite(r, CAPACITE_ENVOI), CREE, a -> VuesAlertes.suivi(AlerteSuivie.nouvelle(cas.envoi().envoyer(a, new GestionnaireEnvoiAlertes.Demande(
                new Composition(SaisiesAlertes.texte(d, "titre"), SaisiesAlertes.texte(d, "corps"), SaisiesAlertes.priorite(d), SaisiesAlertes.textes(d, "reponseIds")),
                new GestionnaireEnvoiAlertes.Cibles(SaisiesAlertes.textes(d, "personnes"), SaisiesAlertes.textes(d, "groupes"), SaisiesAlertes.textes(d, "listes")),
                SaisiesAlertes.texte(d, "modeleId"), critique, SaisiesAlertes.texte(d, "politiqueId"))))));
    }

    /** Le suivi, filtrable : période ({@code du}, {@code au}), priorité, état, expéditeur (ST-G03). */
    @GetMapping("/envoyees")
    public ResponseEntity<Object> envoyees(HttpServletRequest r, @RequestParam Map<String, String> q) {
        return repondre(r, garde.capacite(r, CAPACITE_SUIVI), OK,
                a -> cas.lecture().envoyees(a.tenantId(), SaisiesAlertes.filtre(q)).stream().map(VuesAlertes::suivi).toList());
    }

    /** Le même suivi, au même filtre, en fichier : CSV, XLSX, PDF ou JSON (ST-G03). */
    @GetMapping("/envoyees/export")
    public ResponseEntity<Object> exporter(HttpServletRequest r, @RequestParam Map<String, String> q) {
        GardeAcces.Verdict v = garde.capacite(r, CAPACITE_SUIVI);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            SaisiesAlertes.FormatExport format = SaisiesAlertes.FormatExport.de(q.getOrDefault("format", "csv"));
            boolean csv = format == SaisiesAlertes.FormatExport.CSV;
            List<Map<String, Object>> lignes = cas.lecture().aExporter(v.tenantId(), SaisiesAlertes.filtre(q)).stream().map(a -> VuesAlertes.ligneExport(a, csv)).toList();
            SerialiseursLiasse.Liasse liasse = new SerialiseursLiasse.Liasse(TITRE_EXPORT, Instant.now(horloge).toString(), Map.of(),
                    List.of(new SerialiseursLiasse.Section(TITRE_EXPORT, lignes)));
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, SerialiseursLiasse.TYPES_MIME.get(format.code()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"suivi-alertes." + format.code() + "\"")
                    .body(SerialiseursLiasse.serialiser(liasse, format.code()));
        } catch (RefusAlerte e) {
            return GardeAlertes.erreur(GardeAlertes.statutDe(e.nature()), e.getMessage());
        }
    }

    @GetMapping("/envoyees/{id}")
    public ResponseEntity<Object> fiche(HttpServletRequest r, @PathVariable String id) {
        return repondre(r, garde.capacite(r, CAPACITE_SUIVI), OK, a -> VuesAlertes.suivi(cas.lecture().fiche(a.tenantId(), id)));
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
