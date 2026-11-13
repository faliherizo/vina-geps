package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.personnel.application.commandes.GestionnairePersonnes;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;

import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.application.requetes.LecturePersonnes;
import ca.vina.nexu.personnel.domaine.RepertoirePersonnes.Personne;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/personnel} — le répertoire des personnes de l'établissement : ce que lisent les champs
 * « personne » (délégation, avis, incidents) et le lien d'un compte à sa fiche. Lire est ouvert à
 * l'établissement ; ajouter ou corriger une fiche demande la capacité correspondante.
 */
@RestController
@RequestMapping("/api/personnel")
public class ControleurPersonnel {

    static final String CREER = "employee.create";
    static final String MODIFIER = "employee.update";

    private final GardeAcces garde;
    private final LecturePersonnes lecture;
    private final GestionnairePersonnes gestionnaire;

    public ControleurPersonnel(GardeAcces garde, LecturePersonnes lecture, GestionnairePersonnes gestionnaire) {
        this.garde = garde;
        this.lecture = lecture;
        this.gestionnaire = gestionnaire;
    }

    private static String texte(Map<String, Object> corps, String cle) {
        Object v = corps == null ? null : corps.get(cle);
        return v instanceof String s ? s : null;
    }

    /** Le corps traduit une fois, ici, avec les noms de champs que l'interface emploie déjà. */
    static FichesPersonnes.Demande demande(Map<String, Object> corps) {
        return new FichesPersonnes.Demande(texte(corps, "firstName"), texte(corps, "lastName"), texte(corps, "email"),
                texte(corps, "phone"), texte(corps, "jobTitle"), texte(corps, "hireDate"));
    }

    private static ResponseEntity<Object> refus(int code, String motif) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(motif)));
    }

    private ResponseEntity<Object> ecrire(GardeAcces.Verdict v, int code, java.util.function.Supplier<Personne> geste) {
        if (v.refuse()) {
            return v.refus();
        }
        try {
            Personne p = geste.get();
            Map<String, Object> m = new LinkedHashMap<>(suggestion(p));
            m.putAll(fiche(p));
            return ResponseEntity.status(code).body(m);
        } catch (FichesPersonnes.FicheInvalideException e) {
            return refus(400, e.getMessage());
        } catch (IntrouvableException e) {
            return refus(404, e.getMessage());
        }
    }

    @PostMapping("/employes")
    public ResponseEntity<Object> creer(HttpServletRequest requete, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerCapacite(requete, CREER);
        return ecrire(v, 201, () -> gestionnaire.creer(v.tenantId(), demande(corps)));
    }

    @PutMapping("/employes/{id}")
    public ResponseEntity<Object> modifier(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerCapacite(requete, MODIFIER);
        return ecrire(v, 200, () -> gestionnaire.modifier(v.tenantId(), id, demande(corps)));
    }

    /** La forme d'une suggestion, celle qu'attend le champ « personne » de l'interface. */
    static Map<String, Object> suggestion(Personne p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("nom", p.nomComplet());
        m.put("titre", p.titre());
        m.put("courriel", p.courriel());
        m.put("statut", p.statut());
        return m;
    }

    /** La forme d'une fiche dans une liste de choix : prénom et nom séparés, pour trier et afficher. */
    static Map<String, Object> fiche(Personne p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("firstName", p.prenom());
        m.put("lastName", p.nom());
        return m;
    }

    @GetMapping("/employes")
    public ResponseEntity<Object> employes(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        List<Map<String, Object>> fiches = lecture.tous(v.tenantId()).stream().map(ControleurPersonnel::fiche).toList();
        return ResponseEntity.ok(fiches);
    }

    @GetMapping("/employes/suggestions")
    public ResponseEntity<Object> suggestions(HttpServletRequest requete, @RequestParam(name = "q", required = false) String q) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return ResponseEntity.ok(lecture.suggerer(v.tenantId(), q).stream().map(ControleurPersonnel::suggestion).toList());
    }

    @GetMapping("/employes/{id}")
    public ResponseEntity<Object> employe(HttpServletRequest requete, @PathVariable String id) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return lecture.lire(v.tenantId(), id).<ResponseEntity<Object>>map(p -> ResponseEntity.ok(suggestion(p)))
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of("errors", List.of("employé introuvable : " + id))));
    }
}
