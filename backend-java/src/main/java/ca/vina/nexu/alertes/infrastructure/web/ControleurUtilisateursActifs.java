package ca.vina.nexu.alertes.infrastructure.web;

import ca.vina.nexu.alertes.application.requetes.LectureUtilisateursActifs;
import ca.vina.nexu.alertes.domaine.RefusAlerte;
import ca.vina.nexu.alertes.domaine.UtilisateursActifs;
import ca.vina.nexu.partage.export.infrastructure.SerialiseursLiasse;
import ca.vina.nexu.partage.web.GardeAcces;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.CAPACITE_CONFIGURATION;
import static ca.vina.nexu.alertes.infrastructure.web.GardeAlertes.repondre;

/**
 * {@code /api/alertes/utilisateurs-actifs} (ST-G08) — le compte du mois et son export justificatif. Réservé à qui
 * configure les alertes : c'est une donnée de facturation, pas de suivi clinique.
 */
@RestController
@RequestMapping("/api/alertes/utilisateurs-actifs")
public class ControleurUtilisateursActifs {

    private static final int OK = 200;
    private static final String TITRE = "Utilisateurs actifs";

    private final GardeAlertes garde;
    private final LectureUtilisateursActifs lecture;
    private final Clock horloge;

    public ControleurUtilisateursActifs(GardeAlertes garde, LectureUtilisateursActifs lecture, Clock horloge) {
        this.garde = garde;
        this.lecture = lecture;
        this.horloge = horloge;
    }

    /** Sans {@code mois} : le mois en cours, à Toronto. */
    @GetMapping
    public ResponseEntity<Object> bilan(HttpServletRequest r, @RequestParam(required = false) String mois) {
        return repondre(r, garde.capacite(r, CAPACITE_CONFIGURATION), OK, a -> lecture.du(a.tenantId(), moisOuCourant(mois)));
    }

    @GetMapping("/export")
    public ResponseEntity<Object> exporter(HttpServletRequest r, @RequestParam Map<String, String> q) {
        GardeAcces.Verdict v = garde.capacite(r, CAPACITE_CONFIGURATION);
        if (v.refuse()) {
            return v.refus();
        }
        try {
            SaisiesAlertes.FormatExport format = SaisiesAlertes.FormatExport.de(q.getOrDefault("format", "csv"));
            LectureUtilisateursActifs.Bilan b = lecture.du(v.tenantId(), moisOuCourant(q.get("mois")));
            boolean csv = format == SaisiesAlertes.FormatExport.CSV;
            List<Map<String, Object>> lignes = b.actifs().stream().map(x -> ligne(x, csv)).toList();
            SerialiseursLiasse.Liasse liasse = new SerialiseursLiasse.Liasse(TITRE + " — " + b.mois(), Instant.now(horloge).toString(),
                    Map.of("mois", b.mois(), "total", b.total()), List.of(new SerialiseursLiasse.Section(TITRE, lignes)));
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, SerialiseursLiasse.TYPES_MIME.get(format.code()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"utilisateurs-actifs-" + b.mois() + "." + format.code() + "\"")
                    .body(SerialiseursLiasse.serialiser(liasse, format.code()));
        } catch (RefusAlerte e) {
            return GardeAlertes.erreur(GardeAlertes.statutDe(e.nature()), e.getMessage());
        }
    }

    private String moisOuCourant(String mois) {
        return mois == null || mois.isBlank() ? YearMonth.now(horloge.withZone(UtilisateursActifs.FUSEAU)).toString() : mois;
    }

    /** Une ligne du justificatif ; en CSV, les textes saisis sont neutralisés contre l'injection de formule. */
    static Map<String, Object> ligne(UtilisateursActifs.Actif a, boolean csv) {
        Map<String, Object> l = new LinkedHashMap<>();
        l.put("Personne", VuesAlertes.cellule(a.nom(), csv));
        l.put("Nom de connexion", VuesAlertes.cellule(a.personId(), csv));
        l.put("Première tentative", a.premiereTentative());
        l.put("Alerte", VuesAlertes.cellule(a.titre(), csv));
        l.put("Identifiant de l'alerte", a.alerteId());
        return l;
    }
}
