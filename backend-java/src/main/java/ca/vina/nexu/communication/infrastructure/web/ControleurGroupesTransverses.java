package ca.vina.nexu.communication.infrastructure.web;

import ca.vina.nexu.communication.application.commandes.GestionnaireGroupesTransverses;
import ca.vina.nexu.communication.domaine.GroupesTransverses;
import ca.vina.nexu.communication.domaine.RefusCommunication;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code /api/communication/groupes-transverses} — l'administration des groupes de spécialité, de garde et de comité
 * (ST-G09). Ce sont des données de l'organisme, communes à la messagerie et aux alertes : aucun de ces modules ne les
 * possède, et seule l'administration de l'organisme les tient.
 */
@RestController
@RequestMapping("/api/communication/groupes-transverses")
public class ControleurGroupesTransverses {

    private static final int OK = 200;
    private static final int CREE = 201;
    private static final String ADMINISTRATION = "admin";

    private final GardeAcces garde;
    private final GestionnaireGroupesTransverses groupes;

    public ControleurGroupesTransverses(GardeAcces garde, GestionnaireGroupesTransverses groupes) {
        this.garde = garde;
        this.groupes = groupes;
    }

    private ResponseEntity<Object> administrer(HttpServletRequest r, int code, java.util.function.Function<String, Object> geste) {
        return GardeCommunication.repondre(r, garde.exigerRole(r, ADMINISTRATION), code, a -> geste.apply(a.tenantId()));
    }

    @GetMapping
    public ResponseEntity<Object> lister(HttpServletRequest r) {
        return administrer(r, OK, t -> groupes.fiches(t).stream().map(ControleurGroupesTransverses::vue).toList());
    }

    /** Les personnes à inscrire, cherchées par nom dès trois caractères ; la forme est celle du champ « personne ». */
    @GetMapping("/personnes")
    public ResponseEntity<Object> personnes(HttpServletRequest r, @org.springframework.web.bind.annotation.RequestParam(required = false) String q) {
        return administrer(r, OK, t -> groupes.personnes(t, q).stream().map(p -> Map.<String, Object>of("id", p.personId(), "nom", p.nom())).toList());
    }

    @PostMapping
    public ResponseEntity<Object> creer(HttpServletRequest r, @RequestBody(required = false) Map<String, Object> c) {
        return administrer(r, CREE, t -> vue(groupes.creer(t, texte(c, "nom"), texte(c, "nature"))));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Map<String, Object> c) {
        return administrer(r, OK, t -> vue(groupes.modifier(t, id, texte(c, "nom"), booleen(c, "actif"))));
    }

    @PutMapping("/{id}/membres/{personId}")
    public ResponseEntity<Object> inscrire(HttpServletRequest r, @PathVariable String id, @PathVariable String personId,
                                           @RequestBody(required = false) Map<String, Object> c) {
        return administrer(r, OK, t -> vue(groupes.inscrire(t, id, new GestionnaireGroupesTransverses.Inscription(personId, texte(c, "depuis"),
                texte(c, "jusqua"), c != null && Boolean.TRUE.equals(c.get("moderateur"))))));
    }

    @DeleteMapping("/{id}/membres/{personId}")
    public ResponseEntity<Object> retirer(HttpServletRequest r, @PathVariable String id, @PathVariable String personId) {
        return administrer(r, OK, t -> vue(groupes.retirer(t, id, personId)));
    }

    private static String texte(Map<String, Object> c, String champ) {
        Object v = c == null ? null : c.get(champ);
        if (v != null && !(v instanceof String)) {
            throw RefusCommunication.invalide("« " + champ + " » doit être un texte");
        }
        return (String) v;
    }

    private static boolean booleen(Map<String, Object> c, String champ) {
        if (c == null || !(c.get(champ) instanceof Boolean b)) {
            throw RefusCommunication.invalide("« " + champ + " » doit valoir vrai ou faux");
        }
        return b;
    }

    static Map<String, Object> vue(GestionnaireGroupesTransverses.Fiche f) {
        GroupesTransverses.Groupe g = f.groupe();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.id());
        m.put("nom", g.nom());
        m.put("nature", g.nature().code());
        m.put("actif", g.actif());
        m.put("membres", f.appartenances().stream().map(ControleurGroupesTransverses::membre).toList());
        return m;
    }

    private static Map<String, Object> membre(GroupesTransverses.Appartenance a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("personId", a.personId());
        m.put("nom", a.nom());
        m.put("depuis", a.depuis());
        m.put("jusqua", a.jusqua());
        m.put("moderateur", a.moderateur());
        return m;
    }
}
