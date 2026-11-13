package ca.vina.nexu.pays.infrastructure.web;

import ca.vina.nexu.partage.web.Bord;
import ca.vina.nexu.pays.application.requetes.CalculsPays;
import ca.vina.nexu.pays.application.requetes.LecturePays;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Catalogue public des pays hôtes (il alimente le sélecteur de la page de connexion, donc il
 * ne demande pas de session) et moteurs génériques par pays.
 *
 * <p>Le contrat est figé par le backend Node : mêmes chemins, mêmes statuts, mêmes messages
 * d'erreur — en français quand ils le sont, en anglais quand ils le sont. Reformuler
 * {@code "unknown country"} serait une régression de contrat, pas une amélioration.
 */
@RestController
@RequestMapping("/api/countries")
public class ControleurPays {

    private final LecturePays lecture;
    private final CalculsPays calculs;

    public ControleurPays(LecturePays lecture, CalculsPays calculs) {
        this.lecture = lecture;
        this.calculs = calculs;
    }

    @GetMapping
    public Map<String, Object> catalogue() {
        return lecture.catalogue();
    }

    @GetMapping("/{code}")
    public ResponseEntity<Object> detail(@PathVariable String code) {
        return Bord.publique(() -> lecture.detail(code));
    }

    @GetMapping("/{code}/sales-tax")
    public ResponseEntity<Object> taxeVente(@PathVariable String code, @RequestParam(required = false) String amount) {
        return Bord.publique(() -> lecture.taxeDeVente(code, amount));
    }

    @PostMapping("/{code}/payroll")
    public ResponseEntity<Object> paie(@PathVariable String code, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.publique(() -> calculs.paie(code, Bord.corps(corps)));
    }

    @PostMapping("/{code}/income-tax")
    public ResponseEntity<Object> impot(@PathVariable String code, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.publique(() -> calculs.impot(code, Bord.corps(corps)));
    }

    @PostMapping("/{code}/remittance")
    public ResponseEntity<Object> remise(@PathVariable String code, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.publique(() -> calculs.remise(code, Bord.corps(corps)));
    }

    @PostMapping("/{code}/official-remittance")
    public ResponseEntity<Object> remiseOfficielle(@PathVariable String code, @RequestBody(required = false) Map<String, Object> corps) {
        return Bord.publique(() -> calculs.remiseOfficielle(code, Bord.corps(corps)));
    }
}
