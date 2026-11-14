package ca.vina.nexu.personnel.infrastructure.web;

import ca.vina.nexu.partage.erreurs.domaine.IntrouvableException;
import ca.vina.nexu.partage.web.GardeAcces;
import ca.vina.nexu.personnel.application.commandes.GestionnaireHierarchie;
import ca.vina.nexu.personnel.domaine.AnnuaireEmployes;
import ca.vina.nexu.personnel.domaine.FichesPersonnes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /api/personnel/hierarchie} — l'organigramme : qui relève de qui. C'est ce que lit l'approbateur
 * « supérieur hiérarchique » du flux des feuilles de temps. Lire est ouvert à l'établissement ; désigner
 * un supérieur demande la capacité de corriger une fiche.
 */
@RestController
@RequestMapping("/api/personnel")
public class ControleurHierarchie {

    private final GardeAcces garde;
    private final GestionnaireHierarchie gestionnaire;

    public ControleurHierarchie(GardeAcces garde, GestionnaireHierarchie gestionnaire) {
        this.garde = garde;
        this.gestionnaire = gestionnaire;
    }

    @GetMapping("/hierarchie")
    public ResponseEntity<Object> organigramme(HttpServletRequest requete) {
        GardeAcces.Verdict v = garde.exigerTenant(requete);
        if (v.refuse()) {
            return v.refus();
        }
        return ResponseEntity.ok(gestionnaire.organigramme(v.tenantId()).stream().map(ControleurHierarchie::vue).toList());
    }

    @PutMapping("/employes/{id}/superieur")
    public ResponseEntity<Object> designer(HttpServletRequest requete, @PathVariable String id, @RequestBody(required = false) Map<String, Object> corps) {
        GardeAcces.Verdict v = garde.exigerCapacite(requete, ControleurPersonnel.MODIFIER);
        if (v.refuse()) {
            return v.refus();
        }
        Object brut = corps == null ? null : corps.get("superieurId");
        if (brut != null && !(brut instanceof String)) {
            return refus(400, "« superieurId » : l'identifiant d'une fiche, ou null pour retirer le supérieur");
        }
        try {
            return ResponseEntity.ok(vue(gestionnaire.designer(v.tenantId(), id, (String) brut)));
        } catch (FichesPersonnes.FicheInvalideException e) {
            return refus(400, e.getMessage());
        } catch (IntrouvableException e) {
            return refus(404, e.getMessage());
        }
    }

    static Map<String, Object> vue(AnnuaireEmployes.Fiche f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.id());
        m.put("nom", f.nomComplet());
        m.put("superieurId", f.gestionnaireId());
        return m;
    }

    private static ResponseEntity<Object> refus(int code, String motif) {
        return ResponseEntity.status(code).body(Map.of("errors", List.of(motif)));
    }
}
